/*
 * Copyright 2026 Aiyoplane, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.aiyoplane.springai.rdp;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * In-process RDP that evaluates a policy document shaped exactly like every other AEAP
 * adapter (langchain, crewai, microsoft-agent-framework, etc.).
 *
 * <p>Policy schema (v1):
 * <pre>
 * default: allow                   # applies when no rule matches; allow / escalate / block
 * rules:
 *   - id: payment-small             # optional stable id used as policyRef
 *     action_type: payment          # match the Intent's "type" (required for the rule to match)
 *     amount_max: 1000              # optional conditional constraint on Intent.amount
 *     effect: allow                 # allow / escalate / block
 *     reason: small payment auto-approved
 *   - action_type: payment
 *     amount_max: 10000
 *     effect: escalate
 *   - action_type: payment
 *     effect: block
 *     reason: payments over the per-rule ceiling are refused
 * </pre>
 *
 * <p>Rules are evaluated in document order; the first rule where every declared constraint
 * matches wins. An omitted field is a wildcard. {@code amount_max} matches when the Intent's
 * {@code amount} is less than or equal to the declared ceiling.
 */
final class LocalRuntimeDecisionPoint implements RuntimeDecisionPoint {

    private final Verdict defaultEffect;
    private final List<Rule> rules;

    LocalRuntimeDecisionPoint(InputStream yamlPolicy) {
        this((Object) (yamlPolicy == null ? null : new Yaml().<Object>load(yamlPolicy)));
    }

    @SuppressWarnings("unchecked")
    LocalRuntimeDecisionPoint(Object policyDoc) {
        if (!(policyDoc instanceof Map<?, ?> doc)) {
            this.defaultEffect = Verdict.ALLOW;
            this.rules = List.of();
            return;
        }
        Object defaultValue = doc.get("default");
        this.defaultEffect = parseEffect(defaultValue == null ? "allow" : defaultValue.toString());
        List<Rule> parsed = new ArrayList<>();
        Object rawRules = doc.get("rules");
        if (rawRules instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> m) {
                    parsed.add(Rule.fromMap((Map<String, Object>) m));
                }
            }
        }
        this.rules = List.copyOf(parsed);
    }

    @Override
    public Decision evaluate(String toolName, Map<String, Object> intent, String callerId) {
        Map<String, Object> safeIntent = intent == null ? Map.of() : intent;
        for (Rule rule : rules) {
            if (rule.matches(safeIntent)) {
                return new Decision(rule.effect, rule.reason, rule.id, Map.of());
            }
        }
        return switch (defaultEffect) {
            case ALLOW -> Decision.allow("default");
            case ESCALATE -> Decision.escalate("no matching rule; default is escalate", "default");
            case BLOCK -> Decision.block("no matching rule; default is block", "default");
        };
    }

    private static Verdict parseEffect(String s) {
        return switch (s.trim().toLowerCase()) {
            case "allow" -> Verdict.ALLOW;
            case "escalate" -> Verdict.ESCALATE;
            case "block" -> Verdict.BLOCK;
            default -> throw new IllegalArgumentException("unknown effect: " + s);
        };
    }

    private record Rule(String id, String actionType, Double amountMax, Verdict effect, String reason) {
        static Rule fromMap(Map<String, Object> m) {
            Object id = m.get("id");
            Object actionType = m.get("action_type");
            Object amountMax = m.get("amount_max");
            Object effect = m.get("effect");
            Object reason = m.get("reason");
            if (effect == null) {
                throw new IllegalArgumentException("rule is missing effect: " + m);
            }
            Double parsedAmount = null;
            if (amountMax instanceof Number n) {
                parsedAmount = n.doubleValue();
            }
            return new Rule(
                    id == null ? null : id.toString(),
                    actionType == null ? null : actionType.toString(),
                    parsedAmount,
                    parseEffect(effect.toString()),
                    reason == null ? null : reason.toString()
            );
        }

        boolean matches(Map<String, Object> intent) {
            if (actionType != null) {
                Object intentType = intent.get("type");
                if (!actionType.equals(intentType == null ? null : intentType.toString())) {
                    return false;
                }
            }
            if (amountMax != null) {
                Object amount = intent.get("amount");
                if (!(amount instanceof Number n)) return false;
                if (n.doubleValue() > amountMax) return false;
            }
            return true;
        }
    }
}
