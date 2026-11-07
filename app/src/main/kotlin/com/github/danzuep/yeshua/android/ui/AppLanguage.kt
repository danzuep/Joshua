/*
 * Copyright (C) 2023 Xizhi Zhu
 * Copyright (C) 2026 Daniel Collingwood
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.danzuep.yeshua.android.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** UI languages, which must match res/xml/locales_config.xml and Configurations.supportedLocales (as a resource filter, e.g. `b+zh+Hant`). */
object AppLanguage {
    val CODES = listOf("en", "zh", "zh-Hant", "es", "hi", "ar", "fr", "pt", "bn", "ru", "ur", "id", "de", "ja", "tr", "vi", "ko", "it", "th")

    /** The explicitly chosen language code, or null to follow the system. */
    fun selectedCode(): String? = AppCompatDelegate.getApplicationLocales()[0]?.let { locale ->
        CODES.firstOrNull { it == locale.toLanguageTag() } ?: CODES.firstOrNull { it == locale.language }
    }

    fun select(code: String?) = AppCompatDelegate.setApplicationLocales(
        if (code == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(code)
    )

    /** The language's own name, e.g. "español". */
    fun nativeName(code: String): String = Locale.forLanguageTag(code).let { it.getDisplayName(it) }.replaceFirstChar { it.uppercase() }
}
