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

The default file `values/strings.xml` is icon-first: navigation, actions and status text are language-agnostic symbols, so every locale (including unsupported ones) gets a usable UI without translation. Prose that cannot be an icon (error messages, hints, the Bible and testament names, "On"/"Off") is English by default.

Each `values-<code>/strings.xml` overrides only that prose, plus the `content_description_*` strings used by screen readers. Do not translate the icon strings; they inherit from the default file.

- Shared glyphs are defined once and reused with whole-value references (for example `action_bookmarks` is `@string/title_bookmarks`). Android cannot embed a `@string` reference inside a longer string, so compound labels such as "download translations" are written as `⬇ 🌐`.
- Dates are not stored as strings. `AnnotatedVerseViewModel.formatDate` uses the system's best date pattern for the device locale, so months, order and numerals follow the OS.
- Use positional placeholders (such as `%1$d`) so numbers are formatted for the locale.

## Adding a language

1. Create `app/src/main/res/values-<code>/strings.xml`.
2. Translate only the prose strings from `values/strings.xml` (see above).
3. Add the code to `Configurations.supportedLocales`.

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
