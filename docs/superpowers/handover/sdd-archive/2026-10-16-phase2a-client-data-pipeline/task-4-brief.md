### Task 4: Item core join, schema additions and alt values

**Files:**
- Modify: `importer/src/main/resources/dev/wowforever/importer/schema.sql` (all §4.3 tables/columns incl. rulings 14–16), `sqlite/EntityWriter.java` or a new `sqlite/ClientItemWriter.java`, `sqlite/SqliteWriter.java` (write order: items first, then client tables)
- Create: `importer/src/main/java/dev/wowforever/importer/clientdata/ItemSparseLoader.java`, `ClientItem.java` (record of all per-item client fields), `ItemClassNames.java` (reads `ItemClass`/`ItemSubClass` CSVs; replaces the static `ItemClasses` names when client data is present)
- Test: `ItemSparseLoaderTest.java`, `SqliteWriterClientDataTest.java`

**Interfaces:**
- `ItemSparseLoader.load(ClientDataSource src, Set<Long> questieItemIds, ImportReport report) -> Map<Long, ClientItem>`: joins `Item` (class, subclass, inventory type, icon fdid) and `ItemSparse` (name `Display_lang`, quality `OverallQualityID`, item level, required level, bonding, sell/buy price, max stack `MaxCount`, item set, description `Description_lang`, allowable class/race, required skill/rank, flags, start quest) for ids in `questieItemIds`; counts `clientdata.items.matched` (ItemSparse present), `clientdata.items.iconOnly` (Item only), `clientdata.items.unmatched`; writes `clientdata.unmatched.ranges` note (id ranges of unmatched items in 10k buckets).
- `ClientItem(long id, boolean hasSparse, Integer quality, Integer itemLevel, Integer requiredLevel, Integer classId, Integer subclassId, Integer inventoryType, Integer bonding, Long sellPrice, Long buyPrice, Integer maxStack, Long iconId, Long itemSetId, String name, String description, Integer allowableClass, Long allowableRace, Integer requiredSkill, Integer requiredSkillRank, List<Integer> flags, Long startQuestId, List<StatShare> statShares, Integer delayMs, Double dmgVariance, Integer damageType)`; `record StatShare(int statId, int share)`.
- Writer rules (ruling 14): when `hasSparse`, client `name`, `item_level`, `required_level`, `class_id`, `subclass_id` replace Questie's in `item` and `search_index`; differing Questie values go to `item_alt_value(item_id, field, 'questie', value)`; `count("clientdata.alt.<field>")`. `has_client_data` = `hasSparse ? 1 : 0`; `icon_id` set when the Item row exists.
- `item_class`/`item_subclass` rows come from `ItemClassNames` when client data is present (fallback to the static table otherwise).

- [ ] **Step 1: Write the failing tests** (loader on the fixture: 17204 quality 5, icon 134124, set null; 16800 set 203; 25 "Worn Shortsword" hasSparse; a questie-only fixture id (190179) unmatched; writer: `item.quality`, `has_client_data`, `item_alt_value` row when the fixture's Questie name differs — pick 2874 whose Questie name "An Unsent Letter" vs client name; assert at least the field recorded)
- [ ] **Step 2: Run tests to verify they fail** — [ ] **Step 3: Implement** — [ ] **Step 4: Run tests to verify they pass** (whole importer suite) — [ ] **Step 5: Commit** — `git commit -m "feat(importer): item core client data, schema additions and alt values"`

---

