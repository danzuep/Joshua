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

package me.xizzhu.android.joshua.core.repository

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import me.xizzhu.android.joshua.core.TranslationInfo
import me.xizzhu.android.joshua.core.provider.TimeProvider
import me.xizzhu.android.joshua.core.repository.local.LocalTranslationStorage
import me.xizzhu.android.joshua.core.repository.remote.RemoteTranslationInfo
import me.xizzhu.android.joshua.core.repository.remote.RemoteTranslationService
import me.xizzhu.android.joshua.core.repository.remote.DEFAULT_DOWNLOAD_SITE
import co.touchlab.kermit.Logger
import java.io.IOException

class TranslationRepository(
    private val localTranslationStorage: LocalTranslationStorage,
    private val remoteTranslationService: RemoteTranslationService,
    private val timeProvider: TimeProvider,
    appScope: CoroutineScope
) {
    companion object {
        private val TAG: String = TranslationRepository::class.java.simpleName

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val TRANSLATION_LIST_REFRESH_INTERVAL_IN_MILLIS = 7L * 24L * 3600L * 1000L // 7 day
    }

    private val translationsLock: Any = Any()
    private val _availableTranslations = MutableStateFlow<List<TranslationInfo>?>(null)
    val availableTranslations: Flow<List<TranslationInfo>> = _availableTranslations.filterNotNull()
    private val _downloadedTranslations = MutableStateFlow<List<TranslationInfo>?>(null)
    val downloadedTranslations: Flow<List<TranslationInfo>> = _downloadedTranslations.filterNotNull()

    init {
        appScope.launch {
            // Publish only once, after any bundled install, so observers never see a transient "no translations" state
            val local = try {
                readTranslationsFromLocal()
            } catch (e: Exception) {
                Logger.e(e) { "Failed to initialize translations" }
                emptyList()
            }

            val installed = if (local.none { it.downloaded }) {
                try {
                    installBundledTranslations()
                } catch (e: Exception) {
                    Logger.e(e) { "Failed to install bundled translations" }
                    false
                }
            } else {
                false
            }

            updateTranslations(if (installed) readTranslationsFromLocal() else local)
        }
    }

    private suspend fun installBundledTranslations(): Boolean {
        val bundled = remoteTranslationService.fetchBundledTranslations()
        if (bundled.isEmpty()) return false

        Logger.withTag(TAG).i { "Installing bundled translations - ${bundled.map { it.translationInfo.shortName }}" }
        bundled.forEach { translation ->
            localTranslationStorage.saveTranslation(
                translation.translationInfo.toTranslationInfo(true),
                translation.bookNames, translation.bookShortNames, translation.verses
            )
        }
        return true
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun updateTranslations(updatedTranslations: List<TranslationInfo>) {
        val (available, downloaded) = synchronized(translationsLock) {
            val available = mutableMapOf<String, TranslationInfo>()
            val downloaded = mutableMapOf<String, TranslationInfo>()
            updatedTranslations.forEach { t ->
                if (t.downloaded) {
                    downloaded[t.shortName] = t
                } else {
                    available[t.shortName] = t
                }
            }
            return@synchronized Pair(available, downloaded)
        }
        notifyTranslationsUpdated(available.values.toList(), downloaded.values.toList())
    }

    private fun notifyTranslationsUpdated(available: List<TranslationInfo>, downloaded: List<TranslationInfo>) {
        _availableTranslations.value = available
        _downloadedTranslations.value = downloaded
    }

    suspend fun reload(forceRefresh: Boolean) {
        val translations = if (forceRefresh || translationListTooOld()) {
            try {
                readTranslationsFromBackend()
            } catch (e: Exception) {
                Logger.e(e) { "Failed to read translation list from backend" }
                readTranslationsFromLocal()
            }
        } else {
            readTranslationsFromLocal().ifEmpty {
                Logger.w { "Have fresh but empty local translation list" }
                readTranslationsFromBackend()
            }
        }
        updateTranslations(translations)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun translationListTooOld(): Boolean =
        timeProvider.currentTimeMillis - localTranslationStorage.readTranslationListRefreshTimestamp() >= TRANSLATION_LIST_REFRESH_INTERVAL_IN_MILLIS

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun readTranslationsFromBackend(): List<TranslationInfo> {
        Logger.withTag(TAG).i { "Start fetching translation list" }
        val fetchedTranslations = remoteTranslationService.fetchTranslations(readDownloadSites())
        if (fetchedTranslations.isEmpty()) throw IOException("No translations found on the download sites")
        Logger.withTag(TAG).i { "Translation list downloaded" }
        val localTranslations = readTranslationsFromLocal()

        val translations = ArrayList<TranslationInfo>(fetchedTranslations.size)
        for (fetched in fetchedTranslations) {
            var downloaded = false
            for (local in localTranslations) {
                if (fetched.shortName == local.shortName) {
                    downloaded = local.downloaded
                    break
                }
            }
            translations.add(fetched.toTranslationInfo(downloaded))
        }

        // keeps the translations that are installed but not listed (any more) on the download sites
        localTranslations.filter { local -> local.downloaded && translations.none { it.shortName == local.shortName } }
            .let { translations.addAll(it) }

        localTranslationStorage.replaceTranslations(translations)

        try {
            if (translations.isNotEmpty()) {
                localTranslationStorage.saveTranslationListRefreshTimestamp(timeProvider.currentTimeMillis)
            }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to save translation list refresh timestamp" }
        }

        return translations
    }

    suspend fun readDownloadSites(): List<String> =
        localTranslationStorage.readDownloadSites().ifEmpty { listOf(DEFAULT_DOWNLOAD_SITE) }

    suspend fun saveDownloadSites(sites: List<String>) = localTranslationStorage.saveDownloadSites(sites)

    suspend fun readTranslationsFromLocal(): List<TranslationInfo> = localTranslationStorage.readTranslations()

    /**
     * Emitting 1-99 when downloading, 100 when installing, and 101 when finished..
     */
    fun downloadTranslation(translationToDownload: TranslationInfo): Flow<Int> = channelFlow {
        val downloadProgressChannel = Channel<Int>(Channel.CONFLATED)
        launch { downloadProgressChannel.consumeEach { trySend(it) } }

        downloadTranslation(downloadProgressChannel, translationToDownload)
        downloadProgressChannel.close()

        val (available, downloaded) = synchronized(translationsLock) {
            val available = mutableListOf<TranslationInfo>().apply {
                _availableTranslations.value?.let { addAll(it) }
                removeAll { it.shortName == translationToDownload.shortName }
            }

            val downloaded = mutableListOf<TranslationInfo>().apply {
                _downloadedTranslations.value?.let { addAll(it) }
                removeAll { it.shortName == translationToDownload.shortName }
                add(translationToDownload.copy(downloaded = true))
            }

            return@synchronized Pair(available, downloaded)
        }
        notifyTranslationsUpdated(available, downloaded)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun downloadTranslation(downloadProgressChannel: SendChannel<Int>, translationInfo: TranslationInfo) {
        Logger.withTag(TAG).i { "Start downloading translation - ${translationInfo.shortName}" }
        val toDownload = RemoteTranslationInfo.fromTranslationInfo(translationInfo)
        val translation = remoteTranslationService.fetchTranslation(downloadProgressChannel, toDownload, readDownloadSites())
        Logger.withTag(TAG).i { "Translation downloaded" }
        downloadProgressChannel.trySend(100)

        localTranslationStorage.saveTranslation(
            translation.translationInfo.toTranslationInfo(true),
            translation.bookNames, translation.bookShortNames, translation.verses
        )
        remoteTranslationService.removeTranslationCache(toDownload)
        Logger.withTag(TAG).i { "Translation saved to database" }
        downloadProgressChannel.trySend(101)
    }

    suspend fun removeTranslation(translationInfo: TranslationInfo) {
        Logger.withTag(TAG).i { "Start removing translation - ${translationInfo.shortName}" }
        localTranslationStorage.removeTranslation(translationInfo)
        remoteTranslationService.removeTranslationCache(RemoteTranslationInfo.fromTranslationInfo(translationInfo))
        Logger.withTag(TAG).i { "Translation removed" }

        val (available, downloaded) = synchronized(translationsLock) {
            var removed = false
            val downloaded = mutableListOf<TranslationInfo>().apply {
                _downloadedTranslations.value?.let { addAll(it) }
                removed = removeAll { it.shortName == translationInfo.shortName }
            }

            val available = mutableListOf<TranslationInfo>().apply {
                _availableTranslations.value?.let { addAll(it) }
                if (removed) {
                    add(translationInfo.copy(downloaded = false))
                }
            }

            return@synchronized Pair(available, downloaded)
        }
        notifyTranslationsUpdated(available, downloaded)
    }
}
