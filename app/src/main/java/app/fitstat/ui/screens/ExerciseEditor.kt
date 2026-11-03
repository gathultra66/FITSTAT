package app.fitstat.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.fitstat.data.*
import app.fitstat.ui.app.LocalApp
import app.fitstat.ui.app.LocalRepo
import app.fitstat.ui.app.Route
import app.fitstat.ui.design.*
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

val equipmentOptions = listOf("Peso corporal", "Mancuernas", "Barra", "Polea", "Máquina", "Banda elástica", "Kettlebell", "Otro")

/** Hoja para crear (o editar) un ejercicio propio: nombre, categoría, músculos, descripción y foto. */
fun Overlays.editExercise(
    existing: Exercise? = null,
    presetName: String = "",
    presetCategory: String? = null,
    onSaved: (Exercise) -> Unit = {},
) {
    sheet { close -> ExerciseEditorBody(existing, presetName, presetCategory, close, onSaved) }
}

private fun readBitmap(ctx: Context, uri: Uri, maxSide: Int): Bitmap? = try {
    val cr = ctx.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null
    else {
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (bmp != null) {
            val orientation = try {
                cr.openInputStream(uri)?.use { android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) } ?: 1
            } catch (_: Throwable) {
                1
            }
            val angle = when (orientation) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
            val longest = maxOf(bmp.width, bmp.height)
            val k = if (longest > maxSide) maxSide.toFloat() / longest else 1f
            if (angle != 0f || k < 1f) {
                val m = Matrix().apply { postRotate(angle); postScale(k, k) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
        }
        bmp
    }
} catch (_: Throwable) {
    null
}

private fun saveBitmap(ctx: Context, bmp: Bitmap): String? = try {
    val dir = File(ctx.filesDir, "custom").apply { mkdirs() }
    val name = "c_${System.currentTimeMillis()}.jpg"
    File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 86, it) }
    name
} catch (_: Throwable) {
    null
}

@Composable
private fun ExerciseEditorBody(
    existing: Exercise?,
    presetName: String,
    presetCategory: String?,
    close: () -> Unit,
    onSaved: (Exercise) -> Unit,
) {
    val repo = LocalRepo.current
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = Fs.colors

    var name by remember { mutableStateOf(existing?.name ?: presetName) }
    var category by remember { mutableStateOf(existing?.category ?: presetCategory ?: "pecho") }
    var equipment by remember { mutableStateOf(existing?.equipment?.ifBlank { null } ?: "Peso corporal") }
    var primary by remember { mutableStateOf(existing?.primary?.toSet() ?: Categories.byId(category)?.muscles?.take(1)?.toSet().orEmpty()) }
    var secondary by remember { mutableStateOf(existing?.secondary?.toSet().orEmpty()) }
    var touched by remember { mutableStateOf(existing != null) }
    var desc by remember { mutableStateOf(existing?.description.orEmpty()) }
    var addToSet by remember { mutableStateOf(existing == null) }
    var picked by remember { mutableStateOf<Bitmap?>(null) }
    var removePhoto by remember { mutableStateOf(false) }
    var showSecondary by remember { mutableStateOf(existing?.secondary?.isNotEmpty() == true) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val bmp = withContext(Dispatchers.IO) { readBitmap(ctx, uri, 900) }
            if (bmp == null) app.overlays.toast("No pude abrir esa imagen.", icon = Ic.Alert)
            else { picked = bmp; removePhoto = false }
        }
    }

    val dup = remember(name, repo.exercises) {
        val n = Search.norm(name)
        if (n.length < 3) null else repo.exercises.firstOrNull { it.id != existing?.id && Search.norm(it.name) == n }
    }
    val valid = name.trim().length >= 2 && primary.isNotEmpty()

    SheetBody(if (existing == null) "Nuevo ejercicio" else "Editar ejercicio", subtitle = "Crea tu propia plantilla con la información que necesites.") {
        FsTextField(name, { name = it.take(60) }, placeholder = "Nombre del ejercicio", leading = Ic.Dumbbell, imeAction = ImeAction.Next)
        if (dup != null) {
            Row(
                Modifier.fillMaxWidth().clip(Fs.shapes.chip).background(c.warn.a(0.14f)).tappable({ close(); app.nav.push(Route.Exercise(dup.id)) })
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Ic.Info, size = 18.dp, tint = c.warn)
                Txt("Ya existe «${dup.name}». Toca para abrirlo.", Modifier.weight(1f), Fs.type.small, c.text)
            }
        }

        Label("Categoría")
        ChipFlow {
            for (k in Categories.all) Pill(k.name, selected = category == k.id, onClick = {
                category = k.id
                if (!touched) primary = k.muscles.take(1).toSet()
            })
        }

        Label("Equipo")
        ChipFlow {
            for (e in equipmentOptions) Pill(e, selected = equipment == e, onClick = { equipment = e })
        }

        Label("Músculos principales")
        ChipFlow {
            for (m in Muscle.entries) Pill(m.label, selected = m in primary, onClick = {
                touched = true
                primary = if (m in primary) primary - m else primary + m
                secondary = secondary - m
            })
        }
        if (!showSecondary) {
            FsButton("Agregar músculos secundarios", { showSecondary = true }, kind = BtnKind.Ghost, icon = Ic.Plus, height = 40.dp)
        } else {
            Label("Músculos secundarios (opcional)")
            ChipFlow {
                for (m in Muscle.entries) if (m !in primary) Pill(m.label, selected = m in secondary, soft = true, onClick = {
                    secondary = if (m in secondary) secondary - m else secondary + m
                })
            }
        }

        Label("Descripción")
        FsTextField(desc, { desc = it.take(300) }, placeholder = "Cómo se hace, qué sientes, qué cuidar…", singleLine = false, minLines = 3)

        Label("Foto de ejemplo")
        val hasStored = existing?.customImage != null && !removePhoto
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(92.dp).clip(Fs.shapes.inner).background(c.surface2).border(1.5.dp, c.outline.a(0.7f), Fs.shapes.inner)
                    .tappable({ launcher.launch("image/*") }),
                contentAlignment = Alignment.Center,
            ) {
                val p = picked
                when {
                    p != null -> Image(p.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    hasStored && existing != null -> ExercisePhoto(existing, Modifier.fillMaxSize())
                    else -> Icon(Ic.ImagePlus, size = 30.dp, tint = c.textDim)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt("Opcional. Sin foto se dibuja el músculo que trabaja.", style = Fs.type.small, color = c.textDim)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(if (picked != null || hasStored) "Cambiar" else "Elegir foto", icon = Ic.Image, onClick = { launcher.launch("image/*") })
                    if (picked != null || hasStored) Pill("Quitar", icon = Ic.Trash, soft = true, onClick = { picked = null; removePhoto = true })
                }
            }
        }

        if (existing == null) {
            Row(
                Modifier.fillMaxWidth().clip(Fs.shapes.inner).background(c.surface2).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Txt("Agregar a mi set", style = Fs.type.bodyStrong)
                    Txt("Aparecerá entre los ejercicios que haces seguido.", style = Fs.type.small, color = c.textDim)
                }
                Toggle(addToSet, { addToSet = it })
            }
        }

        FsButton(
            if (existing == null) "Crear ejercicio" else "Guardar cambios",
            {
                var file = if (removePhoto) null else existing?.customImage
                val bmp = picked
                if (bmp != null) {
                    val saved = saveBitmap(ctx, bmp)
                    if (saved != null) {
                        if (existing?.customImage != null) File(File(ctx.filesDir, "custom"), existing.customImage).delete()
                        file = saved
                    }
                }
                val ex = Exercise(
                    id = existing?.id.orEmpty(), name = name.trim(), category = category, equipment = equipment,
                    level = existing?.level.orEmpty(), primary = primary.toList(), secondary = secondary.filter { it !in primary }.toList(),
                    description = desc.trim().ifBlank { "Ejercicio propio." }, custom = true,
                    images = if (file != null) 1 else 0, customImage = file,
                )
                val saved = repo.saveCustomExercise(ex)
                if (existing == null && addToSet) repo.addToSet(saved.id)
                close()
                app.overlays.toast(if (existing == null) "«${saved.name}» creado" else "Cambios guardados", icon = Ic.Check)
                onSaved(saved)
            },
            Modifier.fillMaxWidth(), enabled = valid, icon = Ic.Check,
        )
        if (!valid) {
            Txt(
                if (name.trim().length < 2) "Escribe el nombre del ejercicio." else "Elige al menos un músculo principal.",
                Modifier.fillMaxWidth(), Fs.type.caption, c.textDim, align = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
