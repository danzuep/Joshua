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

package org.ebible.android.bible.preview

import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.viewbinding.ViewBinding
import java.util.concurrent.Executor
import org.ebible.android.bible.R
import org.ebible.android.bible.core.Settings
import org.ebible.android.bible.core.VerseIndex
import org.ebible.android.bible.databinding.ItemPreviewVerseBinding
import org.ebible.android.bible.databinding.ItemPreviewVerseWithQueryBinding
import org.ebible.android.bible.search.highlightKeyword
import org.ebible.android.bible.ui.append
import org.ebible.android.bible.ui.clearAll
import org.ebible.android.bible.ui.createTitleSpans
import org.ebible.android.bible.ui.recyclerview.VerticalRecyclerViewAdapter
import org.ebible.android.bible.ui.recyclerview.VerticalRecyclerViewHolder
import org.ebible.android.bible.ui.recyclerview.VerticalRecyclerViewItem
import org.ebible.android.bible.ui.setPrimaryTextSize
import org.ebible.android.bible.ui.setSpans
import org.ebible.android.bible.ui.toCharSequence
import org.ebible.android.bible.utils.localized

class PreviewAdapter(
    private val inflater: LayoutInflater,
    executor: Executor,
    private val onViewEvent: (ViewEvent) -> Unit,
) : VerticalRecyclerViewAdapter<PreviewItem, PreviewViewHolder<PreviewItem, *>>(PreviewItem.DiffCallback(), executor) {
    sealed class ViewEvent {
        data class OpenVerse(val verseToOpen: VerseIndex) : ViewEvent()
    }

    @Suppress("UNCHECKED_CAST")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewViewHolder<PreviewItem, *> = when (viewType) {
        PreviewItem.Verse.VIEW_TYPE -> PreviewViewHolder.Verse(inflater, parent, onViewEvent)
        PreviewItem.VerseWithQuery.VIEW_TYPE -> PreviewViewHolder.VerseWithQuery(inflater, parent, onViewEvent)
        else -> throw IllegalStateException("Unknown view type - $viewType")
    } as PreviewViewHolder<PreviewItem, *>
}

sealed class PreviewItem(viewType: Int) : VerticalRecyclerViewItem(viewType) {
    companion object {
        private val VERSE_INDEX_SPANS = createTitleSpans()
        private val SPANNABLE_STRING_BUILDER = SpannableStringBuilder()
    }

    class DiffCallback : DiffUtil.ItemCallback<PreviewItem>() {
        override fun areItemsTheSame(oldItem: PreviewItem, newItem: PreviewItem): Boolean = when {
            oldItem is Verse && newItem is Verse -> oldItem.verseIndex == newItem.verseIndex
            oldItem is VerseWithQuery && newItem is VerseWithQuery -> oldItem.verseIndex == newItem.verseIndex
            else -> false
        }

        override fun areContentsTheSame(oldItem: PreviewItem, newItem: PreviewItem): Boolean = oldItem == newItem
    }

    data class Verse(
        val settings: Settings,
        val verseIndex: VerseIndex,
        val verseText: String,
        private val followingEmptyVerseCount: Int
    ) : PreviewItem(VIEW_TYPE) {
        companion object {
            @JvmField val VIEW_TYPE = R.layout.item_preview_verse
        }

        val textForDisplay: CharSequence by lazy(LazyThreadSafetyMode.NONE) {
            // format:
            // <chapter verseIndex>:<verse verseIndex> <verse text>
            SPANNABLE_STRING_BUILDER.clearAll()

            SPANNABLE_STRING_BUILDER.append((verseIndex.chapterIndex + 1).localized()).append(':').append((verseIndex.verseIndex + 1).localized())
            if (followingEmptyVerseCount > 0) {
                SPANNABLE_STRING_BUILDER.append('-').append((verseIndex.verseIndex + followingEmptyVerseCount + 1).localized())
            }

            SPANNABLE_STRING_BUILDER.setSpans(VERSE_INDEX_SPANS)
                .append(' ')
                .append(verseText)

            return@lazy SPANNABLE_STRING_BUILDER.toCharSequence()
        }
    }

    data class VerseWithQuery(
        val settings: Settings,
        val verseIndex: VerseIndex,
        val verseText: String,
        val query: String,
        private val followingEmptyVerseCount: Int
    ) : PreviewItem(VIEW_TYPE) {
        companion object {
            @JvmField val VIEW_TYPE = R.layout.item_preview_verse_with_query
        }

        val textForDisplay: CharSequence by lazy(LazyThreadSafetyMode.NONE) {
            // format:
            // <chapter verseIndex>:<verse verseIndex> <verse text>
            SPANNABLE_STRING_BUILDER.clearAll()

            SPANNABLE_STRING_BUILDER.append((verseIndex.chapterIndex + 1).localized()).append(':').append((verseIndex.verseIndex + 1).localized())
            if (followingEmptyVerseCount > 0) {
                SPANNABLE_STRING_BUILDER.append('-').append((verseIndex.verseIndex + followingEmptyVerseCount + 1).localized())
            }

            SPANNABLE_STRING_BUILDER.setSpans(VERSE_INDEX_SPANS)
                .append(' ')
                .append(verseText)

            // highlights the keywords
            SPANNABLE_STRING_BUILDER.highlightKeyword(query, SPANNABLE_STRING_BUILDER.length - verseText.length)

            return@lazy SPANNABLE_STRING_BUILDER.toCharSequence()
        }
    }
}

sealed class PreviewViewHolder<Item : PreviewItem, VB : ViewBinding>(viewBinding: VB) : VerticalRecyclerViewHolder<Item, VB>(viewBinding) {
    class Verse(inflater: LayoutInflater, parent: ViewGroup, onViewEvent: (PreviewAdapter.ViewEvent) -> Unit)
        : PreviewViewHolder<PreviewItem.Verse, ItemPreviewVerseBinding>(ItemPreviewVerseBinding.inflate(inflater, parent, false)) {
        init {
            itemView.setOnClickListener {
                item?.let { item -> onViewEvent(PreviewAdapter.ViewEvent.OpenVerse(item.verseIndex)) }
            }
        }

        override fun bind(item: PreviewItem.Verse, payloads: List<Any>) = with(viewBinding.verse) {
            text = item.textForDisplay
            setPrimaryTextSize(item.settings)
        }
    }

    class VerseWithQuery(inflater: LayoutInflater, parent: ViewGroup, onViewEvent: (PreviewAdapter.ViewEvent) -> Unit)
        : PreviewViewHolder<PreviewItem.VerseWithQuery, ItemPreviewVerseWithQueryBinding>(ItemPreviewVerseWithQueryBinding.inflate(inflater, parent, false)) {
        init {
            itemView.setOnClickListener {
                item?.let { item -> onViewEvent(PreviewAdapter.ViewEvent.OpenVerse(item.verseIndex)) }
            }
        }

        override fun bind(item: PreviewItem.VerseWithQuery, payloads: List<Any>) = with(viewBinding.verse) {
            text = item.textForDisplay
            setPrimaryTextSize(item.settings)
        }
    }
}
