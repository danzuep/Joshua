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

package me.xizzhu.android.joshua.reading.chapter

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.AttributeSet
import android.widget.ExpandableListView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

class ChapterListView : ExpandableListView {
    private lateinit var adapter: ChapterListAdapter
    private var lastExpandedGroup: Int = -1
    private var currentBookIndex: Int = -1

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    private var onViewEvent: ((ChapterSelectionView.ViewEvent) -> Unit)? = null

    init {
        divider = ColorDrawable(ContextCompat.getColor(context, android.R.color.darker_gray))
        dividerHeight = 1

        setGroupIndicator(null)
        setOnGroupClickListener { _, _, groupPosition, _ ->
            if (isGroupExpanded(groupPosition)) {
                collapseGroup(groupPosition)
            } else {
                val bookIndex = adapter.getGroup(groupPosition).bookIndex
                expandBook(bookIndex = bookIndex)
                // Load the book straight away; the chapter list stays open until a chapter is picked.
                if (bookIndex != currentBookIndex) {
                    onViewEvent?.invoke(ChapterSelectionView.ViewEvent.SelectBook(bookIndex = bookIndex))
                }
            }

            return@setOnGroupClickListener true
        }

        setChildDivider(ColorDrawable(Color.TRANSPARENT))
    }

    private fun expandBook(bookIndex: Int) {
        val groupToExpand = adapter.groupPositionOf(bookIndex)
        // The books may not be loaded yet, or may be missing from this translation; expanding a missing group crashes in the adapter.
        if (groupToExpand !in 0 until adapter.groupCount || isGroupExpanded(groupToExpand)) {
            return
        }

        expandGroup(groupToExpand)
        if (lastExpandedGroup != groupToExpand) {
            collapseGroup(lastExpandedGroup)
            lastExpandedGroup = groupToExpand
        }
        setSelectedGroup(groupToExpand)
    }

    fun initialize(onViewEvent: (ChapterSelectionView.ViewEvent) -> Unit) {
        this.onViewEvent = onViewEvent
        adapter = ChapterListAdapter(context, onViewEvent)
        setAdapter(adapter)
    }

    fun setCurrentChapter(currentBookIndex: Int, currentChapterIndex: Int) {
        this.currentBookIndex = currentBookIndex
        adapter.setCurrentChapter(currentBookIndex = currentBookIndex, currentChapterIndex = currentChapterIndex)
    }

    fun setChapterSelectionItems(chapterSelectionItems: List<ChapterSelectionItem>) {
        adapter.setItems(items = chapterSelectionItems)
    }

    fun expandCurrentBook() {
        if (currentBookIndex == -1) return
        expandBook(bookIndex = currentBookIndex)
    }

    fun scrollToFirstBookFrom(bookIndex: Int) {
        val groupPosition = adapter.firstGroupPositionFrom(bookIndex)
        if (groupPosition < 0) return

        val flatListPosition = getFlatListPosition(ExpandableListView.getPackedPositionForGroup(groupPosition))
        if (flatListPosition >= 0) {
            smoothScrollToPositionFromTop(flatListPosition, 0, 100)
        }
    }
}
