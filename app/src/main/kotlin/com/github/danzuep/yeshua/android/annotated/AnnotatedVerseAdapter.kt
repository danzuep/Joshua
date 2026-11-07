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

package com.github.danzuep.yeshua.android.annotated

import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.viewbinding.ViewBinding
import com.github.danzuep.yeshua.android.R
import com.github.danzuep.yeshua.android.core.Constants
import com.github.danzuep.yeshua.android.core.Settings
import com.github.danzuep.yeshua.android.core.VerseIndex
import com.github.danzuep.yeshua.android.databinding.ItemAnnotatedVerseBookmarkBinding
import com.github.danzuep.yeshua.android.databinding.ItemAnnotatedVerseHighlightBinding
import com.github.danzuep.yeshua.android.databinding.ItemAnnotatedVerseNoteBinding
import com.github.danzuep.yeshua.android.databinding.ItemTitleBinding
import com.github.danzuep.yeshua.android.ui.append
import com.github.danzuep.yeshua.android.ui.clearAll
import com.github.danzuep.yeshua.android.ui.createHighlightSpans
import com.github.danzuep.yeshua.android.ui.createTitleSpans
import com.github.danzuep.yeshua.android.ui.createTitleStyleSpan
import com.github.danzuep.yeshua.android.ui.recyclerview.VerticalRecyclerViewAdapter
import com.github.danzuep.yeshua.android.ui.recyclerview.VerticalRecyclerViewHolder
import com.github.danzuep.yeshua.android.ui.recyclerview.VerticalRecyclerViewItem
import com.github.danzuep.yeshua.android.ui.setPrimaryTextSize
import com.github.danzuep.yeshua.android.ui.setSecondaryTextSize
import com.github.danzuep.yeshua.android.ui.setSpan
import com.github.danzuep.yeshua.android.ui.setSpans
import com.github.danzuep.yeshua.android.ui.toCharSequence
import java.util.concurrent.Executor

class AnnotatedVerseAdapter(
    private val inflater: LayoutInflater,
    executor: Executor,
    private val onViewEvent: (ViewEvent) -> Unit,
) : VerticalRecyclerViewAdapter<AnnotatedVerseItem, AnnotatedVerseViewHolder<AnnotatedVerseItem, *>>(AnnotatedVerseItem.DiffCallback(), executor) {
    sealed class ViewEvent {
        data class OpenVerse(val verseToOpen: VerseIndex) : ViewEvent()
        data class ShowPreview(val verseToPreview: VerseIndex) : ViewEvent()
    }

    @Suppress("UNCHECKED_CAST")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnnotatedVerseViewHolder<AnnotatedVerseItem, *> = when (viewType) {
        AnnotatedVerseItem.Header.VIEW_TYPE -> AnnotatedVerseViewHolder.Header(inflater, parent)
        AnnotatedVerseItem.Bookmark.VIEW_TYPE -> AnnotatedVerseViewHolder.Bookmark(inflater, parent, onViewEvent)
        AnnotatedVerseItem.Highlight.VIEW_TYPE -> AnnotatedVerseViewHolder.Highlight(inflater, parent, onViewEvent)
        AnnotatedVerseItem.Note.VIEW_TYPE -> AnnotatedVerseViewHolder.Note(inflater, parent, onViewEvent)
        else -> throw IllegalStateException("Unknown view type - $viewType")
    } as AnnotatedVerseViewHolder<AnnotatedVerseItem, *>
}

sealed class AnnotatedVerseItem(viewType: Int) : VerticalRecyclerViewItem(viewType) {
    companion object {
        private val BOOK_NAME_SPANS = createTitleSpans()
        private val SPANNABLE_STRING_BUILDER = SpannableStringBuilder()
    }

    class DiffCallback : DiffUtil.ItemCallback<AnnotatedVerseItem>() {
        override fun areItemsTheSame(oldItem: AnnotatedVerseItem, newItem: AnnotatedVerseItem): Boolean = when {
            oldItem is Header && newItem is Header -> oldItem.text == newItem.text
            oldItem is Bookmark && newItem is Bookmark -> oldItem.verseIndex == newItem.verseIndex
            oldItem is Highlight && newItem is Highlight -> oldItem.verseIndex == newItem.verseIndex
            oldItem is Note && newItem is Note -> oldItem.verseIndex == newItem.verseIndex
            else -> false
        }

        override fun areContentsTheSame(oldItem: AnnotatedVerseItem, newItem: AnnotatedVerseItem): Boolean = oldItem == newItem
    }

    data class Header(
        val settings: Settings,
        val text: String,
        val hideDivider: Boolean,
    ) : AnnotatedVerseItem(VIEW_TYPE) {
        companion object {
            @JvmField val VIEW_TYPE = R.layout.item_title
        }
    }

    data class Bookmark(
        val settings: Settings,
        val verseIndex: VerseIndex,
        private val bookName: String,
        private val bookShortName: String,
        private val verseText: String,
        @Constants.SortOrder private val sortOrder: Int
    ) : AnnotatedVerseItem(VIEW_TYPE) {
        companion object {
            @JvmField val VIEW_TYPE = R.layout.item_annotated_verse_bookmark
        }

        val textForDisplay: CharSequence by lazy(LazyThreadSafetyMode.NONE) {
            SPANNABLE_STRING_BUILDER.clearAll()

            if (sortOrder == Constants.SORT_BY_BOOK) {
                // format:
                // <book short name> <chapter verseIndex>:<verse verseIndex> <verse text>
                SPANNABLE_STRING_BUILDER.append(bookShortName).append(' ')
                    .append(verseIndex.chapterIndex + 1).append(':').append(verseIndex.verseIndex + 1)
                    .setSpans(BOOK_NAME_SPANS)
                    .append(' ').append(verseText)
            } else {
                // format:
                // <book name> <chapter verseIndex>:<verse verseIndex>
                // <verse text>
                SPANNABLE_STRING_BUILDER.append(bookName).append(' ')
                    .append(verseIndex.chapterIndex + 1).append(':').append(verseIndex.verseIndex + 1)
                    .setSpans(BOOK_NAME_SPANS)
                    .append('\n').append(verseText)
            }

            return@lazy SPANNABLE_STRING_BUILDER.toCharSequence()
        }
    }

    data class Highlight(
        val settings: Settings,
        val verseIndex: VerseIndex,
        private val bookName: String,
        private val bookShortName: String,
        private val verseText: String,
        @ColorInt private val highlightColor: Int,
        @Constants.SortOrder private val sortOrder: Int
    ) : AnnotatedVerseItem(VIEW_TYPE) {
        companion object {
            @JvmField val VIEW_TYPE = R.layout.item_annotated_verse_highlight
        }

        val textForDisplay: CharSequence by lazy(LazyThreadSafetyMode.NONE) {
            SPANNABLE_STRING_BUILDER.clearAll()

            if (sortOrder == Constants.SORT_BY_BOOK) {
                // format:
                // <book short name> <chapter verseIndex>:<verse verseIndex> <verse text>
                SPANNABLE_STRING_BUILDER.append(bookShortName).append(' ')
                    .append(verseIndex.chapterIndex + 1).append(':').append(verseIndex.verseIndex + 1)
                    .setSpans(BOOK_NAME_SPANS)
                    .append(' ')
            } else {
                // format:
                // <book name> <chapter verseIndex>:<verse verseIndex>
                // <verse text>
                SPANNABLE_STRING_BUILDER.append(bookName).append(' ')
                    .append(verseIndex.chapterIndex + 1).append(':').append(verseIndex.verseIndex + 1)
                    .setSpans(BOOK_NAME_SPANS)
                    .append('\n')
            }

            return@lazy SPANNABLE_STRING_BUILDER.append(verseText)
                .setSpans(createHighlightSpans(highlightColor), SPANNABLE_STRING_BUILDER.length - verseText.length, SPANNABLE_STRING_BUILDER.length)
                .toCharSequence()
        }
    }

    data class Note(
        val settings: Settings,
        val verseIndex: VerseIndex,
        private val bookShortName: String,
        private val verseText: String,
        val note: String
    ) : AnnotatedVerseItem(VIEW_TYPE) {
        companion object {
            @JvmField val VIEW_TYPE = R.layout.item_annotated_verse_note

            private val BOOK_NAME_STYLE_SPAN = createTitleStyleSpan()
        }

        val textForDisplay: CharSequence by lazy(LazyThreadSafetyMode.NONE) {
            // format:
            // <book short name> <chapter index>:<verse index> <verse text>
            return@lazy SPANNABLE_STRING_BUILDER.clearAll()
                .append(bookShortName).append(' ')
                .append(verseIndex.chapterIndex + 1).append(':').append(verseIndex.verseIndex + 1)
                .setSpan(BOOK_NAME_STYLE_SPAN)
                .append(' ').append(verseText)
                .toCharSequence()
        }
    }
}

sealed class AnnotatedVerseViewHolder<Item : AnnotatedVerseItem, VB : ViewBinding>(viewBinding: VB)
    : VerticalRecyclerViewHolder<Item, VB>(viewBinding) {
    class Header(inflater: LayoutInflater, parent: ViewGroup) : AnnotatedVerseViewHolder<AnnotatedVerseItem.Header, ItemTitleBinding>(
        ItemTitleBinding.inflate(inflater, parent, false)
    ) {
        override fun bind(item: AnnotatedVerseItem.Header, payloads: List<Any>) = with(viewBinding) {
            title.setSecondaryTextSize(item.settings)
            title.text = item.text
            divider.isVisible = !item.hideDivider
        }
    }

    class Bookmark(inflater: LayoutInflater, parent: ViewGroup, onViewEvent: (AnnotatedVerseAdapter.ViewEvent) -> Unit)
        : AnnotatedVerseViewHolder<AnnotatedVerseItem.Bookmark, ItemAnnotatedVerseBookmarkBinding>(
        ItemAnnotatedVerseBookmarkBinding.inflate(inflater, parent, false)
    ) {
        init {
            itemView.setOnClickListener {
                item?.let { onViewEvent(AnnotatedVerseAdapter.ViewEvent.OpenVerse(it.verseIndex)) }
            }
            itemView.setOnLongClickListener {
                item?.let { onViewEvent(AnnotatedVerseAdapter.ViewEvent.ShowPreview(it.verseIndex)) }
                true
            }
        }

        override fun bind(item: AnnotatedVerseItem.Bookmark, payloads: List<Any>) = with(viewBinding.text) {
            setPrimaryTextSize(item.settings)
            text = item.textForDisplay
        }
    }

    class Highlight(inflater: LayoutInflater, parent: ViewGroup, onViewEvent: (AnnotatedVerseAdapter.ViewEvent) -> Unit)
        : AnnotatedVerseViewHolder<AnnotatedVerseItem.Highlight, ItemAnnotatedVerseHighlightBinding>(
        ItemAnnotatedVerseHighlightBinding.inflate(inflater, parent, false)
    ) {
        init {
            itemView.setOnClickListener {
                item?.let { onViewEvent(AnnotatedVerseAdapter.ViewEvent.OpenVerse(it.verseIndex)) }
            }
            itemView.setOnLongClickListener {
                item?.let { onViewEvent(AnnotatedVerseAdapter.ViewEvent.ShowPreview(it.verseIndex)) }
                true
            }
        }

        override fun bind(item: AnnotatedVerseItem.Highlight, payloads: List<Any>) = with(viewBinding.text) {
            setPrimaryTextSize(item.settings)
            text = item.textForDisplay
        }
    }

    class Note(inflater: LayoutInflater, parent: ViewGroup, onViewEvent: (AnnotatedVerseAdapter.ViewEvent) -> Unit)
        : AnnotatedVerseViewHolder<AnnotatedVerseItem.Note, ItemAnnotatedVerseNoteBinding>(ItemAnnotatedVerseNoteBinding.inflate(inflater, parent, false)) {
        init {
            itemView.setOnClickListener {
                item?.let { onViewEvent(AnnotatedVerseAdapter.ViewEvent.OpenVerse(it.verseIndex)) }
            }
            itemView.setOnLongClickListener {
                item?.let { onViewEvent(AnnotatedVerseAdapter.ViewEvent.ShowPreview(it.verseIndex)) }
                true
            }
        }

        override fun bind(item: AnnotatedVerseItem.Note, payloads: List<Any>) = with(viewBinding) {
            verse.setSecondaryTextSize(item.settings)
            verse.text = item.textForDisplay

            text.setPrimaryTextSize(item.settings)
            text.text = item.note
        }
    }
}
