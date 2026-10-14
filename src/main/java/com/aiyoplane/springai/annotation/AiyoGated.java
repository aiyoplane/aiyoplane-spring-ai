/*
 * Copyright 2026 Aiyoplane, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.aiyoplane.springai.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Spring AI {@code @Tool} method as subject to AEAP authorization at the Composition
 * Boundary. Methods not annotated with {@code @AiyoGated} are untouched by this library and
 * continue to execute exactly as Spring AI runs them today.
 *
 * <p>Example:
 * <pre>
 * &#64;Tool(description = "Delete a book by id")
 * &#64;AiyoGated(actionType = "book_mutation")
 * public void deleteBookById(String bookId) { ... }
 * </pre>
 *
 * <p>The {@code actionType} value is matched against policy rules' {@code action_type} field,
 * which is the same vocabulary used by every other AEAP adapter (langchain, crewai,
 * microsoft-agent-framework, etc.).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AiyoGated {

    /**
     * A short, stable, project-level identifier for the kind of action this tool performs
     * (for example {@code "book_mutation"}, {@code "external_message"}, {@code "payment"}).
     * Matched against policy rules' {@code action_type} field.
     */
    String actionType() default "";

    /**
     * Optional template rendered into the {@link
     * com.aiyoplane.springai.exception.EscalationRequired#reason()} on ESCALATE. Placeholders
     * use {@code {argName}} and are substituted from the raw tool arguments. Example:
     * {@code "Delete book {bookId}?"}
     */
    String reasonTemplate() default "";
}
