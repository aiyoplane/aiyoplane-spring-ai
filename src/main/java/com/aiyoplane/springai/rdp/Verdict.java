/*
 * Copyright 2026 Aiyoplane Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.aiyoplane.springai.rdp;

/**
 * The three AEAP Runtime Decision Point verdicts.
 *
 * <ul>
 *   <li>{@link #ALLOW} — the tool call proceeds. A signed Execution Receipt is produced.</li>
 *   <li>{@link #ESCALATE} — the tool call does not proceed; a human must approve first. Caller
 *       receives an {@link com.aiyoplane.springai.exception.EscalationRequired} carrying the
 *       pending receipt digest, which can be redeemed on a subsequent run after approval.</li>
 *   <li>{@link #BLOCK} — the tool call is refused outright. Caller receives a
 *       {@link com.aiyoplane.springai.exception.BlockedByPolicy} naming the policy rule.</li>
 * </ul>
 *
 * Fail-closed invariant: in the absence of a Decision, callers MUST NOT execute the tool.
 */
public enum Verdict {
    ALLOW,
    ESCALATE,
    BLOCK
}
