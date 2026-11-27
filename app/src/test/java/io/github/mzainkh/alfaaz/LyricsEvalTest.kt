package io.github.mzainkh.alfaaz

import io.github.mzainkh.alfaaz.lyrics.LrclibClient
import io.github.mzainkh.alfaaz.lyrics.LrclibTrack
import io.github.mzainkh.alfaaz.lyrics.TrackQuery
import io.github.mzainkh.alfaaz.lyrics.lrclibApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.text.Normalizer
import java.util.concurrent.TimeUnit

private const val RESOURCES = "src/test/resources/lyrics-eval"
private const val RECORD_HINT = "./gradlew testDebugUnitTest -PlyricsEval=record"

/** Test runs name themselves plainly, apart from the app. */
private const val USER_AGENT = "lyrics-matching-eval/1.0"

private val pretty = Json { prettyPrint = true }

/**
 * Scores how often the right synced lyrics are found for songs as players report them. The real
 * client runs against saved LRCLIB replies, and each song's outcome must match `baseline.txt`.
 *
 * With `-PlyricsEval=record` missing replies are fetched and saved, `record-all` fetches every one
 * again, and `live` asks the real service and only reports.
 */
class LyricsEvalTest {
    private val mode = System.getProperty("alfaaz.lyricsEval")

    @Test
    fun findsTheRightLyrics() {
        if (mode != null && mode.startsWith("record")) check(System.getenv("CI") == null) { "Record on your own machine" }
        val cases = Json.decodeFromString(ListSerializer(EvalCase.serializer()), resource("cases.json"))
        assertEquals("case ids must differ", cases.size, cases.map { it.id }.distinct().size)
        val saved = if (mode == "live" || mode == "record-all") {
            emptyList()
        } else {
            Json.decodeFromString(ListSerializer(SavedReply.serializer()), resource("recordings.json"))
        }
        val replay = Replay(saved.associateBy { it.request }, if (mode == null) null else Lrclib(USER_AGENT))
        val outcomes = MockWebServer().use { server ->
            server.dispatcher = replay
            server.start()
            run(cases, server)
        }

        if (mode == "live") {
            report("live", cases, outcomes, emptyMap())
            assertTrue("LRCLIB answered nothing at all", outcomes.values.any { it != Outcome.ERROR })
            return
        }
        replay.upstreamFailure?.let { fail("Recording stopped, LRCLIB could not be asked ($it). Nothing was saved.") }
        // Checked first: a missing reply would otherwise look like a failed search.
        if (replay.missing.isNotEmpty()) {
            fail("No saved reply for:\n" + replay.missing.sorted().joinToString("\n") + "\nRecord them: $RECORD_HINT")
        }
        if (mode != null) {
            val replies = replay.used.values.sortedBy { it.request }
            File(RESOURCES, "recordings.json").writeText(
                pretty.encodeToString(ListSerializer(SavedReply.serializer()), replies) + "\n",
            )
        }

        val baselineFile = File(RESOURCES, "baseline.txt")
        val baseline = if (baselineFile.exists()) {
            baselineFile.readLines().filter { it.isNotBlank() }.associate { it.substringBefore(' ') to it.substringAfter(' ') }
        } else {
            emptyMap()
        }
        val summary = report("replay", cases, outcomes, baseline)
        val now = cases.associate { it.id to outcomes.getValue(it.id).label }
        if (now != baseline) {
            val changed = (now.keys + baseline.keys).filter { now[it] != baseline[it] }
                .joinToString("\n") { "  $it: ${baseline[it] ?: "new"} -> ${now[it] ?: "gone"}" }
            fail(
                "Outcomes differ from the baseline.\n$summary\n$changed\n\n" +
                    "If this is intended, replace $RESOURCES/baseline.txt with:\n" +
                    now.entries.joinToString("\n") { "${it.key} ${it.value}" },
            )
        }
    }

    private fun run(cases: List<EvalCase>, server: MockWebServer): Map<String, Outcome> {
        val http = OkHttpClient.Builder().readTimeout(5, TimeUnit.MINUTES).build()
        val api = lrclibApi(USER_AGENT, server.url("/").toString(), http)
        val client = LrclibClient(api, pause = {})
        return cases.associate { case ->
            case.id to try {
                val query = TrackQuery(case.title, case.artist, case.album, case.durationSec)
                judge(case, runBlocking { client.find(query) })
            } catch (e: IOException) {
                Outcome.ERROR
            }
        }
    }

    private enum class Outcome { SYNCED, PLAIN, INSTRUMENTAL, WRONG, NONE, ERROR;
        val label get() = name.lowercase()
    }

    private fun judge(case: EvalCase, track: LrclibTrack?): Outcome = when {
        track == null -> Outcome.NONE
        case.expectTitle == null -> Outcome.WRONG
        !sameTitle(track.trackName, case.expectTitle) || !sameArtist(track.artistName, case.expectArtist.orEmpty()) -> Outcome.WRONG
        !track.syncedLyrics.isNullOrBlank() -> Outcome.SYNCED
        !track.plainLyrics.isNullOrBlank() -> Outcome.PLAIN
        track.instrumental -> Outcome.INSTRUMENTAL
        else -> Outcome.NONE
    }

    private fun resource(name: String): String = File(RESOURCES, name).takeIf { it.exists() }?.readText() ?: "[]"

    /** Writes a table to build/reports and returns its headline. */
    private fun report(kind: String, cases: List<EvalCase>, outcomes: Map<String, Outcome>, baseline: Map<String, String>): String {
        val wanted = cases.filter { it.expectTitle != null }
        val found = wanted.count { outcomes[it.id] == Outcome.SYNCED }
        val unwanted = cases.filter { it.expectTitle == null }
        val rightlyEmpty = unwanted.count { outcomes[it.id] == Outcome.NONE }
        val headline = "Right synced lyrics: $found of ${wanted.size} (${found * 100 / maxOf(wanted.size, 1)}%). " +
            "Rightly nothing: $rightlyEmpty of ${unwanted.size}."
        val rows = cases.joinToString("\n") { case ->
            val before = if (kind == "live") "" else " ${baseline[case.id] ?: "new"} |"
            "| ${case.id} | ${outcomes.getValue(case.id).label} |$before"
        }
        val head = if (kind == "live") "| Song | Outcome |\n|---|---|" else "| Song | Outcome | Baseline |\n|---|---|---|"
        File("build/reports/lyrics-eval").apply { mkdirs() }
            .resolve("$kind.md").writeText("### Lyrics matching ($kind)\n\n$headline\n\n$head\n$rows\n")
        println(headline)
        return headline
    }
}

/** Letters and digits only, without case, accents or vowel marks. */
private fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
    .filter { it.isLetterOrDigit() }
    .lowercase()

/** The same song title, ignoring bracketed notes and "- Remastered" style endings. */
internal fun sameTitle(found: String, wanted: String): Boolean {
    val bare = found.replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "").substringBefore(" - ")
    return fold(bare) == fold(wanted) || fold(found) == fold(wanted)
}

/** The wanted artist is among those named. */
internal fun sameArtist(found: String, wanted: String): Boolean = fold(wanted).isNotEmpty() && fold(wanted) in fold(found)
