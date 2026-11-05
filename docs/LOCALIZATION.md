# Localization

The app UI is translated into the most spoken languages. This is separate from Bible translations (see [TRANSLATIONS.md](TRANSLATIONS.md)), which come from eBible.org.

## Supported UI languages

| Code | Language | Folder |
|------|----------|--------|
| `en` | English (default) | `values/` |
| `zh` | Chinese | `values-zh/` |
| `es` | Spanish | `values-es/` |
| `hi` | Hindi | `values-hi/` |
| `ar` | Arabic (right-to-left) | `values-ar/` |
| `fr` | French | `values-fr/` |
| `pt` | Portuguese | `values-pt/` |

All folders are under `app/src/main/res/`. The list is kept in `Configurations.supportedLocales` in `buildSrc/src/main/kotlin/Configurations.kt`; a locale missing from it is stripped from the APK by `androidResources.localeFilters`. The manifest sets `supportsRtl` so Arabic mirrors the layout.

## How strings are organised

The default file `values/strings.xml` is icon-first: navigation, actions and status text are language-agnostic symbols, so every locale (including unsupported ones) gets a usable UI without translation. Only a handful of strings are prose: the Bible and testament names, the upload hints, the upload failure message, the download sites help and the two `content_description_*` strings. Repeated messages are shared: every "failed, retry?" dialog points at `dialog_message_retry`, and network failures at `dialog_message_network_error`.

Each `values-<code>/strings.xml` therefore has about ten entries. Do not translate the icon strings; they inherit from the default file.

- Shared glyphs are defined once and reused with whole-value references (for example `action_bookmarks` is `@string/title_bookmarks`). Android cannot embed a `@string` reference inside a longer string, so compound labels such as "download translations" are written as `⬇ 🌐`.
- Dates are not stored as strings. `AnnotatedVerseViewModel.formatDate` uses the system's best date pattern for the device locale, so months, order and numerals follow the OS.
- Use positional placeholders (such as `%1$d`) so numbers are formatted for the locale.

## Language picker

Settings has an **A / 文** entry that lists the supported languages by their own names, plus 🗣️ to follow the system. It uses `AppCompatDelegate.setApplicationLocales`, so the choice is remembered (via `AppLocalesMetadataHolderService` before Android 13, and shown in system settings through `res/xml/locales_config.xml`). Without a choice the device language is used.

Chapter and verse numbers are formatted with `Int.localized()` (`java.text.NumberFormat`), so they use the digits of the current locale, such as ١٢٣ in Arabic.

## Adding a language

1. Create `app/src/main/res/values-<code>/strings.xml`.
2. Translate only the prose strings from `values/strings.xml` (see above).
3. Add the code to `Configurations.supportedLocales`, `AppLanguage.CODES` and `res/xml/locales_config.xml`.

## Symbols

| Symbol | Meaning |
|--------|---------|
| 🔖 🖍 📝 📊 | Bookmarks, highlights, notes, reading progress |
| ⚙ | Settings |
| 🌐 | Translations |
| 🔍 | Search |
| A / 文 | Language |
| ⬇ ⬆ | Download, upload |
| 📤 📋 | Share, copy |
| 📦 … | Installing |
| 🗑 | Delete |
| ✓ | Success |
| ⚠ | Error |
| ⏳ | Please wait |
| ✎ … / ✎ ✓ | Notes typing / saved |
| 🔥 N | Consecutive reading days |

Share (📤) and cross references (🔗) use different glyphs, and ⚙ is reserved for settings.

## Review

The translations were written without a native-speaker review. Corrections are welcome.
