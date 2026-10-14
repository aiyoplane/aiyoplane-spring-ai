/*
 * Copyright 2026 Aiyoplane Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.aiyoplane.springai.exception;

/**
 * Thrown when the Runtime Decision Point returns {@code ESCALATE}. Carries the pending
 * receipt digest (bound to the exact tool call and args that would have run) so that a
 * subsequent run that redeems the approval cannot silently apply it to a different call.
 *
 * <p>Catch this at your controller / advisor layer, route to whatever approval UI you have
 * (your {@code internalToolExecutionEnabled=false} loop, Slack handoff, web modal), and when
 * the human approves, re-run the chat turn. The policy will re-evaluate; if the approval
 * context recorded by the host app satisfies the policy, the second run returns ALLOW.
 */
public class EscalationRequired extends RuntimeException {

    private final String toolName;
    private final String policyRef;
    private final String pendingDigest;

    public EscalationRequired(String toolName, String policyRef, String pendingDigest, String reason) {
        super(reason == null ? "escalation required" : reason);
        this.toolName = toolName;
        this.policyRef = policyRef;
        this.pendingDigest = pendingDigest;
    }

    public String toolName() {
        return toolName;
    }

    public String policyRef() {
        return policyRef;
    }

    /**
     * The SHA-256 digest (base64url, no padding) of the canonical pending receipt. Suitable
     * for use as the correlation id between the pre-approval request and the post-approval
     * resume.
     */
    public String pendingDigest() {
        return pendingDigest;
    }
}
