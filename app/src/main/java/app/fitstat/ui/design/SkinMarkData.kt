// Generado por tools/gen_android_res.py (a partir de tools/skin_marks.py). No editar a mano.
package app.fitstat.ui.design

/** Una pieza del logo de una skin: relleno (stroke = 0) o trazo; role 0 = color de la skin, 1 = tenue. */
internal class MarkPrim(val path: String, val stroke: Float, val role: Int, val cap: Int, val join: Int, val evenOdd: Boolean)

/** Logos de las skins en una cuadrícula de 108 x 108 (los mismos de los íconos del lanzador). */
internal object SkinMarkData {
    val marks: Map<String, List<MarkPrim>> = mapOf(
        "carmesi" to listOf(
            MarkPrim("M79.11,46.5 L79.11,40.5 L54,26 L28.89,40.5 L28.89,69.5 L54,84 L79.11,69.5 L79.11,63.5", 6.8f, 0, 0, 0, false),
            MarkPrim("M60.5,55 L54.75,64.96 L43.25,64.96 L37.5,55 L43.25,45.04 L54.75,45.04Z", 0f, 0, 0, 0, false),
            MarkPrim("M61,52.2 L86,52.2 L86,57.8 L61,57.8 Z", 0f, 0, 0, 0, false),
            MarkPrim("M67.5,55 L58.25,71.02 L39.75,71.02 L30.5,55 L39.75,38.98 L58.25,38.98Z", 1.6f, 1, 0, 0, false)
        ),
        "acero" to listOf(
            MarkPrim("M43,29 L79,29 L79,65 L65,79 L29,79 L29,43ZM46.3,36.5 L71.5,36.5 L71.5,61.7 L61.7,71.5 L36.5,71.5 L36.5,46.3Z", 0f, 0, 0, 0, true),
            MarkPrim("M37.5,62 L41.7,62 L57.7,46 L53.5,46ZM45.5,62 L49.7,62 L65.7,46 L61.5,46ZM53.5,62 L57.7,62 L73.7,46 L69.5,46Z", 0f, 0, 0, 0, false)
        )
    )
}
