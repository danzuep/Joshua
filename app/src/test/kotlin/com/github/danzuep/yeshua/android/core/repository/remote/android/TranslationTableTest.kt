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

package com.github.danzuep.yeshua.android.core.repository.remote.android

import com.github.danzuep.yeshua.android.core.repository.remote.RemoteTranslationInfo
import com.github.danzuep.yeshua.android.core.repository.remote.parseTranslationTable
import com.github.danzuep.yeshua.android.core.repository.remote.parseTranslationCsv
import com.github.danzuep.yeshua.android.core.repository.remote.siteOrigin
import com.github.danzuep.yeshua.android.core.repository.remote.toRemoteTranslationInfo
import com.github.danzuep.yeshua.android.core.repository.remote.usfmZipUrl
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
    fun `test parseTranslationCsv() handles quoted values and filters unavailable translations`() {
        val csv = "\uFEFFlanguageCode,translationId,languageName,languageNameInEnglish,dialect,homeDomain,title,description,Redistributable,downloadable,shortTitle,textDirection\r\n" +
            "eng,eng-web,English,English,,ebible.org,\"World English Bible, Expanded\",\"Description, with \"\"quotes\"\"\nand another line\",True,True,\"WEB, Expanded\",ltr\r\n" +
            "eng,eng-private,English,English,,ebible.org,Private Bible,,False,True,Private Bible,ltr\r\n" +
            "eng,eng-unavailable,English,English,,ebible.org,Unavailable Bible,,True,False,Unavailable Bible,ltr\r\n"

        val rows = parseTranslationCsv(csv, "https://ebible.org/download.php")
        assertEquals(listOf("eng-web"), rows.map { it.id })
        assertEquals("World English Bible, Expanded", rows[0].vernacularTitle)
        assertEquals("WEB, Expanded", rows[0].englishTitle)
        assertEquals("ltr", rows[0].textDirection)
        assertEquals("https://ebible.org/details.php?id=eng-web", rows[0].detailsHref)
    }

    @Test
    fun `test toRemoteTranslationInfo()`() {
        val rows = parseTranslationCsv(
            "translationId,languageName,languageNameInEnglish,title,shortTitle,Redistributable\n" +
                "eng-web,English,English,World English Bible,World English Bible & more,True\n",
            "https://ebible.org/download.php"
        )
        assertEquals(RemoteTranslationInfo("eng_web", "World English Bible & more", "eng", 0L), rows[0].toRemoteTranslationInfo())
    }

    @Test
    fun `test toRemoteTranslationInfo() keeps the native name for languages unknown to the JVM`() {
        val rows = parseTranslationCsv(
            "translationId,languageName,languageNameInEnglish,dialect,title,shortTitle,Redistributable\n" +
                "zzzsbl,\u0395\u03bb\u03bb\u03b7\u03bd\u03b9\u03ba\u03ac,Greek,Ancient,x,SBL Greek NT,True\n",
            "https://ebible.org/download.php"
        )
        assertEquals("Greek, Ancient (\u0395\u03bb\u03bb\u03b7\u03bd\u03b9\u03ba\u03ac)", rows[0].toRemoteTranslationInfo().language)
    }

    @Test
    fun `test siteOrigin() and usfmZipUrl()`() {
        assertEquals("https://ebible.org", siteOrigin("https://ebible.org/download.php"))
        assertNull(siteOrigin("not a url"))
        assertNull(siteOrigin("ftp://example.com/a"))
        assertEquals("https://ebible.org/Scriptures/eng-web_usfm.zip", usfmZipUrl("https://ebible.org", "eng_web"))
    }
}
