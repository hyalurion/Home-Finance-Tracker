package com.chronie.homemoney.data.chat

import com.chronie.homemoney.domain.model.chat.ToolSpec

/**
 * Tool schemas advertised to the model. JSON Schema is used verbatim for
 * OpenAI `function.parameters` and Anthropic `input_schema`; the built-in
 * engine receives the same names/descriptions flattened into its prompt.
 *
 * Query tools are only advertised after the user explicitly grants consent
 * in Settings > AI, so a model cannot even ask for financial data otherwise.
 */
object AgentToolSpecs {
    const val ADD_EXPENSE = "add_expense"
    const val QUERY_EXPENSES = "query_expenses"
    const val SPENDING_SUMMARY = "get_spending_summary"
    const val CURRENT_DATE = "get_current_date"

    /** Canonical Chinese category names accepted by the local database. */
    val CATEGORIES = listOf(
        "日常用品", "奢侈品", "通讯费用", "食品", "零食糖果", "冷饮", "方便食品",
        "纺织品", "饮品", "调味品", "交通出行", "餐饮", "医疗费用", "水果",
        "其他", "水产品", "乳制品", "礼物人情", "旅行度假", "政务", "水电煤气",
        "美容美发", "豆制品", "个护美妆", "电子产品", "家用电器", "五金", "服装"
    )

    fun available(queryAuthorized: Boolean): List<ToolSpec> = buildList {
        add(
            ToolSpec(
                name = ADD_EXPENSE,
                description = "Create one or more expense records for the user. " +
                    "Use this whenever the user describes a purchase, a receipt, or " +
                    "a bill. Always confirm the extracted values in your final answer.",
                parametersJson = """
                {
                  "type": "object",
                  "properties": {
                    "records": {
                      "type": "array",
                      "description": "Expense records to create",
                      "items": {
                        "type": "object",
                        "properties": {
                          "type": { "type": "string", "description": "One of the allowed Chinese category names" },
                          "amount": { "type": "number", "description": "Positive amount" },
                          "date": { "type": "string", "description": "YYYY-MM-DD" },
                          "remark": { "type": "string", "description": "What was purchased, in the user's language" }
                        },
                        "required": ["type", "amount", "date", "remark"]
                      }
                    }
                  },
                  "required": ["records"]
                }
                """.trimIndent()
            )
        )
        if (queryAuthorized) {
            add(
                ToolSpec(
                    name = QUERY_EXPENSES,
                    description = "Read the user's expense records within an explicit date range. " +
                        "Only available because the user granted permission in settings. " +
                        "Never widen the range beyond what the user asked for.",
                    parametersJson = """
                    {
                      "type": "object",
                      "properties": {
                        "start_date": { "type": "string", "description": "YYYY-MM-DD inclusive" },
                        "end_date": { "type": "string", "description": "YYYY-MM-DD inclusive" },
                        "type": { "type": "string", "description": "Optional Chinese category filter" },
                        "limit": { "type": "integer", "description": "Max rows, 1..50, default 20" }
                      },
                      "required": ["start_date", "end_date"]
                    }
                    """.trimIndent()
                )
            )
            add(
                ToolSpec(
                    name = SPENDING_SUMMARY,
                    description = "Aggregate spending by category for an explicit date range. " +
                        "Requires the user's query permission.",
                    parametersJson = """
                    {
                      "type": "object",
                      "properties": {
                        "start_date": { "type": "string", "description": "YYYY-MM-DD inclusive" },
                        "end_date": { "type": "string", "description": "YYYY-MM-DD inclusive" }
                      },
                      "required": ["start_date", "end_date"]
                    }
                    """.trimIndent()
                )
            )
        }
        add(
            ToolSpec(
                name = CURRENT_DATE,
                description = "Return today's local date and weekday. Use before filling a date " +
                    "when the user says 'today' or 'yesterday'.",
                parametersJson = """{"type":"object","properties":{}}"""
            )
        )
    }
}
