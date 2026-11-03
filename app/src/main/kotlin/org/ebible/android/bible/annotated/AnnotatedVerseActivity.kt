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

package org.ebible.android.bible.annotated

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import kotlinx.coroutines.asExecutor
import org.ebible.android.bible.Navigator
import org.ebible.android.bible.R
import org.ebible.android.bible.core.VerseAnnotation
import org.ebible.android.bible.core.provider.CoroutineDispatcherProvider
import org.ebible.android.bible.databinding.ActivityAnnotatedBinding
import org.ebible.android.bible.infra.BaseActivity
import org.ebible.android.bible.ui.dialog
import org.ebible.android.bible.ui.fadeIn
import org.ebible.android.bible.ui.listDialog
import javax.inject.Inject
import org.ebible.android.bible.preview.Preview
import org.ebible.android.bible.preview.PreviewAdapter

abstract class AnnotatedVerseActivity<V : VerseAnnotation, VM : AnnotatedVerseViewModel<V>>(
    @StringRes private val toolbarText: Int
) : BaseActivity<ActivityAnnotatedBinding, AnnotatedVerseViewModel.ViewAction, AnnotatedVerseViewModel.ViewState, VM>() {
    @Inject
    lateinit var coroutineDispatcherProvider: CoroutineDispatcherProvider

    private lateinit var adapter: AnnotatedVerseAdapter

    override val viewBinding: ActivityAnnotatedBinding by lazy(LazyThreadSafetyMode.NONE) { ActivityAnnotatedBinding.inflate(layoutInflater) }

    override fun initializeView() = with(viewBinding) {
        toolbar.setNavigationOnClickListener { navigator.goBack(this@AnnotatedVerseActivity) }
        toolbar.initialize(sortOrderUpdated = viewModel::saveSortOrder)
        toolbar.setTitle(toolbarText)

        adapter = AnnotatedVerseAdapter(
            inflater = layoutInflater,
            executor = coroutineDispatcherProvider.default.asExecutor()
        ) { viewEvent ->
            when (viewEvent) {
                is AnnotatedVerseAdapter.ViewEvent.OpenVerse -> viewModel.openVerse(viewEvent.verseToOpen)
                is AnnotatedVerseAdapter.ViewEvent.ShowPreview -> viewModel.loadPreview(viewEvent.verseToPreview)
            }
        }
        verseList.adapter = adapter
    }

    override fun onViewActionEmitted(viewAction: AnnotatedVerseViewModel.ViewAction) = when (viewAction) {
        AnnotatedVerseViewModel.ViewAction.OpenReadingScreen -> navigator.navigate(this, Navigator.SCREEN_READING, extrasForOpeningVerse())
    }

    override fun onViewStateUpdated(viewState: AnnotatedVerseViewModel.ViewState): Unit = with(viewBinding) {
        toolbar.setSortOrder(viewState.sortOrder)

        if (viewState.loading) {
            loadingSpinner.fadeIn()
            verseList.isVisible = false
        } else {
            loadingSpinner.isVisible = false
            verseList.fadeIn()
        }

        adapter.submitList(viewState.items)

        viewState.preview?.handle()
        viewState.error?.handle()
    }

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

    private fun AnnotatedVerseViewModel.ViewState.Error.handle() = when (this) {
        is AnnotatedVerseViewModel.ViewState.Error.AnnotatedVersesLoadingError -> {
            dialog(
                cancelable = false,
                title = R.string.dialog_title_error,
                message = R.string.dialog_message_failed_to_load_annotated_verses,
                onPositive = { _, _ -> viewModel.loadAnnotatedVerses() },
                onNegative = { _, _ -> navigator.goBack(this@AnnotatedVerseActivity) },
                onDismiss = { viewModel.markErrorAsShown(this) }
            )
        }
        is AnnotatedVerseViewModel.ViewState.Error.PreviewLoadingError -> {
            viewModel.markErrorAsShown(this)

            // Very unlikely to fail, so just falls back to open the verse.
            viewModel.openVerse(verseToPreview)
        }
        is AnnotatedVerseViewModel.ViewState.Error.SortOrderSavingError -> {
            dialog(
                cancelable = true,
                title = R.string.dialog_title_error,
                message = R.string.dialog_message_failed_to_save_sort_order,
                onPositive = { _, _ -> viewModel.saveSortOrder(sortOrder) },
                onDismiss = { viewModel.markErrorAsShown(this) }
            )
        }
        is AnnotatedVerseViewModel.ViewState.Error.VerseOpeningError -> {
            dialog(
                cancelable = true,
                title = R.string.dialog_title_error,
                message = R.string.dialog_message_failed_to_select_verse,
                onPositive = { _, _ -> viewModel.openVerse(verseToOpen) },
                onDismiss = { viewModel.markErrorAsShown(this) }
            )
        }
    }

    protected open fun extrasForOpeningVerse(): Bundle? = null
}
