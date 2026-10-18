# Beta source coverage policy recommendation

Status: recommendation for issues [#1](https://github.com/aircan80/vibecraft/issues/1)
and [#6](https://github.com/aircan80/vibecraft/issues/6), not implemented or approved.
Keep Build disabled under the current policy until the reviewed change and acceptance
below exist. Never lower the existing guard silently or borrow Era gameplay values.

## Measured facts

The fresh exact Forever/enUS capture retrieved 51 Wago tables. Projection matches
15,672 of 21,294 named items to ItemSparse (73.6%; the report floors to 73%), below
the existing exact-ratio 85% guard. There are 5,622 unmatched named items:

- 5,603 exist in the same native Item table but lack ItemSparse.
- 19 lack both native Item and ItemSparse records.
- Origins are 4,814 raw Forever Questie records, 806 generated Forever base-item
  records and two Forever traces. Absence is not evidence of an Era dataset.
- Of the 5,622 missing-detail records, 4,379 are armor and 1,109 weapons (97.6%
  combined). ItemSquishEraID is zero on all; that field alone proves no edition.

These counts come from the source audit's coverage-audit-summary.json,
missing-native-sparse.json and named-items-origin.tsv in the external real-job
review evidence. The separate 162 unformatted-spell findings still require a
field-level audit; they are not resolved by the item coverage explanation.

## Additional candidate evidence, not currently selected

A SHA-verified existing third-party Raidbots DBCache projection for the same
70450/enUS build has 4,864 Sparse rows; 3,800 intersect the missing named cohort.
Before invalidation/removal semantics, potential coverage is 19,472/21,294 (91.44%),
with 1,822 remaining unmatched (1,643 raw and 179 generated records). This is a
candidate bound, not validated final projection coverage. It is not CASC evidence
and is not a provider currently selected or allowed by the setup plan.

Prefer investigating an **explicit optional hotfix provider** before changing the
strict coverage policy: source/version/build identity, SHA verification, acquisition,
Java decoding, invalidation/removal and field-selection semantics need review and
implementation. Adds / Needs / If omitted must explain the detail gap it can fill.
Never silently fetch it as a Wago/CDN fallback or enable it by accepting generic
coverage warnings. The potential 91.44% does not resolve spell formatting or prove
a candidate passed the guard.

The formatter audit reports unresolved tokens with preserved raw placeholders and
complete=false metadata (including qN=100, ecN=18, cross-spell u=12, ecim=11 and
proccooldown=6). Preservation is safer than invented values, but safe end-user
presentation and acceptable gating still require explicit review.

## Recommended policy

Do not discard the 5,603 native items to improve the percentage. Retain their exact
native identity and source-supported fields; show missing Sparse-derived fields as
unavailable/hidden only where the established knowledge-state rules justify the
specific label. Native membership does not prove a drop, availability or numeric stat.
Keep the 19 community-only IDs separately qualified as reported source references,
not confirmed native build members. Do not remove them solely to change a denominator.

Replace the single combined notion of completeness only through a separately
reviewed, versioned policy: identity/integrity safety remains mandatory; evidence
coverage becomes an explicit per-cohort result. The default strict policy and its
85% gate remain intact. If product accepts an incomplete beta library, expose a
separate **beta library with missing native details** mode with an explicit opt-in,
selected product/build/locale and exact counts. This recommendation is not permission
to enable that mode or treat it as a complete release.

Non-waivable failures: wrong product/build/locale, unselected providers, incompatible
schema, corrupt hashes, broken integrity/foreign keys, incomplete required table
transport, missing job provenance, invalid inputs, unsafe activation or unsanitized
cross-edition values. Neither optional-provider selection nor beta acknowledgment
may bypass these. Optional hotfix data can fill a field only with separately verified
Forever/build authority and retained provenance, never as a hidden dependency.

The 162 spell findings need a distinct rendering policy: preserve attributed raw
text, mark affected effects as unsupported/unformatted and never present guessed
numbers. A reviewed change may allow a visibly incomplete text field without
waiving semantic/stat errors. Until affected fields, safe presentation and tests
are known, keep that gate open; item-mode opt-in cannot bypass it automatically.

## Wizard and release acceptance

After capture, before activation, show selected build and provider coverage:
15,672 named items with native Sparse details; 5,603 native identities without those
details; 19 reported IDs without native membership; unresolved spell-text count.
Explain this is coverage of the selected sources, not a download progress failure.
Every provider retains Adds / Needs / If omitted. Offer Review gaps, change sources,
retry or cancel; a future approved beta option must name its limitations explicitly.
Preserve the existing working library until a validated candidate is atomically
activated. Keep the coverage notice in Sources & Updates and candidate provenance.

A mode change needs versioned plan/manifest evidence and tests for cohort counts,
missing-field display, no Era fallback, malformed/foreign identity rejection, selected
provider enforcement, cancellation and activation rollback. Audit the 19 IDs and all
162 spell findings before permitting activation. Rerun the actual fresh 51-table job
through validation and the wizard with no existing database. Close neither #1 nor #6
from successful transport or an opt-in alone. Full completeness and final release
acceptance remain separate from an explicitly limited beta library.
