/*
 * Copyright 2026 Aiyoplane Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.aiyoplane.springai.exception;

/**
 * Thrown when the Runtime Decision Point returns {@code BLOCK}. The named policy rule and
 * reason are surfaced so the model and the host app can both render a precise refusal
 * without leaking policy internals.
 *
 * <p>This exception is a hard refusal: the underlying tool method did not run and will not
 * be retried under the same policy. Catch at the controller / advisor layer and translate
 * to a tool-result that the model can incorporate without re-trying the call.
 */
public class BlockedByPolicy extends RuntimeException {

    private final String toolName;
    private final String policyRef;

    public BlockedByPolicy(String toolName, String policyRef, String reason) {
        super(reason == null ? "blocked by policy" : reason);
        this.toolName = toolName;
        this.policyRef = policyRef;
    }

    public String toolName() {
        return toolName;
    }

    public String policyRef() {
        return policyRef;
    }
}
