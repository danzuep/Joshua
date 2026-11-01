/*
 * Copyright (C) 2023 Xizhi Zhu
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

package me.xizzhu.android.joshua.reading.verse

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.DynamicDrawableSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.SuperscriptSpan
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import me.xizzhu.android.joshua.R
import me.xizzhu.android.joshua.core.Settings
import me.xizzhu.android.joshua.core.Verse
import me.xizzhu.android.joshua.core.VerseIndex
import me.xizzhu.android.joshua.databinding.ItemParagraphBinding
import me.xizzhu.android.joshua.reading.VerseUpdate
import me.xizzhu.android.joshua.ui.activity
import me.xizzhu.android.joshua.ui.createDrawableSpan
import me.xizzhu.android.joshua.ui.createHighlightSpans
import me.xizzhu.android.joshua.ui.recyclerview.BaseItem
import me.xizzhu.android.joshua.ui.recyclerview.BaseViewHolder
import me.xizzhu.android.joshua.ui.setPrimaryTextSize
import me.xizzhu.android.joshua.ui.setSpans

/** A paragraph of a chapter, with an optional section heading, that flows like the printed text. */
class ParagraphItem(
        private val context: Context, val heading: String?, val segments: List<Segment>,
        private val bookmarks: MutableSet<Int>, private val highlights: MutableMap<Int, Int>, private val notes: MutableSet<Int>,
        private val selected: MutableSet<Int> = mutableSetOf()
) : BaseItem(R.layout.item_paragraph, { inflater, parent -> ParagraphViewHolder(inflater, parent) }) {
    /** The part of [verse] that is in this paragraph; [label] (e.g. "2" or "3-4") is only set where the verse starts. */
    class Segment(val verse: Verse, val text: String, val label: String?)

    companion object {
        private val ICON_COLOR_FILTER = PorterDuffColorFilter(Color.RED, PorterDuff.Mode.MULTIPLY)
    }

    interface Callback {
        fun onVerseClicked(verse: Verse)

        fun onVerseLongClicked(verse: Verse)
    }

    private class Range(val start: Int, val end: Int, val verse: Verse)

    private var ranges = emptyList<Range>()

    val firstVerseIndex: Int get() = segments.first().verse.verseIndex.verseIndex
    val firstVerse: VerseIndex get() = segments.first().verse.verseIndex

    fun containsVerse(verseIndex: Int): Boolean = segments.any { it.verse.verseIndex.verseIndex == verseIndex }

    fun verseAt(offset: Int): Verse? = ranges.lastOrNull { offset >= it.start && offset <= it.end }?.verse

    fun update(update: VerseUpdate) {
        val verseIndex = update.verseIndex.verseIndex
        when (update.operation) {
            VerseUpdate.VERSE_SELECTED -> selected.add(verseIndex)
            VerseUpdate.VERSE_DESELECTED -> selected.remove(verseIndex)
            VerseUpdate.NOTE_ADDED -> notes.add(verseIndex)
            VerseUpdate.NOTE_REMOVED -> notes.remove(verseIndex)
            VerseUpdate.BOOKMARK_ADDED -> bookmarks.add(verseIndex)
            VerseUpdate.BOOKMARK_REMOVED -> bookmarks.remove(verseIndex)
            VerseUpdate.HIGHLIGHT_UPDATED -> highlights[verseIndex] = update.data as Int
        }
    }

    fun textForDisplay(): CharSequence {
        val builder = SpannableStringBuilder()
        val newRanges = ArrayList<Range>(segments.size)

        heading?.let {
            builder.append(it).append('\n')
            builder.setSpan(StyleSpan(Typeface.BOLD), 0, it.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(RelativeSizeSpan(1.1F), 0, it.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val selectedColor = ContextCompat.getColor(context, R.color.verse_selected)
        segments.forEachIndexed { index, segment ->
            if (index > 0 && !segment.text.startsWith('\n')) builder.append(' ')

            val verseIndex = segment.verse.verseIndex.verseIndex
            val start = builder.length
            segment.label?.let { label ->
                builder.append(label)
                builder.setSpan(SuperscriptSpan(), start, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(RelativeSizeSpan(0.7F), start, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(StyleSpan(Typeface.BOLD), start, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.append('\u00A0')
            }

            val textStart = builder.length
            builder.append(segment.text)
            highlights[verseIndex]?.let { builder.setSpans(createHighlightSpans(it), textStart, builder.length) }

            if (segment.label != null) {
                if (bookmarks.contains(verseIndex)) builder.appendDrawable(R.drawable.ic_bookmark)
                if (notes.contains(verseIndex)) builder.appendDrawable(R.drawable.ic_note)
            }

            if (selected.contains(verseIndex)) {
                builder.setSpan(BackgroundColorSpan(selectedColor), start, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            newRanges.add(Range(start, builder.length, segment.verse))
        }

        ranges = newRanges
        return builder
    }

    private fun SpannableStringBuilder.appendDrawable(@DrawableRes drawableId: Int) {
        createDrawableSpan(context, drawableId, DynamicDrawableSpan.ALIGN_BASELINE, ICON_COLOR_FILTER, 0.67F)?.let { span ->
            append(' ')
            append(' ')
            setSpan(span, length - 1, length, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
private class ParagraphViewHolder(inflater: LayoutInflater, parent: ViewGroup)
    : BaseViewHolder<ParagraphItem, ItemParagraphBinding>(ItemParagraphBinding.inflate(inflater, parent, false)) {
    private val gestureDetector = GestureDetector(itemView.context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            verseAt(e)?.let { callback().onVerseClicked(it) }
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            verseAt(e)?.let { callback().onVerseLongClicked(it) }
        }
    })

    init {
        viewBinding.text.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
    }

    private fun verseAt(event: MotionEvent): Verse? {
        val textView = viewBinding.text
        val layout = textView.layout ?: return null
        val x = event.x - textView.totalPaddingLeft + textView.scrollX
        val y = event.y - textView.totalPaddingTop + textView.scrollY
        val line = layout.getLineForVertical(y.toInt())
        return item?.verseAt(layout.getOffsetForHorizontal(line, x))
    }

    private fun callback(): ParagraphItem.Callback = (itemView.activity as? ParagraphItem.Callback)
            ?: throw IllegalStateException("Attached activity [${itemView.activity.javaClass.name}] does not implement ParagraphItem.Callback")

    override fun bind(settings: Settings, item: ParagraphItem, payloads: List<Any>) {
        payloads.forEach { payload -> item.update(payload as VerseUpdate) }
        viewBinding.text.text = item.textForDisplay()
        viewBinding.text.setPrimaryTextSize(settings)
    }
}
