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

import kotlin.system.exitProcess

/**
 * UI part of the release smoke test (`./gradlew :shared:runSmokeScenario`, called by `.github/scripts/smoke-test.sh`).
 * Exits non-zero on any failure.
 */
fun main() {
    val book = System.getenv("SMOKE_BOOK_INDEX")?.toIntOrNull() ?: Joshua.JOHN
    val john11 = "In the beginning was the Word"
    val app = Joshua(AdbUiDriver())

    // Open Genesis first (importing the bundled translation takes a while), then jump to another book. A page must
    // never be left on the loading spinner after the jump.
    app.openBook(Joshua.GENESIS).pause(20)
    app.navigate().toBookIndex(book).pause(10)

    app.expectText(john11, "John 1 text was not shown")
        // The paragraph / heading markers must never leak into the UI.
        .expectNoText("\u2028", "USFM structure markers leaked into the UI")
        .expectNoText("\u2029", "USFM structure markers leaked into the UI")
        // The chapter flows in paragraphs rather than one row per verse, so verse 5 shares a view with verse 1.
        .expectText("The light shines in the darkness", "expected flowing paragraph text (John 1:1-5 not in one paragraph)")

    // Regression test: submitting "jhn1" used to relaunch SearchActivity in an endless loop, freezing the screen.
    app.search("jhn1").submit()
        .expectResults(john11)
        .clearQuery()
        .expectNoText(john11, "the clear button did not clear the search results")
        .back(2).pause(1)
    app.expectNotResumed("SearchActivity", "back did not leave the search screen")

    exitProcess(if (app.failures.isEmpty()) 0 else 1)
}
