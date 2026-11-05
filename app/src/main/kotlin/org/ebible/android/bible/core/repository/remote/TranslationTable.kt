/*
 * Copyright (C) 2026 eBible
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

package org.ebible.android.bible.core.repository.remote

import java.net.URI
import java.util.Locale

const val DEFAULT_DOWNLOAD_SITE = "https://ebible.org/download.php"

/** A row of the translation table on a download page such as [DEFAULT_DOWNLOAD_SITE]. */
data class TranslationTableRow(
    val territory: String,
    val language: String,
    val languageEnglish: String,
    val vernacularTitle: String,
    val englishTitle: String,
    val detailsHref: String,
    val id: String,
    val textDirection: String = "",
)

private val TABLE_START = Regex("<table\\b", RegexOption.IGNORE_CASE)
private val TABLE_END = Regex("</table>", RegexOption.IGNORE_CASE)
private val HEADER_CELL = Regex("<th\\b", RegexOption.IGNORE_CASE)
private val ROW = Regex("<tr\\b", RegexOption.IGNORE_CASE)
private val CELL = Regex("<t[dh]\\b[^>]*>", RegexOption.IGNORE_CASE)
private val CELL_END = Regex("</t[dh]>|</tr>", RegexOption.IGNORE_CASE)
private val ANCHOR = Regex("<a\\b([^>]*)>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val HREF = Regex("href\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')", RegexOption.IGNORE_CASE)
private val TAG = Regex("<[^>]*>")
private val ENTITY = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);")
private val HEADER_PREFIXES = listOf("territory", "language", "vernacular")

/**
 * Parses the translation table of a download page: the first table whose header starts with "Territory",
 * "Language" or "Vernacular", with the columns territory, language, English language name, vernacular title and
 * English title. The translation id is taken from the `id` query parameter of the details link on the last column.
 */
fun parseTranslationTable(html: String, pageUrl: String): List<TranslationTableRow> {
    val table = html.split(TABLE_START).drop(1).map { it.split(TABLE_END).first() }.firstOrNull { table ->
        splitRows(table).firstOrNull { row -> splitCells(row).any { it.text.isNotEmpty() } }
            ?.let { row -> splitCells(row).any { cell -> HEADER_PREFIXES.any { cell.text.lowercase().startsWith(it) } } } == true
    } ?: return emptyList()

    return splitRows(table).mapNotNull { row ->
        if (HEADER_CELL.containsMatchIn(row)) return@mapNotNull null

        val cells = splitCells(row)
        if (cells.size < 5) return@mapNotNull null

        val href = cells[4].href
        val id = idOf(href) ?: return@mapNotNull null
        TranslationTableRow(
            territory = cells[0].text,
            language = cells[1].anchorText,
            languageEnglish = cells[2].anchorText,
            vernacularTitle = cells[3].anchorText,
            englishTitle = cells[4].anchorText,
            detailsHref = resolve(pageUrl, href),
            id = id,
        )
    }.toList().distinctBy { it.id }
}

/** Parses the eBible.org translations.csv catalog, keeping downloadable, redistributable translations only. */
fun parseTranslationCsv(csv: String, pageUrl: String): List<TranslationTableRow> {
    val records = parseCsvRecords(csv.removePrefix("\uFEFF"))
    val header = records.firstOrNull()?.map { it.trim().lowercase() } ?: return emptyList()
    val columns = header.withIndex().associate { it.value to it.index }

    fun List<String>.value(vararg names: String): String =
        names.firstNotNullOfOrNull { name -> columns[name.lowercase()]?.let { getOrNull(it)?.trim() } }.orEmpty()

    return records.drop(1).mapNotNull { row ->
        if (!row.value("Redistributable").equals("true", ignoreCase = true)) return@mapNotNull null
        if (columns.containsKey("downloadable") && !row.value("downloadable").equals("true", ignoreCase = true)) return@mapNotNull null
        val id = row.value("translationId", "Home").ifEmpty { return@mapNotNull null }
        val language = row.value("languageName", "Language")
        val dialect = row.value("dialect")
        val languageEnglish = row.value("languageNameInEnglish", "Language (English)").let {
            when {
                it.isEmpty() || dialect.isEmpty() || it.endsWith(dialect) -> it
                else -> "$it, $dialect"
            }
        }
        val title = row.value("title", "Vernacular Title")
        val shortTitle = row.value("shortTitle", "English Title")
        TranslationTableRow(
            territory = row.value("territory", "countryName", "Territory"),
            language = language,
            languageEnglish = languageEnglish,
            vernacularTitle = title,
            englishTitle = shortTitle.ifEmpty { title },
            detailsHref = resolve(pageUrl, "details.php?id=$id"),
            id = id,
            textDirection = row.value("textDirection"),
        )
    }.distinctBy { it.id }
}

private fun parseCsvRecords(csv: String): List<List<String>> {
    val records = mutableListOf<List<String>>()
    var record = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var index = 0

    fun endField() {
        record.add(field.toString())
        field.setLength(0)
    }

    fun endRecord() {
        endField()
        if (record.any { it.isNotEmpty() }) records.add(record)
        record = mutableListOf()
    }

    while (index < csv.length) {
        val char = csv[index]
        when {
            char == '"' && quoted && csv.getOrNull(index + 1) == '"' -> {
                field.append('"')
                index++
            }
            char == '"' -> quoted = !quoted
            char == ',' && !quoted -> endField()
            char == '\n' && !quoted -> endRecord()
            char == '\r' && !quoted -> {
                if (csv.getOrNull(index + 1) == '\n') index++
                endRecord()
            }
            else -> field.append(char)
        }
        index++
    }
    require(!quoted) { "Unterminated quoted field in translations.csv" }
    if (field.isNotEmpty() || record.isNotEmpty()) endRecord()
    return records
}

private class Cell(private val html: String) {
    val text: String = decode(html.replace(TAG, " "))
    private val anchor = ANCHOR.find(html)
    val anchorText: String = anchor?.let { decode(it.groupValues[2].replace(TAG, " ")) } ?: text
    val href: String = anchor?.let { a -> HREF.find(a.groupValues[1])?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } } } ?: ""
}

private fun splitRows(table: String): List<String> = table.split(ROW).drop(1)

private fun splitCells(row: String): List<Cell> = row.split(CELL).drop(1).map { Cell(it.split(CELL_END).first()) }

private fun decode(text: String): String = ENTITY.replace(text) { match ->
    val entity = match.groupValues[1]
    when {
        entity.startsWith("#x", ignoreCase = true) -> entity.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
        entity.startsWith("#") -> entity.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
        else -> when (entity.lowercase()) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            "nbsp" -> " "
            else -> null
        }
    } ?: match.value
}.replace(Regex("\\s+"), " ").trim()

private fun idOf(href: String): String? {
    val query = href.substringAfter('?', "").substringBefore('#')
    return query.split('&').firstOrNull { it.startsWith("id=") }?.substringAfter('=')?.trim()?.ifEmpty { null }
}

private fun resolve(pageUrl: String, href: String): String = try {
    URI(pageUrl).resolve(href).toString()
} catch (e: Exception) {
    href
}

/** The scheme and host (and port) of [siteUrl], e.g. `https://ebible.org`, or `null` if it is not a valid web address. */
fun siteOrigin(siteUrl: String): String? = try {
    val uri = URI(siteUrl.trim())
    if (uri.scheme?.lowercase() in listOf("http", "https") && !uri.host.isNullOrEmpty()) {
        "${uri.scheme.lowercase()}://${uri.authority}"
    } else {
        null
    }
} catch (e: Exception) {
    null
}

/** The app uses the translation id as the name of a database table, so it can only contain letters, digits and underscores. */
fun TranslationTableRow.toShortName(): String = id.replace(Regex("[^A-Za-z0-9]"), "_")

/** eBible.org ids start with the ISO 639-3 language code; falls back to `English (native)` if it is unknown to the JVM. */
fun TranslationTableRow.toLanguage(): String {
    val code = id.take(3).lowercase()
    val known = code.length == 3 && code.all { it in 'a'..'z' } && Locale.forLanguageTag(code).displayLanguage.let { it.isNotEmpty() && !it.equals(code, ignoreCase = true) }
    if (known) return code
    val english = languageEnglish.ifEmpty { language }.ifEmpty { code }
    return if (language.isEmpty() || language == english) english else "$english ($language)"
}

fun TranslationTableRow.toRemoteTranslationInfo(): RemoteTranslationInfo =
        RemoteTranslationInfo(toShortName(), englishTitle.ifEmpty { vernacularTitle }.ifEmpty { id }, toLanguage(), 0L)

/** The USFM zip of the translation, which eBible.org publishes in `/Scriptures/<id>_usfm.zip`. */
fun usfmZipUrl(origin: String, shortName: String): String = "$origin/Scriptures/${shortName.replace('_', '-')}_usfm.zip"
