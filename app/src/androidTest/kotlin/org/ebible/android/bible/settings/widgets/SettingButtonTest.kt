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
import android.view.View

@RunWith(AndroidJUnit4::class)
class SettingButtonTest : BaseUnitTest() {
    private lateinit var context: Context

    @BeforeTest
    override fun setup() {
        super.setup()

        context = ApplicationProvider.getApplicationContext<Context>().apply { setTheme(R.style.AppTheme) }
    }

    private fun inflateButton(id: Int): SettingButton =
        LayoutInflater.from(context).inflate(R.layout.test_setting_widgets, null).findViewById<View>(id) as SettingButton

    @Test
    fun test_constructor_without_attrs() {
        val button = SettingButton(context)
        assertTrue(button.findViewById<TextView>(R.id.title).text.isEmpty())
        assertTrue(button.findViewById<TextView>(R.id.description).text.isEmpty())
    }

    @Test
    fun test_constructor_with_only_title() {
        val button = inflateButton(R.id.test_button_title_only)
        assertEquals("title", button.findViewById<TextView>(R.id.title).text.toString())
        assertTrue(button.findViewById<TextView>(R.id.description).text.isEmpty())
    }

    @Test
    fun test_constructor_with_only_description() {
        val button = inflateButton(R.id.test_button_description_only)
        assertTrue(button.findViewById<TextView>(R.id.title).text.isEmpty())
        assertEquals("description", button.findViewById<TextView>(R.id.description).text.toString())
    }

    @Test
    fun test_constructor_with_title_and_description_then_override() {
        val button = inflateButton(R.id.test_button_title_and_description)
        assertEquals("title", button.findViewById<TextView>(R.id.title).text.toString())
        assertEquals("description", button.findViewById<TextView>(R.id.description).text.toString())

        button.setDescription("updated desc")
        assertEquals("title", button.findViewById<TextView>(R.id.title).text.toString())
        assertEquals("updated desc", button.findViewById<TextView>(R.id.description).text.toString())

        button.setDescription(R.string.title_settings)
        assertEquals("title", button.findViewById<TextView>(R.id.title).text.toString())
        assertEquals(context.getString(R.string.title_settings), button.findViewById<TextView>(R.id.description).text.toString())
    }
}
