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

package me.xizzhu.android.joshua.core.repository.remote.android

import android.content.Context
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.withContext
import me.xizzhu.android.joshua.core.repository.remote.RemoteTranslation
import me.xizzhu.android.joshua.core.repository.remote.DEFAULT_DOWNLOAD_SITE
import me.xizzhu.android.joshua.core.repository.remote.RemoteTranslationInfo
import me.xizzhu.android.joshua.core.repository.remote.parseTranslationTsv
import me.xizzhu.android.joshua.core.repository.remote.RemoteTranslationService
import me.xizzhu.android.joshua.core.repository.remote.parseTranslationTable
import me.xizzhu.android.joshua.core.repository.remote.readUsfmZip
import me.xizzhu.android.joshua.core.repository.remote.siteOrigin
import me.xizzhu.android.joshua.core.repository.remote.toRemoteTranslation as buildRemoteTranslation
import me.xizzhu.android.joshua.core.repository.remote.toRemoteTranslationInfo
import me.xizzhu.android.joshua.core.repository.remote.usfmZipUrl
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

private const val TAG = "HttpTranslationService"

class HttpTranslationService(private val context: Context) : RemoteTranslationService {
    private val cacheDir: File by lazy { File(context.cacheDir, "translations").apply { mkdirs() } }

    override suspend fun fetchTranslations(sites: List<String>): List<RemoteTranslationInfo> = withContext(Dispatchers.IO) {
        sites.flatMap { site ->
            try {
                readTable(site).map { it.toRemoteTranslationInfo() }
            } catch (e: Exception) {
                Logger.withTag(TAG).w(e) { "Failed to read translations from $site" }
                emptyList()
            }
        }.distinctBy { it.shortName }
    }

    // The default site is read from the bundled copy of its table, other sites are scraped.
    private fun readTable(site: String) = if (site == DEFAULT_DOWNLOAD_SITE) {
        parseTranslationTsv(context.assets.open("ebible_downloads.tsv").use { it.readBytes().toString(Charsets.UTF_8) }, site)
    } else {
        parseTranslationTable(fetchText(site), site)
    }

    override suspend fun fetchTranslation(
            downloadProgress: SendChannel<Int>, translationInfo: RemoteTranslationInfo, sites: List<String>
    ): RemoteTranslation = withContext(Dispatchers.IO) {
        val destination = File(cacheDir, translationInfo.shortName)
        var lastError: Exception? = null
        for (origin in sites.mapNotNull { siteOrigin(it) }.distinct()) {
            try {
                download(downloadProgress, usfmZipUrl(origin, translationInfo.shortName), translationInfo.size, destination)
                return@withContext FileInputStream(destination).use { toRemoteTranslation(translationInfo, it) }
            } catch (e: Exception) {
                Logger.withTag(TAG).w(e) { "Failed to download ${translationInfo.shortName} from $origin" }
                lastError = e
            }
        }
        throw lastError ?: IOException("No download site is available for ${translationInfo.shortName}")
    }

    internal fun toRemoteTranslation(translationInfo: RemoteTranslationInfo, inputStream: InputStream): RemoteTranslation =
            buildRemoteTranslation(translationInfo, readUsfmZip(inputStream))

    /** Reads `bundled/list.tsv` (`id<TAB>name<TAB>language` per line) and the matching `bundled/translations/<id>.zip` USFM zips. */
    override suspend fun fetchBundledTranslations(): List<RemoteTranslation> = withContext(Dispatchers.IO) {
        val list = try {
            context.assets.open("bundled/list.tsv").use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (_: FileNotFoundException) {
            return@withContext emptyList()
        }
        list.lines().map { it.split('\t') }.filter { it.size >= 3 }.map { (shortName, name, language) ->
            val info = RemoteTranslationInfo(shortName.trim(), name.trim(), language.trim(), 0L)
            context.assets.open("bundled/translations/${info.shortName}.zip").use { toRemoteTranslation(info, it) }
        }
    }

    override suspend fun removeTranslationCache(translationInfo: RemoteTranslationInfo): Unit = withContext(Dispatchers.IO) {
        File(cacheDir, translationInfo.shortName).delete()
    }
}
