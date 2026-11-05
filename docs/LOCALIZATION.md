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

## Adding a language

1. Create `app/src/main/res/values-<code>/strings.xml`.
2. Translate the strings from `values/strings.xml`, keeping `%1$d`-style placeholders and the `text_months` and `text_sort_order` arrays.
3. Add the code to `Configurations.supportedLocales`.

Strings missing from a locale fall back to the default English file.

## Wording and symbols

The UI keeps words to a minimum. In the default strings, status text uses symbols instead:

| Symbol | Meaning |
|--------|---------|
| ✓ | Success (downloaded, deleted, copied, backup or restore finished, uploaded, notes saved) |
| ⬇ … | Downloading |
| ⬇ ? | Download confirmation |
| ⬆ … | Uploading |
| ⚙ … | Installing |
| 🗑 … | Deleting |
| 🗑 ? | Delete confirmation |
| ⏳ | Please wait |
| ⚠ | Error dialog title |
| ✎ … | Notes being typed |
| ✎ ✓ | Notes saved |
| 🔍 N | Number of search results |
| 🔥 N | Consecutive reading days |

The translated locales do not override these strings, so they inherit the symbols. Other labels are short translated words.

## Review

The translations were written without a native-speaker review. Corrections are welcome.
