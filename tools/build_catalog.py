#!/usr/bin/env python3
"""Genera el catálogo de ejercicios de FITSTAT.

- Lee tools/catalog_src.txt (nombres/descripciones en español curados a mano).
- Cruza con free-exercise-db (dominio público) para músculos, nivel, mecánica e imágenes.
- Descarga y reduce las imágenes a app/src/main/assets/ex/<id>_<n>.jpg
- Escribe app/src/main/java/app/fitstat/data/CatalogData.kt
Uso: python3 tools/build_catalog.py exercises.json
"""
import json, os, re, subprocess, sys, unicodedata, urllib.parse, urllib.request, tempfile
import concurrent.futures as cf

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "tools", "catalog_src.txt")
DB = sys.argv[1]
ASSETS = os.path.join(ROOT, "app/src/main/assets/ex")
OUT = os.path.join(ROOT, "app/src/main/java/app/fitstat/data/CatalogData.kt")
RAW = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"
MAXW = 560

MUS = {
    "abdominals": "ABS", "abductors": "ABDUCTORS", "adductors": "ADDUCTORS", "biceps": "BICEPS",
    "calves": "CALVES", "chest": "CHEST", "forearms": "FOREARMS", "glutes": "GLUTES",
    "hamstrings": "HAMSTRINGS", "lats": "LATS", "lower back": "LOWER_BACK", "middle back": "MID_BACK",
    "neck": "NECK", "quadriceps": "QUADS", "shoulders": "SHOULDERS", "traps": "TRAPS", "triceps": "TRICEPS",
}
EQ = {
    "barbell": "Barra", "dumbbell": "Mancuernas", "cable": "Polea", "machine": "Máquina",
    "body only": "Peso corporal", "e-z curl bar": "Barra Z", "kettlebells": "Kettlebell",
    "bands": "Banda elástica", "exercise ball": "Balón", "medicine ball": "Balón medicinal",
    "other": "Otro", "foam roll": "Rodillo", None: "Peso corporal",
}
LEVEL = {"beginner": "Principiante", "intermediate": "Intermedio", "expert": "Avanzado"}
MECH = {"compound": "Compuesto", "isolation": "Aislado", None: ""}
FORCE = {"push": "Empuje", "pull": "Jalón", "static": "Isométrico", None: ""}

# Ajustes puntuales de músculos donde el dato original no refleja bien el ejercicio.
OVERRIDE = {
    "Farmers_Walk": (["FOREARMS"], ["TRAPS", "ABS", "GLUTES"]),
    "Dumbbell_Side_Bend": (["ABS"], ["LOWER_BACK"]),
    "Cable_Hip_Adduction": (["ADDUCTORS"], ["QUADS"]),
    "One-Arm_Kettlebell_Swings": (["GLUTES", "HAMSTRINGS"], ["LOWER_BACK", "SHOULDERS"]),
    "Kettlebell_Turkish_Get-Up_Squat_style": (["SHOULDERS"], ["ABS", "GLUTES", "QUADS"]),
    "Hip_Flexion_with_Band": (["QUADS"], ["ABS"]),
}


def slug(name):
    s = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode()
    s = re.sub(r"[^a-zA-Z0-9]+", "_", s.lower()).strip("_")
    return s


def kq(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def fetch(url, dest, tries=4):
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "fitstat-build"})
            with urllib.request.urlopen(req, timeout=40) as r, open(dest, "wb") as f:
                f.write(r.read())
            return True
        except Exception as e:  # noqa
            err = e
    print("  ! fallo", url, err)
    return False


def process_image(args):
    ds_id, idx, out_name = args
    out = os.path.join(ASSETS, out_name)
    if os.path.exists(out) and os.path.getsize(out) > 1000:
        return out_name, True
    with tempfile.NamedTemporaryFile(suffix=".jpg", delete=False) as t:
        tmp = t.name
    ok = fetch(RAW + urllib.parse.quote(ds_id) + "/%d.jpg" % idx, tmp)
    if ok:
        subprocess.run(["convert", tmp, "-resize", "%dx>" % MAXW, "-strip", "-quality", "64", out], check=True)
    try:
        os.unlink(tmp)
    except OSError:
        pass
    return out_name, ok


def main():
    os.makedirs(ASSETS, exist_ok=True)
    db = {e["id"]: e for e in json.load(open(DB))}
    items, used, jobs = [], set(), []
    for line in open(SRC, encoding="utf8"):
        line = line.rstrip("\n")
        if not line.strip() or line.startswith("#"):
            continue
        ds_id, name, cat, eq_over, aliases, desc = line.split("|")
        e = db.get(ds_id)
        if not e:
            print("!! no existe en la base:", ds_id)
            continue
        base = slug(name)
        sid, n = base, 2
        while sid in used:
            sid = f"{base}_{n}"
            n += 1
        used.add(sid)
        prim = [MUS[m] for m in e["primaryMuscles"] if m in MUS]
        sec = [MUS[m] for m in e["secondaryMuscles"] if m in MUS]
        if ds_id in OVERRIDE:
            prim, sec = OVERRIDE[ds_id]
        equip = eq_over or EQ.get(e.get("equipment"), "Otro")
        if "Smith" in name:
            equip = "Smith"
        nimg = min(2, len(e.get("images", [])))
        for i in range(nimg):
            jobs.append((ds_id, i, f"{sid}_{i}.jpg"))
        items.append(dict(id=sid, name=name, cat=cat, equip=equip,
                          level=LEVEL.get(e.get("level"), ""), mech=MECH.get(e.get("mechanic"), ""),
                          force=FORCE.get(e.get("force"), ""), prim=prim, sec=sec, nimg=nimg,
                          aliases=aliases, desc=desc, ds=ds_id))
    print(len(items), "ejercicios,", len(jobs), "imágenes")

    done = {}
    with cf.ThreadPoolExecutor(max_workers=6) as ex:
        for name, ok in ex.map(process_image, jobs):
            done[name] = ok
    miss = [k for k, v in done.items() if not v]
    print("imágenes fallidas:", miss)
    # Si falta alguna imagen, ajustar nimg
    for it in items:
        cnt = sum(1 for i in range(it["nimg"]) if done.get(f"{it['id']}_{i}.jpg"))
        it["nimg"] = cnt

    lines = ["// ARCHIVO GENERADO por tools/build_catalog.py — no editar a mano.",
             "package app.fitstat.data", "",
             "internal val CATALOG_DATA: List<Exercise> = listOf("]
    for it in items:
        lines.append(
            "    cat({id}, {name}, {cat}, {equip}, {level}, {mech}, {force},\n"
            "        {prim}, {sec}, {nimg},\n"
            "        {aliases},\n"
            "        {desc}),".format(
                id=kq(it["id"]), name=kq(it["name"]), cat=kq(it["cat"]), equip=kq(it["equip"]),
                level=kq(it["level"]), mech=kq(it["mech"]), force=kq(it["force"]),
                prim=kq(",".join(it["prim"])), sec=kq(",".join(it["sec"])), nimg=it["nimg"],
                aliases=kq(it["aliases"]), desc=kq(it["desc"])))
    lines.append(")")
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, "w", encoding="utf8").write("\n".join(lines) + "\n")

    # Resumen para revisión
    from collections import Counter
    print(Counter(i["cat"] for i in items))
    total = sum(os.path.getsize(os.path.join(ASSETS, f)) for f in os.listdir(ASSETS))
    print("peso de imágenes: %.1f MB" % (total / 1e6))
    for it in items:
        print(f"{it['id'][:42]:42} | {it['cat'][:6]:6} | {','.join(it['prim']):18} | {','.join(it['sec'])}")


main()
