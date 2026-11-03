/*
 * Copyright (C) 2023 Xizhi Zhu
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

package org.ebible.android.bible.reading.detail

import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.core.view.isVisible
import org.ebible.android.bible.R
import org.ebible.android.bible.core.Settings
import org.ebible.android.bible.core.Verse
import org.ebible.android.bible.core.VerseIndex
import org.ebible.android.bible.databinding.ItemVerseTextBinding
import org.ebible.android.bible.ui.*
import org.ebible.android.bible.ui.recyclerview.BaseItem
import org.ebible.android.bible.ui.recyclerview.BaseViewHolder
import org.ebible.android.bible.ui.recyclerview.CommonRecyclerView

class VerseTextItem(
        val verseIndex: VerseIndex, followingEmptyVerseCount: Int, val verseText: Verse.Text, private val bookName: String,
        override val isReorderable: Boolean = false
) : BaseItem(R.layout.item_verse_text, { inflater, parent -> VerseTextItemViewHolder(inflater, parent) }) {
    companion object {
        private val BOOK_NAME_SPANS = createTitleSpans()
        private val SPANNABLE_STRING_BUILDER = SpannableStringBuilder()
    }

    interface Callback {
        fun onVerseTextClicked(translation: String)

        fun onVerseTextLongClicked(verse: Verse)
    }

    val textForDisplay: CharSequence by lazy {
        if (!verseIndex.isValid() || !verseText.isValid()) {
            return@lazy ""
        }

        // format:
        // <translation short name>, <book name> <chapter verseIndex>:<verse verseIndex>
        // <verse text>
        SPANNABLE_STRING_BUILDER.clearAll()
                .append(verseText.translationShortName).append(", ")
                .append(bookName).append(' ')
                .append(verseIndex.chapterIndex + 1).append(':').append(verseIndex.verseIndex + 1)
        if (followingEmptyVerseCount > 0) {
            SPANNABLE_STRING_BUILDER.append('-').append(verseIndex.verseIndex + followingEmptyVerseCount + 1)
        }
        return@lazy SPANNABLE_STRING_BUILDER.setSpans(BOOK_NAME_SPANS)
                .append('\n').append(verseText.text)
                .toCharSequence()
    }
}

private class VerseTextItemViewHolder(inflater: LayoutInflater, parent: ViewGroup)
    : BaseViewHolder<VerseTextItem, ItemVerseTextBinding>(ItemVerseTextBinding.inflate(inflater, parent, false)) {
    init {
        itemView.setOnClickListener { item?.let { callback().onVerseTextClicked(it.verseText.translationShortName) } }
        itemView.setOnLongClickListener {
            return@setOnLongClickListener item?.let { item ->
                callback().onVerseTextLongClicked(Verse(item.verseIndex, item.verseText, emptyList()))
                true
            } ?: false
        }
        viewBinding.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked != MotionEvent.ACTION_DOWN) return@setOnTouchListener false
            (itemView.parent as? CommonRecyclerView)?.startDrag(this)
            true
        }
    }

    private fun callback(): VerseTextItem.Callback = (itemView.activity as? VerseTextItem.Callback)
            ?: throw IllegalStateException("Attached activity [${itemView.activity.javaClass.name}] does not implement VerseTextItem.Callback")

    override fun bind(settings: Settings, item: VerseTextItem, payloads: List<Any>) {
        with(viewBinding.text) {
            setPrimaryTextSize(settings)
            text = item.textForDisplay
        }
        viewBinding.dragHandle.isVisible = item.isReorderable
    }
}
