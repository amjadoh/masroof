# M6 — configured network observations

Status: In Progress. Candidate comparison inside Beta/Parsing Lab only; no production parser, database, inbox, ownership, ledger or promotion change. The reference parser, corpus, expected results and old Lab samples remain intact.

## One generic execution path

`SharedLabEngine` `shared-4` and default library revision 3 add `NetworkRuleConfig`. The pure bank contract carries the same data as `NetworkRules`; the Beta adapter captures a deep immutable snapshot and translates it to the existing shared runtime. No bank-specific parser branch or second candidate engine is introduced.

Configuration contains bounded exact aliases keyed by canonical bank ID, role labels for sender/destination bank, explicit network labels, and the closed optional grammar for a standalone `[destination bank]` line. The initial alias vocabulary contains only the four named banks already present in the preserved regression samples. These internal comparison IDs/names are regression configuration under code review, not a new independently verified bank identity authority or permission to resolve ownership.

Names match as complete values after the existing Arabic typography fold, NFC and case/whitespace normalization. Prefix/substring matches never establish identity. All matching bank IDs survive; overlap across IDs, conflicting named banks (even two different external banks), unknown named banks or contradictory network evidence fail closed to UNKNOWN and a validation finding. An unconfigured local bank identity cannot be assumed different from every named bank.

Incoming transfers use sender-bank roles; outgoing transfers use recipient-bank roles or the configured standalone bracket grammar. A generic outgoing title does not prove network. Explicit positive network phrases must occur in the first recognized transfer title or a configured network-label line. Bank/recipient/merchant/account value lines cannot supply network through words inside names. Negative network assertions block rather than confirming the phrase they negate.

Quote-prefixed lines, quoted blocks, fenced blocks, inline ASCII single/double quotes and supported typographic quotes cannot supply network authority. This conservative supported grammar is not a natural-language quote/negation parser and may leave unsupported wording unknown. Alias conflict/unknown findings still pass through the existing validator/finalizer; named banks never lift OTP or pending/rejected/request gates.

Network evidence retains the original UTF-16 ranges through the existing normalizer/index mapping, including Arabic marks and emoji. A recognized local counterparty bank is only a bank-scoped account reference; it does not prove that account belongs to the user. Unknown/external counterparties retain an unknown bank reference.

## Compatibility and comparison

The new configuration defaults to empty when decoding saved JSON. Existing custom/default-saved libraries retain their own revision and are not silently replaced or populated. An old-JSON regression verifies that a named-bank line does not inherit aliases from the newer default configuration. Fresh libraries use revision 3; the engine runtime version is independently `shared-4`. This is read compatibility, not a claim of identical parsing for every saved library: the tighter context/quote/negation guards intentionally apply to older rule phrases too. The secondary-money reservation firewall remains tested for old JSON without metadata. Lab persistence assertions verify increment from the captured revision, full saved configuration after restart, immutable run snapshots and stale-draft rejection. Device UI assertions read the actual fresh default revision and require +1 after Save.

The Beta corpus comparison still reflects every `Facts` field and compares status. The four former network differences must now match the unchanged reference exactly. The ISO due date in `collision_statement_amount_ar_001` remains a visible additive observation: the reference only exposes slash dates. It is not nulled out, hidden from comparison or treated as approved financial equivalence.

## Synthetic adverse contexts and timing

`SharedNetworkResolverTest` and `SyntheticNetworkContextTest` use authored Orchid/Cedar/Maple bank contexts outside the historical corpus. They cover role scope, alias overlap/order, prefixes/substrings, unknown external names, conflicting banks, quotes/fences/apostrophes, negated network statements, counterparty names containing network words, security/unexecuted gates, configuration capture and original evidence offsets. They are **synthetic regression tests**, not independently reviewed real SMS or accepted-precision evidence on a promotion holdout.

`exportSharedNetworkBenchmark` reuses the existing `ContextBenchmarkExport` implementation: one JVM, five warmups and 25 measured parses per case, including rule validation and parsing but excluding file I/O. It also measures a bounded synthetic input of 16,000 UTF-16 units with many short lines, 100 banks × 20 aliases and 20 labels per role/network. Literal patterns are compiled once per resolver call, and aliases use an exact lookup index; no validation guard is removed for timing. Reports contain only summary, fixture IDs, timing and hashes—no message bodies, normalized text or extracted financial facts. The existing exporter's body-bearing pass-through files live in an OS temporary directory and are removed in `finally`. Reports include logical input/library hashes, runtime/platform, p50/p95/mean/max and a separate repeat-pass outcome check.

Reproduce with JDK 17:

```text
./gradlew :lab:engine:test :lab:bank-engine:test :lab:beta:testDebugUnitTest :lab:android:testDebugUnitTest :lab:engine:exportSharedNetworkBenchmark
```

Timing output: `lab/engine/build/reports/shared-network-benchmark/summary.txt` and `case-timings.csv`. This is local candidate/regression timing, not an Android device budget, a speedup against M0, a comparison with a missing baseline, an independently reviewed holdout or a promotion decision.

## Verified local evidence

JDK 17 / Gradle 8.8: **472 tests passed** (403 engine, 12 bank contracts/boundaries, 35 Beta, 22 Parsing Lab Android). Beta debug assembly and lint passed. The 15 new tests include generic resolver/adverse contexts, Beta provenance/gating/snapshot capture, old JSON without inherited aliases and schema bounds. Existing original-evidence, secondary-money/currency, OTP, unexecuted and persistence tests remain green. The reference adapter still retains 40/40 golden and full typed-field expectations with zero loss.

| Regression comparison | Before | This increment |
|---|---:|---:|
| Exact status + every Facts field | 35/40 | 39/40 |
| Candidate Success | 27 | 27 |
| Candidate Success with any reference difference | 4 | 0 |
| Field differences | bankNetwork × 4; ISO due date × 1 | ISO due date × 1 |

All four network migrations are now inside the strict full-field/status equality gate. The remaining ISO due-date extension is explicitly reported for one non-financial statement; it is still not approved financial equivalence. Zero Success differences here describes this regression corpus only, not accepted precision on real independent data.

Local timing on Windows 11 amd64 / JDK 17.0.20.1+1 / 14 visible CPUs:

- 1,000 measured regression calls: p50 **1.705 ms**, p95 **3.657 ms**, mean **1.954 ms**, max **11.286 ms**.
- Bounded 16k/max-configuration synthetic input, 25 measurements: p50 **85.540 ms**, p95 **134.714 ms**, max **136.909 ms**.
- Separate post-timing pass: **40/40 identical outcomes**.
- Logical input SHA-256: `6ba6d49ac3d09179ba6da53e99c0895f4dc9d84c54ade73723e762b9ae9ff7ce`.
- Library SHA-256: `6e6763be92d34b4c342aed74bb53fdbfaac26f5af8002788025f6d4b905a2964`.
- Normalized engine Kotlin source SHA-256: `b27c88b75d5daaf06d56c2c971b6b4fd1bffcc577c82b6ebefdaa989a5395ec9`.

The timing run occurred before committing this increment, on base `73ce1bf` with tracked changes. The reported source/configuration/input fingerprints identify the tested candidate; there is no claimed speedup, M0/device comparison or performance acceptance threshold.

M6 remains open for independent reviewed real data, accepted-error assessment, broader supported formats, device performance budgets and explicit promotion. No code deletion or financial cutover is part of this increment.
