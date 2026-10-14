/*
 * Copyright 2026 Aiyoplane, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.aiyoplane.springai.rdp;

import java.util.Map;
import java.util.Objects;

/**
 * Stub for the hosted Aiyoplane RDP. Published in 1.0.0 so host apps can wire the production
 * bean against a stable factory signature; the hosted endpoint lights up in a point release
 * without any host-app change.
 */
final class HostedRuntimeDecisionPoint implements RuntimeDecisionPoint {

    @SuppressWarnings("unused")
    private final String apiKey;

    HostedRuntimeDecisionPoint(String apiKey) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
    }

    @Override
    public Decision evaluate(String toolName, Map<String, Object> intent, String callerId) {
        throw new UnsupportedOperationException(
                "HostedRuntimeDecisionPoint is a stub in 1.0.0; use createLocalRdp(...) in the "
                + "meantime or follow https://aiyoplane.com/trust for the hosted-endpoint rollout.");
    }
}
