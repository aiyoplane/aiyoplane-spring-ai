/*
 * Copyright 2026 Aiyoplane, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.aiyoplane.springai;

import com.aiyoplane.springai.annotation.AiyoGated;
import com.aiyoplane.springai.callback.AiyoGatedToolCallback;
import com.aiyoplane.springai.config.AiyoContext;
import com.aiyoplane.springai.exception.BlockedByPolicy;
import com.aiyoplane.springai.exception.EscalationRequired;
import com.aiyoplane.springai.intent.IntentBuilder;
import com.aiyoplane.springai.rdp.RuntimeDecisionPoint;
import com.aiyoplane.springai.rdp.Verdict;
import com.aiyoplane.springai.receipt.ExecutionReceipt;
import com.aiyoplane.springai.receipt.ReceiptSigner;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class AiyoGatedToolCallbackTest {

    private static final String POLICY_YAML = """
            default: allow
            rules:
              - id: book-mutation-escalate
                action_type: book_mutation
                effect: escalate
                reason: destructive book operations require human approval
              - id: schema-drop-block
                action_type: schema_drop
                effect: block
                reason: schema operations are never permitted from an agent
              - id: small-payment-allow
                action_type: payment
                amount_max: 1000
                effect: allow
              - id: big-payment-block
                action_type: payment
                effect: block
                reason: payment above the per-rule ceiling
            """;

    private RuntimeDecisionPoint rdp() {
        return RuntimeDecisionPoint.createLocalRdp(
                new ByteArrayInputStream(POLICY_YAML.getBytes(StandardCharsets.UTF_8)));
    }

    private AiyoGated gatedAnnotation(String actionType, String template) {
        return new AiyoGated() {
            @Override public Class<? extends Annotation> annotationType() { return AiyoGated.class; }
            @Override public String actionType() { return actionType; }
            @Override public String reasonTemplate() { return template; }
        };
    }

    @Test
    void allowRunsUnderlyingAndBindsReceipt() {
        AtomicBoolean underlyingRan = new AtomicBoolean(false);
        AiyoGatedToolCallback cb = new AiyoGatedToolCallback(
                "getBookTitle", gatedAnnotation("", ""), null, rdp(), ReceiptSigner.generate(), () -> "alice");
        Object out = cb.call("{\"bookId\":\"42\"}", () -> {
            underlyingRan.set(true);
            ExecutionReceipt r = AiyoContext.currentReceipt();
            assertNotNull(r, "receipt should be bound to the thread during underlying execution");
            assertEquals(Verdict.ALLOW, r.verdict());
            assertEquals("getBookTitle", r.toolName());
            return "ok";
        });
        assertEquals("ok", out);
        assertTrue(underlyingRan.get());
        assertNull(AiyoContext.currentReceipt(), "receipt must be cleared after underlying returns");
    }

    @Test
    void escalateDoesNotRunUnderlyingAndThrowsWithPendingDigest() {
        AtomicBoolean underlyingRan = new AtomicBoolean(false);
        AiyoGatedToolCallback cb = new AiyoGatedToolCallback(
                "deleteBookById",
                gatedAnnotation("book_mutation", "Delete book {bookId}?"),
                null, rdp(), ReceiptSigner.generate(), () -> "alice");
        EscalationRequired ex = assertThrows(EscalationRequired.class,
                () -> cb.call("{\"bookId\":\"42\"}", () -> { underlyingRan.set(true); return "ran"; }));
        assertFalse(underlyingRan.get(), "underlying must not run on ESCALATE");
        assertEquals("deleteBookById", ex.toolName());
        assertEquals("book-mutation-escalate", ex.policyRef());
        assertNotNull(ex.pendingDigest(), "digest must be present so caller can bind approval");
        assertEquals("Delete book 42?", ex.getMessage());
    }

    @Test
    void blockDoesNotRunUnderlyingAndThrowsBlockedByPolicy() {
        AtomicBoolean underlyingRan = new AtomicBoolean(false);
        AiyoGatedToolCallback cb = new AiyoGatedToolCallback(
                "dropSchema", gatedAnnotation("schema_drop", ""), null, rdp(), ReceiptSigner.generate(), () -> "alice");
        BlockedByPolicy ex = assertThrows(BlockedByPolicy.class,
                () -> cb.call("{}", () -> { underlyingRan.set(true); return "ran"; }));
        assertFalse(underlyingRan.get(), "underlying must not run on BLOCK");
        assertEquals("schema-drop-block", ex.policyRef());
    }

    @Test
    void intentBuilderDrivesConditionalPolicyMatch() {
        IntentBuilder paymentIntent = args -> Map.of(
                "type", "payment",
                "amount", args.get("amount"),
                "target", args.get("to_account")
        );
        AiyoGatedToolCallback small = new AiyoGatedToolCallback(
                "transfer_funds", gatedAnnotation("payment", ""),
                paymentIntent, rdp(), ReceiptSigner.generate(), () -> "alice");
        AtomicBoolean ran = new AtomicBoolean(false);
        small.call("{\"amount\":500,\"to_account\":\"acme\"}", () -> { ran.set(true); return "ok"; });
        assertTrue(ran.get(), "500 <= amount_max 1000 should allow");

        AiyoGatedToolCallback large = new AiyoGatedToolCallback(
                "transfer_funds", gatedAnnotation("payment", ""),
                paymentIntent, rdp(), ReceiptSigner.generate(), () -> "alice");
        AtomicBoolean ranBig = new AtomicBoolean(false);
        BlockedByPolicy ex = assertThrows(BlockedByPolicy.class,
                () -> large.call("{\"amount\":9999,\"to_account\":\"acme\"}",
                        () -> { ranBig.set(true); return "ok"; }));
        assertFalse(ranBig.get(), "9999 > amount_max should block");
        assertEquals("big-payment-block", ex.policyRef());
    }

    @Test
    void receiptSignatureVerifies() {
        ReceiptSigner signer = ReceiptSigner.generate();
        AiyoGatedToolCallback cb = new AiyoGatedToolCallback(
                "getBookTitle", gatedAnnotation("", ""), null, rdp(), signer, () -> "alice");
        cb.call("{\"bookId\":\"42\"}", () -> {
            ExecutionReceipt r = AiyoContext.currentReceipt();
            assertTrue(signer.verify(r), "signer must verify its own receipt");
            return null;
        });
    }

    @Test
    void mapPolicyFactoryWorks() {
        RuntimeDecisionPoint rdp = RuntimeDecisionPoint.createLocalRdp(Map.of(
                "default", "block",
                "rules", List.of(
                        Map.of("action_type", "safe", "effect", "allow")
                )
        ));
        AiyoGatedToolCallback cb = new AiyoGatedToolCallback(
                "any", gatedAnnotation("safe", ""), null, rdp, ReceiptSigner.generate(), () -> "alice");
        assertEquals("ok", cb.call("{}", () -> "ok"));

        AiyoGatedToolCallback blocked = new AiyoGatedToolCallback(
                "any", gatedAnnotation("not-in-policy", ""), null, rdp, ReceiptSigner.generate(), () -> "alice");
        assertThrows(BlockedByPolicy.class, () -> blocked.call("{}", () -> "ok"));
    }
}
