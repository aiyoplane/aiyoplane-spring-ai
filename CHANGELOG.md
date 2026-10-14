# Changelog

## 1.0.0 (2026-10-13)

- Initial release.
- AEAP Composition Boundary Adapter for Spring AI tool calling.
- Two integration surfaces: `@AiyoGated` annotation on `@Tool` methods and `AiyoGatedToolCallback` decorator for programmatic `ToolCallback` wrapping.
- `RuntimeDecisionPoint.createLocalRdp(policy)` and `RuntimeDecisionPoint.createHostedRdp(apiKey)` factory methods mirroring the Python packages' shape.
- Ed25519-signed Execution Receipts over RFC 8785 JCS canonical intent, verifiable with a one-line check.
- Spring Boot 3 auto-configuration (`AiyoSpringAiAutoConfiguration`); host apps override any default bean (`RuntimeDecisionPoint`, `ReceiptSigner`, `IntentBuilder` registry) by declaring their own.
- Fail-closed defaults per AEAP §2.2 invariant 5: null decision, unparseable args, or missing policy all route to BLOCK rather than silent allow.
- Apache 2.0.
