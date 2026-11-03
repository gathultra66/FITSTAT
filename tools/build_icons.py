#!/usr/bin/env python3
"""Genera app/src/main/java/app/fitstat/ui/design/IconData.kt a partir de Lucide (ISC).

Uso: python3 tools/build_icons.py <ruta>/lucide-static/icon-nodes.json
Cada ícono es una lista de formas (trazos SVG en cuadrícula 24x24). Las formas cerradas llevan el prefijo '*'
para poder rellenarlas en el estilo duotono.
"""
import json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app/src/main/java/app/fitstat/ui/design/IconData.kt")
NODES = json.load(open(sys.argv[1]))

# (Nombre en Kotlin, nombre en Lucide)
ICONS = [
    ("Home", "house"), ("Dumbbell", "dumbbell"), ("Routines", "list-checks"), ("Stats", "chart-column"),
    ("Bot", "bot"), ("Settings", "settings"), ("Search", "search"), ("Plus", "plus"), ("Minus", "minus"),
    ("Close", "x"), ("Check", "check"), ("CheckCheck", "check-check"), ("Trash", "trash"), ("Edit", "pencil"),
    ("ChevronLeft", "chevron-left"), ("ChevronRight", "chevron-right"), ("ChevronDown", "chevron-down"),
    ("ChevronUp", "chevron-up"), ("ChevronsUpDown", "chevrons-up-down"),
    ("ArrowLeft", "arrow-left"), ("ArrowRight", "arrow-right"), ("ArrowUp", "arrow-up"), ("ArrowDown", "arrow-down"),
    ("ArrowUpRight", "arrow-up-right"), ("ArrowDownRight", "arrow-down-right"), ("Swap", "arrow-up-down"),
    ("More", "ellipsis"), ("MoreV", "ellipsis-vertical"),
    ("Bell", "bell"), ("BellOff", "bell-off"), ("BellRing", "bell-ring"), ("Alarm", "alarm-clock"),
    ("Calendar", "calendar"), ("CalendarCheck", "calendar-check"), ("CalendarDays", "calendar-days"),
    ("CalendarPlus", "calendar-plus"), ("Clock", "clock"), ("Timer", "timer"), ("Hourglass", "hourglass"),
    ("Star", "star"), ("Trophy", "trophy"), ("Flame", "flame"), ("Target", "target"), ("Medal", "medal"),
    ("Award", "award"), ("Crown", "crown"), ("Gem", "gem"), ("Rocket", "rocket"), ("Party", "party-popper"),
    ("Note", "sticky-note"), ("NotebookPen", "notebook-pen"), ("Image", "image"), ("ImagePlus", "image-plus"),
    ("Camera", "camera"), ("Mic", "mic"), ("MicOff", "mic-off"), ("Clip", "paperclip"), ("Send", "send"),
    ("Stop", "square"), ("Sparkles", "sparkles"), ("Sparkle", "sparkle"), ("Wand", "wand-sparkles"),
    ("Palette", "palette"), ("Type", "type"), ("Sliders", "sliders-horizontal"), ("Widgets", "layout-grid"),
    ("Key", "key-round"), ("Cpu", "cpu"), ("Info", "info"), ("Refresh", "refresh-cw"), ("Copy", "copy"),
    ("Moon", "moon"), ("Sun", "sun"), ("SunMoon", "sun-moon"), ("Eye", "eye"), ("EyeOff", "eye-off"),
    ("Filter", "funnel"), ("Share", "share-2"), ("Download", "download"), ("Upload", "upload"),
    ("Layers", "layers"), ("Zap", "zap"), ("TrendUp", "trending-up"), ("TrendDown", "trending-down"),
    ("Heart", "heart"), ("HeartPulse", "heart-pulse"), ("User", "user"), ("Users", "users"), ("Lock", "lock"),
    ("File", "file-text"), ("Body", "person-standing"), ("Repeat", "repeat"), ("Drop", "droplet"), ("Pin", "pin"),
    ("Undo", "undo-2"), ("Redo", "redo-2"), ("Play", "play"), ("Pause", "pause"), ("SkipForward", "skip-forward"),
    ("SkipBack", "skip-back"), ("Volume", "volume-2"), ("VolumeOff", "volume-x"), ("Phone", "smartphone"),
    ("Gauge", "gauge"), ("Activity", "activity"), ("CircleCheck", "circle-check"), ("CircleX", "circle-x"),
    ("Alert", "triangle-alert"), ("External", "external-link"), ("Link", "link"), ("Brain", "brain"),
    ("Languages", "languages"), ("Shield", "shield-check"), ("Scale", "scale"), ("Ruler", "ruler"),
    ("Weight", "weight"), ("Flag", "flag"), ("Bookmark", "bookmark"), ("Folder", "folder"),
    ("ListPlus", "list-plus"), ("ClipboardList", "clipboard-list"), ("ClipboardCheck", "clipboard-check"),
    ("Dashboard", "layout-dashboard"), ("AppWindow", "app-window"), ("Contrast", "contrast"),
    ("TextSize", "a-large-small"), ("RotateCcw", "rotate-ccw"), ("Save", "save"), ("Database", "database"),
    ("Package", "package"), ("Bug", "bug"), ("Help", "circle-question-mark"), ("Message", "message-circle"),
    ("Messages", "messages-square"), ("Grip", "grip-vertical"), ("Menu", "menu"), ("Muscle", "biceps-flexed"),
    ("Footprints", "footprints"), ("Bike", "bike"), ("Vibrate", "vibrate"), ("Battery", "battery-full"),
    ("Circle", "circle"), ("Dot", "circle-dot"), ("EditSquare", "square-pen"), ("Hash", "hash"),
    ("Percent", "percent"), ("Tag", "tag"), ("Hand", "hand"), ("Table", "table-2"), ("Grid", "grid-2x2"),
    ("List", "list"), ("ListOrdered", "list-ordered"), ("Paintbrush", "paintbrush"), ("SwatchBook", "swatch-book"),
    ("Atom", "atom"), ("Maximize", "maximize"), ("Minimize", "minimize"), ("Cloud", "cloud"),
    ("Wifi", "wifi"), ("Plug", "plug-zap"), ("Sigma", "sigma"), ("Infinity", "infinity"), ("Calculator", "calculator"),
    ("Bed", "bed"), ("Coffee", "coffee"), ("Lightbulb", "lightbulb"), ("Quote", "quote"), ("Sunrise", "sunrise"),
    ("Sunset", "sunset"), ("Milestone", "milestone"), ("Gift", "gift"), ("Book", "book-open"),
    ("ThumbsUp", "thumbs-up"), ("ThumbsDown", "thumbs-down"), ("Smile", "face-slightly-smiling"),
    ("Neutral", "face-neutral"), ("Frown", "face-slightly-frowning"), ("Eraser", "eraser"),
    ("Archive", "archive"), ("Inbox", "inbox"), ("Wrench", "wrench"), ("Cog", "cog"), ("Loader", "loader-circle"),
    ("Pie", "chart-pie"), ("Area", "chart-area"), ("Bars", "chart-bar"), ("Lines", "chart-spline"),
    ("ListRestart", "list-restart"), ("MoveUp", "move-up"), ("Navigation", "navigation"), ("Route", "route"),
    ("TextCursor", "text-cursor-input"), ("Scan", "scan-line"), ("ToggleRight", "toggle-right"),
    ("Headphones", "headphones"), ("Crosshair", "crosshair"), ("Compass", "compass"), ("Graduation", "graduation-cap"),
    ("HandFist", "hand-fist"), ("Flask", "flask-conical"), ("Move", "move-horizontal"), ("Square", "square"),
    ("Triangle", "triangle"), ("Hexagon", "hexagon"), ("Diamond", "diamond"), ("Octagon", "octagon"),
    ("Magnet", "magnet"), ("Anchor", "anchor"), ("Mountain", "mountain"), ("Leaf", "leaf"), ("Bolt", "zap-off"),
    ("Music", "music"), ("Watch", "watch"),
]


def fmt(v):
    v = float(v)
    s = ("%.3f" % v).rstrip("0").rstrip(".")
    return s if s not in ("", "-0") else "0"


def to_path(tag, a):
    """Devuelve (path_d, cerrado)"""
    g = lambda k, d=0: float(a.get(k, d))
    if tag == "path":
        d = a["d"]
        closed = bool(re.search(r"[zZ]\s*$", d.strip())) or bool(re.search(r"[zZ]", d))
        return d, closed
    if tag == "circle":
        cx, cy, r = g("cx"), g("cy"), g("r")
        return (f"M{fmt(cx - r)} {fmt(cy)}a{fmt(r)} {fmt(r)} 0 1 0 {fmt(2 * r)} 0a{fmt(r)} {fmt(r)} 0 1 0 {fmt(-2 * r)} 0z"), True
    if tag == "ellipse":
        cx, cy, rx, ry = g("cx"), g("cy"), g("rx"), g("ry")
        return (f"M{fmt(cx - rx)} {fmt(cy)}a{fmt(rx)} {fmt(ry)} 0 1 0 {fmt(2 * rx)} 0a{fmt(rx)} {fmt(ry)} 0 1 0 {fmt(-2 * rx)} 0z"), True
    if tag == "rect":
        x, y, w, h = g("x"), g("y"), g("width"), g("height")
        rx = g("rx", a.get("ry", 0) or 0)
        ry = g("ry", rx)
        if rx == 0 and ry == 0:
            return f"M{fmt(x)} {fmt(y)}h{fmt(w)}v{fmt(h)}h{fmt(-w)}z", True
        return (f"M{fmt(x + rx)} {fmt(y)}h{fmt(w - 2 * rx)}a{fmt(rx)} {fmt(ry)} 0 0 1 {fmt(rx)} {fmt(ry)}"
                f"v{fmt(h - 2 * ry)}a{fmt(rx)} {fmt(ry)} 0 0 1 {fmt(-rx)} {fmt(ry)}h{fmt(-(w - 2 * rx))}"
                f"a{fmt(rx)} {fmt(ry)} 0 0 1 {fmt(-rx)} {fmt(-ry)}v{fmt(-(h - 2 * ry))}"
                f"a{fmt(rx)} {fmt(ry)} 0 0 1 {fmt(rx)} {fmt(-ry)}z"), True
    if tag == "line":
        return f"M{fmt(g('x1'))} {fmt(g('y1'))}L{fmt(g('x2'))} {fmt(g('y2'))}", False
    if tag in ("polyline", "polygon"):
        pts = [float(x) for x in re.split(r"[\s,]+", a["points"].strip()) if x]
        pairs = list(zip(pts[0::2], pts[1::2]))
        d = "M" + "L".join(f"{fmt(x)} {fmt(y)}" for x, y in pairs)
        if tag == "polygon":
            return d + "z", True
        return d, False
    raise ValueError("etiqueta no soportada: " + tag)


def kq(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def main():
    lines = [
        "// ARCHIVO GENERADO por tools/build_icons.py a partir de Lucide (https://lucide.dev, licencia ISC) — no editar a mano.",
        "package app.fitstat.ui.design",
        "",
        "/** Íconos de la app. Cada forma es un trazo SVG en cuadrícula 24x24; '*' al inicio = forma cerrada (rellenable). */",
        "enum class Ic(internal val shapes: Array<String>) {",
    ]
    seen = set()
    missing = []
    for kname, lname in ICONS:
        if kname in seen:
            raise SystemExit("nombre repetido " + kname)
        seen.add(kname)
        if lname not in NODES:
            missing.append((kname, lname))
            continue
        shapes = []
        for tag, attrs in NODES[lname]:
            d, closed = to_path(tag, attrs)
            shapes.append(("*" if closed else "") + d)
        lines.append(f"    {kname}(arrayOf({', '.join(kq(s) for s in shapes)})),")
    if missing:
        raise SystemExit("faltan íconos en Lucide: %s" % missing)
    lines[-1] = lines[-1].rstrip(",") + ";"
    lines.append("}")
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, "w", encoding="utf8").write("\n".join(lines) + "\n")
    print(len(ICONS), "íconos →", OUT)


main()
