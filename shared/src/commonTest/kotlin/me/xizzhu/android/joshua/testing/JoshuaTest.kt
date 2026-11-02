/*
 * Copyright (C) 2026 Xizhi Zhu
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

package me.xizzhu.android.joshua.testing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JoshuaTest {
    private class FakeDriver(var xml: String = "") : UiDriver {
        val events = mutableListOf<String>()
        var resumed = ""

        override fun launch(component: String, intExtras: Map<String, Int>) {
            events += "launch $component $intExtras"
        }

        override fun dump(): UiHierarchy = UiHierarchy(xml)
        override fun tap(x: Int, y: Int) { events += "tap $x,$y" }
        override fun longPress(x: Int, y: Int) { events += "longPress $x,$y" }
        override fun typeText(text: String) { events += "type $text" }
        override fun press(key: Key) { events += "press $key" }
        override fun resumedActivity(): String = resumed
        override fun pause(millis: Long) {}
    }

    private fun node(text: String = "", desc: String = "", bounds: String = "[0,0][10,20]") =
        """<node text="$text" content-desc="$desc" bounds="$bounds" />"""

    @Test
    fun hierarchyFindsNodesByLabel() {
        val hierarchy = UiHierarchy("<hierarchy>${node(text = "Tom &amp; Jerry", bounds = "[0,0][10,20]")}${node(desc = "Clear query", bounds = "[10,10][30,50]")}</hierarchy>")
        assertEquals(5 to 10, hierarchy.find(text = "tom & jerry")?.let { it.centerX to it.centerY })
        assertEquals(20 to 30, hierarchy.find(description = "Clear query")?.let { it.centerX to it.centerY })
        assertEquals(null, hierarchy.find(text = "missing"))
    }

    @Test
    fun openBookLaunchesWithExtra() {
        val driver = FakeDriver()
        Joshua(driver).openBook(Joshua.JOHN)
        assertEquals(listOf("launch ${Joshua.LAUNCHER} {${Joshua.KEY_OPEN_BOOK}=42}"), driver.events)
    }

    @Test
    fun searchTypesSubmitsAndClears() {
        val driver = FakeDriver(node(desc = "Clear query", bounds = "[0,0][10,20]"))
        val app = Joshua(driver)
        app.search("jhn1").submit().clearQuery()
        assertEquals(
            listOf("launch ${Joshua.SEARCH_ACTIVITY} {}", "type jhn1", "press ENTER", "tap 5,10"),
            driver.events
        )
        assertTrue(app.failures.isEmpty())
    }

    @Test
    fun filterAndDownloadTranslation() {
        val driver = FakeDriver(node(desc = "Filter") + node(text = "Berean", bounds = "[0,0][20,20]") + node(text = "Download", bounds = "[0,20][20,40]") + node(text = "Successfully downloaded"))
        val app = Joshua(driver)
        app.translations().filter("Berean").download("Berean")
        assertTrue(app.failures.isEmpty(), app.failures.toString())
        assertTrue(driver.events.contains("type Berean"))
    }

    @Test
    fun failedExpectationsAreCollected() {
        val driver = FakeDriver(node(text = "hello"))
        val app = Joshua(driver)
        app.expectText("hello").expectText("world").expectNoText("hello")
        assertEquals(2, app.failures.size)
        driver.resumed = "mResumedActivity: SearchActivity"
        app.expectNotResumed("SearchActivity")
        assertEquals(3, app.failures.size)
    }

    @Test
    fun verseActionLongPressesVerseThenTapsAction() {
        val driver = FakeDriver(node(text = "In the beginning", bounds = "[0,0][10,10]") + node(desc = "Bookmark", bounds = "[20,20][40,40]"))
        val app = Joshua(driver)
        app.verse("In the beginning").bookmark()
        assertEquals(listOf("longPress 5,5", "tap 30,30"), driver.events)
        assertTrue(app.failures.isEmpty())
    }
}
