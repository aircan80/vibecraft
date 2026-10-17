# WoW expert memo — quest page presentation (2026-10-17)

## What players expect
1. Start and End at the top (Wowhead Quick Facts): Start NPC, End NPC, zone + coordinates, clickable; a map thumbnail
   with the giver "!" and turn-in "?"; two thumbnails when in different zones. Questie conventions: yellow "!" available,
   grey "!" low-level, blue "!" repeatable, yellow "?" complete; hover shows NPC name, quest title, level. Starts are
   not always NPCs: posters (objects, e.g. Wanted: Hogger), items (hunter epic, Darkmoon decks), mail → "Starts from
   item: X (drops from Y)".
2. Objectives, each locatable: per-objective "show on map" toggle; kill = mob spawns (dense kill areas as a shaded hull,
   dots when zoomed); item drops = every mob that drops it with chance, union of spawns; objects/nodes = object spawns;
   provided / required source items under "Provided item:" / "You will need:"; exploration = one trigger point; escorts
   usually have no map data → start point + "Escort", never an empty map.
3. A "Requires" line: level, race, class, faction, profession skill, reputation; prerequisites in words:
   "Requires completing A and B" vs "Requires one of A or B".
4. A chain ("Series") box: Wowhead's numbered list with the current quest bold; shows where am I, what came before
   (incl. side prerequisites), what unlocks next, breadcrumbs marked "optional, can skip", exclusive branches side by
   side labelled (Alliance vs Horde, class-specific, "choose one"); long chains collapse with "Step 9 of 15"; a zone tag
   per step for multi-zone chains (Onyxia attunement, Darkmoon cards).
5. Rewards last: choice vs always, money, XP, reputation.

## Pitfalls in Questie's data
- preQuestGroup = ALL required; preQuestSingle = ANY ONE suffices.
- nextQuestInChain is a display hint, sometimes missing/wrong: build the chain from prerequisite edges walked both ways;
  childQuests/parentQuest = quests done DURING another, never a "next step".
- exclusiveTo = completing/accepting locks the others out; breadcrumbs to the same quest share it; faction/class
  variants are near-duplicate ids that exclude each other.
- Breadcrumbs: newer data has a field; otherwise exclusiveTo + chain shape; never a hard prerequisite.
- Multiple givers/turn-ins (letters, posters in several towns, Darkmoon NPCs that move): lists, not single values.
- Item-started quests: item → drop sources → mob spawns, so the start stays locatable.
- zoneOrSort: positive = zone id; negative = sort category (class, profession, Darkmoon, Seasonal, Epic) → labels,
  never zone lookups.
- Count 0/missing = use/interact or provided-item steps: show the text without "0/0".
- killCredit groups: several NPC ids credit one objective → all spawns under one text.
- Escorts/events: no coordinates → giver location + note. Spawns missing inside instances → "Inside: Molten Core".
- Repeatables/dailies: specialFlags repeatable → "Repeatable" + blue "!"; per-city donation variants.

## Priority order
1. Start/End block (coordinates, mini maps, object/item starts). 2. Objectives with per-objective map overlays.
3. "Requires" line with correct AND/OR. 4. Series box (linear chain first, then breadcrumbs/exclusive branches).
5. Turn-in "?" pins on zone maps. 6. Rewards/reputation.

## Acceptance quests
1. Wanted: Hogger (176) — poster object start, one named mob, turn-in elsewhere.
2. The Defias Brotherhood chain (Westfall, ~65 → 166; ends with VanCleef in Deadmines) — long linear, crosses into an
   instance (verify ids).
3. Drakefire Amulet (6502) — ~15 steps, multi-zone; Horde counterpart Blood of the Black Dragon Champion (6602).
4. Rise, Thunderfury! (7787) after Examine the Vessel (7785) and Thunderaan the Windseeker (7786) — item-gated.
5. A Donation of Wool (~7791, verify) — repeatable, per-city variants.
Spare: OOX-17/TN distress beacon (item-started, escort with no map data).

## Addendum — what makes a companion app feel like WoW
Cues that matter: (1) "!"/"?" markers used exactly as the game does (yellow available, grey low-level, blue repeatable,
yellow "?" ready, grey "?" in progress; never reused for anything else); (2) quality colours on item names everywhere;
(3) the tooltip card on hover over any link (dark, thin grey-blue border, lines in game order); (4) flat small rate
bars; (5) parchment maps with high-contrast gold-yellow pins; (6) a Friz Quadrata-style serif for headings only.
Gimmicks to avoid: heavy gold filigree, parchment textures behind body text, animated glows, sounds, fake leather/stone
frames, game fonts in tables, dark-on-dark that hides quality colours. Readability beats atmosphere.
Icons players recognise instantly: Items = the Backpack bag; Quests = the yellow "!" (Quest Log micro-menu button
secondary); Zones = the World Map micro-menu button (folded map), compass ring as an alternative.
