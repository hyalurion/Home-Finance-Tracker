package com.chronie.homemoney.data.chat

import com.chronie.homemoney.domain.model.chat.ToolProtocol
import com.chronie.homemoney.domain.model.chat.ToolSpec
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Builds the system instruction shared by all three model backends.
 *
 * The DATE REFERENCE block is generated on-device from the real clock. Small
 * models are notoriously bad at date arithmetic (they happily answer that
 * "last month" from 2026-11-14 means 2025-10-15), so every relative range is
 * pre-resolved here and the model is told to copy the exact dates into a tool
 * call instead of computing anything itself.
 */
object ChatSystemPrompt {

    const val TOOL_MARKER_START = TextProtocolMarkers.TOOL_START
    const val TOOL_MARKER_END = TextProtocolMarkers.TOOL_END

    fun today(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    fun build(
        tools: List<ToolSpec>,
        protocol: ToolProtocol,
        onDevice: Boolean = false
    ): String {
        val date = LocalDate.now()
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val categories = AgentToolSpecs.CATEGORIES.joinToString("、")
        val toolList = if (tools.isEmpty()) {
            "(none available)"
        } else {
            tools.joinToString("\n") { "- ${it.name}: ${it.description}" }
        }

        val protocolBlock = when (protocol) {
            ToolProtocol.NATIVE -> """
                TOOL CALL PROTOCOL: use the native tool/function calling API. Never print
                raw JSON tool calls as text. Call a tool as soon as the user's request
                requires reading or writing records; do not guess or fabricate data.
                """.trimIndent()

            ToolProtocol.TEXT -> """
                TOOL CALL PROTOCOL (this endpoint does not support function calling):
                When you need a tool, output EXACTLY one block and nothing else in that turn:
                ${TextProtocolMarkers.TOOL_START}{"name":"tool_name","arguments":{...}}${TextProtocolMarkers.TOOL_END}
                Replace tool_name with a listed tool and put valid JSON in arguments.
                Never wrap tool blocks in Markdown code fences. After the tool result
                is supplied, answer the user normally.
                """.trimIndent()

            ToolProtocol.NONE -> """
                No tools are available for this provider. Answer only from the current
                conversation. Never claim you read, wrote or queried any records.
                """.trimIndent()
        }

        // Plain-text markers are always visible to the renderer, unlike the
        // tokenizer-special <think> tokens used by Qwen3-compatible servers.
        // Small models follow a concrete example far more reliably than an
        // abstract instruction, so the exact output shape is shown here.
        val reasoningBlock = if (protocol == ToolProtocol.TEXT || onDevice) {
            val previousMonth = LocalDate.now().minusMonths(1)
            val start = previousMonth.withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
            val end = previousMonth.withDayOfMonth(previousMonth.lengthOfMonth())
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
            """
            OUTPUT FORMAT (MANDATORY): ALWAYS start your reply with
            ${TextProtocolMarkers.REASON_START}, write a SHORT 1-2 sentence plan,
            then ${TextProtocolMarkers.REASON_END}, then the answer or a tool block.
            Example for a spending question:
            ${TextProtocolMarkers.REASON_START}用户要查上个月，按 $start 到 $end 调用查询工具。${TextProtocolMarkers.REASON_END}
            <<<your answer here>>>
            The app renders the bracketed block separately. Never use the markers
            anywhere else, and never write <think> tags.
            """.trimIndent()
        } else {
            ""
        }

        val deviceBlock = if (onDevice) {
            """
            ON-DEVICE OUTPUT RULES (must obey):
            - Produce exactly ONE assistant reply, then stop.
            - Never write [User], [Assistant], "User:", or any invented follow-up dialogue.
            - Never repeat the user's question or ask for a date you can look up above.
            - Keep the answer under 120 words. No emoji, no ASCII art, no "??" placeholders.
            """.trimIndent()
        } else {
            ""
        }

        return buildString {
            append("You are the AI bookkeeping assistant inside Home Finance Tracker.\n")
            append("Today is $dateStr ($weekday). Reply in the same language the user writes in.\n")
            append("Markdown is supported; keep answers concise and factual.\n\n")

            append("DATE REFERENCE (authoritative; copy these dates verbatim, never compute your own):\n")
            append(dateReference(date)).append("\n\n")

            append("EXPENSE RULES:\n")
            append("- The only valid categories are these Chinese names: $categories\n")
            append("- amounts must be positive numbers, dates must be YYYY-MM-DD\n")
            append("- keep the remark in the user's original language\n")
            append("- never invent a purchase or a financial figure\n")
            append("- after creating records, briefly list what was saved\n\n")

            append("AVAILABLE TOOLS:\n").append(toolList).append("\n\n")
            append(protocolBlock).append("\n\n")

            append("RELATIVE DATE QUERIES:\n")
            append("- Resolve any relative range using DATE REFERENCE above.\n")
            append("- Call the query tool IMMEDIATELY with those exact dates.\n")
            append("- Never ask the user to confirm, restate, or retype the date range.\n")
            append("- Never answer with a guessed date such as a year in the past.\n")
            append("- If query tools are unavailable, say ONCE that the user can enable\n")
            append("  \"Allow AI to query records\" in Settings > AI, then stop. Do not loop.\n\n")

            append("PRIVACY: financial records are private. Only read them through the query tools\n")
            append("and only within the date range the user explicitly asked about.\n")

            if (reasoningBlock.isNotBlank()) append("\n").append(reasoningBlock).append("\n")
            if (deviceBlock.isNotBlank()) append("\n").append(deviceBlock).append("\n")
        }.trim()
    }

    /** Concrete date ranges so no model has to do calendar arithmetic. */
    private fun dateReference(today: LocalDate): String {
        val iso = DateTimeFormatter.ISO_LOCAL_DATE
        fun d(value: LocalDate): String = value.format(iso)
        val monthStart = today.withDayOfMonth(1)
        val previousMonth = today.minusMonths(1)
        val previousMonthStart = previousMonth.withDayOfMonth(1)
        val previousMonthEnd = previousMonth.withDayOfMonth(previousMonth.lengthOfMonth())
        val yearStart = today.withDayOfYear(1)

        return listOf(
            "today" to "${d(today)} ~ ${d(today)}",
            "yesterday" to "${d(today.minusDays(1))} ~ ${d(today.minusDays(1))}",
            "tomorrow" to "${d(today.plusDays(1))} ~ ${d(today.plusDays(1))}",
            "last 7 days / 最近7天 / 近一周" to "${d(today.minusDays(6))} ~ ${d(today)}",
            "last 30 days / 最近30天 / 最近1个月 / 近一个月" to
                "${d(today.minusDays(29))} ~ ${d(today)}",
            "one calendar month back / 过去一个月" to "${d(today.minusMonths(1))} ~ ${d(today)}",
            "this month / 本月 / 这个月" to "${d(monthStart)} ~ ${d(today)}",
            "last month / 上月 / 上个月" to "${d(previousMonthStart)} ~ ${d(previousMonthEnd)}",
            "this year / 今年" to "${d(yearStart)} ~ ${d(today)}"
        ).joinToString("\n") { (label, range) -> "- $label: $range" }
    }
}
