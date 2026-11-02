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

import android.app.Application
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.xizzhu.android.joshua.R
import me.xizzhu.android.joshua.core.BibleReadingManager
import me.xizzhu.android.joshua.core.SettingsManager
import me.xizzhu.android.joshua.core.TranslationInfo
import me.xizzhu.android.joshua.core.TranslationManager
import me.xizzhu.android.joshua.core.containsAnyKeyword
import me.xizzhu.android.joshua.core.repository.remote.parseTranslationTsv
import me.xizzhu.android.joshua.core.toKeywords
import me.xizzhu.android.joshua.ui.TranslationInfoComparator
import me.xizzhu.android.joshua.ui.languageDisplayName
import me.xizzhu.android.joshua.ui.languageDisplayNameWithNative
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import me.xizzhu.android.joshua.core.Settings
import me.xizzhu.android.joshua.core.provider.CoroutineDispatcherProvider
import me.xizzhu.android.joshua.infra.BaseViewModel
import co.touchlab.kermit.Logger

@HiltViewModel
class TranslationsViewModel @Inject constructor(
    private val bibleReadingManager: BibleReadingManager,
    private val translationManager: TranslationManager,
    private val settingsManager: SettingsManager,
    private val application: Application,
    private val coroutineDispatcherProvider: CoroutineDispatcherProvider,
) : BaseViewModel<TranslationsViewModel.ViewAction, TranslationsViewModel.ViewState>(
    initialViewState = ViewState(
        loading = true,
        items = emptyList(),
        translationDownloadingState = ViewState.TranslationDownloadingState.Idle,
        translationRemovalState = ViewState.TranslationRemovalState.Idle,
        error = null,
    )
) {
    sealed class ViewAction {
        object GoBack : ViewAction()
    }

    data class ViewState(
        val loading: Boolean,
        val items: List<TranslationsItem>,
        val translationDownloadingState: TranslationDownloadingState,
        val translationRemovalState: TranslationRemovalState,
        val error: Error?,
    ) {
        sealed class TranslationDownloadingState {
            object Idle : TranslationDownloadingState()
            data class Downloading(val progress: Int) : TranslationDownloadingState()
            object Installing : TranslationDownloadingState()
            data class Completed(val successful: Boolean) : TranslationDownloadingState()
        }

        sealed class TranslationRemovalState {
            object Idle : TranslationRemovalState()
            object Removing : TranslationRemovalState()
            data class Completed(val successful: Boolean) : TranslationRemovalState()
        }

        sealed class Error {
            object NoTranslationsError : Error()
            object TranslationAlreadyInstalledError : Error()
            data class TranslationDownloadingError(val translationToDownload: TranslationInfo) : Error()
            object TranslationNotInstalledError : Error()
            data class TranslationRemovalError(val translationToRemove: TranslationInfo) : Error()
            data class TranslationSelectionError(val translationToSelect: TranslationInfo) : Error()
        }
    }

    private val translationComparator = TranslationInfoComparator(TranslationInfoComparator.SORT_ORDER_LANGUAGE_THEN_NAME)
    private var downloadTranslationJob: Job? = null

    private var loadedDownloadedTranslations: List<TranslationInfo> = emptyList()
    private var loadedAvailableTranslations: List<TranslationInfo> = emptyList()
    var languageFilter: String? = null
        private set
    var keywordFilter: String = ""
        private set
    private var pendingDownloadShortName: String? = null

    init {
        loadTranslations(forceRefresh = false)
    }

    fun loadTranslations(forceRefresh: Boolean) {
        viewModelScope.launch(coroutineDispatcherProvider.default) {
            updateViewState { it.copy(loading = true, items = emptyList()) }

            translationManager.reload(forceRefresh)

            // After the refresh, if no change is detected, nothing will be emitted, therefore we need to manually load here.
            val availableTranslations = translationManager.availableTranslations().first().sortedWith(translationComparator)
            val downloadedTranslations = translationManager.downloadedTranslations().first().sortedWith(translationComparator)
            if (availableTranslations.isEmpty() && downloadedTranslations.isEmpty()) {
                updateViewState { it.copy(loading = false, error = ViewState.Error.NoTranslationsError) }
                return@launch
            }

            loadedDownloadedTranslations = downloadedTranslations
            loadedAvailableTranslations = availableTranslations
            val settings = settingsManager.settings().first()
            val currentTranslation = bibleReadingManager.currentTranslation().first()
            updateViewState { it.copy(loading = false, items = buildItems(settings, currentTranslation)) }
            tryPendingDownload()
        }
    }

    private fun buildItems(settings: Settings, currentTranslation: String): List<TranslationsItem> {
        val items: ArrayList<TranslationsItem> = ArrayList()
        val downloaded = loadedDownloadedTranslations.filter { it.matchesFilter() }
        val available = loadedAvailableTranslations.filter { it.matchesFilter() }
        items.addAll(downloaded.toItems(settings, currentTranslation))
        if (available.isNotEmpty()) {
            items.add(TranslationsItem.Header(settings, application.getString(R.string.header_available_translations), hideDivider = false))
            items.addAll(available.toItems(settings, currentTranslation))
        }
        return items
    }

    private fun TranslationInfo.matchesFilter(): Boolean {
        val languageCode = language.split("_")[0]
        if (languageFilter != null && languageCode != languageFilter) return false
        val keywords = keywordFilter.toKeywords()
        if (keywords.isEmpty()) return true
        return "$shortName $name $language ${languageDisplayNameWithNative(languageCode)}".containsAnyKeyword(keywords, wholeWords = false)
    }

    /** Languages of all known translations, sorted by display name, for the language filter. */
    fun availableLanguages(): List<String> = (loadedDownloadedTranslations + loadedAvailableTranslations)
        .map { it.language.split("_")[0] }
        .distinct()
        .sortedBy { languageDisplayName(it) }

    fun applyFilter(language: String?, keyword: String) {
        languageFilter = language
        keywordFilter = keyword.trim()
        viewModelScope.launch(coroutineDispatcherProvider.default) {
            val settings = settingsManager.settings().first()
            val currentTranslation = bibleReadingManager.currentTranslation().first()
            updateViewState { it.copy(items = buildItems(settings, currentTranslation)) }
        }
    }

    /** Reads every row of the bundled translation table for [translation], as (label, value) pairs. */
    fun readTranslationDetails(translation: TranslationInfo, onLoaded: (List<Pair<String, String>>) -> Unit) {
        viewModelScope.launch {
            val details = runCatching {
                withContext(coroutineDispatcherProvider.default) {
                    val tsv = application.assets.open("ebible_downloads.tsv").use { it.readBytes().toString(Charsets.UTF_8) }
                    parseTranslationTsv(tsv, "https://ebible.org/")
                        .firstOrNull { it.id == translation.shortName }
                        ?.let { row ->
                            listOf(
                                application.getString(R.string.translation_detail_id) to row.id,
                                application.getString(R.string.translation_detail_territory) to row.territory,
                                application.getString(R.string.translation_detail_language) to row.language,
                                application.getString(R.string.translation_detail_language_english) to row.languageEnglish,
                                application.getString(R.string.translation_detail_vernacular_title) to row.vernacularTitle,
                                application.getString(R.string.translation_detail_english_title) to row.englishTitle,
                            )
                        }
                }
            }.onFailure { e -> Logger.withTag(tag).e(e) { "Failed to read translation details" } }
                .getOrNull()
            onLoaded(details ?: listOf(application.getString(R.string.translation_detail_id) to translation.shortName))
        }
    }

    /** Downloads the translation with [shortName] once the translation list is known. Used by one-click downloads. */
    fun downloadTranslation(shortName: String) {
        pendingDownloadShortName = shortName
        tryPendingDownload()
    }

    private fun tryPendingDownload() {
        val shortName = pendingDownloadShortName ?: return
        val translation = (loadedDownloadedTranslations + loadedAvailableTranslations).firstOrNull { it.shortName == shortName } ?: return
        pendingDownloadShortName = null
        if (!translation.downloaded && downloadTranslationJob == null) downloadTranslation(translation)
    }

    private fun List<TranslationInfo>.toItems(settings: Settings, currentTranslation: String): List<TranslationsItem> {
        val items: ArrayList<TranslationsItem> = ArrayList()
        var currentLanguage = ""
        forEach { translationInfo ->
            val language = translationInfo.language.split("_")[0]
            if (currentLanguage != language) {
                items.add(TranslationsItem.Header(settings, languageDisplayNameWithNative(language), hideDivider = true))
                currentLanguage = language
            }
            items.add(TranslationsItem.Translation(
                settings = settings,
                translationInfo = translationInfo,
                isCurrentTranslation = translationInfo.downloaded && translationInfo.shortName == currentTranslation
            ))
        }
        return items
    }

    /** Reads the download sites and passes them to [onLoaded] on the main thread. */
    fun readDownloadSites(onLoaded: (List<String>) -> Unit) {
        viewModelScope.launch {
            val sites = runCatching { withContext(coroutineDispatcherProvider.default) { translationManager.readDownloadSites() } }
                .onFailure { e -> Logger.withTag(tag).e(e) { "Failed to read download sites" } }
                .getOrDefault(emptyList())
            onLoaded(sites)
        }
    }

    fun saveDownloadSites(sites: List<String>) {
        viewModelScope.launch {
            runCatching { withContext(coroutineDispatcherProvider.default) { translationManager.saveDownloadSites(sites) } }
                .onFailure { e -> Logger.withTag(tag).e(e) { "Failed to save download sites" } }
            loadTranslations(forceRefresh = true)
        }
    }

    fun selectTranslation(translationToSelect: TranslationInfo) {
        if (!translationToSelect.downloaded) {
            updateViewState { it.copy(error = ViewState.Error.TranslationNotInstalledError) }
            return
        }

        viewModelScope.launch {
            runCatching {
                bibleReadingManager.saveCurrentTranslation(translationToSelect.shortName)
                emitViewAction(ViewAction.GoBack)
            }.onFailure { e ->
                Logger.withTag(tag).e(e) { "Failed to select translation" }
                updateViewState { it.copy(error = ViewState.Error.TranslationSelectionError(translationToSelect)) }
            }
        }
    }

    fun downloadTranslation(translationToDownload: TranslationInfo) {
        if (translationToDownload.downloaded) {
            updateViewState { it.copy(error = ViewState.Error.TranslationAlreadyInstalledError) }
            return
        }

        updateViewState { it.copy(translationDownloadingState = ViewState.TranslationDownloadingState.Downloading(progress = 0)) }

        downloadTranslationJob = translationManager.downloadTranslation(translationToDownload)
            .onEach { progress ->
                when (progress) {
                    in 0..99 -> updateViewState { it.copy(translationDownloadingState = ViewState.TranslationDownloadingState.Downloading(progress = progress)) }
                    100 -> updateViewState { it.copy(translationDownloadingState = ViewState.TranslationDownloadingState.Installing) }
                    101 -> {
                        viewModelScope.launch {
                            runCatching { bibleReadingManager.saveCurrentTranslation(translationToDownload.shortName) }
                                .onFailure { e -> Logger.withTag(tag).e(e) { "Failed to select downloaded translation" } }
                            loadTranslations(forceRefresh = false)
                        }
                        updateViewState { it.copy(translationDownloadingState = ViewState.TranslationDownloadingState.Completed(successful = true)) }
                    }
                    else -> throw IllegalArgumentException("Unsupported downloading progress: $progress")
                }
            }
            .catch { e ->
                Logger.withTag(tag).e(e) { "Failed to download translation" }
                updateViewState { current ->
                    current.copy(
                        translationDownloadingState = ViewState.TranslationDownloadingState.Completed(successful = false),
                        error = ViewState.Error.TranslationDownloadingError(translationToDownload),
                    )
                }
            }
            .onCompletion { e ->
                if (e is CancellationException) {
                    Logger.withTag(tag).d { "Translation downloading cancelled" }
                    updateViewState { it.copy(translationDownloadingState = ViewState.TranslationDownloadingState.Idle) }
                }
                downloadTranslationJob = null
            }
            .launchIn(viewModelScope)
    }

    fun cancelDownloadingTranslation() {
        downloadTranslationJob?.cancel()
        downloadTranslationJob = null
    }

    fun removeTranslation(translationToRemove: TranslationInfo) {
        if (!translationToRemove.downloaded) {
            updateViewState { it.copy(error = ViewState.Error.TranslationNotInstalledError) }
            return
        }

        updateViewState { it.copy(translationRemovalState = ViewState.TranslationRemovalState.Removing) }

        viewModelScope.launch {
            runCatching {
                translationManager.removeTranslation(translationToRemove)
                loadTranslations(forceRefresh = false)
                updateViewState { it.copy(translationRemovalState = ViewState.TranslationRemovalState.Completed(successful = true)) }
            }.onFailure { e ->
                Logger.withTag(tag).e(e) { "Failed to remove translation" }
                updateViewState { current ->
                    current.copy(
                        translationRemovalState = ViewState.TranslationRemovalState.Completed(successful = false),
                        error = ViewState.Error.TranslationRemovalError(translationToRemove),
                    )
                }
            }
        }
    }

    fun markTranslationDownloadingStateAsIdle() {
        updateViewState { it.copy(translationDownloadingState = ViewState.TranslationDownloadingState.Idle) }
    }

    fun markTranslationRemovalStateAsIdle() {
        updateViewState { it.copy(translationRemovalState = ViewState.TranslationRemovalState.Idle) }
    }

    fun markErrorAsShown(error: ViewState.Error) {
        updateViewState { current -> if (current.error == error) current.copy(error = null) else null }
    }
}
