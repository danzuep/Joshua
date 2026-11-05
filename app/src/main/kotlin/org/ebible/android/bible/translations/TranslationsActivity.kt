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

package org.ebible.android.bible.translations

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.text.InputType
import android.view.MotionEvent
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.asExecutor
import org.ebible.android.bible.R
import org.ebible.android.bible.core.TranslationInfo
import org.ebible.android.bible.core.provider.CoroutineDispatcherProvider
import org.ebible.android.bible.databinding.ActivityTranslationsBinding
import org.ebible.android.bible.databinding.DialogUploadTranslationBinding
import org.ebible.android.bible.infra.BaseActivity
import org.ebible.android.bible.ui.ProgressDialog
import org.ebible.android.bible.ui.dialog
import org.ebible.android.bible.ui.fadeIn
import org.ebible.android.bible.ui.indeterminateProgressDialog
import org.ebible.android.bible.ui.progressDialog
import org.ebible.android.bible.ui.toast

@AndroidEntryPoint
class TranslationsActivity : BaseActivity<ActivityTranslationsBinding, TranslationsViewModel.ViewAction, TranslationsViewModel.ViewState, TranslationsViewModel>() {
    companion object {
        const val KEY_DOWNLOAD_TRANSLATION = "org.ebible.android.bible.KEY_DOWNLOAD_TRANSLATION"
    }

    @Inject
    lateinit var coroutineDispatcherProvider: CoroutineDispatcherProvider

    private lateinit var adapter: TranslationsAdapter

    private val selectFileForUploadLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) result.data?.data?.let { showUploadTranslationDialog(it) }
    }

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

        viewBinding.filterKeyword.setText(viewModel.keywordFilter)
        viewBinding.filterKeyword.doAfterTextChanged { text ->
            viewModel.applyFilter(text.toString())
        }

        with(viewBinding.toolbar) {
            inflateMenu(R.menu.menu_translations)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_download_sites -> {
                        showDownloadSitesDialog()
                        true
                    }
                    R.id.action_upload_translation -> {
                        selectFileForUploadLauncher.launch(Intent.createChooser(
                            Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),
                            getString(R.string.action_upload_translation)
                        ))
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
        // Only language headers are used for the index; the "Available" section header and native name sub headings are not languages.
        val headers = adapter.currentList.withIndex()
            .filter { (it.value as? TranslationsItem.Header)?.isLanguage == true }
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
                .setNeutralButton(R.string.action_copyright_permissions) { _, _ ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ebible.org/${translation.shortName}/copr.htm")))
                }
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

    private fun showUploadTranslationDialog(uri: Uri) {
        val form = DialogUploadTranslationBinding.inflate(layoutInflater)
        val fileName = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }.orEmpty().substringBeforeLast('.')
        form.uploadName.setText(fileName)
        form.uploadShortName.setText(fileName.replace(Regex("[^A-Za-z0-9]"), "_"))
        form.uploadLanguage.setText(java.util.Locale.getDefault().language)

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_title_upload_translation)
            .setView(form.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val progress = indeterminateProgressDialog(R.string.dialog_title_uploading)
                viewModel.uploadTranslation(
                    uri, form.uploadName.text.toString(), form.uploadShortName.text.toString().trim(), form.uploadLanguage.text.toString()
                ) { successful ->
                    progress?.dismiss()
                    if (successful) toast(R.string.toast_uploaded) else dialog(
                        cancelable = true, title = R.string.dialog_title_error, message = R.string.dialog_message_failed_to_upload, onPositive = { _, _ -> },
                    )
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
