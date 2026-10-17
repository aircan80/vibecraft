# Desktop visual design review

This is a simulated desktop designer review of current acceptance screenshots and Swing source, not a human player usability study. No production UI was changed.

## Direction: an explorer’s journal

Keep the existing Slate/Parchment palettes, genuine map art, item quality colors, restrained gold rules, and serif entity titles. Those already communicate Warcraft without copying the game’s heavy frames. Give the newer Forever features the same editorial care as the existing quest and map pages. The most valuable change is stronger information hierarchy, not more ornament.

Use a consistent shell: labelled destination navigation, global search, clickable location breadcrumb, entity heading, then the player’s task. A subtle gold selected indicator and one small source-status chip are enough decoration. Avoid background textures behind table text and avoid a wall of identical framed cards.

## Evidence and priorities

| Priority | Current evidence | Recommended treatment | Review criterion |
|---|---|---|---|
| P1 | `catalog-final/hub-0-dark-1216.png`: long New/Changed counts, 13 plain navigation links, three full-width inputs, then 30,932 mixed results. At 420px the first result starts around y=530. | Replace initial raw diff feed with 4–6 purposeful category cards: Explore, Dungeons & raids, Build planner, Equipment, Professions, What changed. Put complete native-record changes behind an explicit “Browse all changes” destination. Give filters visible labels and combine them into a compact labelled toolbar. | A first-time player can locate dungeons, talents, and sets without interpreting DB categories or scrolling on a normal desktop. Compact layout displays at least one useful category before the fold. |
| P1 | `catalog-final/talents-0-dark-1216.png`: unheaded rows of names, rank 0, raw IDs; enormous full-width +/− buttons; selected description includes `$m1%` and CSV path. | Class icon + class/spec selector; visible points budget and milestones; prerequisite-aware icon/tree view where archive topology is supported. Retain accessible list alternative. Put selected talent detail beside the tree on desktop and below on compact. Rank +/- controls belong to the selected talent, not across the whole window. | A player identifies class, spec, selected talent, available points, and blocked prerequisites at a glance. Unsupported formula appears as a short explicit qualification; raw source is disclosed separately. |
| P1 | `source-evidence-final/item-287012-dark-1216-default.png`: field labels left, state chips at far right, more than 1,000px away; repeated unknown states dominate; “Shares appearance with 0 other items” takes a full panel. | Constrain the fact card to a readable width; keep label, state and value adjacent. Summarize “Name hidden · stats not discovered” beside the heading, retain per-field detail on expansion. Suppress empty appearance section or show quiet inline “No matching appearances recorded.” | Eye movement stays within one card; unknown never resembles zero; no discovered facts are removed. |
| P1 | Same item, expanded evidence: snapshot SHA and paths have equal visual weight to the actual source conclusion. | Two-level disclosure: “Why this value?” shows provider, client build, observation/state and selected-vs-alternative values. “Technical details” adds exact file, line, snapshot/hash and copy actions. Rename retained-knowledge evidence separately from disagreements. | Player understands whether sources disagree without reading a hash. Full provenance remains reachable and copyable. |
| P2 | `native-final/02-eastern-kingdoms.png`: icon-only 40px rail, tiny breadcrumb, Map/List buttons remote on upper right. | Default to a 176–192px labelled navigation sidebar on desktop; allow a remembered 48px collapsed rail. Keep Map/List as an explicit segmented control next to map heading; breadcrumb stays above the map at every depth. | Selected destination has icon, text and indicator; keyboard focus remains visible; breadcrumb is reachable by keyboard. |
| P2 | `standard-final/quest-79976-slate-full.png`: real art, title, objective and giver tables provide a strong baseline, but large vertical voids separate objective/givers and turn-in/quest chain. | Preserve the two-column map/details layout. Standardize section gaps and size rows to content. Keep chain controls near the quest title, with the chain list as a labelled section. | Desktop shows objective, giver and turn-in together without large dead gaps; compact stacks details before map. |
| P2 | Hub list marks every row with a separate New chip, category line and name, yet most new names are muted like disabled controls. | One category icon, a clear linked name, one secondary line, right-aligned change badge. Selected/hover/focus treatments distinguish actionability from unavailable records. | Links are recognizable without hovering; unavailable destination has explanation and is not styled like an active link. |

`native-final/04-westfall-zone.png` shows a transient “Loading zone…” frame, so it does not establish final zone layout quality. Acceptance snapshots should wait for settled content before visual review.

## Proposed tokens

Retain current `Theme`/`Tokens` color values initially: they already document contrast handling for quality text, alternating rows and both modes. Audit any new token combinations in both themes; do not substitute a brighter gold indiscriminately in Parchment.

| Token | Proposal |
|---|---|
| Body | 14px logical baseline; 16px comfortable setting; use installed UI font and respect OS scale |
| Page title | 26px serif, existing Warcraft-like title treatment |
| Section heading | 16px semibold; gold selectively, no mandatory all-caps/small-caps for long titles |
| Supporting text | 12–13px minimum at normal scale; never use muted color for primary clickable names |
| Spacing scale | 4, 8, 12, 16, 24, 32px; page inset 24px desktop / 16px compact; section gap 24px |
| Controls | 32px normal height / 28px dense desktop setting; 40px accessible/touch-friendly setting |
| Tables | 32px normal rows / 26px dense; 8–12px cell inset; explicit headers; retain selected foreground contrast |
| Readable width | 680–760px facts/detail column; overall content may reach 1,280px when a map/tree sits alongside |
| Cards | 12–16px padding, 1px theme hairline, modest 4–6px corner; only group a meaningful entity/task |
| States | Text + icon for Hidden, Not discovered, Reported, Known; amber only for actionable disagreement/qualification; never color alone |
| Focus | Visible 2px focus outline using an audited high-contrast theme token; keyboard state independent of hover |

These are proposed logical dimensions, not permission to hardcode pixel geometry or bypass Swing font scaling. Test a full shell at 1024×768 and 1280×900, 150% and 200% scale; current 420px captures test wrapping of page bodies rather than proving the whole application is usable at a 420px window.

## First coherent slice

Start with the shell, Forever home and one item detail page. This establishes navigation, spacing and evidence disclosure patterns reusable across every feature. Next apply them to dungeon/boss loot and talent planning. Avoid simultaneously reskinning every table before these core patterns are reviewed.

Implementation seams already exist: `Theme`/`Tokens` centralize colors; `PageContent` handles whole-page scrolling and width tracking; `FieldKnowledgePanel` currently forces chip alignment to the far right via `BorderLayout.EAST`. Build shared heading, section, fact-row and evidence-disclosure components around those seams rather than adding page-specific spacing patches.
