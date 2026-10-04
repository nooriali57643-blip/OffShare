package com.alinoori.offshare

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** English ("en") and Persian/Dari ("fa") language switching, saved across launches. */
object LocaleHelper {
    private const val PREFS = "offshare"
    private const val KEY = "lang"

    fun current(ctx: Context): String {
        val saved = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        if (saved != null) return saved
        return if (Locale.getDefault().language == "fa") "fa" else "en"
    }

    fun save(ctx: Context, lang: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, lang).apply()
    }

    fun wrap(ctx: Context): Context {
        val locale = Locale(current(ctx))
        Locale.setDefault(locale)
        val config = Configuration(ctx.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return ctx.createConfigurationContext(config)
    }
}
