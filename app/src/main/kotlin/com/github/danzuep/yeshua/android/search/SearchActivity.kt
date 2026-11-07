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

package com.github.danzuep.yeshua.android.search

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import androidx.activity.viewModels
import androidx.core.os.bundleOf
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.asExecutor
import com.github.danzuep.yeshua.android.Navigator
import com.github.danzuep.yeshua.android.R
import com.github.danzuep.yeshua.android.core.provider.CoroutineDispatcherProvider
import com.github.danzuep.yeshua.android.databinding.ActivitySearchBinding
import com.github.danzuep.yeshua.android.infra.BaseActivity
import com.github.danzuep.yeshua.android.reading.ReadingActivity
import com.github.danzuep.yeshua.android.ui.dialog
import com.github.danzuep.yeshua.android.ui.fadeIn
import com.github.danzuep.yeshua.android.ui.hideKeyboard
import com.github.danzuep.yeshua.android.ui.listDialog
import com.github.danzuep.yeshua.android.ui.toast
import javax.inject.Inject
import com.github.danzuep.yeshua.android.preview.Preview
import com.github.danzuep.yeshua.android.preview.PreviewAdapter

@AndroidEntryPoint
class SearchActivity : BaseActivity<ActivitySearchBinding, SearchViewModel.ViewAction, SearchViewModel.ViewState, SearchViewModel>() {
    @Inject
    lateinit var coroutineDispatcherProvider: CoroutineDispatcherProvider

    private lateinit var adapter: SearchAdapter

    override val viewModel: SearchViewModel by viewModels()

    override val viewBinding: ActivitySearchBinding by lazy(LazyThreadSafetyMode.NONE) { ActivitySearchBinding.inflate(layoutInflater) }

    override fun initializeView() {
        viewBinding.toolbar.setNavigationOnClickListener { navigator.goBack(this) }
        viewBinding.toolbar.initialize(
            onSearchByReferenceChanged = viewModel::searchByReference,
            onIncludeOldTestamentChanged = viewModel::includeOldTestament,
            onIncludeNewTestamentChanged = viewModel::includeNewTestament,
            onIncludeBookmarksChanged = viewModel::includeBookmarks,
            onIncludeHighlightsChanged = viewModel::includeHighlights,
            onIncludeNotesChanged = viewModel::includeNotes,
            onWholeWordsChanged = viewModel::includeWholeWords,
            onQueryTextListener = object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    viewModel.search(query, false)
                    viewBinding.toolbar.hideKeyboard()

                    // so that the system can close the search suggestion
                    return false
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    viewModel.search(newText, true)
                    return true
                }
            },
            clearHistory = viewModel::clearSearchHistory,
        )

        // It's possible that the system has no SearchManager available, and on some devices getSearchableInfo() could return null.
        (applicationContext.getSystemService(Context.SEARCH_SERVICE) as? SearchManager)
            ?.getSearchableInfo(componentName)?.let { viewBinding.toolbar.setSearchableInfo(it) }

        adapter = SearchAdapter(
            inflater = layoutInflater,
            executor = coroutineDispatcherProvider.default.asExecutor()
        ) { viewEvent ->
            when (viewEvent) {
                is SearchAdapter.ViewEvent.OpenVerse -> viewModel.openVerse(viewEvent.verseToOpen)
                is SearchAdapter.ViewEvent.OpenBook -> viewModel.openBook(viewEvent.bookIndex)
                is SearchAdapter.ViewEvent.ShowPreview -> viewModel.loadPreview(viewEvent.verseToPreview)
            }
        }
        viewBinding.searchResult.adapter = adapter

        handleSearchIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSearchIntent(intent)
    }

    /** Voice input (launchRecognizer) returns the recognised text as an ACTION_SEARCH intent carrying SearchManager.QUERY. */
    private fun handleSearchIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_SEARCH) return
        val query = intent.getStringExtra(SearchManager.QUERY)?.trim()
        if (query.isNullOrEmpty()) return

        viewBinding.toolbar.setQuery(query)
        viewModel.search(query, false)
        intent.removeExtra(SearchManager.QUERY)
    }

    override fun onViewActionEmitted(viewAction: SearchViewModel.ViewAction) = when (viewAction) {
        SearchViewModel.ViewAction.OpenReadingScreen -> navigator.navigate(this, Navigator.SCREEN_READING)
        is SearchViewModel.ViewAction.OpenBook -> navigator.navigate(
            this, Navigator.SCREEN_READING, bundleOf(ReadingActivity.KEY_OPEN_BOOK to viewAction.bookIndex))
    }

    override fun onViewStateUpdated(viewState: SearchViewModel.ViewState): Unit = with(viewBinding) {
        if (viewState.loading) {
            loadingSpinner.fadeIn()
            searchResult.isVisible = false
        } else {
            if (viewState.instantSearch) {
                searchResult.isVisible = true
            } else {
                searchResult.fadeIn()
            }

            loadingSpinner.isVisible = false
        }

        viewState.searchConfig?.let { searchConfig ->
            toolbar.setSearchConfiguration(
                searchByReference = searchConfig.searchByReference,
                includeOldTestament = searchConfig.includeOldTestament,
                includeNewTestament = searchConfig.includeNewTestament,
                includeBookmarks = searchConfig.includeBookmarks,
                includeHighlights = searchConfig.includeHighlights,
                includeNotes = searchConfig.includeNotes,
                wholeWords = searchConfig.wholeWords,
            )
        }

        adapter.submitList(viewState.items)
        if (viewState.scrollItemsToPosition >= 0) {
            searchResult.scrollToPosition(viewState.scrollItemsToPosition)
            viewModel.markItemsAsScrolled()
        }

        viewState.searchResultSummary?.let {
            toast(it)
            viewModel.markSearchResultSummaryAsShown()
        }

        viewState.preview?.let { preview ->
            if (preview !== presentedPreview) {
                presentedPreview = preview
                preview.handle()
            }
        } ?: run { presentedPreview = null }
        viewState.error?.let { error ->
            if (error !== presentedError) {
                presentedError = error
                error.handle()
            }
        } ?: run { presentedError = null }
    }

    /** The preview dialog and error dialog are shown once per state, otherwise every state update stacks another one. */
    private var presentedPreview: Preview? = null
    private var presentedError: SearchViewModel.ViewState.Error? = null

    private fun Preview.handle() {
        val previewAdapter = PreviewAdapter(
            inflater = layoutInflater,
            executor = coroutineDispatcherProvider.default.asExecutor()
        ) { viewEvent ->
            when (viewEvent) {
                is PreviewAdapter.ViewEvent.OpenVerse -> viewModel.openVerse(viewEvent.verseToOpen)
            }
        }
        listDialog(
            title = title,
            adapter = previewAdapter,
            scrollToPosition = currentPosition,
            onDismiss = viewModel::markPreviewAsClosed,
        )
        previewAdapter.submitList(items)
    }

    private fun SearchViewModel.ViewState.Error.handle() = when (this) {
        is SearchViewModel.ViewState.Error.PreviewLoadingError -> {
            viewModel.markErrorAsShown(this)

            // Very unlikely to fail, so just falls back to open the verse.
            viewModel.openVerse(verseToPreview)
        }
        is SearchViewModel.ViewState.Error.SearchConfigUpdatingError -> {
            toast(R.string.toast_unknown_error)
            viewModel.markErrorAsShown(this)
        }
        is SearchViewModel.ViewState.Error.VerseOpeningError -> {
            dialog(
                cancelable = true,
                title = R.string.dialog_title_error,
                message = R.string.dialog_message_failed_to_select_verse,
                onPositive = { _, _ -> viewModel.openVerse(verseToOpen) },
                onDismiss = { viewModel.markErrorAsShown(this) }
            )
        }
        is SearchViewModel.ViewState.Error.VerseSearchingError -> {
            dialog(
                cancelable = false,
                title = R.string.dialog_title_error,
                message = R.string.dialog_message_failed_to_search,
                onPositive = { _, _ -> viewModel.retrySearch() },
                onNegative = { _, _ -> navigator.goBack(this@SearchActivity) },
                onDismiss = { viewModel.markErrorAsShown(this) }
            )
        }
    }
}
