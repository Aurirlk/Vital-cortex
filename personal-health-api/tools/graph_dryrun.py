# -*- coding: utf-8 -*-
"""
阶段 0-B 抽取规则的离线核算脚本（只读 MySQL，不改任何数据）。

用途：在无法启动 Spring 的情况下，用真实数据验证 GraphExtractor 的规则
能产出多少节点/边，并核对两线药品的不一致清单。
用法：python tools/graph_dryrun.py
"""
import collections
import json
import os
import re
import subprocess

MYSQL = r"D:\Program Files\MySQL\bin\mysql.exe"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SEED = os.path.join(ROOT, "src", "main", "resources", "ai-data", "seed-triples.json")
DRUGS = os.path.join(ROOT, "ai_data", "drugs.json")


def q(sql):
    r = subprocess.run(
        [MYSQL, "-uroot", "-p1234", "-D", "personal_health",
         "--default-character-set=utf8mb4", "-N", "-e", sql],
        capture_output=True)
    out = r.stdout.decode("utf-8", "replace").replace("\r", "")
    return [l for l in out.split("\n") if l.strip() != ""]


def cells(row):
    return [None if c == "NULL" else c for c in row.split("\t")]


seed = json.load(open(SEED, encoding="utf-8"))
seed_nodes = {n["name"]: n for n in seed["nodes"]}
alias = {}
for n in seed["nodes"]:
    for a in n.get("alias", []):
        alias[a] = n["name"]


def canon(x):
    return alias.get(x, x)


nodes = set()
edges = set()

for n in seed["nodes"]:
    nodes.add((n["label"], n["name"]))
for e in seed["edges"]:
    nodes.add((e["subjectLabel"], e["subject"]))
    nodes.add((e["objectLabel"], e["object"]))
    edges.add((e["subjectLabel"], e["subject"], e["relation"],
               e["objectLabel"], e["object"]))
print("种子词表        → 节点 %d / 边 %d" % (len(nodes), len(edges)))

# ---- 指标字典 ----
rows = q("SELECT name FROM health_model_config")
for r in rows:
    nodes.add(("Indicator", r.strip()))
print("指标字典        → Indicator %d" % len(rows))


def parse_list(s):
    if not s or s.strip() in ("", "[]"):
        return []
    try:
        arr = json.loads(s)
        out = []
        for it in arr:
            out.append(it.get("name") if isinstance(it, dict) else str(it))
        return [x.strip() for x in out if x and x.strip()]
    except Exception:
        return [x.strip() for x in s.replace("，", ",").split(",") if x.strip()]


KIN = ["父亲", "母亲", "祖父", "祖母", "外祖父", "外祖母", "哥哥", "姐姐",
       "弟弟", "妹妹", "儿子", "女儿", "爷爷", "奶奶", "外公", "外婆"]


def strip_kin(s):
    for k in KIN:
        if s.startswith(k) and len(s) > len(k):
            return s[len(k):].strip()
    return s


# ---- 患者档案 ----
prof = q("SELECT user_id, chronic_diseases, allergies, medications, surgeries,"
         " family_history FROM patient_profile ORDER BY user_id")
cnt = collections.Counter()
for row in prof:
    uid, cd, al, med, sur, fh = cells(row)
    p = "患者#" + uid
    nodes.add(("Patient", p))
    for d in parse_list(cd):
        c = canon(d)
        nodes.add(("Disease", c))
        edges.add(("Patient", p, "HAS_DIAGNOSIS", "Disease", c))
        cnt["HAS_DIAGNOSIS"] += 1
    for a in parse_list(al):
        nodes.add(("Allergen", a))
        edges.add(("Patient", p, "ALLERGIC_TO", "Allergen", a))
        cnt["ALLERGIC_TO"] += 1
    for m in parse_list(med):
        nodes.add(("Drug", m))
        edges.add(("Patient", p, "TAKES_DRUG", "Drug", m))
        cnt["TAKES_DRUG"] += 1
    for s in parse_list(sur):
        nodes.add(("Surgery", s))
        edges.add(("Patient", p, "HAD_SURGERY", "Surgery", s))
        cnt["HAD_SURGERY"] += 1
    for f in parse_list(fh):
        d = canon(strip_kin(f))
        nodes.add(("Disease", d))
        edges.add(("Patient", p, "FAMILY_HISTORY_OF", "Disease", d))
        cnt["FAMILY_HISTORY_OF"] += 1
print("患者档案        → %d 份，边 %d %s" % (len(prof), sum(cnt.values()), dict(cnt)))

# ---- 指标测量 ----
mh = q("SELECT uh.user_id, hmc.name FROM user_health uh "
       "LEFT JOIN health_model_config hmc ON hmc.id = uh.health_model_config_id")
before = len(edges)
for r in mh:
    uid, name = cells(r)
    if not name:
        continue
    p = "患者#" + uid
    nodes.add(("Patient", p))
    nodes.add(("Indicator", name.strip()))
    edges.add(("Patient", p, "HAS_MEASUREMENT", "Indicator", name.strip()))
print("指标测量        → %d 条记录，去重后新增边 %d（图不爆炸）" % (len(mh), len(edges) - before))

DOSAGE = ["缓释片", "肠溶片", "分散片", "注射液", "口服液", "气雾剂", "乳膏",
          "胶囊", "颗粒", "滴剂", "软膏", "片", "丸", "散"]
HINTS = ["氨氯地平", "二甲双胍"]


def norm_diag(s):
    s = re.sub(r"[（(][^）)]*[）)]", "", s)
    s = re.sub(r"[0-9]+\s*级", "", s).strip()
    for p in ["原发性", "继发性", "轻度", "中度", "重度", "急性", "慢性"]:
        if s.startswith(p) and len(s) > len(p):
            s = s[len(p):].strip()
            break
    return s


def pick_drugs(s):
    if not s:
        return []
    out = []
    for line in s.replace("\\n", "\n").split("\n"):
        line = line.strip()
        if not line:
            continue
        head = line.split()[0]
        ok = any(head.endswith(d) for d in DOSAGE)
        if not ok:
            for h in HINTS:
                if h in line:
                    head = h
                    ok = True
                    break
        if ok:
            out.append(head)
    return out


vr = q("SELECT id, patient_id, diagnosis, prescription FROM visit_record ORDER BY id")
for r in vr:
    vid, pid, diag, rx = cells(r)
    v = "就诊#" + vid
    nodes.add(("Visit", v))
    p = "患者#" + pid
    nodes.add(("Patient", p))
    edges.add(("Patient", p, "HAD_VISIT", "Visit", v))
    if diag:
        d = norm_diag(diag)
        if d:
            c = canon(d)
            nodes.add(("Disease", c))
            edges.add(("Visit", v, "DIAGNOSED_WITH", "Disease", c))
    for dr in pick_drugs(rx):
        nodes.add(("Drug", dr))
        edges.add(("Visit", v, "PRESCRIBED", "Drug", dr))
print("就诊记录        → %d 条，累计 节点 %d / 边 %d" % (len(vr), len(nodes), len(edges)))

# ---- 药品 ----
dj = json.load(open(DRUGS, encoding="utf-8"))
json_drugs = set()
for d in dj["drugs"]:
    nm = d["name"].strip()
    json_drugs.add(nm)
    nodes.add(("Drug", nm))
    if d.get("category"):
        cat = d["category"].strip()
        nodes.add(("Category", cat))
        edges.add(("Drug", nm, "BELONGS_TO", "Category", cat))
    desc = d.get("description") or ""
    for k in sorted(seed_nodes.keys(), key=len, reverse=True):
        if k in desc and seed_nodes[k]["label"] in ("Disease", "Symptom"):
            lab = seed_nodes[k]["label"]
            nodes.add((lab, k))
            edges.add((lab, k, "TREATED_BY", "Drug", nm))

prod = q("SELECT name, medical_category FROM mall_product WHERE product_type = 'drug'")
mall = set()
for r in prod:
    parts = cells(r)
    nm = parts[0].strip()
    cat = parts[1] if len(parts) > 1 else None
    mall.add(nm)
    nodes.add(("Product", nm))
    nodes.add(("Drug", nm))
    edges.add(("Drug", nm, "SOLD_AS", "Product", nm))
    if cat:
        cat = cat.strip()
        nodes.add(("Category", cat))
        edges.add(("Drug", nm, "BELONGS_TO", "Category", cat))
only_json = sorted(json_drugs - mall)
print("药品两线        → drugs.json %d 个名称 / 商城 %d 个名称；仅 JSON 有 %d 个：%s"
      % (len(json_drugs), len(mall), len(only_json), "、".join(only_json)))

# ---- 文章 ----
news = q("SELECT COALESCE(title, name), COALESCE(summary, ''), COALESCE(content, '')"
         " FROM news WHERE content_type = 'NEWS'")
order = sorted(seed_nodes.keys(), key=len, reverse=True)
ment = 0
for r in news:
    title, sm, ct = cells(r)
    text = title + " " + sm + " " + ct
    hits = []
    covered = []
    for k in order:
        start = 0
        idx = -1
        while True:
            j = text.find(k, start)
            if j < 0:
                break
            if any(j < e and j + len(k) > s for s, e in covered):
                start = j + 1
                continue
            idx = j
            break
        if idx >= 0:
            hits.append(k)
            covered.append((idx, idx + len(k)))
    for h in hits[:8]:
        nodes.add(("Article", title.strip()))
        lab = seed_nodes[h]["label"]
        nodes.add((lab, h))
        edges.add(("Article", title.strip(), "MENTIONS", lab, h))
        ment += 1
print("知识文章        → %d 篇，MENTIONS 边 %d" % (len(news), ment))

print()
print("=== 预估图谱规模：节点 %d / 边 %d ===" % (len(nodes), len(edges)))
print("标签分布   :", dict(collections.Counter(l for l, _ in nodes)))
print("关系分布   :", dict(collections.Counter(e[2] for e in edges)))
