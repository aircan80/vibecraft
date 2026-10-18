# Product and delivery roadmap reconciliation

Date: 2026-10-17. Product scope review for the owner-requested product/project manager reconciliation.

The dashboard roadmap was stale at review: its current-work section stopped at v0.4.1 and its 1,841-test snapshot did not describe the running Explorer Journal or the later feature slices being integrated. The local high-level roadmap also called v0.5–v0.8 entirely planned and multiple games architecture-only. Neither description accurately represented current implementation. Historical verification remains valid for its original artifact; it must not be presented as verification of later changes.

## Agreed product scope

| Workstream | Accurate position | Remaining scope before calling the whole theme complete |
| --- | --- | --- |
| Foundation through v0.4.1 | Development implementation verified; not a released application | Genuine Forever collector capture acceptance remains a release gate. Preserve evidence and source limitations. |
| Explorer Journal | Running baseline; additional navigation/research improvements in integration verification | Review the exact replacement artifact at desktop and compact sizes, retain Original interface and startup chooser, verify Back and keyboard behavior. |
| Cities and music | Six city guides and exact-build background music running; people/services directory in integration verification | Validate directory navigation, partial coverage, compact layout and audio controls. No claim that every district, service or city exists. |
| NPC previews | Generic on-demand pipeline running for supported exact-build appearances | More mappings and supported materials; real appearance-collector acceptance. Metadata coverage is not successful render coverage. |
| v0.5.0 loot research | Partial; class/slot/resistance filters in integration verification | Instance-wide shared pools and dungeon/raid preparation remain open. Existing NPC world-drop grouping is retained, not counted as a new unfinished implementation. |
| v0.6.0 crafting and economy | First native crafting-guide slice in integration verification | Full recursive crafting graph, profession browser, complete recipe acquisition resolver, trainer finder, economics, gathering and disenchanting remain open. City vendor links alone do not complete them. |
| v0.7.0 lore and exploration | City work pulled forward; connected reward trails and bounded quest-text research in integration verification | Full zone gazetteer, storyline browsing, broad faction/lore coverage, hunter pets, rare details, fishing and learning Home remain open. Existing flight layers and PvP pages are earlier completed slices. |
| v0.8.0 glance and compare | Scoped pinned references first slice in integration verification | Compact pinned workspace/always-on-top, item comparison, editable/exportable research lists and verified two-build change view remain open. A bookmark is not the complete wishlist/export feature. |
| Multiple games | Catalog context/session isolation and game picker in integration verification | Only Forever is installed. Separate Classic, Retail and Anniversary/TBC source acquisition, schema/projection validation and acceptance remain future work. Archived Era comparison is not a playable Classic catalog. |

“In integration verification” means code is integrated or being checked, not that the replacement app is already running. The project manager owns the exact artifact, completed test totals, native acceptance and launch evidence; those fields must advance only after actual completion.

## Acceptance adjustments and examples

1. **Loot filters must respect Forever evidence.** The original backlog promised that Molten Core + Warlock + Waist would display Felheart Belt using Era values. That conflicts with the owner's later explicit instruction to use Forever data. The replacement criterion is correct shown/excluded/unknown partitioning using selected Forever/hotfix evidence, with Era available only as explicit comparison. Current real-data fixture can legitimately produce zero shown, 209 excluded and 15 unknown. Never manufacture a successful filter hit by borrowing Era fields. Known canonical zero and hotfix values retain authority over native fallback.
2. **Resistance is recorded evidence.** Deadmines items 5195 (Fire 5), 5201 (Fire 8) and 872 (Nature 7) exercise recorded filtering. Missing data remains unknown; numeric zero is known. All-resistance is disclosed separately rather than silently expanded into every school.
3. **Native crafting guide is a bounded first slice.** Item 251525 resolves spell 1252566, reagents 251524 and 2678, Cooking rank/threshold evidence and fixed yield. Context-dependent creation, such as spells 5504 or 587, must say Context-dependent rather than claim flat output. Missing context fields stay unknown. Class-created items are not automatically called profession recipes. The tab loads asynchronously on demand and Back restores the selected variant and position.
4. **Connected reward trails retain authored semantics.** Item 211527 resolves quest 79976 and its seven-node, six-edge attributed chain with the two faction starts. AND/OR, faction restrictions, missing references and cycles are preserved. It describes research, not character progress.
5. **City directory reflects recorded relationships.** Stormwind 1519 shows deduplicated recorded vendors/quest givers, coordinates and real entity links, with a visible partial-coverage statement. No inferred trainers, banks, auction houses or invented districts.
6. **Quest-text research is corpus-qualified.** A Dalaran search returns bounded excerpts from the selected Questie corpus and version. A word match is not proof of canonical identity or complete Warcraft lore coverage. Literal wildcard characters, query limits, cancellation and navigation are checked.
7. **Pinned references are scoped research bookmarks.** Ctrl+D pins the committed target under edition/projection/build/locale scope; Enter opens and Delete removes it. Foreign or malformed references do not leak into the current game. No character completion state is introduced.
8. **Game selection is honest.** The selector displays the supported game choices but enables only installed Forever. Selecting the current catalog leaves current navigation intact. Scoped asynchronous requests cannot publish after catalog/session invalidation.

## Product priority after this integration

Finish verification and launch the combined Journal changes first. Next close the remaining v0.5 instance-pool and preparation work, while independently researching missing v0.6 acquisition/gathering data. Continue rendering coverage and city fidelity as parallel owner-prioritized tracks. Do not close a release merely because a feature from it was pulled forward.

## Dashboard operating agreement

The dashboard is the owner's status monitor. Update it after each integrated and verified batch, after launch, and whenever a blocker or scope changes. Show separate entries for Running, In verification, In progress, Planned and Waiting for owner/external evidence. Each running snapshot names its artifact and evidence date; each incomplete theme lists the concrete remainder. Retain historical verification with its original artifact and time. Do not reuse old image counts or test totals to imply coverage of a new build.

The older backlog remains historical planning evidence. This reconciliation and the maintained high-level roadmap supersede its Era-filter acceptance, blanket planned statuses and optional-asset network assumptions where the owner's later instructions changed them.
