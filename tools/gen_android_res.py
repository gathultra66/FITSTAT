#!/usr/bin/env python3
"""Genera los recursos de Android de FITSTAT que no se escriben a mano:
íconos adaptativos (10 colores), ícono de notificación, accesos directos, widgets, cadenas
y el AndroidManifest.xml. También escribe IconMarkData.kt (la misma geometría del ícono para
dibujarlo dentro de la app). Uso: python3 tools/gen_android_res.py"""
import os, struct, sys, zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import skin_marks as SM  # logos propios de las skins (Carmesí y Acero)

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
RES = os.path.join(ROOT, "app/src/main/res")
MAIN = os.path.join(ROOT, "app/src/main")
KT = os.path.join(ROOT, "app/src/main/java/app/fitstat/ui/screens/IconMarkData.kt")

PALETTES = [
    ("lavanda", "Lavanda", 0x9B8CF5, 0x7863E6),
    ("menta", "Menta", 0x5CE0B0, 0x29B98A),
    ("mandarina", "Mandarina", 0xFF9B5E, 0xF06A2A),
    ("rosa", "Rosa neón", 0xFF6FB5, 0xE53E94),
    ("azul", "Azul eléctrico", 0x6CA8FF, 0x3B78EE),
    ("lima", "Lima", 0xCBF45C, 0x9CCF2A),
    ("fuego", "Fuego", 0xFF6B5E, 0xE23C33),
    ("ambar", "Ámbar", 0xFFC857, 0xF1A116),
    ("turquesa", "Turquesa", 0x4FD9E8, 0x1FAEC4),
    ("mono", "Monocromo", 0xE8E8EE, 0xB4B4C0),
]


def lum(rgb):
    def lin(c):
        c /= 255.0
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255
    return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)


def ink_for(rgb):
    return 0x14111F if lum(rgb) > 0.34 else 0xFFFFFF


# ── La marca: la cabeza de mancuerna de FITBOT (cuadrícula 100x100 de la mascota) ──
# (x, y, ancho, alto, radio). Las piezas no se solapan para poder usar relleno par-impar.
SOLIDS = [
    (3.5, 12.0, 11.5, 48.0, 5.0),     # placa exterior izquierda
    (85.0, 12.0, 11.5, 48.0, 5.0),    # placa exterior derecha
    (16.5, 19.0, 11.0, 34.0, 4.0),    # placa interior izquierda
    (72.5, 19.0, 11.0, 34.0, 4.0),    # placa interior derecha
    (27.5, 24.0, 45.0, 24.0, 8.0),    # barra / visor
    (49.0, 16.9, 2.0, 7.1, 1.0),      # antena (tallo)
]
SOLID_CIRCLES = [(50.0, 14.0, 2.9)]   # antena (punta)
HOLE_RECTS = [
    (36.2, 30.0, 6.6, 9.0, 2.6),      # ojo izquierdo
    (57.2, 30.0, 6.6, 9.0, 2.6),      # ojo derecho
]
HOLE_CIRCLES = [
    (9.25, 36.0, 2.6), (9.25, 21.0, 1.2), (9.25, 51.0, 1.2),
    (90.75, 36.0, 2.6), (90.75, 21.0, 1.2), (90.75, 51.0, 1.2),
]
CX, CY = 50.0, 36.0


def f(v):
    s = ("%.2f" % v).rstrip("0").rstrip(".")
    return s if s else "0"


def rrect(x, y, w, h, r):
    return (f"M{f(x + r)},{f(y)}H{f(x + w - r)}A{f(r)},{f(r)} 0 0 1 {f(x + w)},{f(y + r)}V{f(y + h - r)}"
            f"A{f(r)},{f(r)} 0 0 1 {f(x + w - r)},{f(y + h)}H{f(x + r)}A{f(r)},{f(r)} 0 0 1 {f(x)},{f(y + h - r)}"
            f"V{f(y + r)}A{f(r)},{f(r)} 0 0 1 {f(x + r)},{f(y)}Z")


def circle(cx, cy, r):
    return f"M{f(cx - r)},{f(cy)}A{f(r)},{f(r)} 0 1 1 {f(cx + r)},{f(cy)}A{f(r)},{f(r)} 0 1 1 {f(cx - r)},{f(cy)}Z"


def mark_path(s, ox, oy, with_antenna=True):
    """Camino del ícono: la cuadrícula 100x100 de FITBOT escalada [s] y centrada en (ox, oy)."""
    def X(x): return ox + (x - CX) * s
    def Y(y): return oy + (y - CY) * s
    parts = []
    for i, (x, y, w, h, r) in enumerate(SOLIDS):
        if not with_antenna and i == 5:
            continue
        parts.append(rrect(X(x), Y(y), w * s, h * s, r * s))
    if with_antenna:
        for (cx, cy, r) in SOLID_CIRCLES:
            parts.append(circle(X(cx), Y(cy), r * s))
    for (x, y, w, h, r) in HOLE_RECTS:
        parts.append(rrect(X(x), Y(y), w * s, h * s, r * s))
    for (cx, cy, r) in HOLE_CIRCLES:
        parts.append(circle(X(cx), Y(cy), r * s))
    return "".join(parts)


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)


def hexc(v, a=0xFF):
    return "#%02X%06X" % (a, v)


XMLNS = 'xmlns:android="http://schemas.android.com/apk/res/android"'
AAPT = 'xmlns:aapt="http://schemas.android.com/aapt"'

# ───────────────────────── Íconos de lanzador
S_ICON = 0.66
for pid, label, a1, a2 in PALETTES:
    ink = ink_for(a1)
    write(f"{RES}/drawable/ic_launcher_bg_{pid}.xml", f'''<?xml version="1.0" encoding="utf-8"?>
<vector {XMLNS} {AAPT}
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="18" android:startY="6" android:endX="92" android:endY="106">
                <item android:offset="0" android:color="{hexc(a1)}"/>
                <item android:offset="1" android:color="{hexc(a2)}"/>
            </gradient>
        </aapt:attr>
    </path>
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="28" android:centerY="20" android:gradientRadius="86">
                <item android:offset="0" android:color="#3DFFFFFF"/>
                <item android:offset="1" android:color="#00FFFFFF"/>
            </gradient>
        </aapt:attr>
    </path>
</vector>
''')
    write(f"{RES}/drawable/ic_launcher_fg_{pid}.xml", f'''<?xml version="1.0" encoding="utf-8"?>
<vector {XMLNS}
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:rotation="-14" android:pivotX="54" android:pivotY="54">
        <path android:fillColor="{hexc(ink)}" android:fillType="evenOdd"
            android:pathData="{mark_path(S_ICON, 54, 54)}"/>
    </group>
</vector>
''')
    write(f"{RES}/mipmap-anydpi/ic_launcher_{pid}.xml", f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon {XMLNS}>
    <background android:drawable="@drawable/ic_launcher_bg_{pid}"/>
    <foreground android:drawable="@drawable/ic_launcher_fg_{pid}"/>
    <monochrome android:drawable="@drawable/ic_launcher_mono"/>
</adaptive-icon>
''')

write(f"{RES}/drawable/ic_launcher_mono.xml", f'''<?xml version="1.0" encoding="utf-8"?>
<vector {XMLNS}
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:rotation="-14" android:pivotX="54" android:pivotY="54">
        <path android:fillColor="#FF000000" android:fillType="evenOdd"
            android:pathData="{mark_path(S_ICON, 54, 54)}"/>
    </group>
</vector>
''')

# ───────────────────────── Íconos de lanzador de las skins (Carmesí y Acero)
SKINS = [("carmesi", "Carmesí", 0xDC1F3C), ("acero", "Acero", 0x9DB8D6)]


def skin_prims_xml(skin, color, dim_alpha, mono=False):
    out = []
    col = "#FF000000" if mono else hexc(color)
    for p in SM.MARKS[skin]:
        dim = p[2] != "main"
        if p[0] == "fill":
            eo = ' android:fillType="evenOdd"' if len(p) > 3 and p[3] else ""
            al = f' android:fillAlpha="{dim_alpha}"' if dim else ""
            out.append(f'    <path android:fillColor="{col}"{eo}{al} android:pathData="{p[1]}"/>')
        else:
            al = f' android:strokeAlpha="{dim_alpha}"' if dim else ""
            out.append(
                f'    <path android:strokeColor="{col}" android:strokeWidth="{SM.f(p[3])}" '
                f'android:strokeLineCap="{p[4]}" android:strokeLineJoin="{p[5]}" android:strokeMiterLimit="4"{al} '
                f'android:pathData="{p[1]}"/>')
    return "\n".join(out)


for sid, slabel, scol in SKINS:
    bg = SM.BG[sid]
    lines = []
    if sid == "acero":
        for i in range(0, 109, 12):
            lines.append(f'    <path android:strokeColor="#14C9D1DB" android:strokeWidth="0.6" android:pathData="M{i},0V108M0,{i}H108"/>')
    else:
        for y in range(6, 108, 6):
            lines.append(f'    <path android:strokeColor="#09FFFFFF" android:strokeWidth="0.8" android:pathData="M0,{y}H108"/>')
    lines_xml = "\n".join(lines)
    write(f"{RES}/drawable/ic_launcher_bg_{sid}.xml", f'''<?xml version="1.0" encoding="utf-8"?>
<vector {XMLNS} {AAPT}
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="{bg['cx']}" android:centerY="{bg['cy']}" android:gradientRadius="{bg['r']}">
                <item android:offset="0" android:color="{hexc(bg['glow'] & 0xFFFFFF)}"/>
                <item android:offset="1" android:color="{hexc(bg['base'] & 0xFFFFFF)}"/>
            </gradient>
        </aapt:attr>
    </path>
{lines_xml}
</vector>
''')
    write(f"{RES}/drawable/ic_launcher_fg_{sid}.xml", f'''<?xml version="1.0" encoding="utf-8"?>
<vector {XMLNS}
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
{skin_prims_xml(sid, scol, 0.55)}
</vector>
''')
    write(f"{RES}/drawable/ic_launcher_mono_{sid}.xml", f'''<?xml version="1.0" encoding="utf-8"?>
<vector {XMLNS}
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
{skin_prims_xml(sid, scol, 0.5, mono=True)}
</vector>
''')
    write(f"{RES}/mipmap-anydpi/ic_launcher_{sid}.xml", f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon {XMLNS}>
    <background android:drawable="@drawable/ic_launcher_bg_{sid}"/>
    <foreground android:drawable="@drawable/ic_launcher_fg_{sid}"/>
    <monochrome android:drawable="@drawable/ic_launcher_mono_{sid}"/>
</adaptive-icon>
''')

# ───────────────────────── Ícono de notificación (24dp, una sola tinta)
write(f"{RES}/drawable/ic_stat_fitstat.xml", f'''<?xml version="1.0" encoding="utf-8"?>
<vector {XMLNS}
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd"
        android:pathData="{mark_path(0.215, 12, 12)}"/>
</vector>
''')

# ───────────────────────── Accesos directos (mantener pulsado el ícono)
def shortcut_icon(name, glyph):
    write(f"{RES}/drawable/ic_shortcut_{name}.xml", f'''<?xml version="1.0" encoding="utf-8"?>
<vector {XMLNS} {AAPT}
    android:width="48dp" android:height="48dp" android:viewportWidth="48" android:viewportHeight="48">
    <path android:pathData="M24,24m-24,0a24,24 0 1,1 48,0a24,24 0 1,1 -48,0">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="8" android:startY="4" android:endX="40" android:endY="46">
                <item android:offset="0" android:color="#FF9B8CF5"/>
                <item android:offset="1" android:color="#FF7863E6"/>
            </gradient>
        </aapt:attr>
    </path>
{glyph}
</vector>
''')

shortcut_icon("routines", '''    <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd"
        android:pathData="''' + mark_path(0.27, 24, 24, with_antenna=False) + '''"/>''')
shortcut_icon("bot", '''    <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd"
        android:pathData="''' + mark_path(0.27, 24, 25, with_antenna=True) + '''"/>''')
shortcut_icon("stats", '''    <path android:fillColor="#FFFFFFFF"
        android:pathData="''' + rrect(14, 25, 5.5, 9, 1.6) + rrect(21.25, 17, 5.5, 17, 1.6) + rrect(28.5, 12, 5.5, 22, 1.6) + '''"/>''')

write(f"{RES}/xml/shortcuts.xml", f'''<?xml version="1.0" encoding="utf-8"?>
<shortcuts {XMLNS}>
    <shortcut android:shortcutId="rutinas" android:enabled="true" android:icon="@drawable/ic_shortcut_routines"
        android:shortcutShortLabel="@string/shortcut_routines" android:shortcutLongLabel="@string/shortcut_routines_long">
        <intent android:action="android.intent.action.VIEW" android:targetPackage="app.fitstat" android:targetClass="app.fitstat.MainActivity"
            android:data="fitstat://open/rutinas">
            <extra android:name="route" android:value="rutinas"/>
        </intent>
    </shortcut>
    <shortcut android:shortcutId="fitbot" android:enabled="true" android:icon="@drawable/ic_shortcut_bot"
        android:shortcutShortLabel="@string/shortcut_bot" android:shortcutLongLabel="@string/shortcut_bot_long">
        <intent android:action="android.intent.action.VIEW" android:targetPackage="app.fitstat" android:targetClass="app.fitstat.MainActivity"
            android:data="fitstat://open/fitbot">
            <extra android:name="route" android:value="fitbot"/>
        </intent>
    </shortcut>
    <shortcut android:shortcutId="progreso" android:enabled="true" android:icon="@drawable/ic_shortcut_stats"
        android:shortcutShortLabel="@string/shortcut_stats" android:shortcutLongLabel="@string/shortcut_stats_long">
        <intent android:action="android.intent.action.VIEW" android:targetPackage="app.fitstat" android:targetClass="app.fitstat.MainActivity"
            android:data="fitstat://open/progreso">
            <extra android:name="route" android:value="progreso"/>
        </intent>
    </shortcut>
</shortcuts>
''')

# ───────────────────────── Widgets
write(f"{RES}/layout/widget_image.xml", f'''<?xml version="1.0" encoding="utf-8"?>
<FrameLayout {XMLNS}
    android:id="@+id/widget_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    <ImageView
        android:id="@+id/widget_img"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:scaleType="fitXY"
        android:contentDescription="@string/app_name" />
</FrameLayout>
''')

WIDGETS = [
    # id, ancho min, alto min, celdas, ancho/alto mínimos al redimensionar
    ("today", 250, 110, 4, 2, 110, 50),
    ("bot", 110, 110, 2, 2, 110, 110),
    ("week", 250, 110, 4, 2, 250, 50),
    ("records", 250, 110, 4, 2, 110, 110),
]
for wid, mw, mh, cw, ch, rw, rh in WIDGETS:
    write(f"{RES}/xml/widget_{wid}.xml", f'''<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider {XMLNS}
    android:minWidth="{mw}dp"
    android:minHeight="{mh}dp"
    android:targetCellWidth="{cw}"
    android:targetCellHeight="{ch}"
    android:minResizeWidth="{rw}dp"
    android:minResizeHeight="{rh}dp"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="1800000"
    android:widgetCategory="home_screen"
    android:initialLayout="@layout/widget_image"
    android:previewImage="@drawable/widget_preview_{wid}"
    android:description="@string/widget_{wid}_desc" />
''')


def png(path, w, h, rgba):
    raw = b"".join(b"\x00" + bytes(rgba) * w for _ in range(h))
    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(data)

# Vistas previas provisionales: las reales se generan con la prueba WidgetPreviewTest.
for wid, mw, mh, *_ in WIDGETS:
    p = f"{RES}/drawable-nodpi/widget_preview_{wid}.png"
    if not os.path.exists(p):
        png(p, mw * 2, mh * 2, (0x17, 0x17, 0x1C, 0xFF))

# ───────────────────────── Cadenas
write(f"{RES}/values/strings.xml", '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">FITSTAT</string>

    <string name="widget_today">FITSTAT · Hoy</string>
    <string name="widget_bot">FITSTAT · FITBOT</string>
    <string name="widget_week">FITSTAT · Racha</string>
    <string name="widget_records">FITSTAT · Récords</string>
    <string name="widget_today_desc">Tu entrenamiento de hoy y el avance de tu meta semanal.</string>
    <string name="widget_bot_desc">FITBOT te saluda y te dice qué toca. Tócalo para chatear.</string>
    <string name="widget_week_desc">Los 7 días de tu semana de un vistazo, con tu racha.</string>
    <string name="widget_records_desc">Tus últimos récords personales.</string>

    <string name="shortcut_routines">Rutinas</string>
    <string name="shortcut_routines_long">Mis rutinas</string>
    <string name="shortcut_bot">FITBOT</string>
    <string name="shortcut_bot_long">Hablar con FITBOT</string>
    <string name="shortcut_stats">Progreso</string>
    <string name="shortcut_stats_long">Ver mi progreso</string>
</resources>
''')

# ───────────────────────── Manifiesto
aliases = []
for pid, label, a1, a2 in PALETTES:
    name = pid[0].upper() + pid[1:]
    enabled = "true" if pid == "lavanda" else "false"
    aliases.append(f'''        <activity-alias
            android:name=".Icon{name}"
            android:enabled="{enabled}"
            android:exported="true"
            android:icon="@mipmap/ic_launcher_{pid}"
            android:roundIcon="@mipmap/ic_launcher_{pid}"
            android:label="@string/app_name"
            android:targetActivity=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts" />
        </activity-alias>''')

for sid, slabel, scol in SKINS:
    name = sid[0].upper() + sid[1:]
    aliases.append(f'''        <activity-alias
            android:name=".Icon{name}"
            android:enabled="false"
            android:exported="true"
            android:icon="@mipmap/ic_launcher_{sid}"
            android:roundIcon="@mipmap/ic_launcher_{sid}"
            android:label="@string/app_name"
            android:targetActivity=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts" />
        </activity-alias>''')

widget_recv = []
for wid, cls in (("today", "WidgetToday"), ("bot", "WidgetBot"), ("week", "WidgetWeek"), ("records", "WidgetRecords")):
    widget_recv.append(f'''        <receiver
            android:name=".sys.{cls}"
            android:exported="true"
            android:label="@string/widget_{wid}">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data android:name="android.appwidget.provider" android:resource="@xml/widget_{wid}" />
        </receiver>''')

manifest = '''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
    <!-- Conectores (v1.7): sueño desde Health Connect (solo lectura) y estimación con el uso del teléfono. -->
    <uses-permission android:name="android.permission.health.READ_SLEEP" />
    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions" />

    <!-- Para ver si Namida y Health Connect están instalados y para leer la carátula que comparte Namida. -->
    <queries>
        <package android:name="com.msob7y.namida" />
        <package android:name="com.msob7y.namida.debug" />
        <package android:name="com.google.android.apps.healthdata" />
    </queries>

    <uses-feature android:name="android.hardware.microphone" android:required="false" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />

    <application
        android:name=".FitstatApp"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher_lavanda"
        android:roundIcon="@mipmap/ic_launcher_lavanda"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Fitstat">

        <activity
            android:name=".MainActivity"
            android:exported="false"
            android:launchMode="singleTask"
            android:screenOrientation="portrait"
            android:windowSoftInputMode="adjustResize" />

        <!-- Un alias por color de ícono: solo uno está activo a la vez (Ajustes › Ícono de la app). -->
''' + "\n".join(aliases) + '''

        <!-- Health Connect pide una pantalla que explique para qué se leen los datos (Android 13 y anteriores, y Android 14 o más). -->
        <activity-alias
            android:name=".HealthRationale"
            android:exported="true"
            android:targetActivity=".MainActivity">
            <intent-filter>
                <action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" />
            </intent-filter>
        </activity-alias>
        <activity-alias
            android:name=".ViewPermissionUsage"
            android:exported="true"
            android:permission="android.permission.START_VIEW_PERMISSION_USAGE"
            android:targetActivity=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.VIEW_PERMISSION_USAGE" />
                <category android:name="android.intent.category.HEALTH_PERMISSIONS" />
            </intent-filter>
        </activity-alias>

        <!-- Existe solo para que Android deje a FITSTAT ver qué reproduce Namida (no lee notificaciones). -->
        <service
            android:name=".conn.FitstatListener"
            android:exported="true"
            android:label="@string/app_name"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>

        <receiver android:name=".sys.ReminderReceiver" android:exported="false" />

        <receiver android:name=".sys.BootReceiver" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
                <action android:name="android.intent.action.TIMEZONE_CHANGED" />
                <action android:name="android.intent.action.TIME_SET" />
            </intent-filter>
        </receiver>

''' + "\n".join(widget_recv) + '''
    </application>
</manifest>
'''
write(f"{MAIN}/AndroidManifest.xml", manifest)

# ───────────────────────── Geometría del ícono para la app
def kt_list(rows):
    return ",\n        ".join("floatArrayOf(" + ", ".join(f(v) + "f" for v in r) + ")" for r in rows)

pal_kt = ",\n        ".join(
    f'IconPalette("{pid}", "{label}", 0xFF{a1:06X}, 0xFF{a2:06X}, 0xFF{ink_for(a1):06X})' for pid, label, a1, a2 in PALETTES)
write(KT, f'''// Generado por tools/gen_android_res.py. No editar a mano.
package app.fitstat.ui.screens

internal class IconPalette(val id: String, val label: String, val a1: Long, val a2: Long, val ink: Long)

/** Geometría del ícono de la app (cabeza de FITBOT) en su cuadrícula de 100 x 100. */
internal object IconMarkData {{
    const val CX = {f(CX)}f
    const val CY = {f(CY)}f
    /** Rectángulos redondeados macizos: x, y, ancho, alto, radio. */
    val solids = listOf(
        {kt_list(SOLIDS)}
    )
    /** Círculos macizos: cx, cy, radio. */
    val solidCircles = listOf(
        {kt_list(SOLID_CIRCLES)}
    )
    /** Rectángulos que se recortan (ojos). */
    val holeRects = listOf(
        {kt_list(HOLE_RECTS)}
    )
    /** Círculos que se recortan (tornillos). */
    val holeCircles = listOf(
        {kt_list(HOLE_CIRCLES)}
    )
    val palettes = listOf(
        {pal_kt}
    )
}}
''')

# ───────────────────────── Logos de las skins para dibujarlos dentro de la app
def kt_prim(p):
    role = 0 if p[2] == "main" else 1
    if p[0] == "fill":
        eo = "true" if len(p) > 3 and p[3] else "false"
        return f'MarkPrim("{p[1]}", 0f, {role}, 0, 0, {eo})'
    cap = {"butt": 0, "round": 1, "square": 2}[p[4]]
    join = {"miter": 0, "round": 1, "bevel": 2}[p[5]]
    return f'MarkPrim("{p[1]}", {SM.f(p[3])}f, {role}, {cap}, {join}, false)'


SKIN_KT = os.path.join(ROOT, "app/src/main/java/app/fitstat/ui/design/SkinMarkData.kt")
marks_kt = ",\n        ".join(
    f'"{sid}" to listOf(\n            ' + ",\n            ".join(kt_prim(p) for p in SM.MARKS[sid]) + "\n        )"
    for sid, _, _ in SKINS)
write(SKIN_KT, f'''// Generado por tools/gen_android_res.py (a partir de tools/skin_marks.py). No editar a mano.
package app.fitstat.ui.design

/** Una pieza del logo de una skin: relleno (stroke = 0) o trazo; role 0 = color de la skin, 1 = tenue. */
internal class MarkPrim(val path: String, val stroke: Float, val role: Int, val cap: Int, val join: Int, val evenOdd: Boolean)

/** Logos de las skins en una cuadrícula de 108 x 108 (los mismos de los íconos del lanzador). */
internal object SkinMarkData {{
    val marks: Map<String, List<MarkPrim>> = mapOf(
        {marks_kt}
    )
}}
''')
print("listo")
