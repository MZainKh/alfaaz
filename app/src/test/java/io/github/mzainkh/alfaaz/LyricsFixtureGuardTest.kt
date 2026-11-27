package io.github.mzainkh.alfaaz

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

private const val ROOT = "src/test/resources"
private const val MAX_TEXT = 200
private const val MAX_RECORDINGS = 600 * 1024

/** The names LRCLIB gives its lyric fields, and a lyric time stamp. */
private val LYRIC_TRACES = listOf(Regex("syncedLyrics"), Regex("plainLyrics"), Regex("""\[\d{1,3}:\d{2}"""))

/**
 * The repository is public and lyrics belong to their writers, so the saved test data must never
 * hold any. This runs with every test run.
 */
class LyricsFixtureGuardTest {
    private val files = File(ROOT).walk()
        // macOS drops this into any folder opened in Finder.
        .filter { it.isFile && it.name != ".DS_Store" }
        .map { it.relativeTo(File(ROOT)).path }.sorted().toList()

    @Test
    fun holdsOnlyTheThreeEvalFiles() {
        assertEquals(listOf("baseline.txt", "cases.json", "recordings.json").map { "lyrics-eval/$it" }, files)
    }

    @Test
    fun readsOnlyTheKnownFields() {
        // The default reader rejects any field it does not know, lyrics included.
        Json.decodeFromString(ListSerializer(EvalCase.serializer()), text("cases.json"))
        Json.decodeFromString(ListSerializer(SavedReply.serializer()), text("recordings.json"))
    }

    @Test
    fun holdsNoLyrics() {
        for (name in files) {
            assertEquals(name, emptyList<String>(), problems(File(ROOT, name).readText(), json = name.endsWith(".json")))
        }
        val size = File(ROOT, "lyrics-eval/recordings.json").length()
        assertEquals("recordings.json is $size bytes", true, size < MAX_RECORDINGS)
    }

    @Test
    fun noticesLyricsWhenTheyAreThere() {
        val leaks = listOf(
            """[{"syncedLyrics":"x"}]""",
            """[{"trackName":"[00:12.00] a line"}]""",
            """[{"trackName":"one\ntwo"}]""",
            """[{"trackName":"${"a".repeat(MAX_TEXT + 1)}"}]""",
        )
        for (leak in leaks) assertEquals(leak, true, problems(leak, json = true).isNotEmpty())
    }

    private fun text(name: String) = File(ROOT, "lyrics-eval/$name").readText()

    private fun problems(content: String, json: Boolean): List<String> {
        val traces = LYRIC_TRACES.filter { it.containsMatchIn(content) }.map { "contains ${it.pattern}" }
        if (!json) return traces
        return traces + strings(Json.parseToJsonElement(content))
            .filter { it.length > MAX_TEXT || '\n' in it }
            .map { "long or multi-line text: ${it.take(40)}" }
    }

    private fun strings(element: JsonElement): List<String> = when (element) {
        is JsonObject -> element.values.flatMap(::strings)
        is JsonArray -> element.flatMap(::strings)
        is JsonPrimitive -> listOfNotNull(element.content.takeIf { element.isString })
    }
}
