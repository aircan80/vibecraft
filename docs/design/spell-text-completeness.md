# Spell-text completeness

Item effects and set bonuses retain each formatter result's `COMPLETE` or `INCOMPLETE`
status, original template, unresolved tokens and spell ID. Rendered spell tooltip
lines carry the same immutable evidence. Existing constructors without that evidence
mean `UNKNOWN`, even when their older `complete` boolean is true.

The optional `spell_text_evidence` table keys rows by owner kind (`effect`,
`set_bonus`, `tooltip`), owner ID and ordinal (set bonuses use their threshold).
Core imports create it; targeted item updates create it if needed and replace only
that item's effect/tooltip evidence inside the caller's transaction. Set-bonus
metadata belongs to the set and is retained. Non-spell lines need no row.

Spell descriptions identify their actual selected `csv/Spell.csv` input. When that
input has a build lock, evidence retains its export base, product, build and recorded
table SHA-256. These are source claims from that input, not proof of root membership
or authentication of the export service. Arbitrary/item-description templates have
an explicitly unknown origin and no inferred product/build/hash. Generic library
metadata never supplies missing per-line provenance.

Readers use an optional left join and load all evidence with the item card. An old
database without the table, or a line without a row, remains `UNKNOWN`; its original
stored text and item source remain intact. No migration or query runs in the widget.
The runtime exporter retains and verifies the table like other gameplay tables.

The UI keeps its existing `?` replacement for unresolved variables. Known incomplete
lines have a compact visible explanation. Legacy unknown lines show an explanation
when variables are being masked; ordinary old text has no extra warning. Hover
on a spell line exposes recorded status/source/build/template/tokens without treating
those tokens as resolved facts. Status text also has an accessible name.

This does not resolve additional formulas or change strict import/coverage policy.
