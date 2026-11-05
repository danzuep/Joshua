Available Translations
======================
Yeshua builds its list of translations from a download site's translation table. The default site is
[eBible.org](https://ebible.org/download.php). Each translation is downloaded as a USFM zip
(`https://ebible.org/Scriptures/<id>_usfm.zip`) and parsed on the device. The default catalog ships in the app as
`app/src/main/assets/translations.csv`; the app only offers translations marked redistributable and downloadable.
The "Update eBible translation catalog" workflow downloads the CSV monthly (or on demand) with
`./gradlew updateEbibleTranslations`.

Most translations are open access: copyrighted with an open licence (typically noncommercial, unchanged text). Only a few
are public domain, and some of those carry restrictions (for example the WEB trademark). The translation details dialog
has a **Copyright and permissions** button linking to the translation's `copr.htm` page on eBible.org.

Adding download sites
---------------------
On the Translations screen, open the menu and choose **Download sites** to edit the list (one web address per line).
A custom site is a page with a table of translations laid out like [eBible.org's download page](https://ebible.org/download.php):
territory, language, English language name, vernacular title and English title, where the title links to
`details.php?id=<id>`. Translations are fetched from `<site origin>/Scriptures/<id>_usfm.zip`, trying each site in order.
Check each translation's license before using it.

The format of the downloaded files is USFM. Strong's numbers and cross references are not supported, as USFM
translations from these sites do not include them.
