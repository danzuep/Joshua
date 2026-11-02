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

package me.xizzhu.android.joshua.translations

import android.text.InputType
import android.view.MotionEvent
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.asExecutor
import me.xizzhu.android.joshua.R
import me.xizzhu.android.joshua.core.TranslationInfo
import me.xizzhu.android.joshua.core.provider.CoroutineDispatcherProvider
import me.xizzhu.android.joshua.databinding.ActivityTranslationsBinding
import me.xizzhu.android.joshua.infra.BaseActivity
import me.xizzhu.android.joshua.ui.ProgressDialog
import me.xizzhu.android.joshua.ui.dialog
import me.xizzhu.android.joshua.ui.fadeIn
import me.xizzhu.android.joshua.ui.indeterminateProgressDialog
import me.xizzhu.android.joshua.ui.languageDisplayName
import me.xizzhu.android.joshua.ui.progressDialog
import me.xizzhu.android.joshua.ui.toast

@AndroidEntryPoint
class TranslationsActivity : BaseActivity<ActivityTranslationsBinding, TranslationsViewModel.ViewAction, TranslationsViewModel.ViewState, TranslationsViewModel>() {
    companion object {
        const val KEY_DOWNLOAD_TRANSLATION = "me.xizzhu.android.joshua.KEY_DOWNLOAD_TRANSLATION"
    }

    @Inject
    lateinit var coroutineDispatcherProvider: CoroutineDispatcherProvider

    private lateinit var adapter: TranslationsAdapter

    private var downloadTranslationDialog: ProgressDialog? = null
    private var removeTranslationDialog: AlertDialog? = null

    override val viewModel: TranslationsViewModel by viewModels()

    override val viewBinding: ActivityTranslationsBinding by lazy(LazyThreadSafetyMode.NONE) { ActivityTranslationsBinding.inflate(layoutInflater) }

    override fun initializeView() {
        viewBinding.toolbar.setNavigationOnClickListener { navigator.goBack(this) }
        adapter = TranslationsAdapter(
            inflater = layoutInflater,
            executor = coroutineDispatcherProvider.default.asExecutor()
        ) { viewEvent ->
            when (viewEvent) {
                is TranslationsAdapter.ViewEvent.DownloadTranslation -> showDownloadTranslationDialog(viewEvent.translationToDownload)
                is TranslationsAdapter.ViewEvent.RemoveTranslation -> {
                    dialog(
                        cancelable = true,
                        title = viewEvent.translationToRemove.name,
                        message = R.string.dialog_message_delete_translation_confirmation,
                        onPositive = { _, _ -> viewModel.removeTranslation(viewEvent.translationToRemove) },
                    )
                }
                is TranslationsAdapter.ViewEvent.SelectTranslation -> viewModel.selectTranslation(viewEvent.translationToSelect)
            }
        }
        viewBinding.translationList.adapter = adapter
        setupFastScroller()

        with(viewBinding.toolbar) {
            inflateMenu(R.menu.menu_translations)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_filter_translations -> {
                        showFilterDialog()
                        true
                    }
                    R.id.action_download_sites -> {
                        showDownloadSitesDialog()
                        true
                    }
                    else -> false
                }
            }
        }

        with(viewBinding.swipeRefresher) {
            setColorSchemeResources(R.color.primary, R.color.secondary, R.color.dark_cyan, R.color.dark_lime)
            setOnRefreshListener { viewModel.loadTranslations(forceRefresh = true) }
        }

        intent.getStringExtra(KEY_DOWNLOAD_TRANSLATION)?.let { viewModel.downloadTranslation(it) }
    }

    private fun setupFastScroller() {
        viewBinding.fastScrollTrack.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val fraction = (event.y / view.height).coerceIn(0f, 1f)
                    scrollToLanguage(fraction)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.performClick()
                    viewBinding.fastScrollBubble.isVisible = false
                    true
                }
                else -> false
            }
        }
    }

    private fun scrollToLanguage(fraction: Float) {
        // Only language headers are used for the index; the "Available" section header has hideDivider = false.
        val headers = adapter.currentList.withIndex()
            .filter { (it.value as? TranslationsItem.Header)?.hideDivider == true }
        if (headers.isEmpty()) return

        val index = (fraction * headers.size).toInt().coerceIn(0, headers.lastIndex)
        val header = headers[index]
        (viewBinding.translationList.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(header.index, 0)

        with(viewBinding.fastScrollBubble) {
            text = (header.value as TranslationsItem.Header).title.firstOrNull()?.uppercase() ?: ""
            isVisible = true
            val trackHeight = viewBinding.fastScrollTrack.height
            translationY = (fraction * trackHeight - height / 2f).coerceIn(0f, (trackHeight - height).toFloat().coerceAtLeast(0f))
        }
    }

    private fun showFilterDialog() {
        val languages = viewModel.availableLanguages()
        val content = layoutInflater.inflate(R.layout.dialog_filter_translations, null)
        val keywordInput = content.findViewById<EditText>(R.id.filter_keyword).apply { setText(viewModel.keywordFilter) }
        val languageSpinner = content.findViewById<Spinner>(R.id.filter_language).apply {
            adapter = ArrayAdapter(
                this@TranslationsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf(getString(R.string.text_all_languages)) + languages.map { languageDisplayName(it) }
            )
            setSelection(viewModel.languageFilter?.let { languages.indexOf(it) + 1 } ?: 0)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_title_filter_translations)
            .setView(content)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.applyFilter(
                    language = languages.getOrNull(languageSpinner.selectedItemPosition - 1),
                    keyword = keywordInput.text.toString(),
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDownloadTranslationDialog(translation: TranslationInfo) {
        viewModel.readTranslationDetails(translation) { details ->
            if (isDestroyed) return@readTranslationDetails
            val content = layoutInflater.inflate(R.layout.dialog_translation_details, null)
            content.findViewById<TextView>(R.id.details_text).text = details.joinToString("\n\n") { (label, value) -> "$label: $value" }
            AlertDialog.Builder(this)
                .setTitle(translation.name)
                .setView(content)
                .setPositiveButton(R.string.action_download) { _, _ -> viewModel.downloadTranslation(translation) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun showDownloadSitesDialog() {
        viewModel.readDownloadSites { sites ->
            val input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                setHorizontallyScrolling(false)
                minLines = 3
                setText(sites.joinToString("\n"))
            }
            AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_download_sites)
                .setMessage(R.string.dialog_message_download_sites)
                .setView(input)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    viewModel.saveDownloadSites(input.text.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct())
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    override fun onViewActionEmitted(viewAction: TranslationsViewModel.ViewAction) = when (viewAction) {
        TranslationsViewModel.ViewAction.GoBack -> navigator.goBack(this)
    }

    override fun onViewStateUpdated(viewState: TranslationsViewModel.ViewState): Unit = with(viewBinding) {
        if (viewState.loading) {
            swipeRefresher.isRefreshing = true
            translationList.isVisible = false
        } else {
            swipeRefresher.isRefreshing = false
            translationList.fadeIn()
        }

        adapter.submitList(viewState.items)

        viewState.translationDownloadingState.handle()
        viewState.translationRemovalState.handle()
        viewState.error?.handle()
    }

    private fun TranslationsViewModel.ViewState.TranslationDownloadingState.handle() {
        when (this) {
            is TranslationsViewModel.ViewState.TranslationDownloadingState.Idle -> {
                downloadTranslationDialog?.dismiss()
                downloadTranslationDialog = null
            }
            is TranslationsViewModel.ViewState.TranslationDownloadingState.Downloading -> {
                if (downloadTranslationDialog == null) {
                    downloadTranslationDialog = progressDialog(R.string.dialog_title_downloading, 100) { viewModel.cancelDownloadingTranslation() }
                }
                downloadTranslationDialog?.setProgress(progress)
            }
            is TranslationsViewModel.ViewState.TranslationDownloadingState.Installing -> {
                downloadTranslationDialog?.let { dialog ->
                    dialog.setTitle(R.string.dialog_title_installing)
                    dialog.setIsIndeterminate(true)
                }
            }
            is TranslationsViewModel.ViewState.TranslationDownloadingState.Completed -> {
                downloadTranslationDialog?.dismiss()
                downloadTranslationDialog = null

                if (successful) {
                    toast(R.string.toast_downloaded)
                }

                viewModel.markTranslationDownloadingStateAsIdle()
            }
        }
    }

    private fun TranslationsViewModel.ViewState.TranslationRemovalState.handle() {
        when (this) {
            is TranslationsViewModel.ViewState.TranslationRemovalState.Idle -> {
                removeTranslationDialog?.dismiss()
                removeTranslationDialog = null
            }
            is TranslationsViewModel.ViewState.TranslationRemovalState.Removing -> {
                if (removeTranslationDialog == null) {
                    removeTranslationDialog = indeterminateProgressDialog(R.string.dialog_title_deleting)
                }
            }
            is TranslationsViewModel.ViewState.TranslationRemovalState.Completed -> {
                removeTranslationDialog?.dismiss()
                removeTranslationDialog = null

                if (successful) {
                    toast(R.string.toast_deleted)
                }

                viewModel.markTranslationRemovalStateAsIdle()
            }
        }
    }

    private fun TranslationsViewModel.ViewState.Error.handle() = when (this) {
        is TranslationsViewModel.ViewState.Error.NoTranslationsError -> {
            dialog(
                cancelable = false,
                title = R.string.dialog_title_error,
                message = R.string.dialog_message_failed_to_load_translation_list,
                onPositive = { _, _ -> viewModel.loadTranslations(forceRefresh = true) },
                onNegative = { _, _ -> navigator.goBack(this@TranslationsActivity) },
                onDismiss = { viewModel.markErrorAsShown(this) },
            )
        }
        is TranslationsViewModel.ViewState.Error.TranslationAlreadyInstalledError,
        is TranslationsViewModel.ViewState.Error.TranslationNotInstalledError -> {
            toast(R.string.toast_unknown_error)
            viewModel.markErrorAsShown(this)
        }
        is TranslationsViewModel.ViewState.Error.TranslationDownloadingError -> {
            dialog(
                cancelable = true,
                title = R.string.dialog_title_error,
                message = R.string.dialog_message_failed_to_download,
                onPositive = { _, _ -> viewModel.downloadTranslation(translationToDownload) },
                onDismiss = { viewModel.markErrorAsShown(this) },
            )
        }
        is TranslationsViewModel.ViewState.Error.TranslationRemovalError -> {
            dialog(
                cancelable = true,
                title = R.string.dialog_title_error,
                message = R.string.dialog_message_failed_to_delete,
                onPositive = { _, _ -> viewModel.removeTranslation(translationToRemove) },
                onDismiss = { viewModel.markErrorAsShown(this) },
            )
        }
        is TranslationsViewModel.ViewState.Error.TranslationSelectionError -> {
            dialog(
                cancelable = true,
                title = R.string.dialog_title_error,
                message = R.string.dialog_message_failed_to_select_translation,
                onPositive = { _, _ -> viewModel.selectTranslation(translationToSelect) },
                onDismiss = { viewModel.markErrorAsShown(this) },
            )
        }
    }
}
