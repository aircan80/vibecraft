package dev.wowforever.importer.sqlite;

import dev.wowforever.importer.atlasloot.AtlasBoss;
import dev.wowforever.importer.atlasloot.AtlasInstance;
import dev.wowforever.importer.atlasloot.AtlasLoot;
import dev.wowforever.importer.drops.DropRate;
import dev.wowforever.importer.drops.DropRates;
import dev.wowforever.importer.report.ImportReport;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * Writes AtlasLoot instances, bosses and boss loot. Ids are sequential from 1: instances in input order,
 * bosses in instance order then sort order. Loot of a boss with an NPC id (other than an extra list such
 * as trash or keys) is also merged into the NPC drop pairs.
 */
final class AtlasWriter {
  private final Connection c;
  private final ImportReport report;

  AtlasWriter(Connection c, ImportReport report) {
    this.c = c;
    this.report = report;
  }

  /**
   * @param zoneExists whether a zone row exists for an id; an instance's zone is its map id when it does
   * @param itemExists whether the item table holds an id; others are counted as {@code atlas.unknownItem}
   */
  void write(List<AtlasInstance> instances, LongPredicate zoneExists, LongPredicate itemExists, DropRates rates,
      NpcDrops drops) throws SQLException {
    Set<Long> unknownItems = new HashSet<>();
    long instanceId = 0;
    long bossId = 0;
    try (Batch instanceRows = new Batch(c, "INSERT INTO instance(id, key, name, map_id, instance_id, content_type,"
            + " level_min, level_max, zone_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
        Batch bossRows = new Batch(c, "INSERT INTO boss(id, instance_id, name, npc_id, object_id, level, special_type,"
            + " is_extra_list, sort_order) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
        Batch lootRows = new Batch(c, "INSERT INTO boss_loot(boss_id, item_id, difficulty, slot, drop_pct, pct_source)"
            + " VALUES (?, ?, ?, ?, ?, ?)")) {
      for (AtlasInstance inst : instances) {
        instanceId++;
        Long zoneId = inst.mapId() != null && zoneExists.test(inst.mapId()) ? inst.mapId() : null;
        instanceRows.add(instanceId, inst.key(), inst.name(), inst.mapId(), inst.instanceId(), inst.contentType(),
            inst.levelMin(), inst.levelMax(), zoneId);
        List<AtlasBoss> bosses = inst.bosses().stream().sorted(Comparator.comparingInt(AtlasBoss::sortOrder)).toList();
        for (AtlasBoss boss : bosses) {
          bossId++;
          bossRows.add(bossId, instanceId, boss.name(), boss.npcId(), boss.objectId(), boss.level(),
              boss.specialType(), boss.extraList() ? 1 : 0, boss.sortOrder());
          Set<String> seen = new HashSet<>();
          for (AtlasLoot loot : boss.loot()) {
            if (!seen.add(loot.itemId() + "/" + loot.difficulty())) {
              report.count("atlas.duplicateLoot", 1);
              continue;
            }
            if (!itemExists.test(loot.itemId())) {
              unknownItems.add(loot.itemId());
            }
            Optional<DropRate> rate = boss.npcId() == null ? Optional.empty() : rates.get(boss.npcId(), loot.itemId());
            lootRows.add(bossId, loot.itemId(), loot.difficulty(), loot.slot(),
                rate.map(DropRate::percent).orElse(null), rate.map(DropRate::source).orElse(null));
            if (boss.npcId() != null && !boss.extraList()) {
              drops.add(loot.itemId(), boss.npcId(), NpcDrops.ATLASLOOT);
            }
          }
        }
      }
    }
    report.count("atlas.unknownItem", unknownItems.size());
  }
}
