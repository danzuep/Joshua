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

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import me.xizzhu.android.joshua.core.Bible
import me.xizzhu.android.joshua.core.Settings
import me.xizzhu.android.joshua.core.VerseIndex
import me.xizzhu.android.joshua.databinding.PageVerseBinding
import me.xizzhu.android.joshua.reading.VerseUpdate
import me.xizzhu.android.joshua.ui.fadeIn
import me.xizzhu.android.joshua.ui.fadeOut
import me.xizzhu.android.joshua.ui.recyclerview.BaseItem
import me.xizzhu.android.joshua.ui.recyclerview.TitleItem

class VersePagerAdapter(
    context: Context,
    private val loadVerses: (Int, Int) -> Unit,
    private val updateCurrentVerse: (VerseIndex) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val inflater = LayoutInflater.from(context)
    private val chapterItems = mutableMapOf<Int, List<BaseItem>>()
    private val loadingPositions = mutableSetOf<Int>()
    private var currentPagePosition = RecyclerView.NO_POSITION
    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            currentPagePosition = position
            val newBookIndex = position.toBookIndex()
            val newChapterIndex = position.toChapterIndex()
            if (currentVerseIndex.bookIndex != newBookIndex || currentVerseIndex.chapterIndex != newChapterIndex) {
                currentVerseIndex = VerseIndex(newBookIndex, newChapterIndex, 0)
            }
        }
    }

    private var attachedRecyclerView: RecyclerView? = null
    private var currentVerseIndex = VerseIndex.INVALID
    private var currentTranslation = ""
    private var parallelTranslations = emptyList<String>()

    var settings: Settings? = null
        set(value) {
            field = value
            notifyDataSetChangedSafely()
        }

    private fun notifyDataSetChangedSafely() {
        attachedRecyclerView?.let { recyclerView ->
            if (recyclerView.isComputingLayout) {
                recyclerView.post { notifyDataSetChanged() }
                return
            }
        }
        notifyDataSetChanged()
    }

    fun setCurrent(verseIndex: VerseIndex, translation: String, parallel: List<String>) {
        val shouldNotifyDataSetChanged = currentTranslation != translation || parallelTranslations != parallel
        if (shouldNotifyDataSetChanged) {
            chapterItems.clear()
            loadingPositions.clear()
        }
        currentVerseIndex = verseIndex
        currentTranslation = translation
        parallelTranslations = parallel
        if (shouldNotifyDataSetChanged) notifyDataSetChangedSafely()
    }

    fun setVerses(bookIndex: Int, chapterIndex: Int, verses: List<BaseItem>) {
        val position = indexToPagePosition(bookIndex, chapterIndex)
        loadingPositions.remove(position)
        chapterItems[position] = verses
        forEachAttachedPage { it.setChapter(position, verses) }
    }

    fun onVersesLoadFailed(bookIndex: Int, chapterIndex: Int) {
        val position = indexToPagePosition(bookIndex, chapterIndex)
        loadingPositions.remove(position)
    }

    fun containsChapterInCurrentPage(verseIndex: VerseIndex): Boolean {
        val position = runCatching { verseIndex.toPagePosition() }.getOrNull() ?: return false
        val page = attachedRecyclerView?.findViewHolderForAdapterPosition(currentPagePosition) as? Page ?: return false
        return page.containsChapter(position)
    }

    fun scrollToChapterStart(position: Int) {
        forEachAttachedPage { it.scrollToChapterStart(position) }
    }

    private fun forEachAttachedPage(action: (Page) -> Unit) {
        attachedRecyclerView?.let { recyclerView ->
            for (index in 0 until recyclerView.childCount) {
                (recyclerView.getChildViewHolder(recyclerView.getChildAt(index)) as? Page)?.let(action)
            }
        }
    }

    private fun getChapter(position: Int): List<BaseItem>? = chapterItems[position]

    private fun loadChapter(position: Int) {
        if (position !in 0 until Bible.TOTAL_CHAPTER_COUNT || !loadingPositions.add(position)) return
        loadVerses(position.toBookIndex(), position.toChapterIndex())
    }

    private fun notifyItemChangedSafely(position: Int, payload: Any) {
        attachedRecyclerView?.let { recyclerView ->
            if (recyclerView.isComputingLayout) {
                recyclerView.post { notifyItemChanged(position, payload) }
                return
            }
        }
        notifyItemChanged(position, payload)
    }

    fun selectVerse(verseIndex: VerseIndex) {
        notifyVerseUpdate(VerseUpdate(verseIndex, VerseUpdate.VERSE_SELECTED))
    }

    fun deselectVerse(verseIndex: VerseIndex) {
        notifyVerseUpdate(VerseUpdate(verseIndex, VerseUpdate.VERSE_DESELECTED))
    }

    fun notifyVerseUpdate(verseUpdate: VerseUpdate) {
        notifyItemChangedSafely(verseUpdate.verseIndex.toPagePosition(), verseUpdate)
        forEachAttachedPage { it.notifyVerseUpdate(verseUpdate) }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        attachedRecyclerView = recyclerView
        (recyclerView.parent as ViewPager2).registerOnPageChangeCallback(pageChangeCallback)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        attachedRecyclerView = null
        currentPagePosition = RecyclerView.NO_POSITION
        (recyclerView.parent as ViewPager2).unregisterOnPageChangeCallback(pageChangeCallback)
    }

    override fun getItemCount(): Int =
        if (currentVerseIndex.isValid() && currentTranslation.isNotEmpty() && settings != null) Bible.TOTAL_CHAPTER_COUNT else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        Page(inflater, parent, ::getChapter, ::loadChapter, updateCurrentVerse)

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as Page).bind(position, settings!!, currentVerseIndex)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            (holder as Page).bind(payloads)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }
}

private fun sameBookNeighbour(position: Int, offset: Int): Int? {
    val neighbour = position + offset
    if (neighbour !in 0 until Bible.TOTAL_CHAPTER_COUNT) return null
    return neighbour.takeIf { it.toBookIndex() == position.toBookIndex() }
}

private class Page(
    inflater: LayoutInflater,
    container: ViewGroup,
    private val getChapter: (Int) -> List<BaseItem>?,
    private val loadChapter: (Int) -> Unit,
    updateCurrentVerse: (VerseIndex) -> Unit
) : RecyclerView.ViewHolder(PageVerseBinding.inflate(inflater, container, false).root) {
    private val chapters = sortedMapOf<Int, List<BaseItem>>()
    private val requestedPositions = mutableSetOf<Int>()
    private var pagePosition = RecyclerView.NO_POSITION
    private var currentVerseIndex = VerseIndex.INVALID
    private var hasRendered = false
    private var renderedItems = emptyList<BaseItem>()

    private val viewBinding = PageVerseBinding.bind(itemView).apply {
        verseList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    firstVisibleVerse(recyclerView)?.let(updateCurrentVerse)
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy == 0 || chapters.isEmpty()) return
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                if (layoutManager.findFirstVisibleItemPosition() <= 1) {
                    sameBookNeighbour(chapters.firstKey(), -1)?.let(::ensureChapter)
                }
                if (layoutManager.findLastVisibleItemPosition() >= (recyclerView.adapter?.itemCount ?: 0) - 2) {
                    sameBookNeighbour(chapters.lastKey(), 1)?.let(::ensureChapter)
                }
            }
        })
    }

    fun bind(position: Int, settings: Settings, currentVerseIndex: VerseIndex) {
        pagePosition = position
        this.currentVerseIndex = currentVerseIndex
        hasRendered = false
        renderedItems = emptyList()
        chapters.clear()
        requestedPositions.clear()
        viewBinding.verseList.visibility = View.GONE
        viewBinding.loadingSpinner.visibility = View.VISIBLE
        viewBinding.verseList.setSettings(settings)

        listOfNotNull(sameBookNeighbour(position, -1), position, sameBookNeighbour(position, 1)).forEach(::ensureChapter)
    }

    fun bind(payloads: MutableList<Any>) {
        payloads.filterIsInstance<VerseUpdate>().forEach(::notifyVerseUpdate)
    }

    private fun ensureChapter(position: Int) {
        if (position in requestedPositions || position !in 0 until Bible.TOTAL_CHAPTER_COUNT) return
        requestedPositions.add(position)
        val cachedItems = getChapter(position)
        if (cachedItems != null) setChapter(position, cachedItems) else loadChapter(position)
    }

    fun setChapter(position: Int, items: List<BaseItem>) {
        if (position !in requestedPositions) return
        if (viewBinding.verseList.isComputingLayout) {
            viewBinding.verseList.post { setChapter(position, items) }
            return
        }

        val anchor = if (hasRendered) captureScrollAnchor() else null

        chapters[position] = items
        if (!chapters.containsKey(pagePosition)) return
        renderedItems = combinedItems()
        if (renderedItems.isEmpty()) return

        viewBinding.verseList.setItems(renderedItems)
        viewBinding.verseList.fadeIn()
        viewBinding.loadingSpinner.fadeOut()

        if (!hasRendered) {
            hasRendered = true
            val targetVerse = currentVerseIndex.takeIf { it.toPagePosition() == pagePosition }
            val target = targetVerse?.let { renderedItems.positionOfVerse(it) }
                ?: renderedItems.positionOfChapter(pagePosition)
            if (target >= 0) scrollToPosition(target, 0)
        } else if (anchor != null) {
            val target = renderedItems.positionOfVerse(anchor.verseIndex)
            if (target >= 0) scrollToPosition(target, anchor.offset)
        }
    }

    private data class ScrollAnchor(val verseIndex: VerseIndex, val offset: Int)

    /** Remembers the first visible paragraph and its on-screen top, so content inserted above it does not shift the view. */
    private fun captureScrollAnchor(): ScrollAnchor? {
        val layoutManager = viewBinding.verseList.layoutManager as LinearLayoutManager
        val firstPosition = layoutManager.findFirstVisibleItemPosition()
        val lastPosition = layoutManager.findLastVisibleItemPosition()
        if (firstPosition == RecyclerView.NO_POSITION) return null
        for (position in firstPosition..lastPosition) {
            val paragraph = renderedItems.getOrNull(position) as? ParagraphItem ?: continue
            val view = layoutManager.findViewByPosition(position) ?: return null
            return ScrollAnchor(paragraph.firstVerse, layoutManager.getDecoratedTop(view))
        }
        return null
    }

    private fun scrollToPosition(position: Int, offset: Int) {
        (viewBinding.verseList.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, offset)
    }

    fun scrollToChapterStart(chapterPosition: Int) {
        if (chapterPosition != pagePosition || !hasRendered) return
        val verseOne = VerseIndex(chapterPosition.toBookIndex(), chapterPosition.toChapterIndex(), 0)
        val target = renderedItems.positionOfVerse(verseOne).takeIf { it >= 0 } ?: renderedItems.positionOfChapter(chapterPosition)
        if (target >= 0) scrollToPosition(target, 0)
    }


    fun containsChapter(position: Int): Boolean = chapters.containsKey(position)

    private fun combinedItems(): List<BaseItem> = buildList {
        chapters.forEach { (position, items) ->
            add(TitleItem("Chapter ${position.toChapterIndex() + 1}", hideDivider = false))
            addAll(items)
        }
    }

    private fun List<BaseItem>.positionOfChapter(position: Int): Int {
        var itemPosition = 0
        chapters.forEach { (chapterPosition, items) ->
            if (chapterPosition == position) return itemPosition
            itemPosition += items.size + 1
        }
        return RecyclerView.NO_POSITION
    }

    private fun List<BaseItem>.positionOfVerse(verseIndex: VerseIndex): Int {
        forEachIndexed { index, item ->
            if (item is ParagraphItem && item.firstVerse.bookIndex == verseIndex.bookIndex
                && item.firstVerse.chapterIndex == verseIndex.chapterIndex && item.containsVerse(verseIndex.verseIndex)) {
                return index
            }
        }
        return RecyclerView.NO_POSITION
    }

    private fun firstVisibleVerse(recyclerView: RecyclerView): VerseIndex? {
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        val firstPosition = layoutManager.findFirstVisibleItemPosition()
        val lastPosition = layoutManager.findLastVisibleItemPosition()
        for (position in firstPosition..lastPosition) {
            (renderedItems.getOrNull(position) as? ParagraphItem)?.let { return it.firstVerse }
        }
        return null
    }

    fun notifyVerseUpdate(verseUpdate: VerseUpdate) {
        if (viewBinding.verseList.isComputingLayout) {
            viewBinding.verseList.post { notifyVerseUpdate(verseUpdate) }
            return
        }
        val chapterPosition = verseUpdate.verseIndex.toPagePosition()
        if (!chapters.containsKey(chapterPosition)) return
        val adapter = viewBinding.verseList.adapter ?: return
        var itemPosition = 0
        chapters.forEach { (position, chapterItems) ->
            if (position == chapterPosition) {
                chapterItems.forEachIndexed { index, item ->
                    if (item is ParagraphItem && item.containsVerse(verseUpdate.verseIndex.verseIndex)) {
                        adapter.notifyItemChanged(itemPosition + index + 1, verseUpdate)
                    }
                }
                return
            }
            itemPosition += chapterItems.size + 1
        }
    }
}
