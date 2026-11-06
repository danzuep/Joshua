package org.ebible.android.bible.core.repository.local.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.ebible.android.bible.core.repository.remote.RemoteTranslationInfo
import org.ebible.android.bible.core.repository.remote.android.HttpTranslationService
import org.ebible.android.bible.core.repository.local.android.db.AndroidDatabase
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Not a real test: the release workflow runs it with `BUNDLE_DIR` set to a directory holding `list.tsv`
 * (id, name and language separated by tabs) and `translations/<id>.zip` (USFM). It parses the zips once and writes
 * `bundled/bundled.db` (see [AndroidTranslationStorage.PREBUILT_ASSET]) there, so the app does not parse them on first launch.
 */
@RunWith(RobolectricTestRunner::class)
class BundledDatabaseGenerator : BaseSqliteTest() {
    @Test
    fun generate() = runTest {
        val dir = System.getenv("BUNDLE_DIR")
        assumeTrue("BUNDLE_DIR is not set", dir != null)
        val bundleDir = File(dir!!)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = HttpTranslationService(context)
        val storage = AndroidTranslationStorage(androidDatabase)

        File(bundleDir, "list.tsv").readLines().map { it.split('\t') }.filter { it.size >= 3 }.forEach { (shortName, name, language) ->
            val info = RemoteTranslationInfo(shortName.trim(), name.trim(), language.trim(), 0L)
            val translation = File(bundleDir, "translations/${info.shortName}.zip").inputStream().use { service.toRemoteTranslation(info, it) }
            storage.saveTranslation(translation.translationInfo.toTranslationInfo(true),
                    translation.bookNames, translation.bookShortNames, translation.verses)
        }

        androidDatabase.close()
        val output = File(bundleDir, AndroidTranslationStorage.PREBUILT_ASSET).apply { parentFile!!.mkdirs() }
        context.getDatabasePath(AndroidDatabase.DATABASE_NAME).copyTo(output, overwrite = true)
        assertTrue(output.length() > 0)
    }
}
