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

package org.ebible.android.bible.reading

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ActionMode
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import co.touchlab.kermit.Logger
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.ebible.android.bible.Navigator
import org.ebible.android.bible.R
import org.ebible.android.bible.core.Bible
import org.ebible.android.bible.core.Highlight
import org.ebible.android.bible.core.Settings
import org.ebible.android.bible.core.Verse
import org.ebible.android.bible.core.VerseIndex
import org.ebible.android.bible.databinding.ActivityReadingBinding
import org.ebible.android.bible.infra.BaseActivity
import org.ebible.android.bible.reading.chapter.ChapterSelectionFragment
import org.ebible.android.bible.reading.detail.VerseDetailViewLayout
import org.ebible.android.bible.reading.detail.VerseTextItem
import org.ebible.android.bible.reading.toolbar.ReadingToolbarFragment
import org.ebible.android.bible.reading.verse.ParagraphItem
import org.ebible.android.bible.reading.verse.VersePagerAdapter
import org.ebible.android.bible.reading.verse.toBookIndex
import org.ebible.android.bible.reading.verse.toChapterIndex
import org.ebible.android.bible.reading.verse.toPagePosition
import org.ebible.android.bible.reading.verse.toStringForCopy
import org.ebible.android.bible.reading.verse.toStringForSharing
import org.ebible.android.bible.translations.TranslationsActivity
import org.ebible.android.bible.ui.highlightSwatch
import org.ebible.android.bible.ui.dialog
import org.ebible.android.bible.ui.indeterminateProgressDialog
import org.ebible.android.bible.ui.listDialog
import org.ebible.android.bible.ui.makeLessSensitive
import org.ebible.android.bible.ui.toast
import org.ebible.android.bible.utils.copyToClipBoard
import org.ebible.android.bible.utils.shareToSystem

@AndroidEntryPoint
class ReadingActivity : BaseActivity<ActivityReadingBinding, ReadingViewModel.ViewAction, ReadingViewModel.ViewState, ReadingViewModel>(),
    ParagraphItem.Callback, VerseTextItem.Callback {
    companion object {
        private const val KEY_OPEN_NOTE = "org.ebible.android.bible.KEY_OPEN_NOTE"

        /** Optional launch extra: the 0-based index of the book to open (e.g. 42 for John) instead of the last reading position. */
        const val KEY_OPEN_BOOK = "org.ebible.android.bible.KEY_OPEN_BOOK"

        fun bundleForOpenNote(): Bundle = Bundle().apply { putBoolean(KEY_OPEN_NOTE, true) }
    }

    override val viewModel: ReadingViewModel by viewModels()

    override val viewBinding: ActivityReadingBinding by lazy(LazyThreadSafetyMode.NONE) { ActivityReadingBinding.inflate(layoutInflater) }

    private val selectedVerses: MutableSet<Verse> = mutableSetOf()
    private var settings: Settings? = null
    private var currentReadingStatus: ReadingViewModel.ViewState.CurrentReadingStatus? = null
    private var translationNames: Map<String, String> = emptyMap()

    private var actionMode: ActionMode? = null
    private var installingDialog: AlertDialog? = null
    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val actions = layoutInflater.inflate(R.layout.view_reading_action_mode, null)
            intArrayOf(R.id.action_highlight_verse, R.id.action_copy, R.id.action_share, R.id.action_bookmark_verse).forEach { id ->
                actions.findViewById<View>(id).setOnClickListener { onVerseActionClicked(id) }
            }
            mode.customView = actions
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false

        override fun onDestroyActionMode(mode: ActionMode) {
            selectedVerses.forEach { verse -> versePagerAdapter.deselectVerse(verse.verseIndex) }
            selectedVerses.clear()
            actionMode = null
        }
    }

    private fun onVerseActionClicked(actionId: Int) {
        when (actionId) {
            R.id.action_highlight_verse -> highlightSelectedVerses()
            R.id.action_copy -> copy()
            R.id.action_share -> share()
            R.id.action_bookmark_verse -> {
                viewModel.toggleBookmarks(selectedVerses.map { it.verseIndex })
                actionMode?.finish()
            }
        }
    }

    private fun translationName(translationShortName: String): String = translationNames[translationShortName] ?: translationShortName

    private fun currentBookName(): String {
        val status = currentReadingStatus ?: return ""
        return status.bookNames.getOrElse(status.currentVerseIndex.bookIndex) { "" }
    }

    private fun copy() {
        try {
            if (selectedVerses.isEmpty()) return

            val bookName = currentBookName()
            val translationName = translationName(currentReadingStatus?.currentTranslation.orEmpty())
            copyToClipBoard(
                label = "${currentReadingStatus?.currentTranslation.orEmpty()} $bookName",
                text = selectedVerses.toStringForCopy(bookName, translationName)
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
                text = selectedVerses.toStringForSharing(currentBookName())
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
    private var verseReportedByScroll: VerseIndex? = null
    private var userSwipingPager = false
    private val referenceBackStack = ArrayDeque<VerseIndex>()

    // The launcher alias reuses a running reader, so an open-book request arrives here instead of onCreate().
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val openBook = intent.getIntExtra(KEY_OPEN_BOOK, -1)
        if (openBook in 0 until Bible.BOOK_COUNT) viewModel.openBook(openBook)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val openBook = intent.getIntExtra(KEY_OPEN_BOOK, -1)
        if (savedInstanceState == null && openBook in 0 until Bible.BOOK_COUNT) viewModel.openBook(openBook)

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

                if (referenceBackStack.isNotEmpty()) {
                    viewModel.selectVerse(referenceBackStack.removeLast())
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
        toolbarFragment.getFragment<ReadingToolbarFragment>().onTitleClicked = {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.closeDrawer(GravityCompat.START)
            else drawerLayout.openDrawer(GravityCompat.START)
        }
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                try {
                    chapterSelectionFragment.getFragment<ChapterSelectionFragment>().expandCurrentBook()
                } catch (e: Exception) {
                    Logger.withTag(tag).e(e) { "Error occurred while expanding current book" }
                }
            }
        })

        versePagerAdapter = VersePagerAdapter(this@ReadingActivity, viewModel::loadVerses) { pagePosition, verseIndex, middleVerse ->
            // Neighbour pages report their own scroll position too; only the selected page may update the current verse.
            // The verse may belong to a neighbouring chapter that the page scrolled into.
            if (pagePosition == verseViewPager.currentItem) {
                verseReportedByScroll = verseIndex
                toolbarFragment.getFragment<ReadingToolbarFragment>().showReadingChapter(verseIndex, middleVerse)
                viewModel.updateCurrentVerse(verseIndex)
            }
        }
        verseViewPager.offscreenPageLimit = 1
        verseViewPager.adapter = versePagerAdapter
        verseViewPager.makeLessSensitive()
        verseViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                when (state) {
                    ViewPager2.SCROLL_STATE_DRAGGING -> userSwipingPager = true
                    ViewPager2.SCROLL_STATE_IDLE -> userSwipingPager = false
                }
            }

            override fun onPageSelected(position: Int) {
                val bookIndex = position.toBookIndex()
                val chapterIndex = position.toChapterIndex()
                if (userSwipingPager) {
                    // A swipe always lands on verse one of the new chapter.
                    versePagerAdapter.scrollToChapterStart(position)
                    appBar.setExpanded(true, true)
                    viewModel.selectChapter(bookIndex, chapterIndex)
                    return
                }
                lifecycleScope.launch {
                    val currentVerseIndex = viewModel.currentVerseIndex()
                    if (bookIndex != currentVerseIndex.bookIndex || chapterIndex != currentVerseIndex.chapterIndex) {
                        viewModel.selectChapter(bookIndex, chapterIndex)
                    }
                }
            }
        })

        chapterSelectionFragment.getFragment<ChapterSelectionFragment>().onChapterSelected = {
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        search.setOnClickListener { startActivity(Navigator.SCREEN_SEARCH) }

        val openNoteWhenCreated = intent.getBooleanExtra(KEY_OPEN_NOTE, false)
        verseDetailView.initialize(
            onClicked = ::hideVerseDetail,
            updateNote = viewModel::saveNote,
            reorderTranslations = viewModel::reorderTranslations,
            openVerse = { verseIndex ->
                // Remember where the reference was followed from, so that back returns there.
                viewBinding.verseDetailView.verseDetail?.verseIndex?.let { referenceBackStack.addLast(it) }
                hideVerseDetail()
                viewModel.selectVerse(verseIndex)
            },
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
                versePagerAdapter.setVerses(viewAction.translation, viewAction.bookIndex, viewAction.chapterIndex, viewAction.items)
            is ReadingViewModel.ViewAction.ShowVerseDetail -> with(viewBinding.verseDetailView) {
                verseDetail = viewAction.verseDetail
                show(viewAction.content)
            }
            is ReadingViewModel.ViewAction.UpdateVerse -> versePagerAdapter.notifyVerseUpdate(viewAction.update)
            is ReadingViewModel.ViewAction.BookmarkSaved, is ReadingViewModel.ViewAction.HighlightSaved -> Unit
            is ReadingViewModel.ViewAction.NoteSaved -> viewBinding.verseDetailView.setNote(viewAction.note)
        }
    }

    override fun onViewStateUpdated(viewState: ReadingViewModel.ViewState) {
        translationNames = viewState.translationNames
        if (viewState.settings != settings) {
            applySettings(viewState.settings)
        }
        if (viewState.currentReadingStatus != currentReadingStatus) {
            viewState.currentReadingStatus?.let { applyCurrentReadingStatus(it) }
        }

        if (viewState.installingTranslation) {
            if (installingDialog == null) installingDialog = indeterminateProgressDialog(R.string.dialog_title_installing)
        } else {
            installingDialog?.dismiss()
            installingDialog = null
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
            versePagerAdapter.setCurrent(
                verseIndex = status.currentVerseIndex,
                translation = status.currentTranslation,
                parallel = status.parallelTranslations
            )
            // A verse reached by the user scrolling is already on screen; moving the pager would jump it to a chapter heading.
            val reportedByScroll = status.currentVerseIndex == verseReportedByScroll
            verseReportedByScroll = null
            val chapterChanged = previousVerseIndex.bookIndex != status.currentVerseIndex.bookIndex
                || previousVerseIndex.chapterIndex != status.currentVerseIndex.chapterIndex
            if (!reportedByScroll && (chapterChanged || !versePagerAdapter.containsChapterInCurrentPage(status.currentVerseIndex))) {
                val targetPage = status.currentVerseIndex.toPagePosition()
                if (verseViewPager.currentItem != targetPage) verseViewPager.setCurrentItem(targetPage, false)
            }
        }
        hideVerseDetail()

        if (previousVerseIndex.bookIndex != status.currentVerseIndex.bookIndex
            || previousVerseIndex.chapterIndex != status.currentVerseIndex.chapterIndex) {
            actionMode?.finish()
        }
    }

    private fun showNoTranslationDownloadedDialog(error: ReadingViewModel.ViewState.Error) {
        if (isDestroyed) return

        val content = layoutInflater.inflate(R.layout.dialog_quick_download_translations, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setCancelable(false)
            .setTitle(R.string.dialog_title_no_translation_downloaded)
            .setMessage(R.string.dialog_message_download_translation_confirmation)
            .setView(content)
            .setPositiveButton(android.R.string.ok) { _, _ -> startActivity(Navigator.SCREEN_TRANSLATIONS) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnDismissListener { viewModel.markErrorAsShown(error) }
            .create()
        listOf(
            R.id.button_download_engbsb to "engbsb",
            R.id.button_download_engwebp to "engwebp",
            R.id.button_download_engerv to "engerv",
        ).forEach { (buttonId, shortName) ->
            content.findViewById<View>(buttonId).setOnClickListener {
                dialog.dismiss()
                startActivity(Navigator.SCREEN_TRANSLATIONS, Bundle().apply { putString(TranslationsActivity.KEY_DOWNLOAD_TRANSLATION, shortName) })
            }
        }
        dialog.show()
    }

    private fun ReadingViewModel.ViewState.Error.handle() {
        when (this) {
            is ReadingViewModel.ViewState.Error.NoTranslationDownloadedError -> showNoTranslationDownloadedDialog(this)
            is ReadingViewModel.ViewState.Error.TranslationSelectionError -> showErrorDialog(R.string.dialog_message_failed_to_select_translation) {
                viewModel.selectTranslation(translationToSelect)
            }
            is ReadingViewModel.ViewState.Error.ChapterSelectionError -> showErrorDialog(R.string.dialog_message_failed_to_select_chapter) {
                viewModel.selectChapter(bookIndex, chapterIndex)
            }
            is ReadingViewModel.ViewState.Error.VerseSelectionError -> showErrorDialog(R.string.dialog_message_failed_to_select_verse) {
                viewModel.selectVerse(verseToSelect)
            }
            is ReadingViewModel.ViewState.Error.VersesLoadingError -> {
                versePagerAdapter.onVersesLoadFailed(bookIndex, chapterIndex)
                showErrorDialog(R.string.dialog_message_failed_to_load_verses) {
                    viewModel.loadVerses(bookIndex, chapterIndex)
                }
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

    override fun onDestroy() {
        installingDialog?.dismiss()
        installingDialog = null
        super.onDestroy()
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

    private fun highlightSelectedVerses() {
        val verseIndexes = selectedVerses.map { it.verseIndex }
        actionMode?.finish()

        val defaultHighlightColor = settings?.defaultHighlightColor ?: Highlight.COLOR_NONE
        if (Highlight.COLOR_NONE == defaultHighlightColor) {
            listDialog(
                title = R.string.text_pick_highlight_color,
                items = Highlight.AVAILABLE_COLORS.map { highlightSwatch(it) }.toTypedArray(),
                selected = 0,
                onSelected = { which -> verseIndexes.forEach { viewModel.saveHighlight(it, Highlight.AVAILABLE_COLORS[which]) } }
            )
        } else {
            verseIndexes.forEach { viewModel.saveHighlight(it, defaultHighlightColor) }
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
                    text = verse.toStringForCopy(bookName, translationName(verse.text.translationShortName))
                )
                toast(R.string.toast_verses_copied)
            } catch (e: Exception) {
                Logger.withTag(tag).e(e) { "Failed to copy" }
                toast(R.string.toast_unknown_error)
            }
        }
    }
}
