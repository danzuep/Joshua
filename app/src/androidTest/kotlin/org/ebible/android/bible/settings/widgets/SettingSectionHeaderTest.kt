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

package org.ebible.android.bible.settings.widgets

import android.content.Context
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.ebible.android.bible.R
import org.ebible.android.bible.tests.BaseUnitTest
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.view.LayoutInflater

@RunWith(AndroidJUnit4::class)
class SettingSectionHeaderTest : BaseUnitTest() {
    private lateinit var context: Context

    @BeforeTest
    override fun setup() {
        super.setup()

        context = ApplicationProvider.getApplicationContext<Context>().apply { setTheme(R.style.AppTheme) }
    }

    @Test
    fun test_constructor_without_attrs() {
        val header = SettingSectionHeader(context)
        assertTrue(header.findViewById<TextView>(R.id.title).text.isEmpty())
    }

    @Test
    fun test_constructor_with_title() {
        val header = LayoutInflater.from(context).inflate(R.layout.test_setting_widgets, null)
            .findViewById<SettingSectionHeader>(R.id.test_section_header)
        assertEquals("title", header.findViewById<TextView>(R.id.title).text.toString())
    }
}
