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

package com.github.danzuep.yeshua.android.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class TranslationDisplayNameTest {
    @Test
    fun `test eng prefix is trimmed and result uppercased`() {
        assertEquals("WEBU", "engwebu".toTranslationDisplayName())
        assertEquals("KJV", "kjv".toTranslationDisplayName())
        assertEquals("KJV", "KJV".toTranslationDisplayName())
    }

    @Test
    fun `test leading underscores are trimmed`() {
        assertEquals("WEBU", "eng_webu".toTranslationDisplayName())
        assertEquals("WEBU", "eng__webu".toTranslationDisplayName())
        assertEquals("WEBU", "_webu".toTranslationDisplayName())
    }

    @Test
    fun `test names without eng prefix are only uppercased`() {
        assertEquals("MSG", "msg".toTranslationDisplayName())
        assertEquals("ESV", "esv".toTranslationDisplayName())
    }

    @Test
    fun `test name that would be empty falls back to original`() {
        assertEquals("eng", "eng".toTranslationDisplayName())
    }
}
