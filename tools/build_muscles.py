#!/usr/bin/env python3
"""Genera app/src/main/java/app/fitstat/ui/design/MuscleGeo.kt a partir de react-body-highlighter (MIT, GV79).

Uso: python3 tools/build_muscles.py <ruta>/react-body-highlighter/src/assets/index.ts [preview.png]
Los polígonos viven en un lienzo de 100 x 200. Se simplifican a 2 decimales.
"""
import os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app/src/main/java/app/fitstat/ui/design/MuscleGeo.kt")
SRC = open(sys.argv[1], encoding="utf8").read()
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else None

NAMES = {
    "TRAPEZIUS": "traps", "UPPER_BACK": "upper_back", "LOWER_BACK": "lower_back", "CHEST": "chest",
    "BICEPS": "biceps", "TRICEPS": "triceps", "FOREARM": "forearm", "BACK_DELTOIDS": "rear_delt",
    "FRONT_DELTOIDS": "front_delt", "ABS": "abs", "OBLIQUES": "obliques", "ABDUCTOR": "abductor_back",
    "ABDUCTORS": "adductor_front", "HAMSTRING": "hamstring", "QUADRICEPS": "quads", "CALVES": "calves",
    "GLUTEAL": "glutes", "HEAD": "head", "NECK": "neck", "KNEES": "knees", "LEFT_SOLEUS": "soleus_l",
    "RIGHT_SOLEUS": "soleus_r",
}


def parse(block):
    regs = []
    for m in re.finditer(r"muscle:\s*MuscleType\.(\w+),\s*svgPoints:\s*\[(.*?)\],\s*\}", block, re.S):
        key = NAMES[m.group(1)]
        polys = re.findall(r"'([^']+)'", m.group(2))
        cleaned = []
        for p in polys:
            nums = [float(x) for x in p.split()]
            cleaned.append(" ".join(("%.2f" % n).rstrip("0").rstrip(".") for n in nums))
        regs.append((key, cleaned))
    return regs


a0 = SRC.index("export const anteriorData")
p0 = SRC.index("export const posteriorData")
front = parse(SRC[a0:p0])
back = parse(SRC[p0:])


def emit(name, regs):
    out = [f"    val {name}: Array<MuscleRegion> = arrayOf("]
    for key, polys in regs:
        out.append(f'        MuscleRegion("{key}", arrayOf(')
        for p in polys:
            out.append(f'            "{p}",')
        out.append("        )),")
    out.append("    )")
    return out


lines = [
    "// ARCHIVO GENERADO por tools/build_muscles.py a partir de react-body-highlighter (MIT, © 2020 GV79) — no editar a mano.",
    "package app.fitstat.ui.design",
    "",
    "/** Región del cuerpo: polígonos 'x y x y …' en un lienzo de 100 x 200. */",
    "internal class MuscleRegion(val key: String, val polys: Array<String>)",
    "",
    "internal object MuscleGeo {",
]
lines += emit("front", front)
lines += [""]
lines += emit("back", back)
lines += ["}"]
os.makedirs(os.path.dirname(OUT), exist_ok=True)
open(OUT, "w", encoding="utf8").write("\n".join(lines) + "\n")
print("front", [(k, len(p)) for k, p in front])
print("back", [(k, len(p)) for k, p in back])

if PREVIEW:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    from matplotlib.patches import Polygon
    import colorsys
    fig, axes = plt.subplots(1, 2, figsize=(10, 10), facecolor="#101014")
    for ax, regs, title in zip(axes, (front, back), ("frente", "espalda")):
        ax.set_facecolor("#101014")
        ax.set_xlim(0, 100)
        ax.set_ylim(200, 0)
        ax.set_aspect("equal")
        ax.axis("off")
        ax.set_title(title, color="white")
        for i, (key, polys) in enumerate(regs):
            col = colorsys.hsv_to_rgb((i * 0.137) % 1, 0.55, 0.85)
            for p in polys:
                n = [float(x) for x in p.split()]
                pts = list(zip(n[0::2], n[1::2]))
                ax.add_patch(Polygon(pts, closed=True, facecolor=col, edgecolor="#101014", linewidth=0.6))
            n = [float(x) for x in polys[0].split()]
            cx = sum(n[0::2]) / (len(n) // 2)
            cy = sum(n[1::2]) / (len(n) // 2)
            ax.text(cx, cy, key, color="black", fontsize=5, ha="center", va="center")
    fig.savefig(PREVIEW, dpi=110, facecolor=fig.get_facecolor())
    print("preview →", PREVIEW)
