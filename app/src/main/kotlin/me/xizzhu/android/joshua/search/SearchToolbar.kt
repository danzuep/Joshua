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

package me.xizzhu.android.joshua.search

import android.app.SearchManager
import android.app.SearchableInfo
import android.content.Context
import android.database.Cursor
import android.util.AttributeSet
import androidx.appcompat.widget.SearchView
import com.google.android.material.appbar.MaterialToolbar
import me.xizzhu.android.joshua.R

class SearchToolbar : MaterialToolbar {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        inflateMenu(R.menu.menu_search)
        menu.findItem(R.id.action_search).expandActionView()
        with(searchView()) {
            isQueryRefinementEnabled = true
            isIconified = false
        }
    }

    private fun searchView(): SearchView = menu.findItem(R.id.action_search).actionView as SearchView

    fun initialize(
        onSearchByReferenceChanged: (Boolean) -> Unit,
        onIncludeOldTestamentChanged: (Boolean) -> Unit,
        onIncludeNewTestamentChanged: (Boolean) -> Unit,
        onIncludeBookmarksChanged: (Boolean) -> Unit,
        onIncludeHighlightsChanged: (Boolean) -> Unit,
        onIncludeNotesChanged: (Boolean) -> Unit,
        onWholeWordsChanged: (Boolean) -> Unit,
        onQueryTextListener: SearchView.OnQueryTextListener,
        clearHistory: () -> Unit
    ) {
        searchView().setOnQueryTextListener(onQueryTextListener)
        // Tapping a history suggestion re-runs that search in place instead of launching SearchActivity again.
        searchView().setOnSuggestionListener(object : SearchView.OnSuggestionListener {
            override fun onSuggestionSelect(position: Int): Boolean = false

            override fun onSuggestionClick(position: Int): Boolean {
                val cursor = searchView().suggestionsAdapter?.getItem(position) as? Cursor ?: return false
                val columnIndex = cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_QUERY)
                        .takeIf { it >= 0 } ?: cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_1)
                if (columnIndex < 0) return false
                val query = cursor.getString(columnIndex)?.takeIf { it.isNotBlank() } ?: return false
                searchView().setQuery(query, false)
                onQueryTextListener.onQueryTextSubmit(query)
                searchView().clearFocus()
                return true
            }
        })

        setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_search_by_reference -> {
                    onSearchByReferenceChanged(!menuItem.isChecked)
                    true
                }
                R.id.action_search_include_old_testament -> {
                    onIncludeOldTestamentChanged(!menuItem.isChecked)
                    true
                }
                R.id.action_search_include_new_testament -> {
                    onIncludeNewTestamentChanged(!menuItem.isChecked)
                    true
                }
                R.id.action_search_include_bookmarks -> {
                    onIncludeBookmarksChanged(!menuItem.isChecked)
                    true
                }
                R.id.action_search_include_highlights -> {
                    onIncludeHighlightsChanged(!menuItem.isChecked)
                    true
                }
                R.id.action_search_include_notes -> {
                    onIncludeNotesChanged(!menuItem.isChecked)
                    true
                }
                R.id.action_search_whole_words -> {
                    onWholeWordsChanged(!menuItem.isChecked)
                    true
                }
                R.id.action_clear_search_history -> {
                    clearHistory()
                    true
                }
                else -> false
            }
        }
    }

    fun setSearchConfiguration(
        searchByReference: Boolean,
        includeOldTestament: Boolean,
        includeNewTestament: Boolean,
        includeBookmarks: Boolean,
        includeHighlights: Boolean,
        includeNotes: Boolean,
        wholeWords: Boolean
    ) {
        menu.findItem(R.id.action_search_by_reference).isChecked = searchByReference
        menu.findItem(R.id.action_search_include_old_testament).isChecked = includeOldTestament
        menu.findItem(R.id.action_search_include_new_testament).isChecked = includeNewTestament
        menu.findItem(R.id.action_search_include_bookmarks).isChecked = includeBookmarks
        menu.findItem(R.id.action_search_include_highlights).isChecked = includeHighlights
        menu.findItem(R.id.action_search_include_notes).isChecked = includeNotes
        menu.findItem(R.id.action_search_whole_words).isChecked = wholeWords
    }

    fun setSearchableInfo(searchable: SearchableInfo) {
        searchView().setSearchableInfo(searchable)
    }
}
