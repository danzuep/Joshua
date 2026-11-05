package org.ebible.android.bible.ui

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import androidx.annotation.StringRes
import org.ebible.android.bible.R
import org.ebible.android.bible.core.Highlight

private const val SWATCH = "\u2003\u2003\u2003\u2003"

fun Context.highlightSwatch(color: Int): CharSequence =
    if (color == Highlight.COLOR_NONE) {
        getString(R.string.text_highlight_color_none)
    } else {
        SpannableString(SWATCH).apply { setSpan(BackgroundColorSpan(color), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    }

fun Context.highlightSwatchFor(@StringRes colorStringRes: Int): CharSequence = highlightSwatch(
    when (colorStringRes) {
        R.string.text_highlight_color_yellow -> Highlight.COLOR_YELLOW
        R.string.text_highlight_color_pink -> Highlight.COLOR_PINK
        R.string.text_highlight_color_orange -> Highlight.COLOR_ORANGE
        R.string.text_highlight_color_purple -> Highlight.COLOR_PURPLE
        R.string.text_highlight_color_red -> Highlight.COLOR_RED
        R.string.text_highlight_color_green -> Highlight.COLOR_GREEN
        R.string.text_highlight_color_blue -> Highlight.COLOR_BLUE
        else -> Highlight.COLOR_NONE
    }
)
