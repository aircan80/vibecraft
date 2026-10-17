package dev.wowforever.app.db;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.GiverSpot;
import dev.wowforever.app.model.InstanceSummary;
import dev.wowforever.app.model.MapCoordinates;
import dev.wowforever.app.model.ObjectiveRow;
import dev.wowforever.app.model.ObjectiveSource;
import dev.wowforever.app.model.ObjectiveVerb;
import dev.wowforever.app.model.QuestDetail;
import dev.wowforever.app.model.QuestGiver;
import dev.wowforever.app.model.QuestObjective;
import dev.wowforever.app.model.QuestPageData;
import dev.wowforever.app.model.Spawn;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Resolves a quest's locations and item sources under one database read lock, off the EDT. */
public final class QuestLocator {
  private final Database db;

  public QuestLocator(Database db) {
    this.db = db;
  }

  public Optional<QuestPageData> load(long questId) {
    return db.read(c -> {
      Optional<QuestDetail> detail = QuestRepository.detail(c, questId);
      if (detail.isEmpty()) {
        return Optional.empty();
      }
      Resolver resolver = new Resolver(c);
      List<GiverSpot> starts = new ArrayList<>();
      List<GiverSpot> ends = new ArrayList<>();
      for (QuestGiver giver : detail.get().givers()) {
        GiverSpot spot = new GiverSpot(giver, resolver.visibleSpawns(giver.ref()),
            giver.ref().kind() == EntityKind.ITEM ? resolver.itemSources(giver.ref().id()) : List.of());
        ("start".equals(giver.role()) ? starts : ends).add(spot);
      }
      Map<Integer, List<QuestObjective>> groups = new LinkedHashMap<>();
      QuestRepository.objectives(c, questId).forEach(o ->
          groups.computeIfAbsent(o.ordinal(), ignored -> new ArrayList<>()).add(o));
      List<ObjectiveRow> objectives = new ArrayList<>();
      for (List<QuestObjective> group : groups.values()) {
        objectives.add(resolver.objective(objectives.size() + 1, group));
      }
      return Optional.of(new QuestPageData(detail.get(), QuestRepository.facts(c, questId).orElseThrow(),
          objectives, QuestRepository.sourceItems(c, questId), starts, ends, QuestChainBuilder.build(c, questId)));
    });
  }

  /** Per-load caches avoid repeating source/spawn work for shared credit members or item givers. */
  private static final class Resolver {
    private final Connection c;
    private final Map<EntityRef, List<Spawn>> spawns = new HashMap<>();
    private final Map<Long, List<ObjectiveSource>> items = new HashMap<>();
    private final Map<Long, List<InstanceSummary>> instances = new HashMap<>();

    Resolver(Connection c) {
      this.c = c;
    }

    List<Spawn> rawSpawns(EntityRef ref) throws SQLException {
      if (ref.kind() != EntityKind.NPC && ref.kind() != EntityKind.OBJECT) {
        return List.of();
      }
      if (!spawns.containsKey(ref)) {
        String table = ref.kind() == EntityKind.NPC ? "npc_spawn" : "object_spawn";
        String id = ref.kind() == EntityKind.NPC ? "npc_id" : "object_id";
        spawns.put(ref, Sql.list(c, "SELECT s.zone_id, z.name AS zone_name, s.x, s.y FROM " + table + " s"
            + " LEFT JOIN zone z ON z.id = s.zone_id WHERE s." + id
            + " = ? AND s.x IS NOT NULL AND s.y IS NOT NULL ORDER BY s.rowid LIMIT ?",
            rs -> new Spawn(rs.getLong("zone_id"), rs.getString("zone_name"), rs.getDouble("x"),
                rs.getDouble("y")), ref.id(), ZoneRepository.PIN_CAP));
      }
      return spawns.get(ref);
    }

    List<Spawn> visibleSpawns(EntityRef ref) throws SQLException {
      return rawSpawns(ref).stream().filter(s -> MapCoordinates.onMap(s.x(), s.y())).toList();
    }

    String name(EntityRef ref) throws SQLException {
      String table = switch (ref.kind()) {
        case NPC -> "npc";
        case OBJECT -> "game_object";
        case ITEM -> "item";
        default -> null;
      };
      return table == null ? null : Sql.one(c, "SELECT name FROM " + table + " WHERE id = ?",
          rs -> Optional.ofNullable(rs.getString("name")), ref.id()).orElse(Optional.empty()).orElse(null);
    }

    List<ObjectiveSource> itemSources(long itemId) throws SQLException {
      if (!items.containsKey(itemId)) {
        List<ObjectiveSource> sources = new ArrayList<>();
        for (ObjectiveSource source : Sql.list(c, """
            SELECT d.npc_id, n.name, d.drop_pct, d.pct_source
            FROM item_npc_drop d LEFT JOIN npc n ON n.id = d.npc_id WHERE d.item_id = ?
            ORDER BY d.npc_id""",
            rs -> new ObjectiveSource(new EntityRef(EntityKind.NPC, rs.getLong("npc_id")), rs.getString("name"),
                Sql.doubleValue(rs, "drop_pct"), rs.getString("pct_source"), List.of()), itemId)) {
          sources.add(new ObjectiveSource(source.ref(), source.name(), source.dropPct(), source.pctSource(),
              visibleSpawns(source.ref())));
        }
        for (ObjectiveSource source : Sql.list(c, """
            SELECT d.object_id, o.name FROM item_object_drop d
            LEFT JOIN game_object o ON o.id = d.object_id WHERE d.item_id = ? ORDER BY d.object_id""",
            rs -> new ObjectiveSource(new EntityRef(EntityKind.OBJECT, rs.getLong("object_id")),
                rs.getString("name"), null, null, List.of()), itemId)) {
          sources.add(new ObjectiveSource(source.ref(), source.name(), null, null, visibleSpawns(source.ref())));
        }
        sources.sort(Comparator.comparing(ObjectiveSource::dropPct,
                Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(s -> s.ref().kind() == EntityKind.NPC ? 0 : 1)
            .thenComparing(ObjectiveSource::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparingLong(s -> s.ref().id()));
        items.put(itemId, List.copyOf(sources));
      }
      return items.get(itemId);
    }

    ObjectiveRow objective(int number, List<QuestObjective> group) throws SQLException {
      QuestObjective first = group.getFirst();
      List<EntityRef> targets = new ArrayList<>();
      List<ObjectiveSource> sources = new ArrayList<>();
      for (QuestObjective objective : group) {
        EntityKind kind = switch (objective.kind()) {
          case "creature" -> EntityKind.NPC;
          case "object" -> EntityKind.OBJECT;
          case "item" -> EntityKind.ITEM;
          default -> null;
        };
        if (kind != null && objective.targetId() != null) {
          EntityRef ref = new EntityRef(kind, objective.targetId());
          if (!targets.contains(ref)) {
            targets.add(ref);
            if (kind == EntityKind.ITEM) {
              sources.addAll(itemSources(ref.id()));
            } else {
              sources.add(new ObjectiveSource(ref, name(ref), null, null, visibleSpawns(ref)));
            }
          }
        }
      }
      List<QuestObjective.Point> points = first.points().stream()
          .filter(p -> MapCoordinates.onMap(p.x(), p.y())).toList();
      return new ObjectiveRow(number, ObjectiveVerb.of(first.kind(), first.text()), targets,
          targets.isEmpty() ? null : name(targets.getFirst()), first.text(), sources, points,
          instance(sources, points));
    }

    List<InstanceSummary> instances(long zoneId) throws SQLException {
      if (!instances.containsKey(zoneId)) {
        instances.put(zoneId, Sql.list(c, InstanceRepository.SUMMARY_SELECT + " WHERE i.zone_id = ? ORDER BY i.id",
            InstanceRepository::summary, zoneId));
      }
      return instances.get(zoneId);
    }

    /** Off-map placeholders still identify instance zones; they are never returned as pin locations. */
    InstanceSummary instance(List<ObjectiveSource> sources, List<QuestObjective.Point> points) throws SQLException {
      InstanceSummary result = null;
      for (ObjectiveSource source : sources) {
        for (long zone : rawSpawns(source.ref()).stream().map(Spawn::zoneId).distinct().toList()) {
          List<InstanceSummary> candidates = instances(zone);
          InstanceSummary found = null;
          if (source.ref().kind() == EntityKind.NPC) {
            found = Sql.one(c, InstanceRepository.SUMMARY_SELECT + " " + """
                WHERE i.zone_id = ? AND EXISTS (
                  SELECT 1 FROM boss b WHERE b.instance_id = i.id AND b.npc_id = ?) ORDER BY i.id LIMIT 1""",
                InstanceRepository::summary, zone, source.ref().id()).orElse(null);
          }
          if (found == null && candidates.size() == 1) {
            found = candidates.getFirst();
          }
          if (found == null || result != null && found.id() != result.id()) {
            return null;
          }
          result = found;
        }
      }
      for (QuestObjective.Point point : points) {
        List<InstanceSummary> candidates = instances(point.zoneId());
        if (candidates.size() != 1 || result != null && candidates.getFirst().id() != result.id()) {
          return null;
        }
        result = candidates.getFirst();
      }
      return result;
    }
  }
}
