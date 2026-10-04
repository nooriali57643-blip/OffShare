package com.alinoori.offshare

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {

    private val vm: ShareViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val lang = LocaleHelper.current(this)
        // The button shows the language you will switch TO.
        val label = if (lang == "fa") "English" else "فارسی / دری"

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF0B7A75),
                    background = Color.White,
                )
            ) {
                App(
                    vm = vm,
                    languageLabel = label,
                    onToggleLanguage = {
                        LocaleHelper.save(this, if (lang == "fa") "en" else "fa")
                        recreate()
                    },
                )
            }
        }
    }
}
