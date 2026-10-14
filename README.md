# aiyoplane-spring-ai

**AEAP Composition Boundary Adapter for Spring AI (Java).**

A thin wrapper that attaches the AEAP Runtime Decision Point (RDP) to Spring AI's tool-calling surface (`ToolCallback`). Consequential tool executions produce independently verifiable Execution Receipts. The adapter does not alter Spring AI's `ChatClient`, advisor, memory, or RAG surfaces — only the specific boundary where a `@Tool` method is about to execute.

```xml
<dependency>
  <groupId>com.aiyoplane</groupId>
  <artifactId>aiyoplane-spring-ai</artifactId>
  <version>1.0.0</version>
</dependency>
```

## What this adapter is (and is not)

**It is:** the shape AEAP takes when attached to a Spring AI `ToolCallback`. The underlying authorization logic — Runtime Decision Point, Execution Receipts, verdict contract — is published across every AEAP adapter (langchain, crewai, microsoft-agent-framework, openai-agents, claude-agent-sdk, langgraph, google-adk, mcp-authz); this package distributes that logic into the Spring AI ecosystem under Spring-native idioms.

**It is not:** a new authorization product. It is not a Spring AI plugin that adds features. It is not a Spring Security replacement. It has no feature backlog of its own — if the behavior needs changing, the change belongs in the AEAP core so every adapter inherits it.

See the [AEAP specification](https://github.com/aiyoplane/aeap) §9 for the Composition Boundary primitive this adapter realizes.

## Why Spring AI needs this

Spring AI's current tool-calling pipeline executes a `@Tool` method as soon as the model selects it, with no pluggable approval step. The gap is named explicitly in four open Spring AI threads:

- [#4878 — Tool Approval Strategy for Spring AI Tool Calling](https://github.com/spring-projects/spring-ai/discussions/4878)
- [#3813 — Enhance Spring AI Tool Calling with @RequiresConsent](https://github.com/spring-projects/spring-ai/issues/3813)
- [#3848 — PR adding @RequiresConsent annotation](https://github.com/spring-projects/spring-ai/pull/3848)
- [#4871 — How to reject tool calls under internalToolExecutionEnabled(false)](https://github.com/spring-projects/spring-ai/issues/4871)

This adapter fills that gap today, with zero changes to Spring AI itself. The design maps 1:1 to the `ToolApprovalStrategy` proposal in #4878 and the `@RequiresConsent` annotation shape in #3848, so when Spring AI ships a native approval surface, migration is a package rename.

## Install

```xml
<dependency>
  <groupId>com.aiyoplane</groupId>
  <artifactId>aiyoplane-spring-ai</artifactId>
  <version>1.0.0</version>
</dependency>
```

Requires Java 17+, Spring Boot 3.3+, and Spring AI 1.0.x or 1.1.x (brought by the host app).

## Minimal usage

```java
import com.aiyoplane.springai.annotation.AiyoGated;
import com.aiyoplane.springai.config.AiyoContext;
import com.aiyoplane.springai.intent.IntentBuilder;
import com.aiyoplane.springai.rdp.RuntimeDecisionPoint;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

// 1. Define a Spring AI tool as you normally would, and mark the risky one with @AiyoGated.
@Service
class BankingTools {

    @Tool(description = "Transfer funds between accounts")
    @AiyoGated(actionType = "payment", reasonTemplate = "Approve transfer of {amount} to {to_account}?")
    public String transfer_funds(long amount, String to_account) {
        // Runs only on ALLOW. Receipt is on AiyoContext.currentReceipt().
        return actuallyTransfer(amount, to_account);
    }
}

// 2. Define the AEAP policy (local RDP for development; createHostedRdp for production).
@Configuration
class AiyoConfig {

    @Bean
    RuntimeDecisionPoint aiyoRuntimeDecisionPoint() {
        Map<String, Object> policy = Map.of(
            "rules", List.of(
                Map.of("action_type", "payment", "amount_max", 1_000, "effect", "allow"),
                Map.of("action_type", "payment", "amount_max", 10_000, "effect", "escalate"),
                Map.of("action_type", "payment", "effect", "block")
            )
        );
        return RuntimeDecisionPoint.createLocalRdp(policy);
    }

    // 3. Translate the tool's raw args into an AEAP Intent the policy can match on.
    @Bean(name = "aiyoIntentBuilders")
    Map<String, IntentBuilder> aiyoIntentBuilders() {
        return Map.of(
            "transfer_funds", args -> Map.of(
                "type", "payment",
                "amount", args.get("amount"),
                "target", args.get("to_account")
            )
        );
    }
}
```

The resulting behaviour, at the Composition Boundary:

- **ALLOW:** the inner `@Tool` method runs. Its result is returned to Spring AI unchanged. `AiyoContext.currentReceipt()` carries the signed Execution Receipt for the duration of the method body, suitable for recording alongside the real-world action.
- **ESCALATE:** `EscalationRequired` is thrown with the pending receipt digest. Catch it in your controller / Spring AI advisor and route to whatever approval UI you already have — the digest binds the approval to the exact tool + args pair that was paused.
- **BLOCK:** `BlockedByPolicy` is thrown naming the policy rule. The inner method does not run.

## Composition Boundary — what the adapter actually does

For each gated `@Tool` method:

1. **Intent construction.** The registered `IntentBuilder` (or a default builder that attaches `@AiyoGated.actionType` to the raw args) produces an AEAP Intent payload.
2. **RDP evaluation.** The `RuntimeDecisionPoint` bean returns ALLOW / ESCALATE / BLOCK + policy ref + optional reason.
3. **Verdict enforcement.**
   - **ALLOW** → underlying method runs; receipt bound to `AiyoContext`.
   - **ESCALATE** → `EscalationRequired` raised; underlying never runs.
   - **BLOCK** → `BlockedByPolicy` raised; underlying never runs.
4. **Fail-closed default** on every ambiguous condition — null decision, unparseable args, missing policy file all route to BLOCK rather than silent allow (AEAP §2.2 invariant 5).

## Local vs. hosted RDP

```java
import com.aiyoplane.springai.rdp.RuntimeDecisionPoint;

// Development:
RuntimeDecisionPoint rdp = RuntimeDecisionPoint.createLocalRdp(policyMap);
// or from a YAML stream on the classpath:
RuntimeDecisionPoint rdp = RuntimeDecisionPoint.createLocalRdp(
    new ClassPathResource("aiyo-policy.yaml").getInputStream());

// Production:
RuntimeDecisionPoint rdp = RuntimeDecisionPoint.createHostedRdp("aiyo_live_...");
```

Both factories return the same `RuntimeDecisionPoint` interface; swapping between them does not change any application code.

## API reference

### `@AiyoGated(actionType, reasonTemplate)`

Marker annotation on `@Tool` methods. `actionType` is matched against policy rules' `action_type` field; `reasonTemplate` is rendered into the escalation reason with `{argName}` placeholders substituted from the raw tool args.

### `RuntimeDecisionPoint.createLocalRdp(Map | InputStream)` · `.createHostedRdp(String)`

Factory methods matching the Python packages' `create_local_rdp(policy=...)` / `create_hosted_rdp(api_key=...)` shape.

### `IntentBuilder`

Functional interface. Translates raw tool args into the Intent payload the RDP evaluates. Registered as a `Map<String, IntentBuilder>` bean named `aiyoIntentBuilders` keyed by Spring AI tool name.

### `AiyoGatedToolCallback`

The enforcement callback. Call `new AiyoGatedToolCallback(toolName, @AiyoGated annotation, intentBuilder, rdp, signer, callerIdSupplier)` to wrap a tool programmatically from your own `ToolCallbackProvider` instead of relying on annotation discovery.

### `AiyoContext.currentReceipt()`

Thread-local accessor to the Execution Receipt bound to the currently executing `@Tool` method body. Returns null outside a gated invocation.

### `BlockedByPolicy` · `EscalationRequired`

Runtime exceptions. `BlockedByPolicy` carries `toolName` and `policyRef`. `EscalationRequired` additionally carries `pendingDigest` — the SHA-256 digest of the canonical pending receipt, suitable for binding an out-of-band approval back to the exact tool + args pair that was paused.

### `ExecutionReceipt`

Signed record. Fields: `version`, `callerId`, `toolName`, `argsDigest`, `action`, `policyRef`, `verdict`, `timestampMs`, `signatureB64`, `digest`. Canonical form is RFC 8785 JCS over the ordered payload; signature algorithm is Ed25519 (JDK 15+ built-in).

## Trust surface and non-guarantees

This library enforces authorization at the Composition Boundary. It does not prevent prompt injection, model misbehaviour, or an attacker with write access to the policy file or signing key. See [aiyoplane.com/trust](https://aiyoplane.com/trust) for the full non-guarantee list (AEAP §15).

## License

Apache 2.0. Copyright 2026 Aiyoplane, Inc.
