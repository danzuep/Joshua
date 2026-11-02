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

enum class Key { ENTER, BACK }

/** The minimal device operations the fluent builders need; implemented over adb on the JVM and by fakes in tests. */
interface UiDriver {
    /** Starts an activity (`package/.Class`) with optional integer extras, waiting until it is launched. */
    fun launch(component: String, intExtras: Map<String, Int> = emptyMap())

    /** Returns the current UI tree. An empty hierarchy means the dump failed, e.g. because the UI is frozen. */
    fun dump(): UiHierarchy

    fun tap(x: Int, y: Int)

    fun longPress(x: Int, y: Int)

    fun typeText(text: String)

    fun press(key: Key)

    /** The component name of the activity in the foreground, or an empty string. */
    fun resumedActivity(): String

    fun pause(millis: Long)
}

data class UiNode(val text: String, val contentDescription: String, val centerX: Int, val centerY: Int)

/** A parsed `uiautomator dump`. */
class UiHierarchy(val raw: String) {
    val nodes: List<UiNode> by lazy(LazyThreadSafetyMode.NONE) { parse(raw) }

    operator fun contains(text: String): Boolean = raw.contains(text)

    /** The first node whose text and content description contain the given labels (case-insensitive). */
    fun find(text: String? = null, description: String? = null): UiNode? = nodes.firstOrNull {
        (text == null || it.text.contains(text, ignoreCase = true))
                && (description == null || it.contentDescription.contains(description, ignoreCase = true))
    }

    private companion object {
        val NODE = Regex("<node\\b(?:\"[^\"]*\"|[^>\"])*>")
        val ATTRIBUTE = Regex("([\\w-]+)=\"([^\"]*)\"")
        val BOUNDS = Regex("\\[(\\d+),(\\d+)]\\[(\\d+),(\\d+)]")

        fun parse(raw: String): List<UiNode> = NODE.findAll(raw).mapNotNull { node ->
            val attributes = ATTRIBUTE.findAll(node.value).associate { it.groupValues[1] to it.groupValues[2] }
            val (x1, y1, x2, y2) = BOUNDS.find(attributes["bounds"] ?: return@mapNotNull null)?.destructured
                ?.toList()?.map { it.toInt() } ?: return@mapNotNull null
            UiNode(unescape(attributes["text"].orEmpty()), unescape(attributes["content-desc"].orEmpty()), (x1 + x2) / 2, (y1 + y2) / 2)
        }.toList()

        fun unescape(value: String): String = value.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&apos;", "'").replace("&#10;", "\n").replace("&amp;", "&")
    }
}
