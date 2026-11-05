package org.ebible.android.bible.utils

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class NumberFormatTest {
    @Test
    fun `test localized() uses the digits of the default locale without grouping`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            assertEquals("1", 1.localized())
            assertEquals("1234", 1234.localized())
            Locale.setDefault(Locale.forLanguageTag("ar-u-nu-arab"))
            assertEquals("١٢٣", 123.localized())
        } finally {
            Locale.setDefault(original)
        }
    }
}
