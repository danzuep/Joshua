/*
 * Copyright (C) 2023 Xizhi Zhu
 * Copyright (C) 2026 Daniel Collingwood
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

package org.ebible.android.bible.core.repository.local.android

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.xizzhu.android.ask.db.transaction
import org.ebible.android.bible.core.TranslationInfo
import org.ebible.android.bible.core.repository.local.LocalTranslationStorage
import org.ebible.android.bible.core.repository.local.android.db.AndroidDatabase
import org.ebible.android.bible.core.repository.local.android.db.MetadataDao
import java.io.File
import java.io.FileNotFoundException

class AndroidTranslationStorage(private val androidDatabase: AndroidDatabase, private val context: Context? = null) : LocalTranslationStorage {
    companion object {
        /** A database built by the release workflow with the translations already parsed, see docs/RELEASING.md. */
        const val PREBUILT_ASSET = "bundled/bundled.db"
    }

    override suspend fun readTranslationListRefreshTimestamp(): Long = withContext(Dispatchers.IO) {
        androidDatabase.metadataDao.read(MetadataDao.KEY_TRANSLATION_LIST_REFRESH_TIMESTAMP, "0").toLong()
    }

    override suspend fun readDownloadSites(): List<String> = withContext(Dispatchers.IO) {
        androidDatabase.metadataDao.read(MetadataDao.KEY_DOWNLOAD_SITES, "").lines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    override suspend fun saveDownloadSites(sites: List<String>) {
        withContext(Dispatchers.IO) { androidDatabase.metadataDao.save(MetadataDao.KEY_DOWNLOAD_SITES, sites.joinToString("\n")) }
    }

    override suspend fun saveTranslationListRefreshTimestamp(timestamp: Long) {
        withContext(Dispatchers.IO) { androidDatabase.metadataDao.save(MetadataDao.KEY_TRANSLATION_LIST_REFRESH_TIMESTAMP, timestamp.toString()) }
    }

    override suspend fun readTranslations(): List<TranslationInfo> = withContext(Dispatchers.IO) {
        androidDatabase.translationInfoDao.read()
    }

    override suspend fun replaceTranslations(translations: List<TranslationInfo>) {
        withContext(Dispatchers.IO) { androidDatabase.translationInfoDao.replace(translations) }
    }

    override suspend fun saveTranslation(translationInfo: TranslationInfo,
                                         bookNames: List<String>,
                                         bookShortNames: List<String>,
                                         verses: Map<Pair<Int, Int>, List<String>>) {
        withContext(Dispatchers.IO) {
            androidDatabase.writableDatabase.transaction {
                androidDatabase.bookNamesDao.save(translationInfo.shortName, bookNames, bookShortNames)
                if (translationInfo.downloaded) {
                    androidDatabase.translationInfoDao.save(translationInfo)
                } else {
                    androidDatabase.translationInfoDao.save(translationInfo.copy(downloaded = true))
                }
                androidDatabase.translationDao.save(translationInfo.shortName, verses)
            }
        }
    }

    override suspend fun removeTranslation(translationInfo: TranslationInfo) {
        withContext(Dispatchers.IO) {
            androidDatabase.writableDatabase.transaction {
                androidDatabase.bookNamesDao.remove(translationInfo.shortName)
                if (translationInfo.downloaded) {
                    androidDatabase.translationInfoDao.save(translationInfo.copy(downloaded = false))
                } else {
                    androidDatabase.translationInfoDao.save(translationInfo)
                }
                androidDatabase.translationDao.remove(translationInfo.shortName)
            }
        }
    }

    override suspend fun installPrebuiltTranslations(): Boolean = withContext(Dispatchers.IO) {
        val context = context ?: return@withContext false
        val file = File(context.cacheDir, "prebuilt-translations.db")
        try {
            try {
                context.assets.open(PREBUILT_ASSET).use { input -> file.outputStream().use { input.copyTo(it) } }
            } catch (_: FileNotFoundException) {
                return@withContext false
            }
            installPrebuiltTranslations(file)
        } finally {
            file.delete()
        }
    }

    /** Copies the book names, translation list entries and verse tables of the database at [file] into the app database. */
    internal fun installPrebuiltTranslations(file: File): Boolean {
        val db = androidDatabase.writableDatabase
        // ATTACH is not allowed inside a transaction.
        db.execSQL("ATTACH DATABASE ? AS prebuilt", arrayOf(file.absolutePath))
        try {
            db.beginTransaction()
            try {
                val tables = db.rawQuery("SELECT shortName FROM prebuilt.translationInfo", null).use { cursor ->
                    generateSequence { if (cursor.moveToNext()) cursor.getString(0) else null }.toList()
                }
                if (tables.isEmpty()) return false

                db.execSQL("INSERT OR REPLACE INTO main.bookNames SELECT * FROM prebuilt.bookNames")
                db.execSQL("INSERT OR REPLACE INTO main.translationInfo SELECT * FROM prebuilt.translationInfo")
                tables.forEach { table ->
                    val name = table.replace("\"", "\"\"")
                    val createSql = db.rawQuery("SELECT sql FROM prebuilt.sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use {
                        if (it.moveToFirst()) it.getString(0) else null
                    } ?: return false
                    db.execSQL("DROP TABLE IF EXISTS main.\"$name\"")
                    db.execSQL(createSql)
                    db.execSQL("INSERT INTO main.\"$name\" SELECT * FROM prebuilt.\"$name\"")
                }
                db.setTransactionSuccessful()
                return true
            } finally {
                db.endTransaction()
            }
        } finally {
            db.execSQL("DETACH DATABASE prebuilt")
        }
    }
}
