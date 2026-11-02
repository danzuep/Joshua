/*
 * Copyright (C) 2026 eBible
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

/**
 * Entry point of the fluent UI test builders. Scenarios read as a list of steps:
 *
 * ```
 * val app = Joshua(AdbUiDriver())
 * app.openBook(Joshua.JOHN).pause(10).expectText("In the beginning was the Word")
 * app.search("jhn1").submit().expectResults("In the beginning was the Word").clearQuery().back(2)
 * ```
 *
 * Failed expectations are collected in [failures] instead of thrown, so one run reports every problem.
 * Steps that tap by label use the strings in `app/src/main/res/values/strings.xml`.
 */
class Joshua(val driver: UiDriver) {
    companion object {
        const val PACKAGE = "me.xizzhu.android.joshua"
        const val LAUNCHER = "$PACKAGE/.LauncherActivity"
        const val SEARCH_ACTIVITY = "$PACKAGE/.search.SearchActivity"
        const val SETTINGS_ACTIVITY = "$PACKAGE/.settings.SettingsActivity"
        const val TRANSLATIONS_ACTIVITY = "$PACKAGE/.translations.TranslationsActivity"
        const val KEY_OPEN_BOOK = "$PACKAGE.KEY_OPEN_BOOK"

        const val GENESIS = 0
        const val JOHN = 42
    }

    private val _failures = mutableListOf<String>()
    val failures: List<String> get() = _failures

    private var lastDump = UiHierarchy("")

    fun pause(seconds: Int): Joshua {
        driver.pause(seconds * 1000L)
        return this
    }

    fun fail(message: String): Joshua {
        println("FAIL: $message")
        _failures += message
        return this
    }

    /** Refreshes and returns the UI dump. */
    fun dump(): UiHierarchy = driver.dump().also { lastDump = it }

    fun tap(text: String? = null, description: String? = null, waitSeconds: Int = 2): Joshua {
        val node = dump().find(text, description)
        if (node == null) fail("nothing to tap for text=$text description=$description") else driver.tap(node.centerX, node.centerY)
        return pause(waitSeconds)
    }

    fun longPress(text: String, waitSeconds: Int = 2): Joshua {
        val node = dump().find(text)
        if (node == null) fail("$text is not on screen") else driver.longPress(node.centerX, node.centerY)
        return pause(waitSeconds)
    }

    fun typeText(text: String): Joshua {
        driver.typeText(text)
        return this
    }

    fun press(key: Key, waitSeconds: Int = 1): Joshua {
        driver.press(key)
        return pause(waitSeconds)
    }

    fun back(times: Int = 1): Joshua {
        repeat(times) { press(Key.BACK, waitSeconds = 2) }
        return this
    }

    fun expectText(text: String, message: String = "'$text' was not shown (or the UI dump timed out because the UI is frozen)"): Joshua {
        if (text !in dump()) fail(message)
        return this
    }

    fun expectNoText(text: String, message: String = "'$text' is still shown"): Joshua {
        if (text in dump()) fail(message)
        return this
    }

    fun expectResumed(activity: String, message: String = "$activity is not resumed"): Joshua {
        if (!driver.resumedActivity().contains(activity)) fail(message)
        return this
    }

    fun expectNotResumed(activity: String, message: String = "$activity is still resumed"): Joshua {
        if (driver.resumedActivity().contains(activity)) fail(message)
        return this
    }

    /** Cold-starts the reader straight into a 0-based book index. */
    fun openBook(bookIndex: Int): Joshua {
        driver.launch(LAUNCHER, mapOf(KEY_OPEN_BOOK to bookIndex))
        return this
    }

    fun navigate(): Navigation = Navigation(this)

    fun translations(): Translations = Translations(this).also { it.open() }

    fun verse(text: String): VerseActions = VerseActions(this, text)

    fun search(query: String? = null): Search = Search(this).also { if (query != null) it.type(query) }

    fun settings(): Settings = Settings(this).also { it.open() }
}

/** Generic steps shared by all builders, so a chain never has to go back to [Joshua]. */
abstract class Steps<S : Steps<S>>(protected val app: Joshua) {
    @Suppress("UNCHECKED_CAST")
    protected val self: S get() = this as S

    fun pause(seconds: Int): S {
        app.pause(seconds)
        return self
    }

    fun expectText(text: String, message: String? = null): S {
        if (message == null) app.expectText(text) else app.expectText(text, message)
        return self
    }

    fun expectNoText(text: String, message: String? = null): S {
        if (message == null) app.expectNoText(text) else app.expectNoText(text, message)
        return self
    }

    fun back(times: Int = 1): S {
        app.back(times)
        return self
    }

    /** Returns to the app to start another builder. */
    fun done(): Joshua = app
}

/** Moves the reader to another translation / book / chapter / verse. */
class Navigation internal constructor(app: Joshua) : Steps<Navigation>(app) {
    fun toTranslation(name: String): Navigation {
        app.tap(description = "Translation").tap(text = name)
        return this
    }

    fun toBook(name: String): Navigation {
        app.tap(description = "Book").tap(text = name)
        return this
    }

    fun toBookIndex(bookIndex: Int): Navigation {
        app.openBook(bookIndex)
        return this
    }

    fun toChapter(number: Int): Navigation {
        app.tap(text = number.toString(), waitSeconds = 3)
        return this
    }

    /** Selects the paragraph that contains the given verse text. */
    fun toVerse(text: String): Navigation {
        app.tap(text = text)
        return this
    }
}

/** The translation management screen: filter the list and download a translation. */
class Translations internal constructor(app: Joshua) : Steps<Translations>(app) {
    internal fun open() {
        app.driver.launch(Joshua.TRANSLATIONS_ACTIVITY)
        app.pause(3)
    }

    fun filter(query: String): Translations {
        app.tap(description = "Filter").typeText(query).press(Key.ENTER, waitSeconds = 2)
        return this
    }

    fun download(name: String, timeoutSeconds: Int = 120): Translations {
        app.tap(text = name).tap(text = "Download")
        var waited = 0
        while (waited < timeoutSeconds) {
            val dump = app.dump()
            if ("Successfully downloaded" in dump || "Delete" in dump) return this
            app.pause(3)
            waited += 3
        }
        app.fail("$name was not downloaded within ${timeoutSeconds}s")
        return this
    }
}

/** Long-presses a verse (found by its text) and applies one action from the verse toolbar. */
class VerseActions internal constructor(app: Joshua, private val text: String) : Steps<VerseActions>(app) {
    private fun act(label: String): VerseActions {
        app.longPress(text).tap(description = label)
        return this
    }

    fun copy(): VerseActions = act("Copy").expectText("Copied to clipboard.")

    fun share(): VerseActions = act("Share")

    fun bookmark(): VerseActions = act("Bookmark")

    fun highlight(color: String = "Yellow"): VerseActions {
        act("Highlight")
        app.tap(text = color)
        return this
    }

    fun upsertNote(note: String): VerseActions {
        app.longPress(text).tap(description = "Notes").tap(text = "Notes").typeText(note).pause(3).back()
        return this
    }
}

/** The search screen. Typing waits for the instant-search pause; [submit] presses enter. */
class Search internal constructor(app: Joshua) : Steps<Search>(app) {
    init {
        app.driver.launch(Joshua.SEARCH_ACTIVITY)
        app.pause(3)
    }

    fun type(query: String): Search {
        app.typeText(query).pause(5)
        return this
    }

    fun submit(): Search {
        app.press(Key.ENTER).pause(8)
        return this
    }

    fun expectResults(text: String): Search = expectText(text)

    /** Taps the clear button, which fails if the main thread is stuck. */
    fun clearQuery(): Search {
        app.tap(description = "Clear query", waitSeconds = 3)
        return this
    }
}

class Settings internal constructor(app: Joshua) : Steps<Settings>(app) {
    internal fun open() {
        app.driver.launch(Joshua.SETTINGS_ACTIVITY)
        app.pause(3)
    }

    fun toggle(title: String): Settings {
        app.tap(text = title)
        return this
    }

    fun nightMode(option: String = "On"): Settings {
        app.tap(text = "Night mode").tap(text = option)
        return this
    }

    fun keepScreenOn(): Settings = toggle("Keep screen on")

    fun hideSearchButton(): Settings = toggle("Hide search button")

    fun consolidateSharing(): Settings = toggle("Consolidate verses when sharing")
}
