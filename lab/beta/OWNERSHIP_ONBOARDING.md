# Beta ownership onboarding

The production onboarding UI is the reference: discovered accounts and cards in separate sections, with My account/My card, Not mine and a visible saved ownership status. Beta uses the unchanged shared Masroof theme, card, icon and spacing tokens. Production app and ui-kit sources are unchanged.

Beta discovery now aggregates all imported evidence rather than grouping one 25-message page at a time. A reference groups observations by bank, account/card role and suffix for an explicit ownership decision; it is not an entity ID. Registry entities retain independent stable IDs. Multiple matching registry identities require selection, conflicting card kinds cannot be confirmed, and the user may explicitly keep a conflict for later review.

Bank selection and optional import precede the ownership list. Ownership and card configuration must be resolved before proceeding. Clear transactions post after that step, while unsafe/ambiguous evidence remains reviewable. Previously confirmed unique registry entries are retained without reconfirming each message. A proven consistent same-evidence account link can connect a confirmed debit card; other links/types require explicit configuration. A new independent credit facility is created only through the explicit editor checkbox for a primary card, atomically with the card save. Exact retries do not create duplicate facilities.

Schema 12 -> 13 adds ownership decisions and a derived evidence-exclusion table only. No evidence, labels, registry identities, manual decisions, ledger audit or old schemas are rewritten. Existing registry ownership is projected at read time; there is no invented decision backfill. The tables are included automatically in existing validated Beta backups and restore/reset flows.

Not mine persists for subsequent observations. The write-side automatic sweep rebuilds exclusions using current proven local references, without hiding pending manual approvals or corrections. Explicitly external assets are omitted from current ownership/financial projections while historical registry rows and audit remain. A later ownership change holds affected manual financial effects for review and preserves their saved decisions. An ownership exclusion is not an incomplete financial write. Stale exclusions cannot hide retries. All writes remain outside dashboard projections, and failure leaves the durable automatic sweep retryable.

Automatic processing uses a new versioned checkpoint so existing Beta data receives the ownership projection. New setup does not post before ownership/configuration completion. Production SMS intake, parser behavior, data and ML activation are outside this change.

Validation is recorded in the PR: Beta JVM suite and lint, native ownership/whole-import/ambiguity/external/restore/configuration checks, schema migration preservation, and actual setup/discovery UI journeys. Tests and screenshots use synthetic isolated data only.
