package com.chronie.homemoney.core.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the display currency, decoupled from the app language.
 *
 * Historically the currency symbol came from the locale string resource
 * `R.string.currency_symbol`, so changing the app language also changed
 * the currency. Users can now pin a currency explicitly; the default
 * (`null` selection) keeps the previous follow-language behaviour.
 *
 * The selection is persisted to SharedPreferences and exposed as a
 * [StateFlow] so every amount on screen recomposes immediately when the
 * user switches currency.
 */
@Singleton
class CurrencyManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /** A selectable currency. [symbol] is used with the existing
     *  `currency_format` / `currency_format_no_decimal` patterns. */
    enum class CurrencyOption(val code: String, val symbol: String) {
        CNY("CNY", "¥"),
        USD("USD", "$"),
        EUR("EUR", "€"),
        GBP("GBP", "£"),
        JPY("JPY", "¥"),
        KRW("KRW", "₩"),
        HKD("HKD", "HK$"),
        TWD("TWD", "NT$"),
        SGD("SGD", "S$"),
        THB("THB", "฿"),
        MYR("MYR", "RM"),
        IDR("IDR", "Rp"),
        VND("VND", "₫");

        /** Compact label for pickers, e.g. "¥ CNY". */
        val label: String get() = "$symbol $code"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * The pinned currency, or `null` when following the app language.
     */
    private val _selectedOption = MutableStateFlow(loadSaved())
    val selectedOption: StateFlow<CurrencyOption?> = _selectedOption.asStateFlow()

    private fun loadSaved(): CurrencyOption? {
        val code = prefs.getString(KEY_CURRENCY, null) ?: return null
        return CurrencyOption.entries.firstOrNull { it.code == code }
    }

    /** Pins [option], or restores follow-language behaviour when `null`. */
    fun setCurrency(option: CurrencyOption?) {
        if (option == null) {
            prefs.edit().remove(KEY_CURRENCY).apply()
        } else {
            prefs.edit().putString(KEY_CURRENCY, option.code).apply()
        }
        _selectedOption.value = option
    }

    /** Resolves the effective symbol for the current selection. */
    fun currentSymbol(): String =
        _selectedOption.value?.symbol
            ?: context.getString(com.chronie.homemoney.R.string.currency_symbol)

    private companion object {
        const val PREFS_NAME = "currency_prefs"
        const val KEY_CURRENCY = "selected_currency"
    }
}

/** Hilt entry point so Compose screens can reach the singleton. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CurrencyEntryPoint {
    fun currencyManager(): CurrencyManager
}

private fun currencyManager(context: Context): CurrencyManager =
    EntryPointAccessors
        .fromApplication(context.applicationContext, CurrencyEntryPoint::class.java)
        .currencyManager()

/**
 * Reactive replacement for `context.getString(R.string.currency_symbol)`:
 * returns the pinned currency's symbol, falling back to the locale string
 * when the user hasn't chosen one. Recomposes on currency changes.
 */
@Composable
fun rememberCurrencySymbol(context: Context): String {
    val manager = remember { currencyManager(context) }
    val option by manager.selectedOption.collectAsState()
    return option?.symbol
        ?: context.getString(com.chronie.homemoney.R.string.currency_symbol)
}

/**
 * Reactive replacement for `NumberFormat.getCurrencyInstance(locale)`:
 * applies the pinned currency to the formatter when one is selected.
 */
@Composable
fun rememberCurrencyNumberFormat(context: Context): NumberFormat {
    val manager = remember { currencyManager(context) }
    val option by manager.selectedOption.collectAsState()
    return remember(option) {
        NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
            option?.let { currency = Currency.getInstance(it.code) }
        }
    }
}
