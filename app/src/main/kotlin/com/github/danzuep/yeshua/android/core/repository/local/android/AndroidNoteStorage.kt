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

package com.github.danzuep.yeshua.android.core.repository.local.android

import com.github.danzuep.yeshua.android.core.Note
import com.github.danzuep.yeshua.android.core.repository.local.android.db.AndroidDatabase
import com.github.danzuep.yeshua.android.core.repository.local.android.db.MetadataDao

class AndroidNoteStorage(androidDatabase: AndroidDatabase) : AndroidVerseAnnotationStorage<Note>(
        androidDatabase.noteDao, androidDatabase.metadataDao, MetadataDao.KEY_NOTES_SORT_ORDER)
