#!/usr/bin/env python3
"""Logos propios de las skins de FITSTAT (Carmesí y Acero), en una cuadrícula de 108 x 108.

Cada marca es una lista de primitivas:
  ("fill", path, role[, even_odd])        relleno
  ("stroke", path, role, ancho, cap, join) trazo
role: "main" = color de la skin, "dim" = el mismo color tenue.
Los usa gen_android_res.py (íconos del lanzador + SkinMarkData.kt para dibujarlos dentro de la app).
"""
import math


def f(v):
    s = ("%.2f" % v).rstrip("0").rstrip(".")
    return s if s else "0"


def poly(points, close=True):
    d = "M" + " L".join(f"{f(x)},{f(y)}" for x, y in points)
    return d + ("Z" if close else "")


def scale_about(points, cx, cy, k):
    return [(cx + (x - cx) * k, cy + (y - cy) * k) for x, y in points]


# ───────────────────────── Carmesí: hexágono "de encendido"
def hexagon(cx, cy, r, rot=-90):
    return [(cx + r * math.cos(math.radians(rot + 60 * i)), cy + r * math.sin(math.radians(rot + 60 * i))) for i in range(6)]


_HEX = hexagon(54, 55, 29)
# El hexágono se abre a la derecha, como una "C" con un núcleo y un haz de luz saliendo.
_ring = [(_HEX[1][0], 46.5), _HEX[1], _HEX[0], _HEX[5], _HEX[4], _HEX[3], _HEX[2], (_HEX[2][0], 63.5)]

CARMESI = [
    ("stroke", poly(_ring, False), "main", 6.8, "butt", "miter"),
    ("fill", poly(hexagon(49, 55, 11.5, 0)), "main"),
    ("fill", "M61,52.2 L86,52.2 L86,57.8 L61,57.8 Z", "main"),
    ("stroke", poly(hexagon(49, 55, 18.5, 0), True), "dim", 1.6, "butt", "miter"),
]

# ───────────────────────── Acero: placa con esquinas cortadas y tres franjas
_PLATE = [(43, 29), (79, 29), (79, 65), (65, 79), (29, 79), (29, 43)]
_INNER = scale_about(_PLATE, 54, 54, 0.70)


def _slash(x, y0=62, y1=46, w=4.2):
    d = y0 - y1
    return poly([(x, y0), (x + w, y0), (x + w + d, y1), (x + d, y1)])


ACERO = [
    ("fill", poly(_PLATE) + poly(_INNER), "main", True),
    ("fill", _slash(37.5) + _slash(45.5) + _slash(53.5), "main"),
]

MARKS = {"carmesi": CARMESI, "acero": ACERO}

# Fondos de los íconos (vector Android): (color base, color de brillo, posición del brillo)
BG = {
    "carmesi": dict(base=0xFF0B090C, glow=0xFF4A0B1B, cx=54, cy=96, r=70, lines=0x0DFFFFFF),
    "acero": dict(base=0xFF06090D, glow=0xFF1B2D44, cx=22, cy=14, r=84, lines=0x14C9D1DB),
}
