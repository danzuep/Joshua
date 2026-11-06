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

package org.ebible.android.bible.tests

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import org.junit.Rule

abstract class BaseActivityTest : BaseUnitTest() {
    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    /**
     * [block] runs on the main thread; [afterLaunch] runs on the test thread while the activity is still resumed (Espresso can't run on the main thread).
     * The lambdas are not inlined: inlined lambdas generate classes named after the test method, and D8 rejects spaces in class names below API 30.
     */
    protected inline fun <reified A : Activity> withActivity(noinline afterLaunch: () -> Unit = {}, noinline block: (activity: A) -> Unit) =
        launchActivity(A::class.java, afterLaunch, block)

    protected fun <A : Activity> launchActivity(activityClass: Class<A>, afterLaunch: () -> Unit, block: (activity: A) -> Unit) {
        val startActivityIntent = Intent.makeMainActivity(ComponentName(ApplicationProvider.getApplicationContext(), activityClass))
        ActivityScenario.launch<A>(startActivityIntent).use {
            it.onActivity { activity -> block(activity) }
            afterLaunch()
        }
    }
}
