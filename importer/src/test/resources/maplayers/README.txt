Exact-cell fixture subsets (CSV quoting/newlines normalized by Python csv writer):

Client build1.60.1.70450, wow_classic_beta, enUS, acquired2026-10-17 from
https://wago.tools/db2/{TABLE}/csv?build=1.60.1.70450
Full acquisition URLs, hashes and export sizes reside in the source archive manifest.
TaxiNodes IDs2,4; TaxiPath connecting only2/4; TaxiPathNode PathID6/7;
UiMapAssignment UiMapID1429/1436/1453; AreaTrigger IDs78/119/121(if present).
An absent client trigger does not acquire invented client coordinates.

VM derived CSVs are reproducible subsets of release db-sqlite-4641790.zip:
https://github.com/vmangos/core/releases/download/db_latest/db-sqlite-4641790.zip
release archive SHA256bb2f1dd69171043a52657574f54948fd30941530fdc6eec12bce62c655428501
commit464179081673cfd240f7ffe7f0daf96bca0b5a70 (tools/serverdb/serverdb.lock).
teleports-derived.csv: areatrigger_teleport joined areatrigger_template byID,
select highest patch and highest template build perID; IDs78,119,121.
flightmasters-derived.csv: creature joined latest creature_template patch,
filter npc_flags&8, NPCs352,523. Positions are actual VM world positions.
These are Vanilla data and never establish Forever custom-instance destinations.
