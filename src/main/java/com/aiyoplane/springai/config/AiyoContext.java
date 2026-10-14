/*
 * Copyright 2026 Aiyoplane Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.aiyoplane.springai.config;

import com.aiyoplane.springai.receipt.ExecutionReceipt;

/**
 * Thread-local access to the Execution Receipt bound to the currently executing {@code @Tool}
 * method. The receipt is set by {@link com.aiyoplane.springai.callback.AiyoGatedToolCallback}
 * before the underlying method body runs and cleared after it returns.
 *
 * <p>Use this when the tool method needs to record the receipt digest in a side system
 * (database, external audit log) alongside the real-world action it performs.
 */
public final class AiyoContext {

    private static final ThreadLocal<ExecutionReceipt> CURRENT = new ThreadLocal<>();

    private AiyoContext() {}

    public static ExecutionReceipt currentReceipt() {
        return CURRENT.get();
    }

    public static void setCurrentReceipt(ExecutionReceipt receipt) {
        if (receipt == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(receipt);
        }
    }

    public static void clear() {
        CURRENT.remove();
    }
}
