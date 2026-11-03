package app.fitstat

import app.fitstat.bot.ActionRecord
import app.fitstat.bot.BotAgent
import app.fitstat.bot.BotHost
import app.fitstat.bot.BotPhase
import app.fitstat.bot.ChatMessage
import app.fitstat.bot.ChatStore
import app.fitstat.bot.ModelInfo
import app.fitstat.bot.OpenRouter
import app.fitstat.bot.Prompt
import app.fitstat.bot.ToolCtx
import app.fitstat.bot.TurnOutcome
import app.fitstat.bot.TurnSink
import app.fitstat.bot.WorkoutStatus
import app.fitstat.bot.allTools
import app.fitstat.data.Demo
import app.fitstat.data.Repo
import app.fitstat.ui.app.Route
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Una petición recibida por el servidor falso: sirve para contestar a mano. */
private class Exchange(val socket: Socket) {
    val out: OutputStream = socket.getOutputStream()

    fun head(code: Int, type: String, length: Int? = null) {
        val reason = when (code) {
            200 -> "OK"
            401 -> "Unauthorized"
            404 -> "Not Found"
            503 -> "Service Unavailable"
            else -> "Status"
        }
        val sb = StringBuilder("HTTP/1.1 $code $reason\r\nContent-Type: $type\r\nConnection: close\r\n")
        if (length != null) sb.append("Content-Length: $length\r\n")
        sb.append("\r\n")
        out.write(sb.toString().toByteArray())
        out.flush()
    }

    fun write(s: String) {
        out.write(s.toByteArray())
        out.flush()
    }
}

/** Servidor falso con la forma de OpenRouter (/chat/completions con SSE) para probar a FITBOT sin internet. */
private class FakeOpenRouter {
    val requests = CopyOnWriteArrayList<JsonObject>()
    val auth = CopyOnWriteArrayList<String?>()
    val clientGone = AtomicBoolean(false)

    @Volatile
    var script: (n: Int, req: JsonObject, ex: Exchange) -> Unit = { _, _, ex -> reply(ex, 500, "{}") }

    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val pool = Executors.newCachedThreadPool()

    @Volatile
    private var running = true
    val baseUrl: String get() = "http://127.0.0.1:${server.localPort}/api/v1"

    init {
        pool.execute {
            while (running) {
                val socket = try {
                    server.accept()
                } catch (e: IOException) {
                    break
                }
                pool.execute { handle(socket) }
            }
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
        }
    }

    private fun handle(socket: Socket) {
        try {
            socket.use {
                val input = BufferedInputStream(socket.getInputStream())
                readLine(input) ?: return
                val headers = HashMap<String, String>()
                while (true) {
                    val l = readLine(input) ?: return
                    if (l.isEmpty()) break
                    val i = l.indexOf(':')
                    if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
                }
                val len = headers["content-length"]?.toIntOrNull() ?: 0
                val body = ByteArray(len)
                var read = 0
                while (read < len) {
                    val n = input.read(body, read, len - read)
                    if (n < 0) break
                    read += n
                }
                requests += Json.parseToJsonElement(String(body, Charsets.UTF_8)).jsonObject
                auth += headers["authorization"]
                script(requests.size, requests.last(), Exchange(socket))
            }
        } catch (e: IOException) {
            clientGone.set(true)
        }
    }

    fun stop() {
        running = false
        runCatching { server.close() }
        pool.shutdownNow()
    }

    companion object {
        fun reply(ex: Exchange, code: Int, body: String) {
            val bytes = body.toByteArray()
            ex.head(code, "application/json", bytes.size)
            ex.out.write(bytes)
            ex.out.flush()
        }

        fun sse(ex: Exchange, chunks: List<String>) {
            ex.head(200, "text/event-stream")
            ex.write(": OPENROUTER PROCESSING\n\n")
            for (c in chunks) ex.write("data: $c\n\n")
            ex.write("data: [DONE]\n\n")
        }
    }
}

private fun q(s: String) = JsonPrimitive(s).toString()
private fun textChunk(s: String) = """{"choices":[{"index":0,"delta":{"role":"assistant","content":${q(s)}}}]}"""
private fun reasoningChunk(s: String) = """{"choices":[{"index":0,"delta":{"reasoning":${q(s)}}}]}"""
private fun finishChunk(r: String) = """{"choices":[{"index":0,"delta":{},"finish_reason":"$r"}]}"""
private fun usageChunk(p: Int, c: Int) = """{"choices":[],"usage":{"prompt_tokens":$p,"completion_tokens":$c,"cost":0.0012}}"""

/** Una llamada a herramienta, partida en trozos como lo hacen los modelos de verdad. */
private fun toolChunks(id: String, name: String, args: String): List<String> {
    val cut = args.length / 2
    fun c(first: Boolean, a: String) =
        """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,${if (first) """"id":"$id","type":"function",""" else ""}"function":{${if (first) """"name":"$name",""" else ""}"arguments":${q(a)}}}]}}]}"""
    return listOf(c(true, args.substring(0, cut)), c(false, args.substring(cut)), finishChunk("tool_calls"))
}

private class TestHost(override val repo: Repo) : BotHost {
    val navigated = CopyOnWriteArrayList<Route>()
    override fun currentScreen() = "home"
    override fun navigate(route: Route) {
        navigated += route
    }

    override fun startWorkout(routineId: String?, exerciseIds: List<String>) {}

    /** Entrenamiento simulado: null = no hay ninguno. */
    var status: WorkoutStatus? = null
    val calls = CopyOnWriteArrayList<String>()
    override fun workoutActive() = status?.finished == false
    override fun workoutStatus() = status
    override fun finishWorkout(): String? {
        val s = status ?: return null
        calls += "finish"
        status = null
        return "Finalizó el entrenamiento: ${s.sets} series. Las series quedaron guardadas."
    }

    override fun cancelWorkout(discardSets: Boolean): String? {
        val s = status ?: return null
        calls += "cancel:$discardSets"
        status = null
        return if (discardSets) "Canceló el entrenamiento y borró ${s.sets} series." else "Canceló el entrenamiento (las series se conservaron)."
    }
}

private class Rec(val allow: Boolean = true, val onFirstText: () -> Unit = {}) : TurnSink {
    val phases = CopyOnWriteArrayList<BotPhase>()
    val text = StringBuffer()
    val reasoning = StringBuffer()
    val actions = CopyOnWriteArrayList<ActionRecord>()
    val asked = CopyOnWriteArrayList<String>()
    override fun onPhase(p: BotPhase) {
        phases += p
    }

    override fun onText(delta: String) {
        if (text.isEmpty()) onFirstText()
        text.append(delta)
    }

    override fun onReasoning(delta: String) {
        reasoning.append(delta)
    }

    override fun onAction(a: ActionRecord) {
        actions += a
    }

    override suspend fun confirm(title: String, destructive: Boolean): Boolean {
        asked += title
        return allow
    }
}

class BotTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val now = LocalDate.of(2026, 10, 28).atTime(19, 0).atZone(zone).toInstant().toEpochMilli()
    private val server = FakeOpenRouter()
    private val dir = Files.createTempDirectory("fitstat-bot").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repo = Repo(dir, scope, { now }) { zone }.also { it.replaceAll(Demo.build(it.data, now, zone, it.formula)) }
    private val host = TestHost(repo)
    private val model = ModelInfo(id = "test/model", name = "Test", parameters = setOf("tools"))
    private fun newAgent() = BotAgent(host, OpenRouter(server.baseUrl), Dispatchers.Unconfined)

    @After
    fun tearDown() {
        server.stop()
        scope.cancel()
    }

    private fun turn(text: String, sink: TurnSink = Rec(), model: ModelInfo = this.model): TurnOutcome = runBlocking {
        newAgent().run("sk-test", model, listOf(ChatMessage("u1", "user", text, ts = now)), dir, sink)
    }

    private fun waitUntil(ms: Long = 4000, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            if (cond()) return true
            Thread.sleep(20)
        }
        return cond()
    }

    // ───────── Conversación

    @Test
    fun streamsTextAndCountsTokens() {
        server.script = { _, _, ex ->
            FakeOpenRouter.sse(ex, listOf(textChunk("Hola "), textChunk("Chris"), finishChunk("stop"), usageChunk(120, 30)))
        }
        val sink = Rec()
        val out = turn("hola", sink)
        assertNull(out.error)
        assertEquals("Hola Chris", out.text)
        assertEquals("Hola Chris", sink.text.toString())
        assertEquals(150, out.tokens)
        assertEquals(0.0012, out.cost!!, 1e-9)
        assertEquals(0L, out.checkpoint)
        assertTrue(BotPhase.Connecting in sink.phases && BotPhase.Writing in sink.phases)

        val req = server.requests.single()
        assertEquals("test/model", req["model"]!!.jsonPrimitive.content)
        assertTrue(req["stream"]!!.jsonPrimitive.boolean)
        assertNotNull(req["tools"])
        assertEquals("Bearer sk-test", server.auth.single())
        val msgs = req["messages"]!!.jsonArray
        assertEquals("system", msgs[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("hola", msgs.last().jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun nonStreamingReply() {
        repo.setSetting("bot.stream", "false")
        server.script = { _, _, ex ->
            FakeOpenRouter.reply(
                ex, 200,
                """{"choices":[{"message":{"role":"assistant","content":"Hola sin stream"},"finish_reason":"stop"}],"usage":{"prompt_tokens":7,"completion_tokens":3}}""",
            )
        }
        val out = turn("hola")
        assertNull(out.error)
        assertEquals("Hola sin stream", out.text)
        assertEquals(10, out.tokens)
        assertFalse(server.requests.single()["stream"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun collectsReasoning() {
        val m = model.copy(parameters = setOf("tools", "reasoning"))
        server.script = { _, _, ex -> FakeOpenRouter.sse(ex, listOf(reasoningChunk("Veo "), reasoningChunk("tus datos"), textChunk("Hecho"), finishChunk("stop"))) }
        val sink = Rec()
        val out = turn("analiza", sink, m)
        assertEquals("Veo tus datos", out.reasoning)
        assertEquals("Veo tus datos", sink.reasoning.toString())
        assertEquals("Hecho", out.text)
        assertTrue(BotPhase.Thinking in sink.phases)
    }

    // ───────── Herramientas

    @Test
    fun toolLoopChangesSettingsAndCheckpointUndoes() {
        val before = repo.prefs.str("theme.mode")
        assertNotEquals("light", before)
        server.script = { n, _, ex ->
            if (n == 1) FakeOpenRouter.sse(ex, toolChunks("call_1", "set_settings", """{"changes":[{"key":"theme.mode","value":"light"}]}"""))
            else FakeOpenRouter.sse(ex, listOf(textChunk("Listo, tema claro."), finishChunk("stop"), usageChunk(10, 5)))
        }
        val sink = Rec()
        val out = turn("pon el tema claro", sink)
        assertNull(out.error)
        assertEquals("light", repo.prefs.str("theme.mode"))
        assertEquals("Listo, tema claro.", out.text)
        assertEquals(1, out.actions.size)
        assertTrue(out.actions[0].ok && out.actions[0].write)
        assertEquals(1, sink.actions.size)
        assertTrue(BotPhase.Tool in sink.phases)

        // La segunda petición lleva la llamada del modelo y el resultado de la herramienta.
        val second = server.requests[1]["messages"]!!.jsonArray
        val toolMsg = second.last().jsonObject
        assertEquals("tool", toolMsg["role"]!!.jsonPrimitive.content)
        assertEquals("call_1", toolMsg["tool_call_id"]!!.jsonPrimitive.content)
        assertTrue(toolMsg["content"]!!.jsonPrimitive.content.contains("\"ok\":true"))
        val assistant = second[second.size - 2].jsonObject
        assertEquals("assistant", assistant["role"]!!.jsonPrimitive.content)
        val args = assistant["tool_calls"]!!.jsonArray[0].jsonObject["function"]!!.jsonObject["arguments"]!!.jsonPrimitive.content
        assertTrue(args.contains("theme.mode"))

        // Deshacer con el punto de restauración del turno.
        assertNotEquals(0L, out.checkpoint)
        assertTrue(repo.hasCheckpoint(out.checkpoint))
        assertTrue(repo.restoreTo(out.checkpoint))
        assertEquals(before, repo.prefs.str("theme.mode"))
    }

    @Test
    fun createsRoutineFromExerciseIds() {
        server.script = { n, _, ex ->
            if (n == 1) {
                FakeOpenRouter.sse(
                    ex,
                    toolChunks(
                        "c1", "create_routine",
                        """{"name":"Torso prueba","exercises":["press_de_banca_con_barra","sentadilla_con_barra"],"days":["lunes","jueves"]}""",
                    ),
                )
            } else FakeOpenRouter.sse(ex, listOf(textChunk("Creé tu rutina."), finishChunk("stop")))
        }
        val out = turn("crea una rutina de torso")
        assertNull(out.error)
        val r = repo.routines.first { it.name == "Torso prueba" }
        assertEquals(2, r.items.size)
        assertEquals(setOf(1, 4), r.days)
        assertTrue(out.actions.single().summary.contains("Torso prueba"))
    }

    @Test
    fun readToolsReturnRealData() {
        val name = repo.routines.first().name
        server.script = { n, _, ex ->
            if (n == 1) FakeOpenRouter.sse(ex, toolChunks("c1", "list_routines", "{}"))
            else FakeOpenRouter.sse(ex, listOf(textChunk("Tienes rutinas."), finishChunk("stop")))
        }
        val out = turn("¿qué rutinas tengo?")
        assertNull(out.error)
        assertEquals(0L, out.checkpoint) // leer no crea punto de restauración
        val toolMsg = server.requests[1]["messages"]!!.jsonArray.last().jsonObject
        assertTrue(toolMsg["content"]!!.jsonPrimitive.content.contains(name))
        assertFalse(out.actions.single().write)
    }

    @Test
    fun unknownToolAndBadArgumentsAreReportedToTheModel() {
        server.script = { n, _, ex ->
            if (n == 1) FakeOpenRouter.sse(ex, toolChunks("c1", "hackear_gimnasio", "{}"))
            else if (n == 2) FakeOpenRouter.sse(ex, toolChunks("c2", "get_exercise", "esto no es json"))
            else FakeOpenRouter.sse(ex, listOf(textChunk("Perdón, no pude."), finishChunk("stop")))
        }
        val out = turn("haz algo raro")
        assertNull(out.error)
        assertEquals("Perdón, no pude.", out.text)
        assertEquals(2, out.actions.size)
        assertTrue(out.actions.none { it.ok })
        val toolMsg = server.requests[1]["messages"]!!.jsonArray.last().jsonObject
        assertTrue(toolMsg["content"]!!.jsonPrimitive.content.contains("no existe"))
    }

    @Test
    fun destructiveToolsAskFirst() {
        repo.setSetting("bot.confirm", "destructive")
        val target = repo.routines.first()
        val call = """{"routine":"${target.id}"}"""
        server.script = { n, _, ex ->
            if (n % 2 == 1) FakeOpenRouter.sse(ex, toolChunks("c$n", "delete_routine", call))
            else FakeOpenRouter.sse(ex, listOf(textChunk("Ok."), finishChunk("stop")))
        }

        val no = Rec(allow = false)
        val denied = turn("borra la rutina", no)
        assertEquals(1, no.asked.size)
        assertTrue(denied.actions.single().cancelled)
        assertNotNull(repo.routine(target.id))
        assertEquals(0L, denied.checkpoint)

        val yes = Rec(allow = true)
        val done = turn("borra la rutina", yes)
        assertEquals(1, yes.asked.size)
        assertTrue(done.actions.single().ok)
        assertNull(repo.routine(target.id))
        assertNotEquals(0L, done.checkpoint)
        assertTrue(repo.restoreTo(done.checkpoint))
        assertNotNull(repo.routine(target.id))
    }

    private fun workout(sets: Int = 3) = WorkoutStatus(
        routine = "Empuje", minutes = 12, sets = sets, exercises = listOf("Press de banca con barra", "Press militar"),
        skipped = emptyList(), current = "Press de banca con barra", resting = false, finished = false,
    )

    @Test
    fun botCanReadAndFinishTheWorkout() {
        host.status = workout()
        server.script = { n, _, ex ->
            when (n) {
                1 -> FakeOpenRouter.sse(ex, toolChunks("c1", "get_workout_status", "{}"))
                2 -> FakeOpenRouter.sse(ex, toolChunks("c2", "finish_workout", "{}"))
                else -> FakeOpenRouter.sse(ex, listOf(textChunk("Listo, terminé tu rutina."), finishChunk("stop")))
            }
        }
        val out = turn("termina mi rutina")
        assertNull(out.error)
        assertEquals(listOf("finish"), host.calls.toList())
        assertEquals(2, out.actions.size)
        assertTrue(out.actions.all { it.ok })
        // El modelo recibió el estado antes de actuar.
        val statusResult = server.requests[1].toString()
        assertTrue(statusResult, statusResult.contains("sets_logged") && statusResult.contains("Empuje"))
        assertNull(host.status)
    }

    @Test
    fun finishOrCancelWithoutWorkoutFailsCleanly() {
        server.script = { n, _, ex ->
            when (n) {
                1 -> FakeOpenRouter.sse(ex, toolChunks("c1", "cancel_workout", "{}"))
                2 -> FakeOpenRouter.sse(ex, toolChunks("c2", "finish_workout", "{}"))
                else -> FakeOpenRouter.sse(ex, listOf(textChunk("No tienes ningún entrenamiento en curso."), finishChunk("stop")))
            }
        }
        val out = turn("cancela mi rutina")
        assertNull(out.error)
        assertTrue(host.calls.isEmpty())
        assertTrue(out.actions.none { it.ok })
    }

    @Test
    fun cancellingTheWorkoutAsksBeforeDeletingSets() {
        repo.setSetting("bot.confirm", "destructive")
        host.status = workout(sets = 4)
        server.script = { n, _, ex ->
            if (n % 2 == 1) FakeOpenRouter.sse(ex, toolChunks("c$n", "cancel_workout", "{}"))
            else FakeOpenRouter.sse(ex, listOf(textChunk("Ok."), finishChunk("stop")))
        }
        val no = Rec(allow = false)
        val denied = turn("cancela la rutina", no)
        assertEquals(1, no.asked.size)
        assertTrue(no.asked.single(), no.asked.single().contains("4 series"))
        assertTrue(denied.actions.single().cancelled)
        assertTrue(host.calls.isEmpty())
        assertNotNull(host.status)

        val yes = Rec(allow = true)
        val done = turn("cancela la rutina", yes)
        assertTrue(done.actions.single().ok)
        assertEquals(listOf("cancel:true"), host.calls.toList())
    }

    @Test
    fun cancellingWhileKeepingSetsDoesNotAsk() {
        repo.setSetting("bot.confirm", "destructive")
        host.status = workout()
        val call = """{"keep_sets":true}"""
        server.script = { n, _, ex ->
            if (n % 2 == 1) FakeOpenRouter.sse(ex, toolChunks("c$n", "cancel_workout", call))
            else FakeOpenRouter.sse(ex, listOf(textChunk("Ok."), finishChunk("stop")))
        }
        val sink = Rec(allow = false)
        val out = turn("cancela la rutina pero guarda lo que hice", sink)
        assertTrue(sink.asked.isEmpty())
        assertTrue(out.actions.single().ok)
        assertEquals(listOf("cancel:false"), host.calls.toList())
    }

    @Test
    fun autoModeAppliesWithoutAskingButEraseAlwaysAsks() {
        val target = repo.routines.first()
        server.script = { n, _, ex ->
            when (n) {
                1 -> FakeOpenRouter.sse(ex, toolChunks("c1", "delete_routine", """{"routine":"${target.id}"}"""))
                2 -> FakeOpenRouter.sse(ex, listOf(textChunk("Borrada."), finishChunk("stop")))
                3 -> FakeOpenRouter.sse(ex, toolChunks("c3", "erase_data", "{}"))
                else -> FakeOpenRouter.sse(ex, listOf(textChunk("Entendido."), finishChunk("stop")))
            }
        }
        val sink1 = Rec(allow = false)
        turn("borra la rutina", sink1)
        assertTrue("en modo automático no debe preguntar", sink1.asked.isEmpty())
        assertNull(repo.routine(target.id))

        val logsBefore = repo.logs.size
        assertTrue(logsBefore > 0)
        val sink2 = Rec(allow = false)
        turn("borra todo", sink2)
        assertEquals("borrar todo siempre pide permiso", 1, sink2.asked.size)
        assertEquals(logsBefore, repo.logs.size)
    }

    @Test
    fun everyToolHasAValidSchemaAndSurvivesEmptyArguments() {
        val names = HashSet<String>()
        fun check(path: String, schema: JsonObject) {
            when (schema["type"]?.jsonPrimitive?.content) {
                "object" -> {
                    val props = schema["properties"]?.jsonObject ?: JsonObject(emptyMap())
                    (schema["required"] as? JsonArray)?.forEach { assertTrue("$path: required '${it.jsonPrimitive.content}' no está en properties", props.containsKey(it.jsonPrimitive.content)) }
                    props.forEach { (k, v) -> check("$path.$k", v.jsonObject) }
                }
                "array" -> {
                    val items = schema["items"]
                    assertNotNull("$path: falta items", items)
                    check("$path[]", items!!.jsonObject)
                }
                null -> throw AssertionError("$path: falta type")
            }
        }
        val ctx = ToolCtx(host)
        for (t in allTools) {
            assertTrue("herramienta repetida ${t.name}", names.add(t.name))
            assertTrue("descripción de ${t.name}", t.description.length > 15)
            check(t.name, t.parameters)
            val spec = t.spec()
            assertEquals("function", spec["type"]!!.jsonPrimitive.content)
            if (t.name == "erase_data") continue
            try {
                t.run(JsonObject(emptyMap()), ctx)
            } catch (e: Throwable) {
                throw AssertionError("${t.name} falla con argumentos vacíos: $e", e)
            }
        }
        assertTrue("debe haber al menos 40 herramientas", allTools.size >= 40)
    }

    // ───────── Errores

    @Test
    fun fallsBackWhenModelRejectsTools() {
        server.script = { _, req, ex ->
            if (req.containsKey("tools")) {
                FakeOpenRouter.reply(ex, 404, """{"error":{"message":"No endpoints found that support tool use. Try disabling tools.","code":404}}""")
            } else FakeOpenRouter.sse(ex, listOf(textChunk("Solo conversación."), finishChunk("stop")))
        }
        val out = turn("hola")
        assertNull(out.error)
        assertEquals("Solo conversación.", out.text)
        assertTrue(out.actions.any { it.tool == "notice" })
        assertEquals(2, server.requests.size)
        assertNull(server.requests[1]["tools"])
    }

    @Test
    fun badKeyGivesAFriendlyError() {
        server.script = { _, _, ex -> FakeOpenRouter.reply(ex, 401, """{"error":{"message":"No auth credentials found","code":401}}""") }
        val out = turn("hola")
        assertTrue(out.error!!.contains("clave"))
        assertEquals(1, server.requests.size)
        assertEquals("", out.text)
    }

    @Test
    fun retriesAfterATemporaryFailure() {
        server.script = { n, _, ex ->
            if (n == 1) FakeOpenRouter.reply(ex, 503, """{"error":{"message":"Provider returned error","code":503}}""")
            else FakeOpenRouter.sse(ex, listOf(textChunk("Ya volví."), finishChunk("stop")))
        }
        val out = turn("hola")
        assertNull(out.error)
        assertEquals("Ya volví.", out.text)
        assertEquals(2, server.requests.size)
    }

    @Test
    fun emptyAnswerIsReportedNotSilent() {
        server.script = { _, _, ex -> FakeOpenRouter.sse(ex, listOf(finishChunk("stop"))) }
        val out = turn("hola")
        assertNotNull(out.error)
    }

    @Test
    fun noConnectionBecomesASpanishMessage() {
        server.stop()
        val out = turn("hola")
        assertNotNull(out.error)
        assertTrue(out.error!!.isNotBlank())
    }

    // ───────── Cancelar

    @Test
    fun cancellingClosesTheConnection() = runBlocking {
        val gotFirst = CompletableDeferred<Unit>()
        server.script = { _, _, ex ->
            ex.head(200, "text/event-stream")
            ex.write("data: " + textChunk("Pensando…") + "\n\n")
            try {
                repeat(100) {
                    Thread.sleep(100)
                    ex.write(": keepalive\n\n")
                }
            } catch (e: IOException) {
                server.clientGone.set(true)
            }
        }
        val sink = Rec(onFirstText = { gotFirst.complete(Unit) })
        val result = CompletableDeferred<TurnOutcome>()
        val job = scope.launch {
            result.complete(newAgent().run("sk-test", model, listOf(ChatMessage("u1", "user", "habla", ts = now)), dir, sink))
        }
        withTimeout(5000) { gotFirst.await() }
        job.cancel()
        val out = withTimeout(5000) { result.await() }
        assertTrue(out.cancelled)
        assertEquals("Pensando…", out.text)
        assertTrue("el servidor debe notar que la app cortó la conexión", waitUntil(4000) { server.clientGone.get() })
    }

    // ───────── Piezas sueltas

    @Test
    fun parseArgsIsTolerant() {
        val a = newAgent()
        assertEquals(JsonObject(emptyMap()), a.parseArgs(""))
        assertEquals(JsonObject(emptyMap()), a.parseArgs("null"))
        assertEquals("x", a.parseArgs("""{"a":"x"}{"b":"y"}""")!!["a"]!!.jsonPrimitive.content)
        assertEquals("1", a.parseArgs("```json\n{\"n\":1}\n```")!!["n"]!!.jsonPrimitive.content)
        assertEquals("v", a.parseArgs("\"{\\\"k\\\":\\\"v\\\"}\"")!!["k"]!!.jsonPrimitive.content)
        assertNull(a.parseArgs("esto no es json"))
        assertNull(a.parseArgs("\"hola\""))
        assertNull(a.parseArgs("42"))
    }

    @Test
    fun systemPromptKnowsTheUserData() {
        val p = Prompt.system(host, model)
        assertTrue(p.contains("FITBOT"))
        assertTrue(p.contains(repo.routines.first().name))
        assertTrue(p.contains("2026-10-28"))
        assertTrue("el prompt debe decir qué skin está activa", p.contains("Skin activa: FITSTAT"))
        repo.setSetting("skin.id", "acero")
        assertTrue(Prompt.system(host, model).contains("Skin activa: MILITECH"))
        repo.setSetting("skin.id", "fitstat")
        assertTrue("el prompt no debería pasar de ~14 000 caracteres (${p.length})", p.length < 14_000)
        repo.setSetting("bot.context", "false")
        assertFalse(Prompt.system(host, model).contains(repo.routines.first().name))
    }

    @Test
    fun modelListIsParsed() {
        val json = """{"data":[
          {"id":"openai/gpt-5-mini","name":"OpenAI: GPT-5 Mini","description":"Rápido","context_length":400000,
           "architecture":{"input_modalities":["text","image","file"],"output_modalities":["text"]},
           "pricing":{"prompt":"0.00000025","completion":"0.000002"},
           "supported_parameters":["tools","reasoning","include_reasoning","temperature"],"created":1754000000},
          {"id":"google/gemma-3-27b-it:free","name":"Google: Gemma 3 27B (free)","context_length":131072,
           "architecture":{"input_modalities":["text","image"],"output_modalities":["text"]},
           "pricing":{"prompt":"0","completion":"0"},"supported_parameters":["temperature"]},
          {"id":"vendor/audio","name":"Audio","architecture":{"input_modalities":["text","audio"]},"supported_parameters":["tools"]},
          {"nombre":"sin id"}
        ]}"""
        val list = ModelInfo.parseList(json)
        assertEquals(3, list.size)
        val a = list[0]
        assertEquals("GPT-5 Mini", a.name)
        assertTrue(a.images && a.files && !a.audio && a.tools && a.reasoning && !a.free)
        assertEquals(0.25, a.promptPerMillion!!, 1e-9)
        assertEquals(2.0, a.completionPerMillion!!, 1e-9)
        assertEquals("400K", a.contextLabel)
        val b = list[1]
        assertTrue(b.free && b.images && !b.tools && !b.reasoning)
        assertEquals("Gratis", b.priceLabel)
        assertTrue(list[2].audio)
        assertTrue(ModelInfo.unknown("x/y").tools) // un modelo desconocido se prueba con herramientas
    }

    @Test
    fun chatStoreSavesAndReloads() {
        val chatDir = Files.createTempDirectory("fitstat-chat").toFile()
        val store = ChatStore(chatDir, scope)
        store.add(ChatMessage("a", "user", "hola", ts = now))
        store.add(ChatMessage("b", "assistant", "¡Hola!", ts = now, actions = listOf(ActionRecord("set_settings", "Ajustes: Tema → Claro", write = true)), checkpoint = 7))
        store.writeNow()
        val again = ChatStore(chatDir, scope)
        assertEquals(2, again.messages.size)
        assertEquals("Ajustes: Tema → Claro", again.messages[1].actions.single().summary)
        assertEquals(7L, again.messages[1].checkpoint)
        again.removeFrom("b")
        assertEquals(1, again.messages.size)
        again.clear()
        assertTrue(again.messages.isEmpty())
    }
}
