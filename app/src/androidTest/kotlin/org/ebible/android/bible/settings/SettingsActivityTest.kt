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

package org.ebible.android.bible.settings

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import io.mockk.Ordering
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import javax.inject.Singleton
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import org.ebible.android.bible.R
import org.ebible.android.bible.core.Highlight
import org.ebible.android.bible.NavigationModule
import org.ebible.android.bible.Navigator
import org.ebible.android.bible.databinding.ActivitySettingsBinding
import org.ebible.android.bible.databinding.InnerSettingButtonBinding
import org.ebible.android.bible.settings.widgets.SettingButton
import org.ebible.android.bible.tests.BaseActivityTest
import org.ebible.android.bible.tests.getProperty
import org.ebible.android.bible.tests.performClickPressed
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.intent.rule.IntentsRule
import org.ebible.android.bible.tests.lastStartedIntent
import org.ebible.android.bible.tests.stubAllIntents
import org.junit.Rule

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
@UninstallModules(NavigationModule::class)
class SettingsActivityTest : BaseActivityTest() {
    @Module
    @InstallIn(SingletonComponent::class)
    object MockNavigationModule {
        val mockNavigator: Navigator = mockk()

        @Provides
        @Singleton
        fun provideNavigator(): Navigator = mockNavigator
    }

    @BindValue
    val viewModel: SettingsViewModel = mockk(relaxed = true)

    @get:Rule
    val intentsRule = IntentsRule()

    @BeforeTest
    override fun setup() {
        super.setup()
        stubAllIntents()
        clearMocks(MockNavigationModule.mockNavigator)
    }

    @Test
    fun test_initializeView() {
        withActivity<SettingsActivity> { activity ->
            val viewBinding: ActivitySettingsBinding = activity.getProperty("viewBinding")
            viewBinding.keepScreenOn.performClickPressed()
            viewBinding.hideSearchButton.performClickPressed()
            viewBinding.fontSize.performClick()
            viewBinding.nightMode.performClick()
            viewBinding.defaultHighlightColor.performClick()
            viewBinding.backup.performClick()
            viewBinding.restore.performClick()
            viewBinding.rate.performClick()
            viewBinding.website.performClick()

            verify(ordering = Ordering.ORDERED) {
                viewModel.saveKeepScreenOn(true)
                viewModel.saveHideSearchButton(true)
                viewModel.selectFontSizeScale()
                viewModel.selectNightMode()
                viewModel.selectHighlightColor()
                viewModel.backup()
                viewModel.restore()
                viewModel.openRateMe()
                viewModel.openWebsite()
            }
        }
    }

    @Test
    fun test_onViewActionEmitted_OpenRateMe() {
        every { viewModel.viewAction() } returns flowOf(SettingsViewModel.ViewAction.OpenRateMe)
        every { MockNavigationModule.mockNavigator.navigate(any(), Navigator.SCREEN_RATE_ME) } returns Unit

        withActivity<SettingsActivity> { activity ->
            verify(exactly = 1) { MockNavigationModule.mockNavigator.navigate(activity, Navigator.SCREEN_RATE_ME) }
        }
    }

    @Test
    fun test_onViewActionEmitted_OpenWebsite() {
        every { viewModel.viewAction() } returns flowOf(SettingsViewModel.ViewAction.OpenWebsite)
        every { MockNavigationModule.mockNavigator.navigate(any(), Navigator.SCREEN_WEBSITE) } returns Unit

        withActivity<SettingsActivity> { activity ->
            verify(exactly = 1) { MockNavigationModule.mockNavigator.navigate(activity, Navigator.SCREEN_WEBSITE) }
        }
    }

    @Test
    fun test_onViewActionEmitted_RequestUriForBackup() {
        every { viewModel.viewAction() } returns flowOf(SettingsViewModel.ViewAction.RequestUriForBackup)

        withActivity<SettingsActivity> {
            with(lastStartedIntent()) {
                assertEquals(Intent.ACTION_CREATE_DOCUMENT, action)
                assertEquals("application/json", type)
                assertEquals(setOf(Intent.CATEGORY_OPENABLE), categories)
            }
        }
    }

    @Test
    fun test_onViewActionEmitted_RequestUriForRestore() {
        every { viewModel.viewAction() } returns flowOf(SettingsViewModel.ViewAction.RequestUriForRestore)

        withActivity<SettingsActivity> {
            with(lastStartedIntent()) {
                assertEquals(Intent.ACTION_CHOOSER, action)

                val intent = getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                assertEquals(Intent.ACTION_GET_CONTENT, intent?.action)
                assertEquals("*/*", intent?.type)
                assertEquals(setOf(Intent.CATEGORY_OPENABLE), intent?.categories)
            }
        }
    }

    @Test
    fun test_onViewStateUpdated_with_completed_backup_state_null_font_size_selection_null_night_mode_selection_null_highlight_color_selection_and_null_error() {
        every { viewModel.viewState() } returns flowOf(SettingsViewModel.ViewState(
            fontSizeScale = 1.0F,
            bodyTextSizePx = 2,
            captionTextSizePx = 3,
            keepScreenOn = true,
            nightModeStringRes = R.string.settings_text_night_mode_off,
            simpleReadingModeOn = true,
            hideSearchButton = true,
            defaultHighlightColor = Highlight.COLOR_NONE,
            backupState = SettingsViewModel.ViewState.BackupRestoreState.Completed(successful = true),
            restoreState = SettingsViewModel.ViewState.BackupRestoreState.Idle,
            version = "app_version",
            fontSizeScaleSelection = null,
            nightModeSelection = null,
            highlightColorSelection = null,
            error = null,
        ))

        withActivity<SettingsActivity> { activity ->
            val viewBinding: ActivitySettingsBinding = activity.getProperty("viewBinding")
            assertEquals("1.0x", viewBinding.fontSize.getProperty<SettingButton, InnerSettingButtonBinding>("viewBinding").description.text)
            assertTrue(viewBinding.keepScreenOn.isChecked)
            assertTrue(viewBinding.hideSearchButton.isChecked)
            assertEquals(activity.getString(R.string.settings_text_night_mode_off), viewBinding.nightMode.getProperty<SettingButton, InnerSettingButtonBinding>("viewBinding").description.text)
            assertEquals(activity.getString(R.string.text_highlight_color_none), viewBinding.defaultHighlightColor.getProperty<SettingButton, InnerSettingButtonBinding>("viewBinding").description.text)
            assertEquals("app_version", viewBinding.version.getProperty<SettingButton, InnerSettingButtonBinding>("viewBinding").description.text)
            verify(exactly = 1) { viewModel.markBackupStateAsIdle() }
        }
    }

    @Test
    fun test_onViewStateUpdated_with_completed_restore_state_null_font_size_selection_null_night_mode_selection_null_highlight_color_selection_and_null_error() {
        every { viewModel.viewState() } returns flowOf(SettingsViewModel.ViewState(
            fontSizeScale = 1.0F,
            bodyTextSizePx = 2,
            captionTextSizePx = 3,
            keepScreenOn = true,
            nightModeStringRes = R.string.settings_text_night_mode_off,
            simpleReadingModeOn = true,
            hideSearchButton = true,
            defaultHighlightColor = Highlight.COLOR_NONE,
            backupState = SettingsViewModel.ViewState.BackupRestoreState.Idle,
            restoreState = SettingsViewModel.ViewState.BackupRestoreState.Completed(successful = false),
            version = "app_version",
            fontSizeScaleSelection = null,
            nightModeSelection = null,
            highlightColorSelection = null,
            error = null,
        ))

        withActivity<SettingsActivity> { activity ->
            val viewBinding: ActivitySettingsBinding = activity.getProperty("viewBinding")
            assertEquals("1.0x", viewBinding.fontSize.getProperty<SettingButton, InnerSettingButtonBinding>("viewBinding").description.text)
            assertTrue(viewBinding.keepScreenOn.isChecked)
            assertTrue(viewBinding.hideSearchButton.isChecked)
            assertEquals(activity.getString(R.string.settings_text_night_mode_off), viewBinding.nightMode.getProperty<SettingButton, InnerSettingButtonBinding>("viewBinding").description.text)
            assertEquals(activity.getString(R.string.text_highlight_color_none), viewBinding.defaultHighlightColor.getProperty<SettingButton, InnerSettingButtonBinding>("viewBinding").description.text)
            assertEquals("app_version", viewBinding.version.getProperty<SettingButton, InnerSettingButtonBinding>("viewBinding").description.text)
            verify(exactly = 1) { viewModel.markRestoreStateAsIdle() }
        }
    }
}
