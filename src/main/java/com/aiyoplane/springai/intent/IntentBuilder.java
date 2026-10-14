/*
 * Copyright 2026 Aiyoplane, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.aiyoplane.springai.intent;

import java.util.Map;

/**
 * Converts a tool's raw arguments into the AEAP Intent payload the Runtime Decision Point
 * evaluates. This mirrors the {@code intent_builder=lambda args: {...}} parameter used by
 * every Python AEAP adapter (langchain, crewai, microsoft-agent-framework, etc.).
 *
 * <p>Example — translate a {@code transfer_funds(amount, to_account)} tool call into a
 * payment-shaped Intent that policy rules can match on {@code action_type}, {@code amount},
 * and {@code target}:
 *
 * <pre>
 * IntentBuilder transferIntent = args -&gt; Map.of(
 *     "type", "payment",
 *     "amount", args.get("amount"),
 *     "target", args.get("to_account")
 * );
 * </pre>
 *
 * <p>If no {@code IntentBuilder} is registered for a tool, the adapter passes the raw args as
 * the Intent, enriched with a {@code type} field taken from the {@code @AiyoGated.actionType}
 * annotation attribute. That default is adequate for simple policies that match purely on
 * action type.
 */
@FunctionalInterface
public interface IntentBuilder {

    /**
     * Build the AEAP Intent payload for one invocation of a gated tool.
     *
     * @param args the raw tool arguments as parsed from the Spring AI JSON input
     * @return a JSON-serialisable map; MUST include a {@code type} key
     */
    Map<String, Object> build(Map<String, Object> args);
}
