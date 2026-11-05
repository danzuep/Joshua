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

package org.ebible.android.bible.reading.verse

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.style.ReplacementSpan
import kotlin.math.roundToInt
import android.graphics.ColorFilter
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
import org.ebible.android.bible.R
import org.ebible.android.bible.core.Settings
import org.ebible.android.bible.core.Verse
import org.ebible.android.bible.core.VerseIndex
import org.ebible.android.bible.databinding.ItemParagraphBinding
import org.ebible.android.bible.reading.VerseUpdate
import org.ebible.android.bible.ui.activity
import org.ebible.android.bible.ui.createDrawableSpan
import org.ebible.android.bible.ui.createHighlightSpans
import org.ebible.android.bible.ui.recyclerview.BaseItem
import org.ebible.android.bible.ui.recyclerview.BaseViewHolder
import org.ebible.android.bible.ui.setPrimaryTextSize
import org.ebible.android.bible.ui.setSpans

/** A paragraph of a chapter, with an optional section heading, that flows like the printed text. */
class ParagraphItem(
        private val context: Context, val heading: String?, val segments: List<Segment>,
        private val bookmarks: MutableSet<Int>, private val highlights: MutableMap<Int, Int>, private val notes: MutableSet<Int>,
        private val selected: MutableSet<Int> = mutableSetOf(), private val footnotes: Set<Int> = emptySet()
) : BaseItem(R.layout.item_paragraph, { inflater, parent -> ParagraphViewHolder(inflater, parent) }) {
    /**
     * The part of [verse] that is in this paragraph; [label] (e.g. "2" or "3-4") is only set where the verse starts.
     * [chapterNumber] is set on the start of verse 1, which shows a bold "¶<chapter>" before the verse label.
     * It is display only, so it is not part of the verse text that is searched or copied.
     */
    class Segment(val verse: Verse, val text: String, val label: String?, val chapterNumber: Int? = null) {
        /** Whether this is the last part of [verse], where its note and footnote icons go. */
        var endsVerse = true
    }

    companion object {
        const val CHAPTER_MARK = '¶'
        const val LEFT_TO_RIGHT_ISOLATE = '\u2066'
        const val POP_DIRECTIONAL_ISOLATE = '\u2069'

        /** Whether the first letter of [text] is from a right to left script. */
        fun isRightToLeft(text: CharSequence): Boolean {
            for (c in text) {
                when (Character.getDirectionality(c)) {
                    Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
                    Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
                }
            }
            return false
        }

        private const val NO_BREAK_SPACE = '\u00A0'
        private val ICON_COLOR_FILTER = PorterDuffColorFilter(Color.RED, PorterDuff.Mode.MULTIPLY)
        private val FOOTNOTE_COLOR_FILTER = PorterDuffColorFilter(Color.GRAY, PorterDuff.Mode.MULTIPLY)
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
            segment.chapterNumber?.let { chapter ->
                val chapterStart = builder.length
                builder.append(CHAPTER_MARK)
                // Isolated, so in right to left text the chapter stays next to the mark instead of merging with the verse number.
                val rightToLeft = isRightToLeft(segment.text)
                if (rightToLeft) builder.append(LEFT_TO_RIGHT_ISOLATE)
                builder.append(chapter.toString())
                if (rightToLeft) builder.append(POP_DIRECTIONAL_ISOLATE)
                builder.setSpan(StyleSpan(Typeface.BOLD), chapterStart, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                // Non-breaking, so the chapter mark stays with the verse number and text that follow.
                builder.append(NO_BREAK_SPACE)
            }
            segment.label?.let { label ->
                val labelStart = builder.length
                builder.append(label)
                val bookmark = if (bookmarks.contains(verseIndex)) ContextCompat.getDrawable(context, R.drawable.ic_bookmark) else null
                if (bookmark != null) {
                    bookmark.mutate().colorFilter = ICON_COLOR_FILTER
                    builder.setSpan(LabelWithIconSpan(label, bookmark), labelStart, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else {
                    builder.setSpan(SuperscriptSpan(), labelStart, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.setSpan(RelativeSizeSpan(0.7F), labelStart, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.setSpan(StyleSpan(Typeface.BOLD), labelStart, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                builder.append(NO_BREAK_SPACE)
            }

            val textStart = builder.length
            builder.append(segment.text)
            highlights[verseIndex]?.let { builder.setSpans(createHighlightSpans(it), textStart, builder.length) }

            if (segment.endsVerse) {
                if (notes.contains(verseIndex)) builder.appendDrawable(R.drawable.ic_note)
                if (footnotes.contains(verseIndex)) builder.appendDrawable(R.drawable.ic_note, FOOTNOTE_COLOR_FILTER)
            }

            if (selected.contains(verseIndex)) {
                builder.setSpan(BackgroundColorSpan(selectedColor), start, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            newRanges.add(Range(start, builder.length, segment.verse))
        }

        ranges = newRanges
        return builder
    }

    private fun SpannableStringBuilder.appendDrawable(
            @DrawableRes drawableId: Int, colorFilter: ColorFilter = ICON_COLOR_FILTER
    ) {
        createDrawableSpan(context, drawableId, DynamicDrawableSpan.ALIGN_BASELINE, colorFilter, 0.67F)?.let { span ->
            // Non-breaking, so the icon can't land on a wrapped line's hanging trailing whitespace, where it is not drawn.
            append(NO_BREAK_SPACE)
            append(NO_BREAK_SPACE)
            setSpan(span, length - 1, length, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
        }
    }
}

/**
 * Draws a verse label as superscript with a small [icon] stacked under it, resting on the text baseline.
 * Both stay within the line's ascent, so the line height and the body text alignment are unchanged.
 */
internal class LabelWithIconSpan(private val label: String, private val icon: Drawable) : ReplacementSpan() {
    private fun labelPaint(paint: Paint) = Paint(paint).apply {
        textSize = paint.textSize * LABEL_SCALE
        typeface = Typeface.create(paint.typeface, Typeface.BOLD)
    }

    private fun iconSize(paint: Paint) = (paint.textSize * ICON_SCALE).roundToInt()

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int =
            maxOf(labelPaint(paint).measureText(label).roundToInt(), iconSize(paint))

    override fun draw(
            canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint
    ) {
        val width = getSize(paint, text, start, end, null)
        val labelPaint = labelPaint(paint)
        val size = iconSize(paint)
        canvas.drawText(label, x + (width - labelPaint.measureText(label)) / 2, y - paint.textSize * LABEL_BASELINE, labelPaint)
        val left = (x + (width - size) / 2).roundToInt()
        icon.setBounds(left, y - size, left + size, y)
        icon.draw(canvas)
    }

    private companion object {
        const val LABEL_SCALE = 0.6F
        const val ICON_SCALE = 0.4F
        const val LABEL_BASELINE = 0.5F
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
