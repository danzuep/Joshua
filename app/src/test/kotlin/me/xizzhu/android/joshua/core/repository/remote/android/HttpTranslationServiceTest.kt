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

import androidx.test.core.app.ApplicationProvider
import me.xizzhu.android.joshua.core.Bible
import me.xizzhu.android.joshua.core.repository.remote.RemoteTranslationInfo
import me.xizzhu.android.joshua.tests.BaseUnitTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class HttpTranslationServiceTest : BaseUnitTest() {
    private lateinit var httpTranslationService: HttpTranslationService

    @BeforeTest
    override fun setup() {
        super.setup()

        httpTranslationService = HttpTranslationService(ApplicationProvider.getApplicationContext())
    }

    private fun usfmZip(vararg entries: Pair<String, String>): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }

    @Test
    fun `test toRemoteTranslation()`() {
        val info = RemoteTranslationInfo("eng_web", "World English Bible", "eng", 0L)
        val actual = httpTranslationService.toRemoteTranslation(
            info,
            usfmZip(
                "02-GENeng-web.usfm" to "\\id GEN\n\\h Genesis\n\\c 1\n\\p\n\\v 1 In the beginning\n\\v 2 Earth was formless",
                "readme.txt" to "ignored",
            )
        )
        assertEquals(info, actual.translationInfo)
        assertEquals(Bible.BOOK_COUNT, actual.bookNames.size)
        assertEquals("Genesis", actual.bookNames[0])
        assertEquals("EXO", actual.bookNames[1])
        assertEquals(mapOf(Pair(0, 0) to listOf("\u2029In the beginning", "Earth was formless")), actual.verses)
    }
}
