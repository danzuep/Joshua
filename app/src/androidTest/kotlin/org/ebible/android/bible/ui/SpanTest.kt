/*
 * Copyright (C) 2023 Xizhi Zhu
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

import android.content.Context
import android.graphics.Color
import android.text.style.BackgroundColorSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import io.mockk.every
import io.mockk.mockk
import org.ebible.android.bible.R
import org.ebible.android.bible.core.Highlight
import org.ebible.android.bible.tests.BaseUnitTest
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SpanTest : BaseUnitTest() {
    @Test
    fun test_createDrawableSpan_with_non_existing_drawable() {
        val context = mockk<Context>().apply { every { getDrawable(any()) } returns null }
        assertNull(createDrawableSpan(context, R.drawable.ic_bookmark))
    }

    @Test
    fun test_createHighlightSpans() {
        assertTrue(createHighlightSpans(Highlight.COLOR_NONE).isEmpty())
        `verify highlight spans`(Highlight.COLOR_YELLOW, Color.BLACK, createHighlightSpans(Highlight.COLOR_YELLOW))
        `verify highlight spans`(Highlight.COLOR_PINK, Color.BLACK, createHighlightSpans(Highlight.COLOR_PINK))
        `verify highlight spans`(Highlight.COLOR_ORANGE, Color.BLACK, createHighlightSpans(Highlight.COLOR_ORANGE))
        `verify highlight spans`(Highlight.COLOR_PURPLE, Color.BLACK, createHighlightSpans(Highlight.COLOR_PURPLE))
        `verify highlight spans`(Highlight.COLOR_RED, Color.WHITE, createHighlightSpans(Highlight.COLOR_RED))
        `verify highlight spans`(Highlight.COLOR_GREEN, Color.BLACK, createHighlightSpans(Highlight.COLOR_GREEN))
        `verify highlight spans`(Highlight.COLOR_BLUE, Color.WHITE, createHighlightSpans(Highlight.COLOR_BLUE))
    }

    private fun verify_highlight_spans(expectedBackgroundColor: Int, expectedForegroundColor: Int, actualStyles: Array<CharacterStyle>) {
        assertEquals(2, actualStyles.size)
        assertEquals(expectedBackgroundColor, (actualStyles[0] as BackgroundColorSpan).backgroundColor)
        assertEquals(expectedForegroundColor, (actualStyles[1] as ForegroundColorSpan).foregroundColor)
    }
}
