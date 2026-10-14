/*
 * Copyright 2026 Aiyoplane, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.aiyoplane.springai.callback;

import com.aiyoplane.springai.annotation.AiyoGated;
import com.aiyoplane.springai.config.AiyoContext;
import com.aiyoplane.springai.exception.BlockedByPolicy;
import com.aiyoplane.springai.exception.EscalationRequired;
import com.aiyoplane.springai.intent.IntentBuilder;
import com.aiyoplane.springai.rdp.Decision;
import com.aiyoplane.springai.rdp.RuntimeDecisionPoint;
import com.aiyoplane.springai.rdp.Verdict;
import com.aiyoplane.springai.receipt.ExecutionReceipt;
import com.aiyoplane.springai.receipt.ReceiptSigner;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Composition Boundary enforcement for a single Spring AI tool. Wraps an underlying
 * {@code ToolCallback} (framework type invoked via a {@link Supplier} so this package also
 * compiles against future Spring AI versions where the exact type path may evolve).
 *
 * <p>Life of a call:
 * <ol>
 *   <li>Parse arguments from the Spring AI JSON input.</li>
 *   <li>Build the Intent payload via the registered {@link IntentBuilder} (or a default
 *       builder that wraps raw args with the {@code @AiyoGated.actionType} value).</li>
 *   <li>Ask the {@link RuntimeDecisionPoint} for a {@link Decision}.</li>
 *   <li>On {@link Verdict#BLOCK} — throw {@link BlockedByPolicy}; underlying method never runs.</li>
 *   <li>On {@link Verdict#ESCALATE} — sign a pending receipt, throw
 *       {@link EscalationRequired} carrying its digest; underlying method never runs.</li>
 *   <li>On {@link Verdict#ALLOW} — sign the receipt, bind it to {@link AiyoContext}, invoke
 *       the underlying callback, clear the context, return the result.</li>
 * </ol>
 */
public final class AiyoGatedToolCallback {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final String toolName;
    private final AiyoGated annotation;
    private final IntentBuilder intentBuilder;
    private final RuntimeDecisionPoint rdp;
    private final ReceiptSigner signer;
    private final Supplier<String> callerIdSupplier;

    public AiyoGatedToolCallback(
            String toolName,
            AiyoGated annotation,
            IntentBuilder intentBuilder,
            RuntimeDecisionPoint rdp,
            ReceiptSigner signer,
            Supplier<String> callerIdSupplier) {
        this.toolName = toolName;
        this.annotation = annotation;
        this.intentBuilder = intentBuilder;
        this.rdp = rdp;
        this.signer = signer;
        this.callerIdSupplier = callerIdSupplier;
    }

    /**
     * Evaluate the proposed call, enforce the verdict, and (on ALLOW) run {@code underlying}
     * with the receipt bound to the thread-local {@link AiyoContext}.
     *
     * @param toolInput  the raw Spring AI tool input (JSON string of the argument object)
     * @param underlying a lambda that invokes the real underlying tool and returns its result
     * @return the underlying tool's result, verbatim
     */
    public Object call(String toolInput, Supplier<Object> underlying) {
        Map<String, Object> args = parseArgs(toolInput);
        String actionType = annotation == null || annotation.actionType().isEmpty()
                ? null : annotation.actionType();
        Map<String, Object> intent = buildIntent(args, actionType);
        String callerId = callerIdSupplier == null ? null : callerIdSupplier.get();
        if (callerId == null) callerId = "anonymous";

        Decision decision = rdp.evaluate(toolName, intent, callerId);
        if (decision == null) {
            throw new BlockedByPolicy(toolName, null, "RDP returned null decision; failing closed");
        }

        long now = System.currentTimeMillis();
        ExecutionReceipt receipt = signer.sign(
                callerId, toolName, args, actionType, decision.policyRef(), decision.verdict(), now);

        switch (decision.verdict()) {
            case BLOCK -> throw new BlockedByPolicy(toolName, decision.policyRef(), decision.reason());
            case ESCALATE -> throw new EscalationRequired(
                    toolName, decision.policyRef(), receipt.digest(),
                    renderReason(decision.reason(), args));
            case ALLOW -> {
                AiyoContext.setCurrentReceipt(receipt);
                try {
                    return underlying.get();
                } finally {
                    AiyoContext.clear();
                }
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private Map<String, Object> buildIntent(Map<String, Object> args, String actionType) {
        if (intentBuilder != null) {
            Map<String, Object> built = intentBuilder.build(args);
            return built == null ? Map.of() : built;
        }
        Map<String, Object> defaultIntent = new LinkedHashMap<>();
        if (actionType != null) defaultIntent.put("type", actionType);
        defaultIntent.putAll(args);
        return defaultIntent;
    }

    private Map<String, Object> parseArgs(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) return Map.of();
        try {
            Map<String, Object> parsed = MAPPER.readValue(toolInput, MAP_TYPE);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String renderReason(String base, Map<String, Object> args) {
        String template = annotation == null ? null : annotation.reasonTemplate();
        if (template == null || template.isEmpty()) {
            return base == null ? "approval required" : base;
        }
        String rendered = template;
        for (Map.Entry<String, Object> e : args.entrySet()) {
            rendered = rendered.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
        }
        return rendered;
    }
}
