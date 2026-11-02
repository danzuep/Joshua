/*
 * Copyright (C) 2023 Xizhi Zhu
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

package me.xizzhu.android.joshua.ui

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
