package app.fitstat.data

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

/**
 * Buscador predictivo de ejercicios: ignora acentos y mayúsculas, entiende varias palabras,
 * tolera errores de dedo y propone cómo completar lo que escribes.
 */
object Search {
    /** Pasa a minúsculas y quita acentos letra por letra, conservando la longitud del texto. */
    fun flat(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            val d = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD)
            val base = d.firstOrNull() ?: ch
            sb.append(if (base.isLetterOrDigit()) base.lowercaseChar() else ch.lowercaseChar())
        }
        return sb.toString()
    }

    fun norm(s: String): String {
        val f = flat(s)
        val sb = StringBuilder(f.length)
        var lastSpace = true
        for (ch in f) {
            if (ch.isLetterOrDigit()) { sb.append(ch); lastSpace = false } else if (!lastSpace) { sb.append(' '); lastSpace = true }
        }
        return sb.toString().trim()
    }

    fun tokens(s: String): List<String> = norm(s).split(' ').filter { it.isNotEmpty() }

    class Doc(
        val ex: Exercise,
        val name: String,
        val nameTokens: List<String>,
        val aliases: List<String>,
        val aliasTokens: List<String>,
        val extraTokens: List<String>,
    )

    class Index(val docs: List<Doc>)

    class Hit(val ex: Exercise, val score: Double, val order: Int = 0)

    fun index(exercises: List<Exercise>): Index = Index(
        exercises.map { e ->
            val cat = Categories.byId(e.category)?.name ?: ""
            val extra = buildList {
                add(e.equipment); add(cat)
                e.primary.forEach { add(it.label) }
                e.secondary.forEach { add(it.label) }
            }.flatMap { tokens(it) }.distinct()
            val al = e.aliases.map { norm(it) }
            Doc(e, norm(e.name), tokens(e.name), al, al.flatMap { it.split(' ') }.filter { it.isNotEmpty() }, extra)
        },
    )

    private fun near(word: String, q: String): Boolean {
        if (q.length < 4) return false
        val k = if (q.length >= 8) 2 else 1
        if (kotlin.math.abs(word.length - q.length) > k + 2) return false
        return lev(word.take(q.length), q) <= k || lev(word, q) <= k
    }

    /** Distancia de edición (con transposición de letras vecinas). */
    fun lev(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev2 = IntArray(b.length + 1)
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = min(min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = min(v, prev2[j - 2] + 1)
                cur[j] = v
            }
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    private fun scoreDoc(d: Doc, qFull: String, qTokens: List<String>): Double {
        var total = 0.0
        if (d.name.startsWith(qFull)) total += 100.0
        else if (d.name.contains(qFull)) total += 45.0
        if (d.aliases.any { it.startsWith(qFull) }) total += 55.0 else if (d.aliases.any { it.contains(qFull) }) total += 25.0
        for (t in qTokens) {
            var best = 0.0
            for (nt in d.nameTokens) {
                val s = when {
                    nt == t -> 30.0
                    nt.startsWith(t) -> 26.0
                    t.length >= 3 && nt.contains(t) -> 12.0
                    near(nt, t) -> 14.0
                    else -> 0.0
                }
                best = max(best, s)
            }
            if (best < 26.0) {
                for (at in d.aliasTokens) {
                    val s = when {
                        at == t -> 24.0
                        at.startsWith(t) -> 20.0
                        near(at, t) -> 11.0
                        else -> 0.0
                    }
                    best = max(best, s)
                }
            }
            if (best < 20.0) {
                for (et in d.extraTokens) {
                    val s = when {
                        et == t -> 12.0
                        et.startsWith(t) -> 9.0
                        else -> 0.0
                    }
                    best = max(best, s)
                }
            }
            if (best == 0.0) return 0.0
            total += best
        }
        return total
    }

    fun search(index: Index, query: String, limit: Int = 40, boost: (Exercise) -> Double = { 0.0 }): List<Hit> {
        val qFull = norm(query)
        if (qFull.isEmpty()) return emptyList()
        val qTokens = qFull.split(' ')
        return index.docs.asSequence()
            .mapIndexed { i, d -> Hit(d.ex, scoreDoc(d, qFull, qTokens).let { s -> if (s > 0.0) s + boost(d.ex) else 0.0 }, i) }
            .filter { it.score > 0.0 }
            .sortedWith(compareByDescending<Hit> { it.score }.thenBy { it.order })
            .take(limit)
            .toList()
    }

    /**
     * Texto que falta para completar lo que escribe la persona (aparece tenue después del cursor).
     * Si lo escrito es el inicio del nombre de un ejercicio, completa el nombre; si no, completa la última palabra.
     */
    fun complete(hits: List<Hit>, query: String): String? {
        if (query.isEmpty() || query.last().isWhitespace()) return null
        val fq = flat(query)
        for (h in hits.take(8)) {
            val fn = flat(h.ex.name)
            if (fn.startsWith(fq) && h.ex.name.length > query.length) return h.ex.name.substring(query.length)
        }
        val lastTok = fq.substringAfterLast(' ')
        if (lastTok.length < 2) return null
        for (h in hits.take(8)) {
            val fn = flat(h.ex.name)
            var from = 0
            while (true) {
                val i = fn.indexOf(lastTok, from)
                if (i < 0) break
                val atWordStart = i == 0 || !fn[i - 1].isLetterOrDigit()
                if (atWordStart) {
                    var end = i + lastTok.length
                    while (end < fn.length && fn[end].isLetterOrDigit()) end++
                    if (end > i + lastTok.length) return h.ex.name.substring(i + lastTok.length, end)
                }
                from = i + 1
            }
        }
        return null
    }

    /** Rangos de letras del nombre que coinciden con lo escrito (para resaltarlas). */
    fun highlight(name: String, query: String): List<IntRange> {
        val fn = flat(name)
        val out = ArrayList<IntRange>()
        for (t in tokens(query)) {
            var best = -1
            var from = 0
            while (true) {
                val i = fn.indexOf(t, from)
                if (i < 0) break
                if (i == 0 || !fn[i - 1].isLetterOrDigit()) { best = i; break }
                if (best < 0) best = i
                from = i + 1
            }
            if (best >= 0) out.add(best until best + t.length)
        }
        if (out.isEmpty()) return out
        out.sortBy { it.first }
        val merged = ArrayList<IntRange>()
        for (r in out) {
            val last = merged.lastOrNull()
            if (last != null && r.first <= last.last + 1) merged[merged.size - 1] = last.first..max(last.last, r.last) else merged.add(r)
        }
        return merged
    }
}
