/*
 * Copyright 2026 Aiyoplane Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.aiyoplane.springai.receipt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Minimal RFC 8785 (JSON Canonicalization Scheme) implementation sufficient for the receipt
 * payload shape used here.
 *
 * <p>Supported inputs: {@link Map}, {@link Collection}, {@link String}, {@link Boolean},
 * numeric types (serialised with Java {@code toString()} after narrowing), and {@code null}.
 *
 * <p>Object keys are sorted lexicographically by UTF-16 code unit. Strings are escaped with
 * minimal JSON escapes (control characters, {@code \"}, {@code \\}). This library does not
 * include every edge case in RFC 8785 (notably non-finite doubles, which it rejects).
 */
public final class Jcs {

    private Jcs() {}

    public static String canonicalize(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Map<?, ?> map) {
            writeObject(sb, map);
        } else if (v instanceof Collection<?> coll) {
            writeArray(sb, coll);
        } else if (v instanceof String s) {
            writeString(sb, s);
        } else if (v instanceof Boolean b) {
            sb.append(b ? "true" : "false");
        } else if (v instanceof Number n) {
            writeNumber(sb, n);
        } else {
            // Fallback: treat as string. Keeps the library robust in the face of odd argument
            // shapes without silently reinterpreting them.
            writeString(sb, v.toString());
        }
    }

    private static void writeObject(StringBuilder sb, Map<?, ?> map) {
        TreeMap<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            sorted.put(String.valueOf(e.getKey()), e.getValue());
        }
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : sorted.entrySet()) {
            if (!first) sb.append(',');
            writeString(sb, e.getKey());
            sb.append(':');
            write(sb, e.getValue());
            first = false;
        }
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, Collection<?> coll) {
        List<Object> list = new ArrayList<>(coll);
        // RFC 8785 preserves array order; do not sort.
        sb.append('[');
        boolean first = true;
        for (Object item : list) {
            if (!first) sb.append(',');
            write(sb, item);
            first = false;
        }
        sb.append(']');
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    private static void writeNumber(StringBuilder sb, Number n) {
        double d = n.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new IllegalArgumentException("non-finite numbers are not allowed by RFC 8785");
        }
        if (n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte) {
            sb.append(n.toString());
        } else if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e16) {
            sb.append(Long.toString((long) d));
        } else {
            sb.append(Double.toString(d));
        }
    }

    @SuppressWarnings("unused")
    private static final List<Object> UNUSED = Collections.emptyList();
}
