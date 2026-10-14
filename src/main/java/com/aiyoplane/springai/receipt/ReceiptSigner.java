/*
 * Copyright 2026 Aiyoplane Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.aiyoplane.springai.receipt;

import com.aiyoplane.springai.rdp.Verdict;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Base64;
import java.util.Map;

/**
 * Ed25519 signer / verifier for {@link ExecutionReceipt}. Uses the JDK's built-in
 * {@code EdDSA} provider (JEP 339, Java 15+).
 */
public final class ReceiptSigner {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public ReceiptSigner(PrivateKey privateKey, PublicKey publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    /** Generates a fresh Ed25519 keypair for in-process signing. Intended for tests and local use. */
    public static ReceiptSigner generate() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            KeyPair kp = kpg.generateKeyPair();
            return new ReceiptSigner(kp.getPrivate(), kp.getPublic());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Ed25519 not available in this JVM; need Java 15+", e);
        }
    }

    public PublicKey publicKey() {
        return publicKey;
    }

    public ExecutionReceipt sign(
            String callerId,
            String toolName,
            Map<String, Object> args,
            String action,
            String policyRef,
            Verdict verdict,
            long timestampMs) {
        String argsCanonical = Jcs.canonicalize(args == null ? Map.of() : args);
        String argsDigest = "sha256:" + base64UrlNoPad(sha256(argsCanonical.getBytes(StandardCharsets.UTF_8)));
        Map<String, Object> payload = ExecutionReceipt.payload(
                callerId, toolName, argsDigest, action, policyRef, verdict, timestampMs);
        String canonical = Jcs.canonicalize(payload);
        byte[] canonicalBytes = canonical.getBytes(StandardCharsets.UTF_8);
        byte[] signature = sign(canonicalBytes);
        String digest = base64UrlNoPad(sha256(canonicalBytes));
        return new ExecutionReceipt(
                1, callerId, toolName, argsDigest, action, policyRef, verdict, timestampMs,
                Base64.getUrlEncoder().withoutPadding().encodeToString(signature),
                digest
        );
    }

    public boolean verify(ExecutionReceipt receipt) {
        return verify(receipt, publicKey);
    }

    public static boolean verify(ExecutionReceipt receipt, PublicKey publicKey) {
        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(publicKey);
            sig.update(receipt.toCanonicalJson().getBytes(StandardCharsets.UTF_8));
            return sig.verify(receipt.signatureBytes());
        } catch (NoSuchAlgorithmException | SignatureException | java.security.InvalidKeyException e) {
            return false;
        }
    }

    private byte[] sign(byte[] message) {
        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(privateKey);
            sig.update(message);
            return sig.sign();
        } catch (NoSuchAlgorithmException | SignatureException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("Ed25519 sign failed", e);
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 missing", e);
        }
    }

    private static String base64UrlNoPad(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
