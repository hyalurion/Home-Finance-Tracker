package com.chronie.homemoney.domain.model

/**
 * Core expense/transaction domain model.
 *
 * Represents a single financial transaction with support for soft-deletion
 * and optimistic sync tracking.
 *
 * @property id Unique identifier for this expense record.
 * @property type The category/type of this expense (e.g., FOOD, TRANSPORTATION).
 * @property remark Optional user-provided note or description.
 * @property amount The monetary amount of the transaction.
 * @property date The date of the transaction in "YYYY-MM-DD" format.
 * @property version Optimistic locking version for conflict resolution during sync.
 * @property updatedAt Epoch millis timestamp of the last modification.
 * @property deletedAt Non-null when the record has been soft-deleted (epoch millis), null otherwise.
 * @property isSynced Whether this record has been synchronized with the server.
 */
data class Expense(
    val id: String,
    val type: ExpenseType,
    val remark: String?,
    val amount: Double,
    val date: String,
    val version: Int = 1,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val isSynced: Boolean = false
)

/**
 * Enum representing all supported expense categories.
 *
 * [chineseName] is the canonical value stored in Room and sent to the server;
 * [displayNameKey] points at the localized UI label. Keeping the canonical
 * name on the enum removes the five duplicated hard-coded category tables
 * that used to drift apart whenever a category was added.
 *
 * Categories can be looked up from a canonical name, a localized label, an
 * English label or the enum name itself via [resolveCategory] / [fromString].
 */
enum class ExpenseType(val displayNameKey: String, val chineseName: String) {
    /** Daily household goods and supplies */
    DAILY_GOODS("expense_type_daily_goods", "日常用品"),
    /** Luxury items and high-end purchases */
    LUXURY("expense_type_luxury", "奢侈品"),
    /** Phone bills, internet, postage, and communication costs */
    COMMUNICATION("expense_type_communication", "通讯费用"),
    /** General food and grocery items */
    FOOD("expense_type_food", "食品"),
    /** Snacks, candies, and confectionery */
    SNACKS("expense_type_snacks", "零食糖果"),
    /** Cold beverages and ice cream */
    COLD_DRINKS("expense_type_cold_drinks", "冷饮"),
    /** Instant noodles, frozen meals, and other convenience foods */
    CONVENIENCE_FOOD("expense_type_convenience_food", "方便食品"),
    /** Textiles, fabrics, and related products */
    TEXTILES("expense_type_textiles", "纺织品"),
    /** Non-alcoholic beverages (tea, juice, etc.) */
    BEVERAGES("expense_type_beverages", "饮品"),
    /** Cooking condiments, sauces, and seasonings */
    CONDIMENTS("expense_type_condiments", "调味品"),
    /** Public transit, fuel, parking, and travel costs */
    TRANSPORTATION("expense_type_transportation", "交通出行"),
    /** Eating out at restaurants and food delivery */
    DINING("expense_type_dining", "餐饮"),
    /** Medical bills, prescriptions, and healthcare expenses */
    MEDICAL("expense_type_medical", "医疗费用"),
    /** Fresh and dried fruits */
    FRUITS("expense_type_fruits", "水果"),
    /** Miscellaneous expenses that don't fit other categories */
    OTHER("expense_type_other", "其他"),
    /** Seafood, fish, and aquatic products */
    SEAFOOD("expense_type_seafood", "水产品"),
    /** Dairy products (milk, cheese, yogurt, etc.) */
    DAIRY("expense_type_dairy", "乳制品"),
    /** Gifts, red envelopes, and social obligations */
    GIFTS("expense_type_gifts", "礼物人情"),
    /** Vacation, hotel, and leisure travel costs */
    TRAVEL("expense_type_travel", "旅行度假"),
    /** Government fees, taxes, and administrative charges */
    GOVERNMENT("expense_type_government", "政务"),
    /** Electricity, water, gas, and utility bills */
    UTILITIES("expense_type_utilities", "水电煤气"),
    /** Haircuts, salon, and personal grooming services */
    BEAUTY("expense_type_beauty", "美容美发"),
    /** Tofu, soy milk, and bean-based food products */
    BEAN_PRODUCTS("expense_type_bean_products", "豆制品"),
    /** Skincare, makeup, and personal care items */
    COSMETICS("expense_type_cosmetics", "个护美妆"),
    /** Phones, computers, gadgets, and electronic devices */
    ELECTRONICS("expense_type_electronics", "电子产品"),
    /** Refrigerators, washing machines, and home appliances */
    HOUSEHOLD_APPLIANCES("expense_type_household_appliances", "家用电器"),
    /** Tools, screws, paint, and hardware supplies */
    HARDWARE("expense_type_hardware", "五金"),
    /** Clothing, shoes, and apparel */
    CLOTHING("expense_type_clothing", "服装");

    companion object {
        /** Canonical Chinese storage names, the primary lookup table. */
        private val BY_CHINESE_NAME: Map<String, ExpenseType> =
            entries.associateBy { it.chineseName }

        /**
         * Traditional-Chinese and shortened UI labels. The zh-CN UI uses
         * "通讯"/"医疗" while the database stores "通讯费用"/"医疗费用",
         * so both directions must round-trip.
         */
        private val CJK_ALIASES: Map<String, ExpenseType> = mapOf(
            "通訊費用" to COMMUNICATION, "通訊" to COMMUNICATION, "通讯" to COMMUNICATION,
            "冷飲" to COLD_DRINKS, "紡織品" to TEXTILES, "飲品" to BEVERAGES,
            "調味品" to CONDIMENTS, "餐飲" to DINING,
            "醫療費用" to MEDICAL, "醫療" to MEDICAL, "医疗" to MEDICAL,
            "水產品" to SEAFOOD, "乳製品" to DAIRY, "禮物人情" to GIFTS,
            "政務" to GOVERNMENT, "水電煤氣" to UTILITIES, "美容美髮" to BEAUTY,
            "豆製品" to BEAN_PRODUCTS, "個護美妝" to COSMETICS,
            "電子產品" to ELECTRONICS, "家用電器" to HOUSEHOLD_APPLIANCES,
            "服裝" to CLOTHING, "飲料" to BEVERAGES, "水" to BEVERAGES
        )

        /** English resource labels plus common synonyms used by hosted models. */
        private val ENGLISH_ALIASES: Map<String, ExpenseType> = mapOf(
            "daily goods" to DAILY_GOODS, "household goods" to DAILY_GOODS,
            "luxury" to LUXURY,
            "communication" to COMMUNICATION, "telecom" to COMMUNICATION,
            "food" to FOOD, "groceries" to FOOD,
            "snacks" to SNACKS, "candy" to SNACKS,
            "cold drinks" to COLD_DRINKS, "beverages" to COLD_DRINKS,
            "convenience food" to CONVENIENCE_FOOD,
            "textiles" to TEXTILES,
            "drinks" to BEVERAGES, "drink" to BEVERAGES, "beverage" to BEVERAGES,
            "condiments" to CONDIMENTS, "seasonings" to CONDIMENTS,
            "transportation" to TRANSPORTATION, "transport" to TRANSPORTATION,
            "dining out" to DINING, "dining" to DINING,
            "healthcare" to MEDICAL, "medical" to MEDICAL, "medicine" to MEDICAL,
            "fruits" to FRUITS, "fruit" to FRUITS,
            "other" to OTHER, "misc" to OTHER, "miscellaneous" to OTHER,
            "seafood" to SEAFOOD,
            "dairy" to DAIRY,
            "gifts" to GIFTS, "gift" to GIFTS,
            "travel" to TRAVEL, "vacation" to TRAVEL,
            "government fees" to GOVERNMENT, "government" to GOVERNMENT,
            "utilities" to UTILITIES, "utility bills" to UTILITIES,
            "beauty salon" to BEAUTY, "beauty" to BEAUTY,
            "bean products" to BEAN_PRODUCTS,
            "cosmetics" to COSMETICS, "personal care" to COSMETICS,
            "electronics" to ELECTRONICS,
            "home appliances" to HOUSEHOLD_APPLIANCES, "appliances" to HOUSEHOLD_APPLIANCES,
            "hardware" to HARDWARE,
            "clothing & accessories" to CLOTHING,
            "clothing &amp; accessories" to CLOTHING,
            "clothing" to CLOTHING, "apparel" to CLOTHING
        )

        /** Strips full-width whitespace and quotes models frequently emit. */
        private fun normalize(value: String): String = value
            .replace('\u3000', ' ')
            .replace('\u00A0', ' ')
            .trim()
            .trim('"', '\'', '`', '“', '”', '‘', '’', '「', '」', '『', '』')
            .trim()

        /**
         * Resolves any category representation to an [ExpenseType].
         * Returns null (rather than silently mapping to [OTHER]) when nothing
         * matches, so callers can report the rejected value.
         */
        fun resolveCategory(value: String): ExpenseType? {
            val key = normalize(value)
            if (key.isEmpty()) return null
            BY_CHINESE_NAME[key]?.let { return it }
            CJK_ALIASES[key]?.let { return it }
            ENGLISH_ALIASES[key.lowercase()]?.let { return it }
            val enumKey = key.uppercase(java.util.Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_')
            return entries.firstOrNull { it.name == enumKey }
        }

        /**
         * Converts a category label to the corresponding [ExpenseType].
         * Canonical Chinese names, localized/English labels and enum names are
         * all accepted; unrecognized values fall back to [OTHER].
         */
        fun fromString(value: String): ExpenseType = resolveCategory(value) ?: OTHER
    }
}
