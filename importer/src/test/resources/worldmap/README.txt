Authentic comparison fixture provenance

UiMapAssignment rows: ATTWoWAddon/AllTheThings commit
 a8c2fce5e8e52af3925352f5e61cbf7520ae3148
 .contrib/.db/forever/.config/.wago/UiMapAssignment.1.60.1.70374.csv
assignments.csv is an exact CSV-cell subset (IDs46739,46746,46763), not invented bounds.

VM world spawns: tools/serverdb/serverdb.lock commit
 464179081673cfd240f7ffe7f0daf96bca0b5a70
 db-sqlite-4641790.zip SHA256
 bb2f1dd69171043a52657574f54948fd30941530fdc6eec12bce62c655428501
 creature(guid,id,map,position_x,position_y,position_z).
Downloaded and verified with tools/serverdb/fetch.py.

Questie percentages: shipped npc_spawn joined zone.ui_map_id, sourced from
QuestieDB Forever commit49e7dbca1f18eb3b8e57ee5d9562ac3e5c9adcc4.
No coordinates were reverse-generated from expected percentages. The fixed corpus contains21 explicitly named NPCs (NPC_IDS in the reproduction tool),
including448 and550, selected for Elwynn/Westfall/city coverage. It is not a representative
worldwide sample, and no percentage of the global population is claimed. Every corpus NPC
is retained regardless of error:20 agreements and one outlier (Hogger448). Each comparison
pairs one genuine VM spawn with its closest genuine Questie percentage on an explicitly
known target UiMap; ties sort by guid,zone_id,QuestieX,QuestieY. No tolerance filter is used.
Reproduce with tools/maplayers/comparison_corpus.py --assignments PATH --vm-database PATH
--app-database PATH --output PATH. All21 rows and all observed corpus outliers are emitted.
This checks coordinate projection, not an assertion that all patrol positions match.

Known release-gate limitation: required Hogger448 does NOT pass the spawn comparison.
Closest VM Elwynn spawn guid81027 transforms to(26.42603546,93.65043228), while
Questie records(25.02,92.9); X error1.40603546 exceeds one point. A genuine VM Hogger
movement-template point6 transforms to(25.11761766,92.66969406), but a movement point
must not be relabelled as a spawn to satisfy the gate. The21-row corpus has20 passing NPCs including
Defias Messenger550, with maximum-axis error0.61638328, and retains Hogger as its sole outlier.

Further geometric limitation: adjacent UiMap rectangles overlap (e.g. Duskwood1431
and Elwynn1429 around Hogger). Smallest containing rectangle is deterministic but
cannot establish actual terrain AreaID. projectTo supports an explicitly known map;
callers must not claim rectangle selection determines the true terrain zone.
