package app.fitstat

import app.fitstat.data.*
import app.fitstat.sys.AppIcon
import androidx.compose.ui.graphics.toArgb
import app.fitstat.ui.theme.SkinStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.ZoneId

/** Lógica y datos de las skins (lo visual se revisa en SkinShotTest). */
class SkinTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val now = java.time.LocalDate.of(2026, 10, 28).atTime(19, 0).atZone(zone).toInstant().toEpochMilli()
    private fun repo() = Repo(Files.createTempDirectory("fitstat-skin").toFile(), CoroutineScope(SupervisorJob() + Dispatchers.Default), { now }) { zone }

    @Test fun everySpecialSkinHasACompleteSettingSet() {
        val needed = listOf("accent", "custom", "surface", "cut", "bg", "fx", "font", "led", "caps", "codes", "words", "anim", "mascot")
        for (s in Skins.special) {
            for (k in needed) assertTrue("falta skin.${s.id}.$k", Settings.byKey.containsKey("skin.${s.id}.$k"))
            val accent = Settings.byKey["skin.${s.id}.accent"]!!
            assertTrue(accent.default == "base")
            val ids = (accent.type as SType.Choice).options.map { it.value }
            assertEquals(s.accents.map { it.id } + "custom", ids)
            // El color por defecto del selector personalizado es el principal de la skin.
            assertEquals("#" + "%06X".format(s.accents.first().a1 and 0xFFFFFF), Settings.default("skin.${s.id}.custom"))
            assertEquals(s.defCut, Settings.default("skin.${s.id}.cut"))
            assertEquals(s.defBg, Settings.default("skin.${s.id}.bg"))
            assertEquals(s.defFx.toString(), Settings.default("skin.${s.id}.fx"))
            assertEquals(s.defDigits, Settings.default("skin.${s.id}.led"))
        }
    }

    @Test fun skinSettingsAreOnTheirOwnPageAboveTheme() {
        val pages = Settings.pages.map { it.id }
        assertTrue("la página de skins debe ir antes que Tema y color", pages.indexOf(Pages.SKIN) in 0 until pages.indexOf(Pages.THEME))
        assertTrue(Settings.specsOf(Pages.SKIN).size >= 2 + 13 * Skins.special.size)
    }

    @Test fun skinIdNormalizesNamesAndRejectsUnknown() {
        val spec = Settings.byKey["skin.id"]!!
        assertEquals("acero", Settings.normalize(spec, "Acero"))
        assertEquals("carmesi", Settings.normalize(spec, "carmesi"))
        assertEquals("fitstat", Settings.normalize(spec, "FITSTAT"))
        assertNull(Settings.normalize(spec, "neón"))
        assertEquals(Skins.fitstat.id, Skins.byId("no existe").id)
    }

    @Test fun repoAppliesSkinAndNotifies() {
        val r = repo()
        val seen = ArrayList<Pair<String, String>>()
        r.onSettingChanged = { k, v -> seen.add(k to v) }
        assertEquals("fitstat", r.prefs.str("skin.id"))
        assertTrue(r.setSetting("skin.id", "acero").isSuccess)
        assertEquals("acero", r.prefs.str("skin.id"))
        assertTrue(r.setSetting("skin.acero.accent", "cobalto").isSuccess)
        assertTrue(r.setSetting("skin.acero.accent", "naranja").isFailure) // el color de otra skin no vale aquí
        assertTrue(r.setSetting("skin.acero.custom", "#12ab34").getOrNull() == "#12AB34")
        assertTrue(seen.contains("skin.id" to "acero"))
        // Cada skin guarda sus propios ajustes: cambiar los de Acero no toca los de Carmesí.
        assertEquals("base", r.prefs.str("skin.carmesi.accent"))
        r.setSetting("skin.id", "fitstat")
        assertEquals("cobalto", r.prefs.str("skin.acero.accent"))
    }

    @Test fun skinStyleReflectsSettings() {
        val a = SkinStyle.from(Prefs(mapOf("skin.id" to "acero")))
        assertEquals("acero", a.id)
        assertEquals("acero", a.head)
        assertEquals("cut", a.cut)
        assertEquals(1f, a.anim, 0f)
        val off = SkinStyle.from(Prefs(mapOf("skin.id" to "acero", "skin.acero.mascot" to "false", "skin.acero.anim" to "off")))
        assertFalse(off.mascotOn)
        assertNull(off.head)           // vuelve FITBOT clásico
        assertEquals(0f, off.anim, 0f)
        val classic = SkinStyle.from(Prefs(emptyMap()))
        assertTrue(classic.isDefault)
        assertNull(classic.head)
        assertEquals("Rutinas", classic.word("Rutinas"))
        assertEquals("Operaciones", a.word("Rutinas"))
        assertEquals("Mandos", a.word("Mandos"))   // lo que no está en el diccionario se queda igual
        val plain = SkinStyle.from(Prefs(mapOf("skin.id" to "acero", "skin.acero.words" to "false")))
        assertEquals("Rutinas", plain.word("Rutinas"))
        assertEquals("Entrenamiento", SkinStyle.from(Prefs(mapOf("skin.id" to "carmesi", "skin.carmesi.words" to "false"))).word("Entrenamiento"))
        assertEquals("Misión", SkinStyle.from(Prefs(mapOf("skin.id" to "carmesi"))).word("Entrenamiento"))
        // Color personalizado válido y color por defecto si el hex está mal.
        val custom = SkinStyle.from(Prefs(mapOf("skin.id" to "carmesi", "skin.carmesi.accent" to "custom", "skin.carmesi.custom" to "#1FA2FF")))
        assertEquals("custom", custom.accent.id)
        val broken = SkinStyle.from(Prefs(mapOf("skin.id" to "carmesi", "skin.carmesi.accent" to "custom", "skin.carmesi.custom" to "nada")))
        assertEquals(Skins.carmesi.accents.first().a1.toInt(), broken.accent.accent.toArgb())
    }

    @Test fun launcherIconFollowsTheSkin() {
        assertEquals("lavanda", AppIcon.wanted(Prefs(emptyMap())))
        assertEquals("acero", AppIcon.wanted(Prefs(mapOf("skin.id" to "acero"))))
        assertEquals("carmesi", AppIcon.wanted(Prefs(mapOf("skin.id" to "carmesi", "app.icon" to "menta"))))
        // Con la sincronía apagada vuelve el color elegido.
        assertEquals("menta", AppIcon.wanted(Prefs(mapOf("skin.id" to "carmesi", "app.icon" to "menta", "skin.icon_sync" to "false"))))
        // Con la skin clásica manda el color elegido.
        assertEquals("rosa", AppIcon.wanted(Prefs(mapOf("app.icon" to "rosa"))))
    }

    @Test fun manifestHasAnAliasForEveryIcon() {
        val f = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first { it.exists() }
        val xml = f.readText()
        val aliases = Regex("""<activity-alias[^>]*android:name="\.Icon(\w+)"""").findAll(xml).map { it.groupValues[1].lowercase() }.toSet()
        val expected = (AppIcon.ids + AppIcon.skinIds).toSet()
        assertEquals(expected, aliases)
        // Solo el ícono por defecto empieza activado.
        val enabled = Regex("""<activity-alias[^>]*android:name="\.Icon(\w+)"[^>]*android:enabled="true"""").findAll(xml).map { it.groupValues[1].lowercase() }.toList()
        assertEquals(listOf(AppIcon.DEFAULT), enabled)
        for (id in AppIcon.skinIds) {
            val res = File(f.parentFile, "res")
            assertTrue("falta el ícono adaptable de $id", File(res, "mipmap-anydpi/ic_launcher_$id.xml").exists())
            for (part in listOf("bg", "fg", "mono")) assertTrue("falta ic_launcher_${part}_$id", File(res, "drawable/ic_launcher_${part}_$id.xml").exists())
        }
    }

    @Test fun skinsAreAnnouncedInTheChangelog() {
        val e = Changelog.entries.first { it.version == "1.5" }
        val text = e.sections.flatMap { it.second }.joinToString("\n")
        assertTrue(text.contains("Carmesí") && text.contains("Acero"))
    }
}
