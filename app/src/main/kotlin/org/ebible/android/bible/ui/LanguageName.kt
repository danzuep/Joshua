/*
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

package org.ebible.android.bible.ui

import java.util.Locale

/** The localized name of [language] (e.g. `en`, `eng` or `zh_CN`), or [language] itself if it is not a known language code. */
fun languageDisplayName(language: String): String {
    val code = language.split("_")[0]
    return Locale.forLanguageTag(code).displayLanguage.ifEmpty { code }
}

/** The English name followed by the native name in brackets (e.g. `Greek (Ελληνικά)`), or the English name alone if no other native name is known. */
fun languageDisplayNameWithNative(language: String): String {
    val code = language.split("_")[0]
    val english = languageDisplayName(code)
    val locale = Locale.forLanguageTag(code)
    val native = locale.getDisplayLanguage(locale)
    return if (native.isEmpty() || native == english) english else "$english ($native)"
}

/** Languages that eBible.org lists under a more specific code than the one the system uses (e.g. `zh` for `cmn` or `yue`). */
private val RELATED_LANGUAGES = mapOf(
    "zho" to setOf("cmn", "yue", "wuu", "hak", "nan", "gan", "hsn", "cdo"),
    "ara" to setOf("arb", "arz", "apd", "ary", "acm"),
    "msa" to setOf("zsm", "ind"),
    "fas" to setOf("pes", "prs"),
    "swa" to setOf("swh"),
)

private fun iso3(code: String): String? = if (code.length == 3) code.lowercase() else runCatching { Locale.forLanguageTag(code).isO3Language }.getOrNull()?.ifEmpty { null }

/** Whether the translation [language] (e.g. `en`, `eng`, `cmn` or `zh_CN`) is the language of [locale], comparing ISO 639 codes. */
fun languageMatchesLocale(language: String, locale: Locale = Locale.getDefault()): Boolean {
    val code = iso3(language.split("_")[0]) ?: return false
    val user = iso3(locale.language) ?: return false
    if (code == user) return true
    // The bibliographic and terminological codes differ for some languages, e.g. `zho` and `chi`.
    return RELATED_LANGUAGES.any { (macro, members) -> (user == macro || user in members) && (code == macro || code in members) }
}
