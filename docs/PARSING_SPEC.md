# Bank interpretation

The production adapter is `app/.../bank/NewEngineSmsAdapter.kt`; interpretation runs in `bank-engine/`. Routing evaluates all adapters before parsing. The retained `AlJaziraBankDetector` is a routing detector, not the retired parser.

`RuleLibrary` and `AlJaziraRuleProfile` define literal family/role vocabulary. `RuleInterpreter` extracts facts with evidence and blockers. Local family assistance is injected from `ml-assist`; rules and security checks remain authoritative. `InterpretationDraftMapper` preserves blockers and sends drafts through the existing validator and `ParseFinalizer`. Only that finalizer emits successful financial parse results.

The old message parser, parsing pipeline, classification resolver, heuristics and extraction helpers are removed from runtime and tests. Tests replay canonical fixtures and financial ledger scenarios through the new engine. Bank-qualified references do not infer ownership. Missing, contradictory or unexecuted facts remain reviewable.

The financial schema and transaction assembler remain unchanged. A parse result is not a posted transaction. See [AGENTS.md](../AGENTS.md) for gating, timezone, completion and retry requirements.
