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

package me.xizzhu.android.joshua.reading

import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.view.ActionMode
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import co.touchlab.kermit.Logger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.xizzhu.android.joshua.Navigator
import me.xizzhu.android.joshua.R
import me.xizzhu.android.joshua.core.Highlight
import me.xizzhu.android.joshua.core.Settings
import me.xizzhu.android.joshua.core.Verse
import me.xizzhu.android.joshua.core.VerseIndex
import me.xizzhu.android.joshua.databinding.ActivityReadingBinding
import me.xizzhu.android.joshua.infra.BaseActivity
import me.xizzhu.android.joshua.reading.chapter.ChapterSelectionFragment
import me.xizzhu.android.joshua.reading.detail.VerseDetailViewLayout
import me.xizzhu.android.joshua.reading.detail.VerseTextItem
import me.xizzhu.android.joshua.reading.toolbar.ReadingToolbarFragment
import me.xizzhu.android.joshua.reading.verse.ParagraphItem
import me.xizzhu.android.joshua.reading.verse.VersePagerAdapter
import me.xizzhu.android.joshua.reading.verse.toBookIndex
import me.xizzhu.android.joshua.reading.verse.toChapterIndex
import me.xizzhu.android.joshua.reading.verse.toPagePosition
import me.xizzhu.android.joshua.reading.verse.toStringForSharing
import me.xizzhu.android.joshua.ui.dialog
import me.xizzhu.android.joshua.ui.listDialog
import me.xizzhu.android.joshua.ui.makeLessSensitive
import me.xizzhu.android.joshua.ui.toast
import me.xizzhu.android.joshua.utils.copyToClipBoard
import me.xizzhu.android.joshua.utils.shareToSystem
import kotlin.math.max

@AndroidEntryPoint
class ReadingActivity : BaseActivity<ActivityReadingBinding, ReadingViewModel.ViewAction, ReadingViewModel.ViewState, ReadingViewModel>(),
    ParagraphItem.Callback, VerseTextItem.Callback {
    companion object {
        private const val KEY_OPEN_NOTE = "me.xizzhu.android.joshua.KEY_OPEN_NOTE"

        fun bundleForOpenNote(): Bundle = Bundle().apply { putBoolean(KEY_OPEN_NOTE, true) }
    }

    override val viewModel: ReadingViewModel by viewModels()

    override val viewBinding: ActivityReadingBinding by lazy(LazyThreadSafetyMode.NONE) { ActivityReadingBinding.inflate(layoutInflater) }

    private val selectedVerses: MutableSet<Verse> = mutableSetOf()
    private var settings: Settings? = null
    private var currentReadingStatus: ReadingViewModel.ViewState.CurrentReadingStatus? = null

    private var actionMode: ActionMode? = null
    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_reading_action_mode, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = when (item.itemId) {
            R.id.action_copy -> {
                copy()
                true
            }
            R.id.action_share -> {
                share()
                true
            }
            else -> false
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            selectedVerses.forEach { verse -> versePagerAdapter.deselectVerse(verse.verseIndex) }
            selectedVerses.clear()
            actionMode = null
        }
    }

    private fun consolidateVersesForSharing(): Boolean = settings?.consolidateVersesForSharing ?: false

    private fun currentBookName(): String {
        val status = currentReadingStatus ?: return ""
        return status.bookNames.getOrElse(status.currentVerseIndex.bookIndex) { "" }
    }

    private fun copy() {
        try {
            if (selectedVerses.isEmpty()) return

            val bookName = currentBookName()
            copyToClipBoard(
                label = "${currentReadingStatus?.currentTranslation.orEmpty()} $bookName",
                text = selectedVerses.toStringForSharing(bookName, consolidateVersesForSharing())
            )
            toast(R.string.toast_verses_copied)
        } catch (e: Exception) {
            Logger.withTag(tag).e(e) { "Failed to copy" }
            toast(R.string.toast_unknown_error)
        } finally {
            actionMode?.finish()
        }
    }

    private fun share() {
        try {
            if (selectedVerses.isEmpty()) return

            shareToSystem(
                title = getString(R.string.text_share_with),
                text = selectedVerses.toStringForSharing(currentBookName(), consolidateVersesForSharing())
            )
        } catch (e: Exception) {
            Logger.withTag(tag).e(e) { "Failed to share" }
            toast(R.string.toast_unknown_error)
        } finally {
            actionMode?.finish()
        }
    }

    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var versePagerAdapter: VersePagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle back navigation using OnBackPressedDispatcher
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewBinding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    viewBinding.drawerLayout.closeDrawer(GravityCompat.START)
                    return
                }

                if (hideVerseDetail()) {
                    return
                }

                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
    }

    override fun initializeView() = with(viewBinding) {
        drawerToggle = ActionBarDrawerToggle(this@ReadingActivity, drawerLayout, toolbarFragment.getFragment<ReadingToolbarFragment>().toolbar, 0, 0)
        drawerLayout.addDrawerListener(drawerToggle)
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                try {
                    chapterSelectionFragment.getFragment<ChapterSelectionFragment>().expandCurrentBook()
                } catch (e: Exception) {
                    Logger.withTag(tag).e(e) { "Error occurred while expanding current book" }
                }
            }
        })

        versePagerAdapter = VersePagerAdapter(this@ReadingActivity, viewModel::loadVerses, viewModel::updateCurrentVerse)
        verseViewPager.offscreenPageLimit = 1
        verseViewPager.adapter = versePagerAdapter
        verseViewPager.makeLessSensitive()
        verseViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val bookIndex = position.toBookIndex()
                val chapterIndex = position.toChapterIndex()
                lifecycleScope.launch {
                    val currentVerseIndex = viewModel.currentVerseIndex()
                    if (bookIndex != currentVerseIndex.bookIndex || chapterIndex != currentVerseIndex.chapterIndex) {
                        viewModel.selectChapter(bookIndex, chapterIndex)
                    }
                }
            }
        })

        search.setOnClickListener { startActivity(Navigator.SCREEN_SEARCH) }

        val openNoteWhenCreated = intent.getBooleanExtra(KEY_OPEN_NOTE, false)
        verseDetailView.initialize(
            onClicked = ::hideVerseDetail,
            updateBookmark = ::onBookmarkClicked,
            updateHighlight = ::onHighlightClicked,
            updateNote = viewModel::saveNote,
            hide = !openNoteWhenCreated
        )
        if (openNoteWhenCreated) {
            lifecycleScope.launch { showVerseDetail(viewModel.currentVerseIndex(), VerseDetailViewLayout.VERSE_DETAIL_NOTE) }
        }
    }

    private fun startActivity(@Navigator.Companion.Screen screen: Int, extras: Bundle? = null) {
        try {
            navigator.navigate(this, screen, extras)
        } catch (e: Exception) {
            Logger.withTag(tag).e(e) { "Failed to open activity" }
            dialog(true, R.string.dialog_title_error, R.string.dialog_message_failed_to_navigate, { _, _ -> startActivity(screen) })
        }
    }

    override fun onViewActionEmitted(viewAction: ReadingViewModel.ViewAction) {
        when (viewAction) {
            is ReadingViewModel.ViewAction.ShowVerses ->
                versePagerAdapter.setVerses(viewAction.bookIndex, viewAction.chapterIndex, viewAction.items)
            is ReadingViewModel.ViewAction.ShowVerseDetail -> with(viewBinding.verseDetailView) {
                verseDetail = viewAction.verseDetail
                show(viewAction.content)
            }
            is ReadingViewModel.ViewAction.UpdateVerse -> versePagerAdapter.notifyVerseUpdate(viewAction.update)
            is ReadingViewModel.ViewAction.BookmarkSaved -> viewBinding.verseDetailView.setBookmarked(viewAction.bookmarked)
            is ReadingViewModel.ViewAction.HighlightSaved -> viewBinding.verseDetailView.setHighlightColor(viewAction.color)
            is ReadingViewModel.ViewAction.NoteSaved -> viewBinding.verseDetailView.setNote(viewAction.note)
        }
    }

    override fun onViewStateUpdated(viewState: ReadingViewModel.ViewState) {
        if (viewState.settings != settings) {
            applySettings(viewState.settings)
        }
        if (viewState.currentReadingStatus != currentReadingStatus) {
            viewState.currentReadingStatus?.let { applyCurrentReadingStatus(it) }
        }

        viewState.error?.handle()
    }

    private fun applySettings(settings: Settings) {
        this.settings = settings

        window.decorView.keepScreenOn = settings.keepScreenOn
        if (settings.hideSearchButton) {
            viewBinding.search.hide()
        } else {
            viewBinding.search.show()
        }

        versePagerAdapter.settings = settings
        viewBinding.verseDetailView.setSettings(settings)
    }

    private fun applyCurrentReadingStatus(status: ReadingViewModel.ViewState.CurrentReadingStatus) {
        val previousVerseIndex = currentReadingStatus?.currentVerseIndex ?: VerseIndex.INVALID
        currentReadingStatus = status

        with(viewBinding) {
            drawerLayout.closeDrawer(GravityCompat.START)

            versePagerAdapter.setCurrent(
                verseIndex = status.currentVerseIndex,
                translation = status.currentTranslation,
                parallel = status.parallelTranslations
            )
            verseViewPager.setCurrentItem(status.currentVerseIndex.toPagePosition(), false)
        }
        hideVerseDetail()

        if (previousVerseIndex.bookIndex != status.currentVerseIndex.bookIndex
            || previousVerseIndex.chapterIndex != status.currentVerseIndex.chapterIndex) {
            actionMode?.finish()
        }
    }

    private fun ReadingViewModel.ViewState.Error.handle() {
        when (this) {
            is ReadingViewModel.ViewState.Error.NoTranslationDownloadedError -> dialog(
                cancelable = false,
                title = R.string.dialog_title_no_translation_downloaded,
                message = R.string.dialog_message_download_translation_confirmation,
                onPositive = { _, _ -> startActivity(Navigator.SCREEN_TRANSLATIONS) },
                onNegative = { _, _ -> finish() },
                onDismiss = { viewModel.markErrorAsShown(this) },
            )
            is ReadingViewModel.ViewState.Error.TranslationSelectionError -> showErrorDialog(R.string.dialog_message_failed_to_select_translation) {
                viewModel.selectTranslation(translationToSelect)
            }
            is ReadingViewModel.ViewState.Error.ChapterSelectionError -> showErrorDialog(R.string.dialog_message_failed_to_select_chapter) {
                viewModel.selectChapter(bookIndex, chapterIndex)
            }
            is ReadingViewModel.ViewState.Error.VerseSelectionError -> showErrorDialog(R.string.dialog_message_failed_to_select_verse) {
                viewModel.selectVerse(verseToSelect)
            }
            is ReadingViewModel.ViewState.Error.VersesLoadingError -> showErrorDialog(R.string.dialog_message_failed_to_load_verses) {
                viewModel.loadVerses(bookIndex, chapterIndex)
            }
            is ReadingViewModel.ViewState.Error.VerseDetailLoadingError -> showErrorDialog(R.string.dialog_message_failed_to_load_verse_detail) {
                viewModel.loadVerseDetail(verseIndex, content)
            }
            is ReadingViewModel.ViewState.Error.BookmarkSavingError,
            is ReadingViewModel.ViewState.Error.HighlightSavingError,
            is ReadingViewModel.ViewState.Error.NoteSavingError -> {
                toast(R.string.toast_unknown_error)
                viewModel.markErrorAsShown(this)
            }
        }
    }

    private fun ReadingViewModel.ViewState.Error.showErrorDialog(@StringRes message: Int, onRetry: () -> Unit) {
        dialog(
            cancelable = true,
            title = R.string.dialog_title_error,
            message = message,
            onPositive = { _, _ -> onRetry() },
            onDismiss = { viewModel.markErrorAsShown(this) },
        )
    }

    private fun hideVerseDetail(): Boolean {
        val verseIndex = viewBinding.verseDetailView.verseDetail?.verseIndex ?: return false
        return if (viewBinding.verseDetailView.hide()) {
            viewModel.updateVerse(VerseUpdate(verseIndex, VerseUpdate.VERSE_DESELECTED))
            true
        } else {
            false
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        drawerToggle.syncState()
    }

    override fun onStart() {
        super.onStart()
        viewModel.checkDownloadedTranslation()
    }

    override fun onResume() {
        super.onResume()
        viewModel.startTrackingReadingProgress()
    }

    override fun onPause() {
        viewModel.stopTrackingReadingProgress()
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle.onConfigurationChanged(newConfig)
    }

    override fun onVerseClicked(verse: Verse) {
        if (actionMode == null) {
            showVerseDetail(verse.verseIndex, VerseDetailViewLayout.VERSE_DETAIL_VERSES)
            return
        }

        if (selectedVerses.contains(verse)) {
            // de-select the verse
            selectedVerses.remove(verse)
            if (selectedVerses.isEmpty()) {
                actionMode?.finish()
            }

            versePagerAdapter.deselectVerse(verse.verseIndex)
        } else {
            // select the verse
            selectedVerses.add(verse)
            versePagerAdapter.selectVerse(verse.verseIndex)
        }
    }

    private fun showVerseDetail(verseIndex: VerseIndex, @VerseDetailViewLayout.Companion.VerseDetail content: Int) {
        viewModel.loadVerseDetail(verseIndex, content)
    }

    override fun onVerseLongClicked(verse: Verse) {
        if (actionMode == null) {
            actionMode = startSupportActionMode(actionModeCallback)
        }
        onVerseClicked(verse)
    }

    fun onBookmarkClicked(verseIndex: VerseIndex, currentlyBookmarked: Boolean) {
        viewModel.saveBookmark(verseIndex = verseIndex, toBeBookmarked = !currentlyBookmarked)
    }

    fun onHighlightClicked(verseIndex: VerseIndex, @Highlight.Companion.AvailableColor currentHighlightColor: Int) {
        val defaultHighlightColor = settings?.defaultHighlightColor ?: Highlight.COLOR_NONE
        if (Highlight.COLOR_NONE == defaultHighlightColor) {
            listDialog(
                title = R.string.text_pick_highlight_color,
                items = resources.getStringArray(R.array.text_highlight_colors),
                selected = max(0, Highlight.AVAILABLE_COLORS.indexOf(currentHighlightColor)),
                onSelected = { which -> viewModel.saveHighlight(verseIndex, Highlight.AVAILABLE_COLORS[which]) }
            )
        } else {
            viewModel.saveHighlight(verseIndex, if (currentHighlightColor == Highlight.COLOR_NONE) defaultHighlightColor else Highlight.COLOR_NONE)
        }
    }

    fun onNoteClicked(verseIndex: VerseIndex) {
        showVerseDetail(verseIndex, VerseDetailViewLayout.VERSE_DETAIL_NOTE)
    }

    override fun onVerseTextClicked(translation: String) {
        viewModel.selectTranslation(translation)
    }

    override fun onVerseTextLongClicked(verse: Verse) {
        lifecycleScope.launch {
            try {
                val bookName = viewModel.bookName(verse.text.translationShortName, verse.verseIndex.bookIndex)
                copyToClipBoard(
                    label = "${verse.text.translationShortName} $bookName",
                    text = verse.toStringForSharing(bookName)
                )
                toast(R.string.toast_verses_copied)
            } catch (e: Exception) {
                Logger.withTag(tag).e(e) { "Failed to copy" }
                toast(R.string.toast_unknown_error)
            }
        }
    }
}
