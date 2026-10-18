# aiyoplane-spring-ai

**AEAP Composition Boundary Adapter for Spring AI (Java). Composes above Spring AI's native tool-call confirmation to add portable, independently verifiable authorization receipts and policy-driven ALLOW / ESCALATE / BLOCK semantics that travel across supported agent frameworks.**

A thin wrapper that attaches the AEAP Runtime Decision Point (RDP) to Spring AI's tool-calling surface. The adapter does not alter Spring AI's `ChatClient`, advisor, memory, or RAG surfaces — only the specific boundary where a `@Tool` method is about to execute. Spring AI's own tool-call confirmation remains the pause/resume mechanism; this adapter adds the authorization-protocol layer around it.

```xml
<dependency>
  <groupId>com.aiyoplane</groupId>
  <artifactId>aiyoplane-spring-ai</artifactId>
  <version>1.0.0</version>
</dependency>
```

## What this adapter is (and is not)

**It is:** the shape AEAP takes when attached to Spring AI's tool-calling surface. The underlying authorization protocol — Runtime Decision Point, signed Execution Receipts, verdict contract — is published across every AEAP adapter (langchain, crewai, microsoft-agent-framework, openai-agents, claude-agent-sdk, langgraph, google-adk, mcp-authz); this package distributes that protocol into the Spring AI ecosystem under Spring-native idioms. It is additive to any native Spring AI tool-call confirmation mechanism, not a replacement for it.

**It is not:** a new authorization product. It is not a Spring AI plugin that adds features. It is not a Spring Security replacement. It is not a replacement for Spring AI's own tool-call confirmation. It has no feature backlog of its own — if the behavior needs changing, the change belongs in the AEAP core so every adapter inherits it.

See the [AEAP specification](https://github.com/aiyoplane/aeap) §9 for the Composition Boundary primitive this adapter realizes.

## How this composes with Spring AI's native tool-call confirmation

Spring AI merged native tool-call confirmation on October 15, 2026 (PR [#7157](https://github.com/spring-projects/spring-ai/pull/7157), milestone `2.1.0-RC1`). The native feature gives Spring AI applications a built-in way to pause the tool-calling loop before a `@Tool` method runs, surface the pending call ids to the application, and resume with per-call approve/deny decisions supplied through a new `ToolCallDecisions` object. Native confirmation is the right primitive for a Spring AI application that owns its approval UX end-to-end.

Native tool-call confirmation and a portable execution-authorization protocol solve related but different problems:

| Question | Native Spring AI confirmation | AEAP's intended role |
|---|---|---|
| Should this tool call pause for a decision? | Yes — pause on `@Tool(requiresConfirmation = true)` or a `ToolCallConfirmationPolicy` match. | Can represent a policy decision to allow, escalate, or block. |
| Can the application resume after approval? | Yes — pass `ToolCallDecisions` back through `ToolCallingManager.executeToolCalls`. | Must integrate with a safe, verifiable execution path. |
| Can another system independently verify the authorization record? | Not established by PR #7157. | A core reason for AEAP's signed-receipt design. |
| Can multiple frameworks share one authorization format? | Not the purpose of this Spring AI feature. | A core AEAP objective across the nine shipped adapters. |
| Does any receipt prove the tool was safely executed? | No. | Also no — execution enforcement and outcome evidence remain separate. The receipt lets a third party verify the authorization claim without trusting the application's internal logs. |

**The adapter's role, framed precisely:** AEAP determines whether a proposed tool invocation is permitted, needs escalation, or must be refused, and produces a signed artifact a different system can verify. Spring AI's native confirmation surface — where that is available — is the recommended pause/resume mechanism. The adapter does not require native confirmation to be enabled, and it does not disable or bypass native confirmation when it is.

## Install

```xml
<dependency>
  <groupId>com.aiyoplane</groupId>
  <artifactId>aiyoplane-spring-ai</artifactId>
  <version>1.0.0</version>
</dependency>
```

Requires Java 17+, Spring Boot 3.3+, and Spring AI 1.0.x, 1.1.x, or 2.1.x (brought by the host app).

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

Behavior at the Composition Boundary:

- **ALLOW:** the inner `@Tool` method runs. Its result is returned to Spring AI unchanged. `AiyoContext.currentReceipt()` carries the signed Execution Receipt for the duration of the method body, suitable for recording alongside the real-world action.
- **ESCALATE:** `EscalationRequired` is thrown with the pending receipt digest. Catch it in your controller / Spring AI advisor and route to whatever approval UX you have (including Spring AI's native tool-call confirmation surface when available). The digest is bound to the exact tool and arguments the RDP evaluated.
- **BLOCK:** `BlockedByPolicy` is thrown naming the policy rule. The inner method does not run.

### Composing with native Spring AI tool-call confirmation

On Spring AI versions that ship PR #7157 (`2.1.0-RC1` and later), native tool-call confirmation is Spring AI's own pause/resume mechanism. The intended integration pattern is:

- Mark tools that should pause for a human with both `@Tool(requiresConfirmation = true)` and `@AiyoGated(...)`.
- Native Spring AI pauses the loop and surfaces the pending call ids.
- The application consults AEAP for the authorization decision (ALLOW / ESCALATE / BLOCK) against the pending call's canonical intent.
- The application resumes native execution via `ToolCallingManager.executeToolCalls(prompt, reply, decisions)`.
- On ALLOW, the AEAP signed receipt is attached alongside the native decision — the native resume path is unchanged; AEAP's artifact is additive.

Note: the specific integration surface — whether AEAP consumes the native `ToolCallDecisions` object, provides a Spring AI–compatible `ToolCallConfirmationPolicy`, or is invoked by application code at the pause point — is a design question the project will investigate with a working integration example before documenting here as the recommended pattern. The current `1.0.0` release does not yet ship a `ToolCallConfirmationPolicy` integration; a follow-on release will land once the composition is verified in tests across approve, deny, altered-argument, expiration, and retry conditions.

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

Runtime exceptions. `BlockedByPolicy` carries `toolName` and `policyRef`. `EscalationRequired` additionally carries `pendingDigest` — the SHA-256 digest of the canonical pending receipt. The digest is bound to the exact tool and arguments the RDP evaluated against the policy; a different operation at execution time would need separate authorization. See the receipt-binding caveat below.

### `ExecutionReceipt`

Signed record. Fields: `version`, `callerId`, `toolName`, `argsDigest`, `action`, `policyRef`, `verdict`, `timestampMs`, `signatureB64`, `digest`. Canonical form is RFC 8785 JCS over the ordered payload; signature algorithm is Ed25519 (JDK 15+ built-in).

**What the receipt lets a third party verify:** the signature is valid against the published JWKS, the claims in the payload are the ones the RDP produced, and the receipt has not expired. **What the receipt does not yet prove on its own:** that the arguments the model proposed match the operation that actually executed, and that execution followed authorization through the Composition Boundary rather than through a sibling path. Those properties depend on what the receipt binds to and how the execution boundary enforces it; see AEAP §15 non-guarantees and the open operation-binding question under Pattern 42 in the [Aiyo Governance Pattern Library](https://aiyoplane.com/trust) before relying on the receipt as proof of safe execution.

## Trust surface and non-guarantees

This library enforces authorization at the Composition Boundary. It does not prevent prompt injection, model misbehaviour, or an attacker with write access to the policy file or signing key. The signed receipt lets another system verify the receipt's signature and claims without trusting the Spring AI application's internal logs; it does not by itself establish that the approving human saw the full operation, that the exact arguments were executed, or that execution followed the authorized path. Those properties depend on the host application's enforcement discipline. See [aiyoplane.com/trust](https://aiyoplane.com/trust) for the full non-guarantee list (AEAP §15).

## Cross-framework portability

The same receipt format and `action_type` / `effect` / `amount_max` policy schema are published across the nine shipped AEAP adapters (`mcp-authz`, `langchain`, `langgraph`, `openai-agents`, `claude-agent-sdk`, `microsoft-agent-framework`, `crewai`, `google-adk`, `spring-ai`). A receipt issued by one adapter is verifiable by any other adapter's verifier without a bespoke format translation. Reach across nine adapters is not the same as proof of complete interoperability — conformance test vectors in AEAP spec Appendix A are the vehicle for that claim; the vectors cover the receipt format and verdict contract today, with coverage of adapter boundary conformance in progress.

## License

Apache 2.0. Copyright 2026 Aiyoplane, Inc.
