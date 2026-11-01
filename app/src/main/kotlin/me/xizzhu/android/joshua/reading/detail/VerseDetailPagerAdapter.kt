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
import android.content.res.ColorStateList
import android.content.res.Resources
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import androidx.viewbinding.ViewBinding
import me.xizzhu.android.joshua.R
import me.xizzhu.android.joshua.core.Settings
import me.xizzhu.android.joshua.core.VerseIndex
import me.xizzhu.android.joshua.databinding.PageVerseDetailNoteBinding
import me.xizzhu.android.joshua.databinding.PageVerseDetailVersesBinding
import me.xizzhu.android.joshua.reading.VerseDetailViewData
import me.xizzhu.android.joshua.ui.setPrimaryTextSize

class VerseDetailPagerAdapter(context: Context) : RecyclerView.Adapter<VerseDetailPage<*>>() {
    companion object {
        const val PAGE_VERSES = 0
        const val PAGE_NOTE = 1
        private const val PAGE_COUNT = 2
    }

    private val resources: Resources = context.resources
    private val inflater: LayoutInflater = LayoutInflater.from(context)

    private var updateNote: ((VerseIndex, String) -> Unit)? = null
    private var notePage: NotePage? = null

    var settings: Settings? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    var verseDetail: VerseDetailViewData? = null
        set(value) {
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
        return if (fullyInitialized) PAGE_COUNT else 0
    }

    override fun getItemViewType(position: Int): Int = position

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerseDetailPage<*> =
            when (viewType) {
                PAGE_VERSES -> VersesPage(inflater, parent)
                PAGE_NOTE -> NotePage(inflater, parent, updateNote!!).also { notePage = it }
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

    fun pageTitle(position: Int): CharSequence = when (position) {
        PAGE_VERSES -> resources.getString(R.string.text_verse_comparison)
        PAGE_NOTE -> resources.getString(R.string.text_note)
        else -> throw IllegalArgumentException("Unsupported position: $position")
    }

    fun markNoteSaved(note: String) {
        verseDetail?.note = note
        notePage?.markSaved()
    }
}

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

private class NotePage(inflater: LayoutInflater, container: ViewGroup, updateNote: (VerseIndex, String) -> Unit)
    : VerseDetailPage<PageVerseDetailNoteBinding>(PageVerseDetailNoteBinding.inflate(inflater, container, false)) {
    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable) {
            viewBinding.noteInputLayout.helperText = null
            verseDetail?.let { updateNote(it.verseIndex, s.toString()) }
        }
    }

    private var verseDetail: VerseDetailViewData? = null

    init {
        viewBinding.note.addTextChangedListener(textWatcher)
        viewBinding.noteInputLayout.setHelperTextColor(
            ColorStateList.valueOf(ContextCompat.getColor(viewBinding.root.context, R.color.light_gray))
        )
    }

    fun markSaved() {
        viewBinding.noteInputLayout.helperText = viewBinding.root.context.getString(R.string.text_saved)
    }

    override fun bind(verseDetail: VerseDetailViewData, settings: Settings) {
        this.verseDetail = verseDetail
        viewBinding.noteInputLayout.helperText = null
        with(viewBinding.note) {
            removeTextChangedListener(textWatcher)
            setText(verseDetail.note)
            addTextChangedListener(textWatcher)

            setPrimaryTextSize(settings)
        }
    }
}
