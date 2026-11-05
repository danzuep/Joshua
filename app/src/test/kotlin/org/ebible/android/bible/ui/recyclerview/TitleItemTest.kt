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

package org.ebible.android.bible.ui.recyclerview

import org.ebible.android.bible.R
import org.ebible.android.bible.tests.BaseUnitTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TitleItemTest : BaseUnitTest() {
    @Test
    fun testItemViewType() {
        assertEquals(R.layout.item_title, TitleItem("", false).viewType)
    }
}
