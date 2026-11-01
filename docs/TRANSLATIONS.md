Available Translations
======================
Joshua builds its list of translations from a download site's translation table. The default site is
[eBible.org](https://ebible.org/download.php), which offers hundreds of public domain and freely licensed translations
in many languages. Each translation is downloaded as a USFM zip (`https://ebible.org/Scriptures/<id>_usfm.zip`) and
parsed on the device. The default site's table ships in the app as `app/src/main/assets/ebible_downloads.tsv`; the
"Update eBible downloads" workflow regenerates it monthly (or on demand) with `./gradlew updateEbibleDownloads`.

Adding download sites
---------------------
On the Translations screen, open the menu and choose **Download sites** to edit the list (one web address per line).
A site is a page with a table of translations laid out like [eBible.org's download page](https://ebible.org/download.php):
territory, language, English language name, vernacular title and English title, where the title links to
`details.php?id=<id>`. Translations are fetched from `<site origin>/Scriptures/<id>_usfm.zip`, trying each site in order.
Check each translation's license before using it.

The format of the downloaded files is USFM. Strong's numbers and cross references are not supported, as USFM
translations from these sites do not include them.
