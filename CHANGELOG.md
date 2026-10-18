# Changelog

## Unreleased

### Documentation

- README repositioned to reflect that Spring AI merged native tool-call confirmation in PR [#7157](https://github.com/spring-projects/spring-ai/pull/7157) (milestone `2.1.0-RC1`, merged 2026-10-15). The adapter is framed as composing above native confirmation, not filling an absent native feature. The "Why Spring AI needs this" section is replaced with "How this composes with Spring AI's native tool-call confirmation," including an explicit table of what native confirmation and AEAP each address. Receipt claims tightened to distinguish what the receipt lets a third party verify from what the receipt does not by itself prove (operation-field binding, execution-followed-authorization). Cross-framework portability framed as reach across nine adapters, with conformance test vectors — not adapter count — as the vehicle for interoperability claims. Code is unchanged; this is a documentation-only update.
- The planned code pass for composition with native `@Tool(requiresConfirmation = true)` is deferred until a focused integration spike demonstrates behavior under approve, deny, altered-argument, expiration, and retry conditions. The follow-on release that ships that integration will carry a semver-appropriate bump.

## 1.0.0 (2026-10-13)

- Initial release.
- AEAP Composition Boundary Adapter for Spring AI tool calling.
- Two integration surfaces: `@AiyoGated` annotation on `@Tool` methods and `AiyoGatedToolCallback` decorator for programmatic `ToolCallback` wrapping.
- `RuntimeDecisionPoint.createLocalRdp(policy)` and `RuntimeDecisionPoint.createHostedRdp(apiKey)` factory methods mirroring the Python packages' shape.
- Ed25519-signed Execution Receipts over RFC 8785 JCS canonical intent, verifiable with a one-line check.
- Spring Boot 3 auto-configuration (`AiyoSpringAiAutoConfiguration`); host apps override any default bean (`RuntimeDecisionPoint`, `ReceiptSigner`, `IntentBuilder` registry) by declaring their own.
- Fail-closed defaults per AEAP §2.2 invariant 5: null decision, unparseable args, or missing policy all route to BLOCK rather than silent allow.
- Apache 2.0.
