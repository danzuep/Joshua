package com.github.danzuep.yeshua.android.utils

import java.text.NumberFormat
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeDigitsTest {
    private fun format(tag: String, number: Long, grouping: Boolean = true) =
        NumberFormat.getInstance(Locale.forLanguageTag(tag).withNativeDigits()).apply { isGroupingUsed = grouping }.format(number)

    @Test
    fun `Thai uses Thai digits`() = assertEquals("๒๐๒๔", format("th-TH", 2024, grouping = false))

    @Test
    fun `Hindi uses Devanagari digits`() = assertEquals("१,२३४", format("hi-IN", 1234))

    @Test
    fun `Bengali uses Bengali digits`() = assertEquals("১২৩", format("bn", 123))

    @Test
    fun `English stays Latin`() = assertEquals("2,024", format("en-US", 2024))

    @Test
    fun `Arabic is left unchanged`() {
        val arabic = Locale.forLanguageTag("ar")
        assertEquals(arabic, arabic.withNativeDigits())
    }

    @Test
    fun `explicit numbering system is respected`() {
        assertEquals("2024", format("th-u-nu-latn", 2024, grouping = false))
        assertEquals("th-u-nu-latn", Locale.forLanguageTag("th-u-nu-latn").withNativeDigits().toLanguageTag())
    }
}
