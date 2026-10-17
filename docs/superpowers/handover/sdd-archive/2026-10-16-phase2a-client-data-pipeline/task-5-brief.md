### Task 5: Stats, damage, armour calculators and the known-items gate

**Files:**
- Create: `clientdata/ScalingTables.java` (RandPropPoints, ItemDamage*, ItemArmor*, ArmorLocation loaders), `ItemStatsCalculator.java`, `ItemDamageCalculator.java`, `ItemArmorCalculator.java`, `StatNames.java` (stat id → "Stamina" etc., the ITEM_MOD table from the wowdev wiki; resistances 51–55 mapped to schools)
- Create: `importer/src/test/resources/fixtures/clientdata/known-items.csv` and `clientdata/KnownItemsOracle.md` (how it was produced)
- Test: `ItemStatsCalculatorTest`, `ItemDamageCalculatorTest`, `ItemArmorCalculatorTest`, `KnownItemsTest` (fixture-driven; also runs against the real converted data when `~/.cache/wowforever-clientdata/out/<build>` exists)

**Interfaces:**
- `ScalingTables.load(ClientDataSource)`; `randPropBudget(int itemLevel, int quality, int inventoryType) -> OptionalInt` (column by quality: Epic/Superior/Good; slot group by inventory type per the wowdev wiki table; document the mapping in a comment table); `dps(DamageKind kind, int itemLevel, int quality) -> OptionalDouble`; `armor(int itemLevel, int quality, int inventoryType, int material) -> OptionalInt`; `shieldArmor(...)`.
- `ItemStatsCalculator.stats(ClientItem, ScalingTables) -> List<ItemStat(int statId, int value)>`: `value = Math.round(budget * share / 10000.0)`; zero shares skipped; resistances (stat ids 51–55 and 56 if present) included in order.
- `ItemDamageCalculator.damage(ClientItem, ScalingTables) -> Optional<ItemDamage(int min, int max, int school, int delayMs, double dps)>`.
- `ItemArmorCalculator.armor(ClientItem, ScalingTables) -> Optional<ItemArmor(int armor, int block)>`.
- Each returns empty and counts `clientdata.unverified.<category>` when its inputs are inconsistent (missing table row, unknown kind).
- `known-items.csv` columns: `item_id, name, expected_lines` (expected lines `|`-separated in game order, e.g. `Binds when picked up|One-Hand Sword|44 - 115 Damage|Speed 1.90|(41.8 damage per second)|+5 Agility|+8 Stamina|+8 Fire Resistance|+9 Nature Resistance|Requires Level 60`), `compare_kinds` (comma list of tooltip kinds the pipeline implements that this row verifies). Produced with the **oracle** (ruling 10): run wow.tools.local (download `Release-win-x64.zip` 0.9.9 from `https://github.com/Marlamin/wow.tools.local/releases/latest` into `C:\Users\aircan80\tools\wow.tools.local\`, run it with `-wowFolder "C:\Program Files (x86)\World of Warcraft" -wowProduct wow_classic_beta`, read `/dbc/tooltip/item/{id}` for the 25 ids) or, if that cannot be made to run within an hour, read Wowhead's Forever item pages for those 25 items by hand (one WebFetch per item; document which oracle was used in `KnownItemsOracle.md`).

- [ ] **Step 1: Write the failing tests** (worked examples with hand-computed values from the fixture tables; `zeroBudgetProducesNoStats`; `KnownItemsTest` reading the CSV and comparing `TooltipBuilder` output — stub `TooltipBuilder` until Task 6 by comparing the stat/damage/armour lines only: `compare_kinds` limited to `damage,speed,dps,armor,stat,resist` in this task)
- [ ] **Step 2: Run tests to verify they fail** — [ ] **Step 3: Implement; iterate on the formula until every known item in the compared kinds matches** (if a category cannot be matched after honest effort, remove it from every row's `compare_kinds`, count `clientdata.unverified.<category>`, and say so in the report) — [ ] **Step 4: Run tests to verify they pass** — [ ] **Step 5: Commit** — `git commit -m "feat(importer): item stat, damage and armour computation with known-items gate"`

---

