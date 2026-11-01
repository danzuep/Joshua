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

package me.xizzhu.android.joshua.core.repository.remote.android

import me.xizzhu.android.joshua.core.repository.remote.RemoteTranslationInfo
import me.xizzhu.android.joshua.core.repository.remote.parseTranslationTable
import me.xizzhu.android.joshua.core.repository.remote.parseTranslationTsv
import me.xizzhu.android.joshua.core.repository.remote.siteOrigin
import me.xizzhu.android.joshua.core.repository.remote.toRemoteTranslationInfo
import me.xizzhu.android.joshua.core.repository.remote.usfmZipUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TranslationTableTest {
    private val html = """
        <html><body>
        <table><tr><td>menu</td></tr></table>
        <table class="downloadtable">
          <thead><tr><th>Territory</th><th>Language</th><th>Language (English)</th><th>Vernacular title</th><th>English title</th></tr></thead>
          <tbody>
          <tr><td>United States</td><td>English</td><td>English</td><td>World English Bible</td>
              <td><a href="details.php?id=eng-web">World English Bible &amp; more</a></td></tr>
          <tr><td>Spain</td><td>Espa&#241;ol</td><td>Spanish</td><td>Reina Valera</td>
              <td><a href="/details.php?id=spaRV">Reina Valera 1909</a></td></tr>
          <tr><td>Short</td><td>row</td></tr>
          <tr><td>Dup</td><td>English</td><td>English</td><td>x</td><td><a href="details.php?id=eng-web">Dup</a></td></tr>
          </tbody>
        </table></body></html>
    """.trimIndent()

    @Test
    fun `test parseTranslationTable()`() {
        val rows = parseTranslationTable(html, "https://ebible.org/download.php")
        assertEquals(listOf("eng-web", "spaRV"), rows.map { it.id })
        assertEquals("United States", rows[0].territory)
        assertEquals("World English Bible & more", rows[0].englishTitle)
        assertEquals("https://ebible.org/details.php?id=eng-web", rows[0].detailsHref)
        assertEquals("Español", rows[1].language)
        assertEquals("https://ebible.org/details.php?id=spaRV", rows[1].detailsHref)
    }

    @Test
    fun `test parseTranslationTable() without a translation table`() {
        assertEquals(emptyList(), parseTranslationTable("<table><tr><td>nothing</td></tr></table>", "https://example.com"))
    }

    @Test
    fun `test parseTranslationTsv()`() {
        val rows = parseTranslationTsv(
            "Territory\tLanguage\tLanguage (English)\tVernacular Title\tEnglish Title\tHome\n" +
                "United States\tEnglish\tEnglish\tWEB\tWorld English Bible Updated\tengwebu\n\nbad\tline\n",
            "https://ebible.org/download.php"
        )
        assertEquals(listOf("engwebu"), rows.map { it.id })
        assertEquals("World English Bible Updated", rows[0].englishTitle)
    }

    @Test
    fun `test toRemoteTranslationInfo()`() {
        val rows = parseTranslationTable(html, "https://ebible.org/download.php")
        assertEquals(RemoteTranslationInfo("eng_web", "World English Bible & more", "eng", 0L), rows[0].toRemoteTranslationInfo())
    }

    @Test
    fun `test siteOrigin() and usfmZipUrl()`() {
        assertEquals("https://ebible.org", siteOrigin("https://ebible.org/download.php"))
        assertNull(siteOrigin("not a url"))
        assertNull(siteOrigin("ftp://example.com/a"))
        assertEquals("https://ebible.org/Scriptures/eng-web_usfm.zip", usfmZipUrl("https://ebible.org", "eng_web"))
    }
}
