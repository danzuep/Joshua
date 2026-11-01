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

package me.xizzhu.android.joshua.core.repository.remote

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

/**
 * Parses a tab-separated copy of the translation table (see `scripts/update_ebible_downloads.py`): a header line, then
 * territory, language, English language name, vernacular title, English title and translation id per line.
 */
fun parseTranslationTsv(tsv: String, pageUrl: String): List<TranslationTableRow> =
    tsv.lines().drop(1).mapNotNull { line ->
        val cells = line.split('\t').map { it.trim() }
        val id = cells.getOrNull(5)?.ifEmpty { null } ?: return@mapNotNull null
        TranslationTableRow(cells[0], cells[1], cells[2], cells[3], cells[4], resolve(pageUrl, "details.php?id=$id"), id)
    }.distinctBy { it.id }

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

/** Joshua uses the translation id as the name of a database table, so it can only contain letters, digits and underscores. */
fun TranslationTableRow.toShortName(): String = id.replace(Regex("[^A-Za-z0-9]"), "_")

/** eBible.org ids start with the ISO 639-3 language code; falls back to the English language name if it is unknown. */
fun TranslationTableRow.toLanguage(): String {
    val code = id.take(3).lowercase()
    val known = code.length == 3 && code.all { it in 'a'..'z' } && Locale(code).displayLanguage.let { it.isNotEmpty() && !it.equals(code, ignoreCase = true) }
    return if (known) code else languageEnglish.ifEmpty { language }.ifEmpty { code }
}

fun TranslationTableRow.toRemoteTranslationInfo(): RemoteTranslationInfo =
        RemoteTranslationInfo(toShortName(), englishTitle.ifEmpty { vernacularTitle }.ifEmpty { id }, toLanguage(), 0L)

/** The USFM zip of the translation, which eBible.org publishes in `/Scriptures/<id>_usfm.zip`. */
fun usfmZipUrl(origin: String, shortName: String): String = "$origin/Scriptures/${shortName.replace('_', '-')}_usfm.zip"
