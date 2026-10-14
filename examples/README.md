# Example — Spring Boot app with aiyoplane-spring-ai

A single `BookService` with two `@Tool` methods — one un-gated, one gated with `@AiyoGated` — plus the YAML policy the local RDP evaluates.

## Files

- `BookService.java` — the service with the gated and un-gated tool methods.
- `aiyo-policy.yaml` — the policy document the local RDP evaluates.

## Dropping into a real app

1. Add the dependency (`com.aiyoplane:aiyoplane-spring-ai:1.0.0`) to your `pom.xml`.
2. Copy `aiyo-policy.yaml` into `src/main/resources/`.
3. Add the service class to a package Spring component-scans.
4. Start the app. Ask the model to "delete book 42" — the model should emit a `deleteBookById` tool call. The gate throws `EscalationRequired` with the pending receipt digest.
5. Catch `EscalationRequired` in your controller / Spring AI advisor and route to whatever approval UI you already have (your `internalToolExecutionEnabled(false)` loop, Slack handoff, web modal). The digest binds the approval to the exact tool + args pair that was paused.

## Production: supply your own signer and hosted RDP

The default auto-config generates a fresh in-process keypair on startup. Production deployments MUST supply their own `ReceiptSigner` bean loaded from a durable keypair so receipts remain verifiable across restarts and across process boundaries.

```java
@Bean
public ReceiptSigner receiptSigner() throws Exception {
    PrivateKey  pk  = loadFromVaultOrFile();
    PublicKey   pub = derivePublicKeyFrom(pk);
    return new ReceiptSigner(pk, pub);
}

@Bean
public RuntimeDecisionPoint aiyoRuntimeDecisionPoint() {
    return RuntimeDecisionPoint.createHostedRdp(System.getenv("AIYOPLANE_API_KEY"));
}
```
