#!/usr/bin/env python3
"""Reproduce an explicit 21-NPC comparison corpus from authentic source files.

This corpus is deliberately named, not a representative sample or a release-gate bypass.
No tolerance filtering: all 21 nearest genuine spawn/Questie pairs, including outliers,
are emitted. Inputs are read-only. See importer/src/test/resources/worldmap/README.txt.
"""
import argparse
import csv
import sqlite3

NPC_IDS = (448, 550, 6, 30, 36, 38, 40, 43, 46, 54, 60, 61, 66, 68, 69, 74, 78, 80, 94, 95, 97)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assignments", required=True)
    parser.add_argument("--vm-database", required=True)
    parser.add_argument("--app-database", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    with open(args.assignments, newline="", encoding="utf-8-sig") as stream:
        assignments = list(csv.DictReader(stream))
    vm = sqlite3.connect("file:" + args.vm_database + "?mode=ro", uri=True)
    app = sqlite3.connect("file:" + args.app_database + "?mode=ro", uri=True)
    rows = []
    for npc in NPC_IDS:
        candidates = []
        for guid, world, x, y, z in vm.execute(
                "SELECT guid,map,position_x,position_y,position_z FROM creature WHERE id=? ORDER BY guid", (npc,)):
            targets = app.execute("SELECT s.zone_id,z.ui_map_id,s.x,s.y FROM npc_spawn s "
                                  "JOIN zone z ON z.id=s.zone_id WHERE npc_id=? ORDER BY zone_id,x,y", (npc,))
            for zone, ui, qx, qy in targets:
                matches = [r for r in assignments if int(r["UiMapID"]) == ui and int(r["MapID"]) == world
                           and int(r["WMODoodadPlacementID"]) == 0 and int(r["WMOGroupID"]) == 0
                           and all(float(r["Region_" + str(i)]) <= point <= float(r["Region_" + str(i+3)])
                                   for i, point in enumerate((x, y, z)))
                           and float(r["Region_3"]) > float(r["Region_0"])
                           and float(r["Region_4"]) > float(r["Region_1"])]
                if not matches:
                    continue
                r = min(matches, key=lambda r: ((float(r["Region_3"])-float(r["Region_0"])) *
                                                (float(r["Region_4"])-float(r["Region_1"])),
                                               int(r["OrderIndex"]), int(r["ID"])))
                px = 100*(float(r["UiMax_0"])-(y-float(r["Region_1"]))/
                          (float(r["Region_4"])-float(r["Region_1"]))*
                          (float(r["UiMax_0"])-float(r["UiMin_0"])))
                py = 100*(float(r["UiMax_1"])-(x-float(r["Region_0"]))/
                          (float(r["Region_3"])-float(r["Region_0"]))*
                          (float(r["UiMax_1"])-float(r["UiMin_1"])))
                candidates.append((max(abs(px-qx), abs(py-qy)), guid, zone, qx, qy,
                                   [npc, guid, world, x, y, z, zone, ui, qx, qy, int(r["ID"])]))
        if not candidates:
            raise RuntimeError("No genuine comparable spawn for NPC " + str(npc))
        nearest = min(candidates)
        rows.append(nearest[-1])
        print("NPC %s: maximum-axis error %.9f%s" %
              (npc, nearest[0], " OUTLIER" if nearest[0] > 1 else ""))
    with open(args.output, "w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream)
        writer.writerow(("npc_id", "vm_guid", "map_id", "world_x", "world_y", "world_z", "questie_zone_id",
                         "ui_map_id", "questie_x", "questie_y", "assignment_id"))
        writer.writerows(rows)


if __name__ == "__main__":
    main()
