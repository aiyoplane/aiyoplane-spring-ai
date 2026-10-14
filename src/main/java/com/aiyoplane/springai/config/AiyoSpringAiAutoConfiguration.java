/*
 * Copyright 2026 Aiyoplane, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.aiyoplane.springai.config;

import com.aiyoplane.springai.intent.IntentBuilder;
import com.aiyoplane.springai.rdp.RuntimeDecisionPoint;
import com.aiyoplane.springai.receipt.ReceiptSigner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Spring Boot auto-configuration for aiyoplane-spring-ai. Every bean here is defaulted and
 * overridable: a host app declares its own {@link RuntimeDecisionPoint}, {@link ReceiptSigner},
 * or {@code Map<String, IntentBuilder>} to replace the defaults.
 *
 * <p>The default RDP is a local in-process one reading a YAML policy from
 * {@code aiyoplane.spring-ai.policy-path} (default: {@code classpath:aiyo-policy.yaml}).
 * The default signer generates a fresh in-process keypair — fine for development, but
 * production deployments MUST supply their own {@link ReceiptSigner} bean loaded from a
 * durable keypair.
 */
@Configuration
public class AiyoSpringAiAutoConfiguration {

    @Value("${aiyoplane.spring-ai.policy-path:classpath:aiyo-policy.yaml}")
    private String policyPath;

    @Bean
    @ConditionalOnMissingBean
    public RuntimeDecisionPoint aiyoRuntimeDecisionPoint(ResourceLoader resourceLoader) throws IOException {
        Resource resource = resourceLoader.getResource(policyPath);
        if (!resource.exists()) {
            return RuntimeDecisionPoint.createLocalRdp((InputStream) null);
        }
        try (InputStream in = resource.getInputStream()) {
            return RuntimeDecisionPoint.createLocalRdp(in);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public ReceiptSigner aiyoReceiptSigner() {
        return ReceiptSigner.generate();
    }

    /**
     * Default empty registry. Host apps declare a {@code Map<String, IntentBuilder>} bean
     * (keyed by Spring AI tool name) to translate raw tool args into AEAP Intent payloads.
     * Tools without an entry fall back to {@code @AiyoGated.actionType} + raw args.
     */
    @Bean
    @ConditionalOnMissingBean(name = "aiyoIntentBuilders")
    public Map<String, IntentBuilder> aiyoIntentBuilders() {
        return Map.of();
    }
}
