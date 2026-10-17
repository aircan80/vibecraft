"""Lines of code at every commit of the day, per branch, from git (blob line counts cached by hash)."""
import subprocess, json, os
from datetime import datetime

REPO = r"H:\claude_java"
CODE = (".java", ".py", ".sql", ".lua", ".sh", ".ps1")
DOCS = (".md",)
HERE = os.path.dirname(os.path.abspath(__file__))


def git(*a):
    return subprocess.run(["git", "-C", REPO, *a], capture_output=True, text=True, encoding="utf-8", errors="replace").stdout


cache = {}


def lines_of(blobs):
    missing = [b for b in blobs if b not in cache]
    if missing:
        payload = ("\n".join(missing) + "\n").encode()
        p = subprocess.run(["git", "-C", REPO, "cat-file", "--batch"], input=payload, capture_output=True)
        out = p.stdout
        i = 0
        for b in missing:
            nl = out.index(b"\n", i)
            hdr = out[i:nl].decode()
            i = nl + 1
            size = int(hdr.split()[2])
            body = out[i:i + size]
            i += size + 1
            cache[b] = body.count(b"\n") + (1 if body and not body.endswith(b"\n") else 0)
    return sum(cache[b] for b in blobs)


def measure(commit):
    rows = [l.split("\t") for l in git("ls-tree", "-r", commit).splitlines()]
    kinds = {"main": [], "test": [], "docs": []}
    for meta, path in rows:
        blob = meta.split()[2]
        if path.endswith(CODE):
            is_test = "/test/" in path or "/tests/" in path or path.startswith("tests/")
            kinds["test" if is_test else "main"].append(blob)
        elif path.endswith(DOCS):
            kinds["docs"].append(blob)
    return {k: lines_of(v) for k, v in kinds.items()}


def local_iso(s):
    s = s.strip()
    return datetime.strptime(s, "%Y-%m-%d %H:%M:%S %z").isoformat()


series = [
    ("Phase 2B branch", ["8508453..cd696ab^2"]),
    ("Phase 3A branch", ["8508453..18cdf05^2", "--not", "d88031e"]),
    ("main", ["--first-parent", "8508453^..main"]),
]
out = []
for name, rng in series:
    for line in git("log", "--reverse", "--format=%h|%ci", *rng).splitlines():
        h, when = line.split("|")
        m = measure(h)
        out.append({"series": name, "hash": h, "time": local_iso(when), "main": m["main"], "test": m["test"],
                    "docs": m["docs"], "total": m["main"] + m["test"]})
with open(os.path.join(HERE, "loc.json"), "w", encoding="utf-8") as f:
    json.dump(out, f, indent=0)
print(len(out), "rows")
for name, _ in series:
    r = [o for o in out if o["series"] == name]
    if r:
        print(name, len(r), "first", r[0]["hash"], r[0]["total"], "last", r[-1]["hash"], r[-1]["total"], r[-1]["time"])
