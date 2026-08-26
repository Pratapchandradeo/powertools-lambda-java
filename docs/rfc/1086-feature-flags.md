# RFC: Add Feature Flags module

This write-up follows the
[RFC discussion template](https://github.com/aws-powertools/powertools-lambda-java/blob/main/.github/DISCUSSION_TEMPLATE/rfcs.yml).
It is intended to be pasted into a GitHub Discussion after review.

- **Related issue:** [#1086](https://github.com/aws-powertools/powertools-lambda-java/issues/1086)
- **Area:** Other (new utility; Feature Flags)
- **Roadmap:** Feature Parity (p2)

## Summary

Add a `powertools-feature-flags` module that evaluates a JSON feature-flag document using the same schema and
rule-engine semantics as Powertools for AWS Lambda (Python). Customers can evaluate one feature, list every feature
enabled for a request context, and load the document from AWS AppConfig or from an in-memory / custom store.

The Java API is builder-based (`FeatureFlags`, `AppConfigStore`, `InMemoryStore`). There is no AspectJ annotation
for evaluation. AppConfig I/O is reused from `powertools-parameters-appconfig` rather than reimplemented.

## Use case

Java customers who already standardise on Powertools in Python cannot share a feature-flag document or evaluation
model today. Issue #1086 asks for feature parity: a small rule engine that turns features on or off from input
context, using the Python schema.

Typical needs:

- Static campaign flags (`ten_percent_off_campaign: { "default": true }`)
- Per-customer rules (`tier == premium`)
- Time windows and percentage rollouts (`SCHEDULE_BETWEEN_*`, `MODULO_RANGE`)
- Non-boolean payloads (`boolean_type: false`)
- Fetch from AppConfig with a short in-memory cache, or inject a fixture in tests

## Proposal

### Module and public API

New Maven artifact `software.amazon.lambda:powertools-feature-flags`.

```java
FeatureFlags flags = FeatureFlags.builder()
        .withStore(AppConfigStore.builder()
                .withApplication("product-catalogue")
                .withEnvironment("dev")
                .withName("features")
                .withEnvelope("features")          // optional JMESPath
                .withMaxAge(10, ChronoUnit.MINUTES)
                .build())
        .build();

boolean premium = flags.evaluate("premium_features", Map.of("tier", "premium"), false);
List<String> enabled = flags.getEnabledFeatures(context);
Map<String, Object> raw = flags.getConfiguration();
```

`StoreProvider` is the extension point. `InMemoryStore.fromJson` / `fromMap` is the test store.

### Evaluation contract (Python-compatible)

1. Feature exists and a rule matches → that rule’s `when_match`
2. Feature exists, no rules or no match → feature `default`
3. Feature missing or store fails → caller default (`evaluate`) or empty list (`getEnabledFeatures`)
4. Malformed schema → `SchemaValidationException` (not swallowed)
5. Rules are first-match-wins; conditions are AND; empty conditions never match
6. `boolean_type` defaults to `true` and uses Python truthiness
7. Time actions ignore user context and use `Clock` (UTC by default)

All Python condition actions are implemented, including list membership aliases, time ranges, and `MODULO_RANGE`.

### AppConfig integration

`AppConfigStore` does not open its own AppConfig session. It delegates fetch and cache to
`AppConfigProvider` and only parses JSON plus an optional JMESPath envelope. Failures are mapped to
`ConfigurationStoreException`.

Only **freeform** AppConfig profiles are supported, matching Python.

### Java runtime fit

- User-agent interceptor `PT/FEATURE-FLAGS/<version>` via AWS SDK `execution.interceptors`
- CRaC `Resource` on `FeatureFlags` primes evaluation before SnapStart checkpoint
- GraalVM `reflect` / `resource` / `jni` metadata under `META-INF/native-image`

### Documentation and examples

- `docs/utilities/feature_flags.md` (install, IAM, schema, testing)
- SAM example under `examples/powertools-examples-feature-flags/sam`
- E2E handler + `FeatureFlagsE2ET` against a real AppConfig deployment

### Before / after

**Before.** Java customers encode flags as env vars or fetch a blob with Parameters and write their own matcher.
Documents written for Python Feature Flags cannot be evaluated in Java.

**After.** The same JSON document is evaluated in Java with `evaluate` / `getEnabledFeatures`, loaded from AppConfig
or an in-memory store, without AspectJ.

## Out of scope

- AspectJ annotations (`@FeatureFlag` or similar)
- Third-party rules engines
- AWS AppConfig **native** / multi-variant feature-flag profiles
- Event-to-context extractors (callers pass a `Map`)
- Additional SAM-GraalVM / CDK / Terraform examples (SAM only)
- GraalVM native e2e (JVM e2e only; metadata is unit-tested)
- Publishing the RFC as a GitHub Discussion (this file is the write-up)

## Potential challenges

| Challenge | Mitigation |
|-----------|------------|
| Schema drift vs Python | Treat the Python schema as a hard contract; unit tests cover first-match, AND, empty conditions, truthiness, time, modulo, store fail-safe |
| Last-writer-wins user-agent when Parameters AppConfig is also on the classpath | Same `UserAgentConfigurator` behaviour as other modules; interceptor still registers `feature-flags` |
| `ClassPreLoader` / `classesloaded.txt` can name optional classes | `beforeCheckpoint` catches `LinkageError` around preload |
| Hard dependency on `powertools-parameters-appconfig` even for `InMemoryStore` | Matches Python’s single module; keeps AppConfig as the default store |
| AppConfig cold start (two API calls) | Document cache TTL; reuse Parameters cache |

## Dependencies and integrations

- `powertools-parameters-appconfig` — fetch and cache
- `powertools-common` — user-agent and `ClassPreLoader`
- `org.crac:crac` — SnapStart hook
- `jackson-databind`, `jmespath-jackson` — parse and envelope

Does not depend on Logging, Metrics, Tracing, or AspectJ.

## Alternative solutions

1. **Tell customers to use Parameters only.** Covers static blobs, not rule evaluation or Python schema parity.
2. **Embed a third-party rules engine.** Extra dependency, different schema, violates “keep it lean”.
3. **Call AppConfig native feature flags.** Different schema from Python; Python explicitly supports freeform only.
4. **Split stores into extra artifacts.** Consistent with Parameters, but overkill for one default store plus an
   in-memory test double.

## Acknowledgment

- This RFC meets the [Powertools for AWS Lambda (Java) tenets](https://docs.powertools.aws.dev/lambda/java/latest/#tenets):
  Lambda-first, lean (no extra rules engine), backwards-compatible API surface, and feature parity with Python.
- Other languages: Python is the source of the schema. TypeScript and .NET may adopt the same document later;
  this RFC does not change those runtimes.

---

**Disclaimer:** After creating an RFC, wait until it is reviewed and signed-off by a maintainer before implementing
it. This write-up documents the design that was implemented against issue #1086 so maintainers can review it in
place.

* RFC PR:
* Approved by: ''
* Reviewed by: ''
