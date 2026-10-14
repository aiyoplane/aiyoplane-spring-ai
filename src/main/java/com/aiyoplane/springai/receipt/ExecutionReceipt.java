/*
 * Copyright 2026 Aiyoplane Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.aiyoplane.springai.receipt;

import com.aiyoplane.springai.rdp.Verdict;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A signed Execution Receipt binding an authorized tool call to the caller, the policy that
 * admitted it, and the exact arguments it was admitted for.
 *
 * <p>Canonical form is RFC 8785 JCS over the ordered payload below. The signature covers that
 * canonical form. Signature algorithm: Ed25519.
 *
 * <pre>
 * {
 *   "v": 1,
 *   "callerId": "...",
 *   "toolName": "...",
 *   "argsDigest": "sha256:...",
 *   "action": "..." | null,
 *   "policyRef": "..." | null,
 *   "verdict": "ALLOW",
 *   "timestampMs": 1760000000000
 * }
 * </pre>
 *
 * <p>{@code digest} is a stable short identifier (base64url of SHA-256 over the canonical
 * payload) suitable for single-use idempotency keys and for exception-carried reference.
 */
public record ExecutionReceipt(
        int version,
        String callerId,
        String toolName,
        String argsDigest,
        String action,
        String policyRef,
        Verdict verdict,
        long timestampMs,
        String signatureB64,
        String digest
) {

    public static Map<String, Object> payload(
            String callerId, String toolName, String argsDigest,
            String action, String policyRef, Verdict verdict, long timestampMs) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("v", 1);
        m.put("callerId", callerId);
        m.put("toolName", toolName);
        m.put("argsDigest", argsDigest);
        m.put("action", action);
        m.put("policyRef", policyRef);
        m.put("verdict", verdict.name());
        m.put("timestampMs", timestampMs);
        return m;
    }

    public String toCanonicalJson() {
        return Jcs.canonicalize(payload(callerId, toolName, argsDigest, action, policyRef, verdict, timestampMs));
    }

    public byte[] signatureBytes() {
        return Base64.getUrlDecoder().decode(signatureB64);
    }
}
