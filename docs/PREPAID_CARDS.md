# Prepaid cards

Prepaid cards are explicitly configured independent containers. A funding account
is not a debit-card link: subsequent purchases debit the card only.

`CARD_TOP_UP` requires an explicit account source and card destination, confirmed
ownership of both, a confirmed `PREPAID` card type, and the existing parse-status
gate. It assembles as `SELF_TRANSFER`. It leaves the current account under the
prepaid-funding cash-flow category, but is excluded from spending. Purchases and
refunds use the existing expense/refund rules and do not create credit liability.

The card view reports movements separately per currency. Net movement is not a
balance: only an explicit balance observation can supply a balance, accompanied
by its receipt date. Foreign funding without an actual SMS exchange rate remains
unconverted; market and historical merchant estimates cannot establish its bank debit.

The family model has no trained top-up label; only explicit configured patterns
recognize this family. Existing posted evidence and human decisions remain protected.

Room remains schema 18. These additions introduce new string enum values without
changing tables or their identity hash. Older application versions cannot read
the new enum values. Retain a local pre-upgrade backup; do not downgrade a database
that has stored prepaid card configurations or top-up events.

Release gates include parser, assembly, registry, dashboard and currency tests,
full CI, signed release verification, and local device/data acceptance. Real SMS
and account mappings are never fixtures or bundled defaults.
