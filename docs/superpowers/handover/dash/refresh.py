"""Regenerate the progress-dashboard data files from git, the SDD ledger and the surefire reports.

Writes tasks.json, commits.json, tests.json, milestones.json, findings.json next to this script.
"""
import glob, json, os, re, subprocess, sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

HERE = os.path.dirname(os.path.abspath(__file__))
MAIN = r"H:\claude_java"
WT = r"H:\claude_java"
LEDGER = os.path.normpath(os.path.join(HERE, "..", "sdd-archive", "2026-10-16-phase2b-ui-overhaul", "progress.md"))
BRANCH = "cd696ab^2"  # the 2B branch tip, reached through the merge commit
LEDGER3A = os.path.normpath(os.path.join(HERE, "..", "sdd-archive", "2026-10-17-phase3a-quest-data", "progress.md"))
BRANCH3A = "18cdf05^2"  # the 3A branch tip, reached through its merge commit
TITLES3A = {
    1: "Schema v3 and the quest raw-field reader",
    2: "Quest objectives, points and source items",
    3: "Quest relations, flags and sort labels",
    4: "Client UI icon textures for the rail",
    5: "Regenerate the shipped database and tests",
    6: "Minimal app readers (no UI yet)",
    7: "NPC patrol routes and despawn points",
    8: "Crafting data from the client",
    9: "Community drop rates (VMaNGOS)",
    10: "Regenerate the shipped database once",
}

LEDGER3B = os.path.join(MAIN, ".superpowers", "sdd", "2026-10-17-phase3b-quest-page-and-wow-look", "progress.md")
BRANCH3B = "feature/phase3b-quest-ui"
TITLES3B = {
    1: "Fonts and theme tokens",
    2: "WoW chrome widgets",
    3: "Rail icons from client art",
    4: "Parchment theme",
    5: "Shared page skeleton and section rules",
    6: "Merged Dropped by, section filter, search chips, list auto-collapse",
    7: "Community drop rates labelled Vanilla rate",
    8: "Map layers, objective pins, coordinates and /way",
    9: "NPC patrol routes",
    10: "Quest page data (QuestLocator)",
    11: "Quest page skeleton: header, stepper, map, givers",
    12: "Objectives section and map filtering",
    13: "Quest chain tree",
    14: "Involved in quests on NPC, object and item pages",
    15: "Quest page keys and copy-to-game links",
    16: "Final polish, quality-fix audit, screenshots and docs",
    17: "Client data 70450 and Forever dungeons (data)",
    18: "Forever dungeon pages (UI)",
    19: "Hotfix-revealed items, build watch, AtlasLoot Forever (data)",
    20: "In-game collector addon and import",
}

TITLES = {
    1: "Theme tokens, dark default, quality colours",
    2: "Master list, icon rail, detail-first split",
    3: "Single-click links, mouse back and forward",
    4: "Search ranking, merged hits, worded subtitles",
    5: "Item page as one scrolling sources page",
    6: "NPC, instance, zone, object and quest pages",
    7: "Type-ahead search, kind chips, recents, paste parsing",
    8: "Filterable list pages from the category rail",
    9: "Keyboard map, window state, text size, accessibility",
    10: "Compact second-screen mode",
    11: "Tooltip card, icons and quality colours everywhere",
    12: "Zone maps with spawn pins",
    13: "Provenance markers and source filter",
    14: "Final polish, screenshots, docs, CI",
    15: "Quest-giver pins on zone maps (owner request)",
}
PART = {n: ("Part 1" if n <= 10 else "Part 2") for n in TITLES}
ORDER = [1,2,3,4,5,6,7,8,9,10,11,12,13,15,14]


def git(cwd, *args):
    return subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, check=True).stdout


def local_iso(s):
    """git %ci ('2026-10-16 17:52:03 -0700') -> '2026-10-16T17:52' (local time, minutes)."""
    return s[:16].replace(" ", "T")


def ledger_lines():
    with open(LEDGER, encoding="utf-8") as f:
        return [l.rstrip("\n") for l in f]


def tasks(lines, commits_by_task):
    done, review, fixing, started = set(), set(), set(), set()
    fix_rounds = {n: 0 for n in TITLES}
    app_tests = {n: None for n in TITLES}
    last_commit = {n: None for n in TITLES}
    for l in lines:
        m = re.match(r"Task (\d+): complete \(commits [0-9a-f^]+\.\.([0-9a-f]+)", l)
        if m:
            n = int(m.group(1)); done.add(n); last_commit[n] = m.group(2)
            t = re.search(r"[Aa]pp (\d{3,4})", l)
            if t: app_tests[n] = int(t.group(1))
            continue
        m = re.match(r"Task (\d+): fix round (\d+)/5", l)
        if m:
            n = int(m.group(1)); fix_rounds[n] = max(fix_rounds[n], int(m.group(2))); fixing.add(n); continue
        m = re.match(r"Task (\d+) review:", l)
        if m:
            n = int(m.group(1)); review.add(n)
            if "fix round" in l and "dispatched" in l: fixing.add(n); fix_rounds[n] = max(fix_rounds[n], 1)
            continue
        m = re.match(r"Task (\d+) (concerns|pre-dispatch|dispatched)", l)
        if m: started.add(int(m.group(1)))
    # a task whose brief exists but which is not done is in progress
    briefs = {int(re.search(r"task-(\d+)-brief", p).group(1))
              for p in glob.glob(os.path.join(os.path.dirname(LEDGER), "task-*-brief.md"))}
    reports = {int(re.search(r"task-(\d+)-report", p).group(1))
               for p in glob.glob(os.path.join(os.path.dirname(LEDGER), "task-*-report.md"))}
    rows = []
    next_up = next((n for n in ORDER if n not in done and n not in review and n not in fixing and n not in reports), None)
    for n in ORDER:
        if n in done: status = "Done"
        elif n in fixing: status = "Fix round"
        elif n in review: status = "In review"
        elif n in reports or (n in started and n in briefs): status = "In progress"
        elif n == next_up: status = "Up next"
        else: status = "Planned"
        if last_commit[n] is None and commits_by_task.get(n):
            last_commit[n] = commits_by_task[n][-1]
        rows.append({"task": n, "title": TITLES[n], "part": PART[n], "status": status,
                     "fix_rounds": fix_rounds[n] if n in done or n in fixing or n in review or n in reports else None,
                     "app_tests": app_tests[n], "importer_tests": None, "commit": last_commit[n]})
    return rows


def commits(lines):
    # commit → task from the ledger's ranges
    ranges = []
    for l in lines:
        m = re.match(r"Task (\d+): (?:complete|fix round \d+/5) \(.*?commits? ([0-9a-f^]+)\.\.([0-9a-f]+)", l)
        if m: ranges.append((int(m.group(1)), m.group(2).rstrip("^"), m.group(3)))
        m2 = re.search(r"Task (\d+): fix round \d+/5 \(.*?commit ([0-9a-f]+)\)", l)
        if m2: ranges.append((int(m2.group(1)), m2.group(2) + "^", m2.group(2)))
    task_of = {}
    for n, a, b in ranges:
        try:
            for h in git(WT, "rev-list", "--abbrev-commit", f"{a}..{b}").split():
                task_of.setdefault(h[:7], n)
        except subprocess.CalledProcessError:
            pass
    out, by_task = [], {}
    done_tasks = {int(m.group(1)) for l in lines for m in [re.match(r"Task (\d+): complete", l)] if m}
    highest = 0
    log = git(WT, "log", "--reverse", "--format=%h|%ci|%s", f"3da3ab9..{BRANCH}")
    # the branch left main before 2A merged: include its own first commits too
    for line in log.strip().splitlines():
        h, when, subject = line.split("|", 2)
        kind = subject.split("(")[0].split(":")[0]
        n = task_of.get(h)
        if n is None and kind == "feat" and "Merge" not in subject:
            n = min((t for t in TITLES if t not in done_tasks and t > highest), default=None)
        if n:
            highest = max(highest, n)
        out.append({"hash": h, "time": local_iso(when), "type": kind, "task": n, "subject": subject})
        if n: by_task.setdefault(n, []).append(h)
    return out, by_task


def tests():
    # after the v0.2.0 merge the numbers come from main's merge build (hand-maintained main-green.json);
    # the main checkout's surefire reports belong to the 3A branch and are not main's.
    mg = os.path.join(HERE, "main-green.json")
    if os.path.exists(mg):
        return json.load(open(mg, encoding="utf-8"))
    roles = {"app": "Swing browser", "importer": "Data importer", "lua-table": "Lua table reader"}
    rows = []
    for mod in ("app", "importer", "lua-table"):
        files = glob.glob(os.path.join(WT, mod, "target", "surefire-reports", "TEST-*.xml"))
        t = f_ = 0
        for p in files:
            r = ET.parse(p).getroot()
            t += int(r.get("tests", 0)); f_ += int(r.get("failures", 0)) + int(r.get("errors", 0))
        rows.append({"module": mod, "role": roles[mod], "tests": t, "failures": f_, "classes": len(files)})
    cache = os.path.join(HERE, "last-green.json")
    if all(r["failures"] == 0 and r["tests"] > 0 for r in rows):
        json.dump(rows, open(cache, "w", encoding="utf-8"))
        return rows
    if os.path.exists(cache):
        return json.load(open(cache, encoding="utf-8"))
    return rows


def milestones(lines):
    v01 = local_iso(git(MAIN, "log", "-1", "--format=%ci", "v0.1.0"))
    merge2a = local_iso(git(MAIN, "log", "-1", "--format=%ci", "--grep=Merge feature/phase2a", "main"))
    text = "\n".join(lines)
    part1 = "Part 1 complete" in text
    part1_when = None
    gate = "Part 1 gate passed" in text
    rebased = "rebased onto main" in text
    part2 = "Part 2 complete" in text
    v02 = local_iso(git(MAIN, "log", "-1", "--format=%ci", "v0.2.0")) if part2 else None
    return [
        {"step": 1, "milestone": "v0.1 browser", "status": "Done", "when": v01, "detail": "Item and drop browser merged, tagged v0.1.0"},
        {"step": 2, "milestone": "Phase 2A client data", "status": "Done", "when": merge2a, "detail": "Stats, tooltips, icons, maps and provenance merged"},
        {"step": 3, "milestone": "Phase 2B Part 1", "status": "Done" if part1 else "In progress", "when": None, "detail": "UI overhaul without the new data: tasks 1 to 10"},
        {"step": 4, "milestone": "Part 1 gate", "status": "Done" if rebased else ("In progress" if part1 else "Planned"), "when": None, "detail": "Full build, visual check, rebase onto the 2A data"},
        {"step": 5, "milestone": "Phase 2B Part 2, v0.2.0", "status": "Done" if part2 else ("In progress" if rebased else "Planned"), "when": v02, "detail": "Tooltips, maps, provenance, quest pins, polish, final review and fix wave; merged to main and tagged v0.2.0"},
    ]


def tasks3a():
    """Phase 3A rows: task ids 21-29 so they never collide with the 2B numbers."""
    if not os.path.exists(LEDGER3A):
        return [], [], {}
    with open(LEDGER3A, encoding="utf-8") as f:
        lines = [l.rstrip("\n") for l in f]
    done, started, review, fixing = {}, set(), set(), set()
    fix_rounds = {n: 0 for n in TITLES3A}
    for l in lines:
        m = re.match(r"Task (\d+): complete \((?:commits? )([0-9a-f]+(?: \+ [0-9a-f]+)*)", l)
        if m:
            done[int(m.group(1))] = m.group(2).split(" + ")[-1]; continue
        m = re.match(r"Task (\d+) dispatched", l)
        if m: started.add(int(m.group(1))); continue
        m = re.match(r"Task (\d+) reported", l)
        if m: review.add(int(m.group(1))); continue
        m = re.match(r"Task (\d+):? (?:fix round|fix for|fix commit)", l)
        if m:
            n = int(m.group(1)); fixing.add(n); fix_rounds[n] = max(fix_rounds[n], 1)
    ws = os.path.dirname(LEDGER3A)
    reports = {int(re.search(r"task-(\d+)-report", q).group(1)) for q in glob.glob(os.path.join(ws, "task-*-report.md"))}
    rows = []
    measured = {}
    for q in glob.glob(os.path.join(HERE, "task-tests", "task-*.json")):
        with open(q, encoding="utf-8") as f:
            m = json.load(f)
        if m.get("exit") == 0 and all(v["failures"] == 0 for v in m["modules"].values()):
            measured[m["task"]] = {k: v["tests"] for k, v in m["modules"].items()}
    next_up = next((n for n in TITLES3A if n not in done and n not in started), None)
    for n in TITLES3A:
        if n in done: status = "Done"
        elif n in fixing and n not in done: status = "Fix round"
        elif n in review or n in reports: status = "In review"
        elif n in started: status = "In progress"
        elif n == next_up: status = "Up next"
        else: status = "Planned"
        rows.append({"task": 20 + n, "title": "3A-%d  %s" % (n, TITLES3A[n]), "part": "Phase 3A", "status": status,
                     "fix_rounds": fix_rounds[n] if n in done or n in fixing or n in review else None,
                     "app_tests": measured.get(n, {}).get("app"), "importer_tests": measured.get(n, {}).get("importer"),
                     "commit": done.get(n)})
    # commits on the 3A branch, task attribution by subject keyword
    keys = {1: "schema v3", 2: "quest objectives", 3: "quest relations", 4: "ui icon", 5: "regenerate", 6: "app readers",
            7: "patrol", 8: "crafting", 9: "vmangos"}
    out = []
    log = git(MAIN, "log", "--reverse", "--format=%h|%ci|%s", f"d88031e..{BRANCH3A}")
    for line in log.strip().splitlines():
        h, when, subject = line.split("|", 2)
        kind = subject.split("(")[0].split(":")[0]
        n = None
        if kind in ("feat", "fix"):
            for k, kw in keys.items():
                if kw in subject.lower(): n = 20 + k; break
            if n is None and "preQuestGroup" in subject: n = 23
        out.append({"hash": h, "time": local_iso(when), "type": kind, "task": n, "subject": subject})
    text = "\n".join(lines)
    v4 = "Backlog v4 committed" in text
    all_done = all(n in done for n in TITLES3A)
    miles = [
        {"step": 6, "milestone": "Phase 3 design + product backlog", "status": "Done" if v4 else "In progress", "when": None,
         "detail": "Quest companion + WoW look spec; PM backlog v4 with WoW: Forever systems sized on verified build data"},
        {"step": 7, "milestone": "Phase 3A quest data", "status": "Done" if all_done else ("In progress" if done else "Planned"), "when": local_iso(git(MAIN, "log", "-1", "--format=%ci", "18cdf05")) if all_done else None,
         "detail": "Objectives, relations, UI icons, patrol routes, crafting, VMaNGOS rates, one DB regeneration: tasks 3A-1 to 3A-10"},
    ]
    return rows, out, miles


def tasks3b():
    """Phase 3B rows: task ids 31-46. Ledger lines follow the 3A conventions; counts come from "app NNN" / "importer NNN"."""
    if not os.path.exists(LEDGER3B):
        return [], [], []
    with open(LEDGER3B, encoding="utf-8") as f:
        lines = [l.rstrip("\n") for l in f]
    done, started, review, fixing = {}, set(), set(), set()
    fix_rounds = {n: 0 for n in TITLES3B}
    app_tests = {n: None for n in TITLES3B}
    for l in lines:
        m = re.match(r"Task (\d+): complete \((?:commits? )([0-9a-f]+(?: \+ [0-9a-f]+)*)", l)
        if m:
            n = int(m.group(1)); done[n] = m.group(2).split(" + ")[-1]
            t = re.search(r"[Aa]pp (\d{3,4})", l)
            if t: app_tests[n] = int(t.group(1))
            continue
        m = re.match(r"Task (\d+) dispatched", l)
        if m: started.add(int(m.group(1))); continue
        m = re.match(r"Task (\d+) reported", l)
        if m: review.add(int(m.group(1))); continue
        m = re.match(r"Task (\d+):? (?:fix round|fix for|fix commit)", l)
        if m:
            n = int(m.group(1)); fixing.add(n); fix_rounds[n] = max(fix_rounds[n], 1)
    rows = []
    next_up = next((n for n in TITLES3B if n not in done and n not in started), None)
    for n in TITLES3B:
        if n in done: status = "Done"
        elif n in fixing and n not in done: status = "Fix round"
        elif n in review: status = "In review"
        elif n in started: status = "In progress"
        elif n == next_up: status = "Up next"
        else: status = "Planned"
        rows.append({"task": 30 + n, "title": "3B-%d  %s" % (n, TITLES3B[n]), "part": "Phase 3B", "status": status,
                     "fix_rounds": fix_rounds[n] if n in done or n in fixing or n in review else None,
                     "app_tests": app_tests[n], "importer_tests": None, "commit": done.get(n)})
    out = []
    keys = {1: "marcellus", 2: "wow chrome", 3: "rail icons", 4: "parchment", 5: "page skeleton", 6: "merged dropped by",
            7: "vanilla rate", 8: "objective pin", 9: "patrol routes", 10: "quest page locator", 11: "quest page header",
            12: "quest objectives", 13: "quest chain tree", 14: "involved in quests", 15: "quest page keys", 16: "v0.3.0 screenshots", 17: "forever dungeons", 18: "forever dungeon pages", 19: "hotfix-revealed", 20: "collector"}
    log = git(MAIN, "log", "--reverse", "--format=%h|%ci|%s", f"main..{BRANCH3B}")
    for line in log.strip().splitlines():
        h, when, subject = line.split("|", 2)
        kind = subject.split("(")[0].split(":")[0]
        n = None
        for k, kw in keys.items():
            if kw in subject.lower(): n = 30 + k; break
        out.append({"hash": h, "time": local_iso(when), "type": kind, "task": n, "subject": subject})
    all_done = all(n in done for n in TITLES3B)
    status = "Done" if all_done else ("In progress" if (done or started) else "Planned")
    miles = [{"step": 8, "milestone": "Phase 3B quest page + WoW look, v0.3.0", "status": status, "when": None,
              "detail": "Quest page, chain tree, objective pins, rail icons, chrome, Parchment theme, Vanilla rates, coordinates, copy helpers, plus the owner-reported Forever dungeons and client refresh: tasks 3B-1 to 3B-18"}]
    return rows, out, miles


def findings():
    with open(os.path.join(HERE, "findings-source.json"), encoding="utf-8") as f:
        return json.load(f)


def main():
    lines = ledger_lines()
    cm, by_task = commits(lines)
    t3, c3, m3 = tasks3a()
    t4, c4, m4 = tasks3b()
    data = {"tasks.json": tasks(lines, by_task) + t3 + t4, "commits.json": cm + c3 + c4, "tests.json": tests(),
            "milestones.json": milestones(lines) + m3 + m4, "findings.json": findings()}
    for name, rows in data.items():
        with open(os.path.join(HERE, name), "w", encoding="utf-8") as f:
            json.dump(rows, f, indent=1)
    print(json.dumps({k: len(v) for k, v in data.items()}), datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"))
    for r in data["tasks.json"]:
        print(r["task"], r["status"], r["fix_rounds"], r["app_tests"], r["commit"])
    print(data["tests.json"])


if __name__ == "__main__":
    main()
