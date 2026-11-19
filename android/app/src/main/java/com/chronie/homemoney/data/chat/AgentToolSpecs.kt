package com.chronie.homemoney.data.chat

import com.chronie.homemoney.domain.model.ExpenseType
import com.chronie.homemoney.domain.model.chat.ToolSpec
import java.time.LocalDate

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

    /**
     * Canonical Chinese category names accepted by the local database.
     * Derived from the enum so a newly added category cannot be missing here.
     */
    val CATEGORIES: List<String> = ExpenseType.entries.map { it.chineseName }

    /** One concrete record shape, injected into the system prompt for TEXT mode. */
    val ADD_EXPENSE_EXAMPLE: String
        get() = """{"name":"$ADD_EXPENSE","arguments":{"records":[{"category":"饮品","amount":18.0,"date":"${LocalDate.now()}","remark":"买的水"}]}}"""

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
                          "category": { "type": "string", "description": "One of the allowed Chinese category names, e.g. 饮品" },
                          "type": { "type": "string", "description": "Legacy alias for category" },
                          "amount": { "type": "number", "description": "Positive amount" },
                          "date": { "type": "string", "description": "YYYY-MM-DD" },
                          "remark": { "type": "string", "description": "What was purchased, in the user's language" }
                        },
                        "required": ["amount", "date", "remark"]
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
                        "category": { "type": "string", "description": "Optional Chinese category filter" },
                        "type": { "type": "string", "description": "Legacy alias for category" },
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
