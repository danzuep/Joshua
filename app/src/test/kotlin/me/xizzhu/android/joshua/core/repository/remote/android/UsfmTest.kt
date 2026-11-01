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

import me.xizzhu.android.joshua.core.repository.remote.parseUsfm
import me.xizzhu.android.joshua.core.repository.remote.stripUsfmMarkup
import kotlin.test.Test
import kotlin.test.assertEquals

class UsfmTest {
    @Test
    fun `test stripUsfmMarkup()`() {
        assertEquals(
            "In the beginning God created",
            stripUsfmMarkup("\\w In|strong=\"H7225\"\\w* the \\add beginning\\add* God\\f + \\fr 1:1 \\ft note\\f* created")
        )
    }

    @Test
    fun `test parseUsfm()`() {
        val book = parseUsfm(
            """
            \id GEN
            \h Genesis
            \toc2 Genesis (long)
            \c 1
            \s1 Heading
            \p
            \v 1 First
            \q1 continued
            \q2 indented
            \v 2-3 Bridged
            \v 4 Fourth
            \c 2
            \v 1 Next chapter
            """.trimIndent()
        )
        assertEquals("GEN", book.code)
        assertEquals("Genesis (long)", book.name)
        assertEquals(mapOf(1 to "\u2028Heading\u2028\u2029First\ncontinued\n\u2003indented", 2 to "Bridged", 3 to "", 4 to "Fourth"), book.chapters.getValue(1))
        assertEquals(mapOf(1 to "Next chapter"), book.chapters.getValue(2))
    }
}
