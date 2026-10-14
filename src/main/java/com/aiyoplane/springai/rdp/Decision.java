/*
 * Copyright 2026 Aiyoplane Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.aiyoplane.springai.rdp;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * The authoritative output of a {@link RuntimeDecisionPoint}. One Decision describes exactly
 * one proposed tool call.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code verdict} — ALLOW / ESCALATE / BLOCK. Never null.</li>
 *   <li>{@code reason} — short human-readable string. For BLOCK and ESCALATE, this is surfaced
 *       in the thrown exception. For ALLOW it is typically null; a policy that returns a reason
 *       on ALLOW is signalling policy-ref provenance, not a warning.</li>
 *   <li>{@code policyRef} — stable identifier for the rule that produced this decision. Used
 *       to join the receipt back to the policy document for audit. May be null.</li>
 *   <li>{@code metadata} — arbitrary string-keyed data the RDP wants to attach to the receipt.
 *       Must be JSON-serialisable. Never null (empty map is fine).</li>
 * </ul>
 */
public record Decision(Verdict verdict, String reason, String policyRef, Map<String, Object> metadata) {

    public Decision {
        Objects.requireNonNull(verdict, "verdict");
        metadata = (metadata == null) ? Map.of() : Collections.unmodifiableMap(metadata);
    }

    public static Decision allow() {
        return new Decision(Verdict.ALLOW, null, null, Map.of());
    }

    public static Decision allow(String policyRef) {
        return new Decision(Verdict.ALLOW, null, policyRef, Map.of());
    }

    public static Decision escalate(String reason, String policyRef) {
        return new Decision(Verdict.ESCALATE, reason, policyRef, Map.of());
    }

    public static Decision block(String reason, String policyRef) {
        return new Decision(Verdict.BLOCK, reason, policyRef, Map.of());
    }
}
