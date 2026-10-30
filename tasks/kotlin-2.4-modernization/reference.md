# Kotlin 2.0 → 2.4 modernization reference

Research date: 2026-10-30. Target compiler/stdlib: 2.4.21. This is source
modernization, not permission to upgrade dependencies or enable experimental flags.

## Retrieval and authority

Before source review, Context7 resolved Kotlin to `/jetbrains/kotlin-web-site`,
the repository behind kotlinlang.org. Queries covered (1) 2.1/2.2 language and
stdlib stabilization, (2) 2.3/2.4 stable versus experimental additions, and
(3) Android/Compose/KSP compatibility. Follow-up queries checked exact backing
field syntax/limitations and stdlib APIs. Coroutines was resolved separately;
`/kotlin/kotlinx.coroutines/1.11.0` was queried for cancellation/Flow contracts.
No private source or financial data was sent to Context7.

Context7 returned official repository excerpts, including historical 2.3 backing
field examples still labeled experimental. The 2.4 release notes supersede that
historical status. The official feature-status summary sometimes lists availability
at the stable/major release rather than the first preview (for example, guards
show 2.2 there, while 2.1 release notes document the preview). Introduction
versions here follow the focused release notes. The official rendered release pages, API references, Android
Room guidance and toolchain compatibility table were cross-checked directly.

## Language and compiler

| Capability | Introduction → stable/default | Potential benefit and Masroof decision | Official source |
| --- | --- | --- | --- |
| Guard conditions on subject `when` | 2.1 preview → 2.2 stable | Flatten typed-result conditions; adopt only where priority/side effects remain identical. Already used for partial ingestion at baseline. | [2.1](https://kotlinlang.org/docs/whatsnew21.html#guard-conditions-in-when-with-a-subject), [2.2](https://kotlinlang.org/docs/whatsnew22.html#stable-features-guard-conditions-non-local-break-and-continue-and-multi-dollar-interpolation) |
| Non-local `break`/`continue` in inline lambdas | 2.1 preview → 2.2 stable | Can remove loop-control flags; no flag workaround requiring this was found. Labeled collection returns are usually clearer and have different scopes. | [Inline functions](https://kotlinlang.org/docs/inline-functions.html#break-and-continue) |
| Multi-dollar interpolation | 2.1 preview → 2.2 stable | Useful for raw JSON/schema text with literal dollar signs. No production escaping hotspot justifies conversion. | [Strings](https://kotlinlang.org/docs/strings.html#multi-dollar-string-interpolation) |
| Nested type aliases | 2.2 experimental → 2.3 stable | Can scope repeated complex types to an owner. Reject aliases that merely rename existing domain types or conceal contracts. | [2.2](https://kotlinlang.org/docs/whatsnew22.html#support-for-nested-type-aliases), [2.3](https://kotlinlang.org/docs/whatsnew23.html#stable-features) |
| Data-flow-based `when` exhaustiveness | 2.2.20 experimental → 2.3 stable | Compiler accounts for earlier exclusions; prefer exhaustive type branches where useful, never remove intentional future/default financial handling. | [2.2.20](https://kotlinlang.org/docs/whatsnew2220.html#data-flow-based-exhaustiveness-checks-for-when-expressions), [2.3](https://kotlinlang.org/docs/whatsnew23.html#stable-features) |
| `return` in explicitly typed expression bodies | Default in 2.3 | Allows direct early exits; avoid converting clear block bodies for style alone. | [2.3](https://kotlinlang.org/docs/whatsnew23.html#features-enabled-by-default) |
| Explicit backing fields | 2.3 experimental → 2.4 stable | Can remove mutable/read-only backing-property pairs. Requires a private subtype field, non-open `val`, no custom getter/delegate. Do not replace Flow wrappers with the mutable flow instance. | [Properties and limitations](https://kotlinlang.org/docs/properties.html#explicit-backing-fields) |
| Context parameters | 2.2 preview → 2.4 stable, with exceptions | Avoid introducing implicit dependencies across workflow boundaries; explicit constructor/parameter contracts already fit the architecture. Explicit context arguments and callable-reference support are not covered by blanket stability. | [Context parameters](https://kotlinlang.org/docs/context-parameters.html), [2.4](https://kotlinlang.org/docs/whatsnew24.html#stable-features) |
| Annotation `@all` / default target rules | 2.2 preview → 2.4 stable | No Room/entity/serialization annotation rewrite: target changes can alter processor output and storage contracts. | [Annotations](https://kotlinlang.org/docs/annotations.html#all-meta-target) |
| Optional extra compiler checks | 2.1 | Useful diagnostics, separate from source features. Preserve current build/check configuration. | [2.1](https://kotlinlang.org/docs/whatsnew21.html#extra-compiler-checks) |
| Unified warning management | 2.2 | Per-diagnostic severity controls; do not weaken existing checks to accept a refactor. | [2.2](https://kotlinlang.org/docs/whatsnew22.html#kotlin-compiler-unified-management-of-compiler-warnings) |
| Import deprecation diagnostic improvement | 2.4 | Could narrow file suppressions/star-import workarounds; none should be removed without verifying the actual deprecated call sites. | [2.4](https://kotlinlang.org/docs/whatsnew24.html#no-more-deprecation-warnings-on-the-last-segments-of-imports) |
| JVM 21 `when` via `invokedynamic` | 2.2.20 experimental → 2.4.20 stable/default | Compiler-owned optimization, already available with installed toolchain. No manual flags or claimed speedup. | [2.4.20](https://kotlinlang.org/docs/whatsnew2420.html#new-stable-features) |

## Standard library

| Capability | Introduction → stable | Potential benefit and decision | Official source |
| --- | --- | --- | --- |
| Base64 | 1.8.20 experimental → 2.2 stable; PEM added 2.2 | JVM APIs already concise; changing decoder acceptance/error behavior or backup/encrypted formats adds risk without benefit. Retain. | [2.2 Base64](https://kotlinlang.org/docs/whatsnew22.html#stable-base64-encoding-and-decoding) |
| `HexFormat` / hex conversion | 1.9 experimental → 2.2 stable | Already adopted in hashing/identities. Retain permissive debug PDU parsing: generic parsing would accept/reject different input. | [2.2 HexFormat](https://kotlinlang.org/docs/whatsnew22.html#stable-hexadecimal-parsing-and-formatting-with-the-hexformat-api) |
| `kotlin.time.Clock` / `Instant` | 2.1.20 experimental → 2.3 stable | No replacement of `java.time` zone/period policies, stored timestamps or injected clocks. | [2.3 time tracking](https://kotlinlang.org/docs/whatsnew23.html#stable-time-tracking-functionality) |
| Common UUID API | 2.0.20 experimental → 2.4 stable, except V4/V7 generation APIs | No JVM-only benefit that warrants changing identity generation, formatting or ordering. | [UUID](https://kotlinlang.org/docs/uuids.html), [2.4 UUID](https://kotlinlang.org/docs/whatsnew24.html#stable-uuid-api-in-the-common-kotlin-standard-library) |
| `isSorted`, `isSortedBy`, descending/comparator variants | 2.4 stable | Short-circuit order checks; existing adjacent-pair logic validates spacing/overlap/cadence, not just monotonic order. No equivalent replacement found. | [Collection ordering](https://kotlinlang.org/docs/collection-ordering.html#check-if-a-collection-is-sorted) |
| Unsigned integer → `BigInteger` | 2.4 stable | No relevant unsigned conversion workaround; retain distinct Money models and decimal arithmetic. | [2.4 unsigned conversion](https://kotlinlang.org/docs/whatsnew24.html#new-api-for-converting-unsigned-integers-to-biginteger-on-the-jvm) |

## Explicit exclusions

Do not enable context-sensitive resolution, unused-result checking, collection
literals, explicit context arguments, new nullable-map fallback APIs or their
opt-ins. The first two remain pre-stable in the researched release history;
2.4 labels the latter additions experimental. Kotlin 2.4.20 also labels new
collection equality/uniqueness helpers and coroutine stack recovery APIs
experimental. These are not stable opportunities simply because 2.4.21 is installed.

Sources: [current language status](https://kotlinlang.org/docs/kotlin-language-features-and-proposals.html),
[current unused-result checker](https://kotlinlang.org/docs/unused-return-value-checker.html),
[2.4 language/stdlib](https://kotlinlang.org/docs/whatsnew24.html),
[2.4.20 exclusions](https://kotlinlang.org/docs/whatsnew2420.html#new-experimental-features).
Native/Swift/Wasm/JS changes do not apply to Masroof's Android/JVM targets.

Common atomic types were introduced experimentally in 2.1.20 and still carry
`ExperimentalAtomicApi` in the 2.4 API. Keep existing JVM atomics.
[Introduction](https://kotlinlang.org/docs/whatsnew2120.html#common-atomic-types),
[current atomic API](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.concurrent.atomics/).

## Android, Compose, Coroutines, Flow and Room

- Compose compiler plugin and serialization plugin reference the Kotlin version
  catalog pin. Kotlin's Compose compiler ships with the matching Kotlin release;
  no Compose compiler flags, stability annotations, remember/effect keys or UI
  composition structure need changing. [Migration guide](https://kotlinlang.org/docs/compose-compiler-migration-guide.html).
- Compose 2.1.20 fixed default parameters on open composables and restartability
  of final overrides. Masroof has no relevant open-composable workaround to
  remove. Source information is already default on Android.
  [2.1.20 Compose changes](https://kotlinlang.org/docs/whatsnew2120.html#compose-compiler).
- Coroutines/Flow are independently versioned libraries, not APIs upgraded by
  selecting Kotlin 2.4. Preserve dispatchers, cancellation, atomic `update`,
  replay/buffering and lifecycle collection. Keep the existing read-only wrappers.
  [asStateFlow](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/as-state-flow.html),
  [cancellation](https://kotlinlang.org/docs/cancellation-and-timeouts.html).
- Room 2.8.5 uses KSP 2.3.12 here. Official Room guidance uses KSP for Kotlin;
  no switch to kapt, entity annotation rewrite or schema generation change is
  needed. Verify generated-code compilation and unchanged exported schemas.
  [Room releases/setup](https://developer.android.com/jetpack/androidx/releases/room).
- Pins are Kotlin/Compose compiler/serialization 2.4.21, AGP 9.5.0, Gradle 9.8.2,
  Compose BOM 2026.09.00, Coroutines 1.11.0, Room 2.8.5, KSP 2.3.12.
  minSdk 26, compileSdk 37, targetSdk 36, JVM bytecode/toolchains 21 remain fixed.
- Kotlin's fully supported table lists 2.4.20–2.4.21 through Gradle 9.7.0 and AGP
  9.3.1; project pins exceed that published range. Android's Kotlin class-file
  table is a separate minimum-support requirement, not proof of full KGP
  compatibility. Record actual build/device results; do not upgrade/downgrade pins
  or claim vendor-qualified compatibility. [KGP table](https://kotlinlang.org/docs/gradle-configure-project.html),
  [Android D8/R8 support](https://developer.android.com/build/kotlin-support).
