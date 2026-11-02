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

package me.xizzhu.android.joshua.reading.detail

import android.content.Context
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import me.xizzhu.android.joshua.R
import me.xizzhu.android.joshua.core.Settings
import me.xizzhu.android.joshua.core.VerseIndex
import me.xizzhu.android.joshua.databinding.PageVerseDetailNoteBinding
import me.xizzhu.android.joshua.databinding.PageVerseDetailNotesBinding
import me.xizzhu.android.joshua.databinding.PageVerseDetailVersesBinding
import me.xizzhu.android.joshua.reading.VerseDetailViewData
import me.xizzhu.android.joshua.ui.setPrimaryTextSize

class VerseDetailPagerAdapter(context: Context) : RecyclerView.Adapter<VerseDetailPage<*>>() {
    companion object {
        const val PAGE_NOTE = 0
        const val PAGE_VERSES = 1
        const val PAGE_PARATEXT = 2
        private const val NOTE_SAVE_DELAY_MILLIS = 1000L
    }

    private val resources: Resources = context.resources
    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private val handler = Handler(Looper.getMainLooper())

    private var updateNote: ((VerseIndex, String) -> Unit)? = null
    private var pendingNote: Pair<VerseIndex, String>? = null
    private val saveNoteRunnable = Runnable { flushNote() }

    var settings: Settings? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    var verseDetail: VerseDetailViewData? = null
        set(value) {
            flushNote()
            field = value
            notifyDataSetChanged()
        }

    fun initialize(updateNote: (VerseIndex, String) -> Unit) {
        this.updateNote = updateNote
    }

    override fun getItemCount(): Int {
        val fullyInitialized = settings != null
                && verseDetail != null
                && updateNote != null
        return if (fullyInitialized) visiblePages().size else 0
    }

    override fun getItemViewType(position: Int): Int = visiblePages()[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerseDetailPage<*> =
            when (viewType) {
                PAGE_VERSES -> VersesPage(inflater, parent)
                PAGE_NOTE -> NotePage(inflater, parent, this::scheduleNoteSave)
                PAGE_PARATEXT -> ParatextPage(inflater, parent)
                else -> throw IllegalArgumentException("Unsupported view type: $viewType")
            }

    override fun onBindViewHolder(holder: VerseDetailPage<*>, position: Int) {
        holder.bind(verseDetail!!, settings!!)
    }

    override fun onViewAttachedToWindow(holder: VerseDetailPage<*>) {
        // Workaround for https://issuetracker.google.com/issues/37095917
        if (holder is NotePage) {
            holder.viewBinding.note.isEnabled = false
            holder.viewBinding.note.isEnabled = true
        }
    }

    private fun visiblePages(): List<Int> = verseDetail?.let { visibleVersePages(it) } ?: emptyList()

    fun pageTitle(position: Int): CharSequence = when (visiblePages()[position]) {
        PAGE_NOTE -> resources.getString(R.string.text_notes)
        PAGE_VERSES -> resources.getString(R.string.text_parallel)
        PAGE_PARATEXT -> resources.getString(R.string.text_paratext)
        else -> throw IllegalArgumentException("Unsupported position: $position")
    }

    fun pageIndexOf(page: Int): Int = visiblePages().indexOf(page)

    fun defaultPageIndex(): Int = verseDetail?.let { pageIndexOf(defaultVersePage(it)) }?.coerceAtLeast(0) ?: 0

    fun markNoteSaved(note: String) {
        verseDetail?.note = note
    }

    private fun scheduleNoteSave(verseIndex: VerseIndex, note: String) {
        pendingNote = verseIndex to note
        handler.removeCallbacks(saveNoteRunnable)
        handler.postDelayed(saveNoteRunnable, NOTE_SAVE_DELAY_MILLIS)
    }

    fun flushNote() {
        handler.removeCallbacks(saveNoteRunnable)
        val (verseIndex, note) = pendingNote ?: return
        pendingNote = null
        updateNote?.invoke(verseIndex, note)
    }
}

internal fun visibleVersePages(verseDetail: VerseDetailViewData): List<Int> = listOfNotNull(
        VerseDetailPagerAdapter.PAGE_NOTE,
        VerseDetailPagerAdapter.PAGE_VERSES.takeIf { verseDetail.hasParallel },
        VerseDetailPagerAdapter.PAGE_PARATEXT.takeIf { verseDetail.hasParatext }
)

internal fun defaultVersePage(verseDetail: VerseDetailViewData): Int =
        if (verseDetail.hasParatext && verseDetail.note.isBlank()) VerseDetailPagerAdapter.PAGE_PARATEXT
        else VerseDetailPagerAdapter.PAGE_NOTE

abstract class VerseDetailPage<VB : ViewBinding>(val viewBinding: VB) : RecyclerView.ViewHolder(viewBinding.root) {
    abstract fun bind(verseDetail: VerseDetailViewData, settings: Settings)
}

private class VersesPage(inflater: LayoutInflater, container: ViewGroup)
    : VerseDetailPage<PageVerseDetailVersesBinding>(PageVerseDetailVersesBinding.inflate(inflater, container, false)) {
    init {
        viewBinding.verseTextList.isNestedScrollingEnabled = false
    }

    override fun bind(verseDetail: VerseDetailViewData, settings: Settings) {
        with(viewBinding.verseTextList) {
            setItems(verseDetail.verseTextItems)
            setSettings(settings)
            scrollToPosition(0)
        }
    }
}

private class ParatextPage(inflater: LayoutInflater, container: ViewGroup)
    : VerseDetailPage<PageVerseDetailNotesBinding>(PageVerseDetailNotesBinding.inflate(inflater, container, false)) {
    override fun bind(verseDetail: VerseDetailViewData, settings: Settings) {
        bindSection(viewBinding.headingsTitle, viewBinding.headingsScroll, viewBinding.headings,
                verseDetail.headings, settings)
        bindSection(viewBinding.footnotesTitle, viewBinding.footnotesScroll, viewBinding.footnotes,
                verseDetail.footnotes, settings)
        bindSection(viewBinding.crossReferencesTitle, viewBinding.crossReferencesScroll, viewBinding.crossReferences,
                verseDetail.crossReferences, settings)
    }

    private fun bindSection(title: TextView, scroll: View, content: TextView, items: List<String>, settings: Settings) {
        val visible = items.isNotEmpty()
        title.visibility = if (visible) View.VISIBLE else View.GONE
        scroll.visibility = if (visible) View.VISIBLE else View.GONE
        content.text = items.joinToString("\n\n")
        content.setPrimaryTextSize(settings)
        title.setPrimaryTextSize(settings)
    }
}

private const val NOTE_SAVED_INDICATOR_DELAY_MILLIS = 500L

private class NotePage(inflater: LayoutInflater, container: ViewGroup, scheduleNoteSave: (VerseIndex, String) -> Unit)
    : VerseDetailPage<PageVerseDetailNoteBinding>(PageVerseDetailNoteBinding.inflate(inflater, container, false)) {
    private val handler = Handler(Looper.getMainLooper())
    private val showSavedIndicator = Runnable {
        viewBinding.noteInputLayout.hint = viewBinding.root.context.getString(R.string.text_notes)
        viewBinding.noteInputLayout.isEndIconVisible = true
    }
    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable) {
            viewBinding.noteInputLayout.hint = viewBinding.root.context.getString(R.string.text_notes_typing)
            viewBinding.noteInputLayout.isEndIconVisible = false
            handler.removeCallbacks(showSavedIndicator)
            handler.postDelayed(showSavedIndicator, NOTE_SAVED_INDICATOR_DELAY_MILLIS)
            verseDetail?.let { scheduleNoteSave(it.verseIndex, s.toString()) }
        }
    }

    private var verseDetail: VerseDetailViewData? = null

    init {
        viewBinding.note.addTextChangedListener(textWatcher)
        viewBinding.noteInputLayout.isEndIconVisible = false
    }

    override fun bind(verseDetail: VerseDetailViewData, settings: Settings) {
        this.verseDetail = verseDetail
        handler.removeCallbacks(showSavedIndicator)
        viewBinding.noteInputLayout.hint = viewBinding.root.context.getString(R.string.text_notes)
        viewBinding.noteInputLayout.isEndIconVisible = false
        with(viewBinding.note) {
            removeTextChangedListener(textWatcher)
            setText(verseDetail.note)
            addTextChangedListener(textWatcher)

            setPrimaryTextSize(settings)
        }
    }
}
