/*
 * Copyright 2026 Aiyoplane, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.aiyoplane.springai.rdp;

import java.io.InputStream;
import java.util.Map;

/**
 * The AEAP Runtime Decision Point. Implementations evaluate one proposed tool call against
 * a policy and return a {@link Decision}.
 *
 * <p>Static factory methods mirror the Python packages' public API shape:
 * {@link #createLocalRdp(Map)} / {@link #createLocalRdp(InputStream)} parallel
 * {@code create_local_rdp(policy=...)}; {@link #createHostedRdp(String)} parallels
 * {@code create_hosted_rdp(api_key=...)}.
 *
 * <p>Implementations MUST be deterministic for the same (toolName, intent, callerId) tuple
 * within a single policy version. They SHOULD fail closed: on evaluation error, return
 * {@link Decision#block(String, String)} rather than throwing. Throwing is reserved for
 * programmer errors (bad policy file, missing key).
 *
 * <p>Implementations MUST NOT execute the tool. The RDP produces a decision; the
 * {@link com.aiyoplane.springai.callback.AiyoGatedToolCallback} enforces it.
 */
public interface RuntimeDecisionPoint {

    /**
     * Evaluate one proposed tool call.
     *
     * @param toolName  the fully-qualified Spring AI tool name
     * @param intent    the AEAP Intent payload (built from raw args by an {@code IntentBuilder});
     *                  conventionally contains a {@code type} key equal to the policy's
     *                  {@code action_type}
     * @param callerId  the identity attempting the call; for Spring Security apps this is
     *                  typically {@code SecurityContextHolder.getContext().getAuthentication().getName()}
     * @return a non-null Decision
     */
    Decision evaluate(String toolName, Map<String, Object> intent, String callerId);

    /**
     * Create a local in-process RDP that evaluates a Java-map policy document. The policy
     * schema matches every other AEAP adapter: a {@code rules} list of {@code action_type} /
     * {@code effect} entries with optional conditional constraints such as {@code amount_max}.
     *
     * <p>Example:
     * <pre>
     * Map&lt;String, Object&gt; policy = Map.of(
     *     "rules", List.of(
     *         Map.of("action_type", "payment", "amount_max", 1_000, "effect", "allow"),
     *         Map.of("action_type", "payment", "amount_max", 10_000, "effect", "escalate"),
     *         Map.of("action_type", "payment", "effect", "block")
     *     )
     * );
     * RuntimeDecisionPoint rdp = RuntimeDecisionPoint.createLocalRdp(policy);
     * </pre>
     */
    static RuntimeDecisionPoint createLocalRdp(Map<String, Object> policy) {
        return new LocalRuntimeDecisionPoint(policy);
    }

    /**
     * Create a local in-process RDP that reads a YAML policy document from the supplied
     * stream. See {@link LocalRuntimeDecisionPoint} for the schema.
     */
    static RuntimeDecisionPoint createLocalRdp(InputStream yamlPolicy) {
        return new LocalRuntimeDecisionPoint(yamlPolicy);
    }

    /**
     * Create an RDP backed by the hosted Aiyoplane evaluation service. Intended for
     * production. The API key authenticates to the hosted service, which stores and
     * evaluates the policy document outside the host app process.
     *
     * <p>In this release the hosted RDP is a stub that throws {@link UnsupportedOperationException}
     * on evaluation; the hosted endpoint is coordinated through {@code aiyoplane.com/trust}.
     * The method is published now so host apps can wire the production bean against a stable
     * factory signature; the implementation will light up without any host-app change.
     */
    static RuntimeDecisionPoint createHostedRdp(String apiKey) {
        return new HostedRuntimeDecisionPoint(apiKey);
    }
}
