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

package me.xizzhu.joshua.ebible

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConverterTest {
    private fun usfm(code: String, chapters: Int) = buildString {
        append("\\id $code test\n\\h Book $code\n\\toc2 Name $code\n\\mt1 Title\n")
        for (c in 1..chapters) {
            append("\\c $c\n\\s Heading\n\\p\n")
            append("\\v 1 In the \\w begin|strong=\"H1\"\\w* ning\\f + \\fr 1:1 \\ft note\\f* done.\n")
            append("\\q1\n\\v 2-3 Bridged\n\\q2 cont.\n\\v 5 Last\n")
        }
    }

    private fun sourceZip(skip: String? = null) = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            BOOK_CODES.forEachIndexed { i, code ->
                if (code == skip) return@forEachIndexed
                zip.putNextEntry(ZipEntry("%02d-$code.usfm".format(i)))
                zip.write(usfm(code, CHAPTER_COUNTS[i]).toByteArray())
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private fun convertToEntries(source: ByteArray): Map<String, String> {
        val out = ByteArrayOutputStream()
        writeJoshuaZip(readUsfmZip(ByteArrayInputStream(source)), TranslationMeta("T", "Te\"st", "en_us"), out)
        val entries = hashMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entries[it.name] = zip.readBytes().toString(Charsets.UTF_8) }
        }
        return entries
    }

    @Test
    fun `strips markup and aligns bridged verses and gaps`() {
        val entries = convertToEntries(sourceZip())
        assertEquals(1190, entries.size)
        assertEquals("""{"verses":["In the beginning done.","Bridged cont.","","","Last"]}""", entries["0-0.json"])
        assertEquals(true, entries.getValue("books.json").startsWith("""{"name":"Te\"st","shortName":"T","language":"en_us","bookNames":["Name GEN","Name EXO""""))
    }

    @Test
    fun `fails when a book is missing`() {
        assertFailsWith<IllegalArgumentException> { convertToEntries(sourceZip(skip = "REV")) }
    }

    @Test
    fun `merges into existing list json`() {
        val dir = kotlin.io.path.createTempDirectory().toFile()
        try {
            File(dir, "list.json").writeText("""{"translations":[{"shortName":"KJV","name":"King James","language":"en_gb","size":1}]}""")
            val source = File(dir, "s.zip").apply { writeBytes(sourceZip()) }
            repeat(2) { convert(source, TranslationMeta("T", "Test", "en_us"), dir) }
            val list = File(dir, "list.json").readText()
            assertEquals(1, Regex("\"shortName\":\"KJV\"").findAll(list).count())
            assertEquals(1, Regex("\"shortName\":\"T\"").findAll(list).count())
        } finally {
            dir.deleteRecursively()
        }
    }
}
