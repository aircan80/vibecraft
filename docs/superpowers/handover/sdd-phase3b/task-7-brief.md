### Task 7: Community drop rates labelled "Vanilla rate"

**Files:**
- Modify `{main}model/RateSource.java` (+ `vmangos`), `{main}model/LootRow.java` (+ `pctSource` as the last component; every existing constructor passes null), `{main}db/NpcRepository.java` and `{main}db/InstanceRepository.java` (select `pct_source` into `LootRow`), `{main}ui/widgets/RateBarRenderer.java` (headline marker, boss suffix), `{main}ui/Columns.java` (`VANILLA = " V"` marker beside `DISAGREES`), `{main}ui/pages/ItemPage.java`, `NpcPage.java` and `InstancePage.java` (pass "is a boss" to the tooltip), and `{main}ui/widgets/SourceFilterControl.java` (legend line).
- Tests: modify `{test}model/RateSourceTest.java` and `{test}ui/widgets/RateBarRendererTest.java`; extend `{test}ShippedDataTest.java` (or the split `ShippedQuestDataTest`, see Task 10).

**Interfaces:**
- Consumes: `drop_rate_source(source='vmangos')`, `item_npc_drop.pct_source`, `disagrees`, `source_count` (3A Task 9/10); `boss.npc_id` to tell bosses.
- Produces:
  - `RateSource.ORDER` is questie, questie-wowhead, questie-correction, atlasloot, atlasloot-override, vmangos, others.
  - `RateSource.label()` for `vmangos` is `"Vanilla rate (VMaNGOS)"`.
  - `static String RateSource.BOSS_NOTE = "Vanilla rate; Forever loot reworked"`.
  - `RateBarRenderer(Function<Object, List<RateSource>> sourcesOf, Predicate<Object> onBoss, Function<Object, String> headlineSource)`.
  - `static String RateBarRenderer.sourcesText(List<RateSource> sources, boolean boss)`.

- [ ] **Step 1: failing tests.**
  - `RateSourceTest.vmangosReadsVanillaRate`: `new RateSource("vmangos", 12.5).label()` is `"Vanilla rate (VMaNGOS)"`, and it sorts after `atlasloot-override`.
  - `RateBarRendererTest.vmangosHeadlineIsMarked`: a row whose headline `pct_source` is `vmangos` renders `"12.50 % V"`. The `V` is painted muted and the number in text.primary. The tooltip's first line starts with "Vanilla rate (VMaNGOS) 12.50 %".
  - `RateBarRendererTest.bossTooltipSaysLootReworked`: with `onBoss` true and a vmangos source, the tooltip contains "Vanilla rate; Forever loot reworked". Without a vmangos source it contains no such line.
  - `RateBarRendererTest.disagreementStillMarked`: questie 2.31 and vmangos 9.0 with `disagrees` give `"2.31 % ≠"` and both sources in the tooltip.
  - `ShippedQuestDataTest.hoggerClawHasAVanillaRate`: `drop_rate_source` holds a `vmangos` row for (448, 1931). The NPC page of Hogger 448 lists it in the Huge Gnoll Claw rate tooltip.
- [ ] **Step 2:** run the tests and expect FAIL.
- [ ] **Step 3:** implement. The boss test is `boss.npc_id = npc` (instance pages always are) or the NPC's zone kind `dungeon`/`raid`. It is loaded with the rate sources in `DetailPanels.load` as a `Set<Long> bossNpcs`.
- [ ] **Step 4:** run and expect PASS, then the full verify. Also check that `NOTICE.md` carries the VMaNGOS GPL-2.0 note from 3A Task 9; add it if missing.
- [ ] **Step 5:** commit `feat(ui): community drop rates read Vanilla rate`.

