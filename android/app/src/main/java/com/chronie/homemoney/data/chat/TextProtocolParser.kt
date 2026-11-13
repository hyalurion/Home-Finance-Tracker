package com.chronie.homemoney.data.chat

import com.chronie.homemoney.domain.model.chat.PendingToolCall
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.util.UUID

/**
 * Plain-text markers used when a backend has no native function calling.
 *
 * `<think>` / `</think>` are special tokens in Qwen3's tokenizer and MNN's
 * chat template already injects an empty `<think>\n\n</think>\n\n` assistant
 * prefix, so they never reliably appear in streamed output. The CJK bracket
 * markers below are ordinary text tokens and therefore always survive
 * tokenizer decoding, which is what makes on-device reasoning observable.
 */
object TextProtocolMarkers {
    const val TOOL_START = "<<<TOOL_CALL>>>"
    const val TOOL_END = "<<<END_TOOL_CALL>>>"
    const val REASON_START = "【思考】"
    const val REASON_END = "【/思考】"
    const val LEGACY_REASON_START = "<think>"
    const val LEGACY_REASON_END = "</think>"

    /** Labels small models invented when fed a flattened transcript. */
    val ROLE_STOP_MARKERS = listOf(
        "[User]", "[Assistant]", "[Tool result", "[System]",
        "\nUser:", "\nAssistant:", "\n用户：", "\n助手："
    )

    val REASON_PAIRS = listOf(
        REASON_START to REASON_END,
        LEGACY_REASON_START to LEGACY_REASON_END
    )

    /** Replays a previous text-protocol tool call inside assistant history. */
    fun renderToolCall(name: String, argumentsJson: String): String =
        "$TOOL_START{\"name\":\"$name\",\"arguments\":$argumentsJson}$TOOL_END"

    /** Renders a tool result as ordinary user content for text-only backends. */
    fun renderToolResult(name: String?, result: String?): String =
        "[Tool result: ${name ?: "tool"}]\n${result ?: "{}"}"
}

/** Everything a parser call produced since the previous call. */
data class TextEmission(
    val reasoningDelta: String = "",
    val textDelta: String = "",
    val tools: List<PendingToolCall> = emptyList(),
    /** True once reasoning has finished and should be closed in the UI. */
    val reasoningEnded: Boolean = false,
    val stopped: Boolean = false
)

/**
 * Incrementally parses a token stream that may contain reasoning blocks and
 * text-marker tool calls.
 *
 * The visible/reasoning split is recomputed from the accumulated raw text on
 * every chunk, but only the newly grown suffix is returned. Partial markers
 * are held back, so a tag split across JNI/SSE boundaries never flashes in the
 * UI. Invented `[User]` / `[Assistant]` continuations are cut off as well.
 *
 * This is a pure pull API: [feed] and [finish] return [TextEmission] so the
 * caller can forward them with `emit` (coroutine Flow) or `trySend` (JNI
 * callbackFlow) without capturing a suspend function in a plain lambda.
 */
class TextProtocolParser(private val gson: Gson) {
    private val raw = StringBuilder()
    private var emittedVisible = 0
    private var emittedReasoning = 0
    private var emittedTools = 0
    private var reasoningEnded = false
    private var stopped = false
    private var finished = false

    /** Feeds one decoded chunk (token, SSE delta, or whole response). */
    fun feed(chunk: String): TextEmission {
        if (stopped || finished || chunk.isEmpty()) {
            return TextEmission(stopped = stopped)
        }
        raw.append(chunk)
        return emit(parse(raw.toString()))
    }

    /**
     * Flushes the last incomplete tail. Also recovers a tool call that the
     * model emitted without its closing marker, which small models do often.
     */
    fun finish(): TextEmission {
        if (finished) return TextEmission()
        finished = true
        val result = emit(parse(raw.toString(), allowUnclosedTool = true))
        val closeNow = !reasoningEnded && emittedReasoning > 0
        if (closeNow) reasoningEnded = true
        return result.copy(
            reasoningEnded = result.reasoningEnded || closeNow,
            stopped = result.stopped || stopped
        )
    }

    private fun emit(parsed: Parsed): TextEmission {
        var reasoningDelta = ""
        var textDelta = ""
        var closed = false

        if (parsed.reasoning.length > emittedReasoning) {
            reasoningDelta = parsed.reasoning.substring(emittedReasoning)
            emittedReasoning = parsed.reasoning.length
        }
        if (parsed.visible.length > emittedVisible) {
            textDelta = parsed.visible.substring(emittedVisible)
            emittedVisible = parsed.visible.length
            if (emittedReasoning > 0 && !reasoningEnded) {
                reasoningEnded = true
                closed = true
            }
        }
        val tools = parsed.tools.drop(emittedTools)
        emittedTools += tools.size
        if (parsed.stopHit) stopped = true

        return TextEmission(reasoningDelta, textDelta, tools, closed, parsed.stopHit)
    }

    /** Pure function: derives all channels from the accumulated raw text. */
    private fun parse(rawText: String, allowUnclosedTool: Boolean = false): Parsed {
        val cleaned = rawText.replace("```json", "").replace("```", "")
        val (beforeRole, stopHit) = cutAtRoleMarker(cleaned)
        val answer = answerPrefix(beforeRole)
        val (visible, reasoning) = splitReasoning(answer)
        val tools = extractTools(cleaned, allowUnclosedTool)
        return Parsed(visible, reasoning, tools, stopHit)
    }

    /** Hides everything from the first tool marker onwards. */
    private fun answerPrefix(text: String): String {
        val full = text.indexOf(TextProtocolMarkers.TOOL_START)
        if (full >= 0) return text.substring(0, full)
        return stripAnyPartial(text, listOf(TextProtocolMarkers.TOOL_START))
    }

    /** Extracts reasoning blocks while leaving ordinary prose visible. */
    private fun splitReasoning(text: String): Pair<String, String> {
        val reasoning = StringBuilder()
        val visible = StringBuilder()
        var cursor = 0
        while (cursor <= text.length) {
            var openIndex = -1
            var openTag = ""
            var closeTag = ""
            TextProtocolMarkers.REASON_PAIRS.forEach { (open, close) ->
                val index = text.indexOf(open, cursor)
                if (index >= 0 && (openIndex < 0 || index < openIndex)) {
                    openIndex = index
                    openTag = open
                    closeTag = close
                }
            }
            if (openIndex < 0) {
                visible.append(stripOpenTags(text.substring(cursor)))
                break
            }
            visible.append(text.substring(cursor, openIndex))
            val contentStart = openIndex + openTag.length
            val closeIndex = text.indexOf(closeTag, contentStart)
            if (closeIndex < 0) {
                reasoning.append(stripPartialTag(text.substring(contentStart), closeTag))
                break
            }
            reasoning.append(text.substring(contentStart, closeIndex))
            cursor = closeIndex + closeTag.length
        }
        return Pair(stripOpenTags(visible.toString()), reasoning.toString())
    }

    /** Removes a trailing prefix of any marker so it can be emitted later. */
    private fun stripAnyPartial(text: String, tags: List<String>): String {
        var result = text
        var changed = true
        while (changed) {
            changed = false
            tags.forEach { tag ->
                val trimmed = stripPartialTag(result, tag)
                if (trimmed.length != result.length) {
                    result = trimmed
                    changed = true
                }
            }
        }
        return result
    }

    private fun stripOpenTags(text: String): String =
        stripAnyPartial(
            text,
            TextProtocolMarkers.REASON_PAIRS.map { it.first } +
                listOf(TextProtocolMarkers.TOOL_START)
        )

    private fun stripPartialTag(text: String, tag: String): String {
        val maxProbe = minOf(tag.length - 1, text.length)
        for (length in maxProbe downTo 1) {
            if (text.endsWith(tag.take(length))) {
                return text.substring(0, text.length - length)
            }
        }
        return text
    }

    /**
     * Truncates at the first invented dialogue label. A partial bracket label
     * at the tail is withheld too, so `[Us` never flashes before completion.
     */
    private fun cutAtRoleMarker(text: String): Pair<String, Boolean> {
        var first = -1
        TextProtocolMarkers.ROLE_STOP_MARKERS.forEach { marker ->
            val index = text.indexOf(marker)
            if (index >= 0 && (first < 0 || index < first)) first = index
        }
        if (first >= 0) return Pair(text.substring(0, first), true)
        // Only bracketed labels are safe to hold back: a bare "\n" would
        // otherwise be stripped as the first character of "\nUser:".
        val partialSafe = TextProtocolMarkers.ROLE_STOP_MARKERS.filter { it.startsWith("[") }
        return Pair(stripAnyPartial(text, partialSafe), false)
    }

    private fun extractTools(cleaned: String, allowUnclosedTool: Boolean): List<PendingToolCall> {
        val payloads = mutableListOf<String>()
        val regex = Regex(
            Regex.escape(TextProtocolMarkers.TOOL_START) +
                "([\\s\\S]*?)" +
                Regex.escape(TextProtocolMarkers.TOOL_END)
        )
        regex.findAll(cleaned).forEach { payloads.add(it.groupValues[1].trim()) }

        if (allowUnclosedTool) {
            val open = cleaned.indexOf(TextProtocolMarkers.TOOL_START)
            if (open >= 0 && cleaned.lastIndexOf(TextProtocolMarkers.TOOL_END) < open) {
                payloads.add(cleaned.substring(open + TextProtocolMarkers.TOOL_START.length).trim())
            }
        }

        return payloads.mapNotNull { payload ->
            val obj = runCatching {
                gson.fromJson(payload.trim().trimEnd('`', ' ', '\n'), JsonObject::class.java)
            }.getOrNull() ?: return@mapNotNull null
            val name = runCatching { obj.getAsJsonPrimitive("name")?.asString }
                .getOrNull() ?: return@mapNotNull null
            if (name.isBlank()) return@mapNotNull null
            // Some models ignore the "arguments" wrapper and put the fields
            // at the top level next to "name"; accept that shape too.
            val arguments = obj.get("arguments")?.let {
                if (it.isJsonObject) it.toString() else "{}"
            } ?: JsonObject().apply {
                obj.entrySet().forEach { (key, value) ->
                    if (key != "name") add(key, value)
                }
            }.toString()
            PendingToolCall(UUID.randomUUID().toString(), name, arguments)
        }
    }

    private data class Parsed(
        val visible: String,
        val reasoning: String,
        val tools: List<PendingToolCall>,
        val stopHit: Boolean
    )
}
