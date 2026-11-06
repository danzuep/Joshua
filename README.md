[![Build Status](https://img.shields.io/github/actions/workflow/status/danzuep/Yeshua/android.yml?branch=main&color=blue)](https://github.com/danzuep/Yeshua/actions/workflows/android.yml)
[![Latest Release](https://img.shields.io/github/v/release/danzuep/Yeshua?color=blue)](https://github.com/danzuep/Yeshua/releases/latest)
![Languages](https://img.shields.io/badge/languages-Kotlin-blue.svg?longCache=true&style=flat)
[![API](https://img.shields.io/badge/API-23%2B-blue.svg?style=flat)](https://www.android.com/versions/marshmallow/)
[![Apache License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)

# Yeshua

A simple, free and open source software Bible reader for Android with a minimalist design. Read offline, compare translations side by side, search every verse, and keep your notes, bookmarks and highlights, all in a tiny install of less than 4 MB.

The default Bible is the **World English Bible (WEB)**, a modern-English public domain translation (with a trademark restriction on the name), so it is free to read, share and redistribute.

## Download

Get the latest APK from **[GitHub Releases](https://github.com/danzuep/Yeshua/releases/latest)**. Two files are published with each release:

| File | What it is |
|------|------------|
| `Yeshua-vX.Y.Z-with-WEB.apk` | **Recommended.** Includes the WEB Bible, so it works offline from the first launch. under 5 MB as of v0.33.0 |
| `Yeshua-vX.Y.Z.apk` | Download translations from inside the app. 1.67 MB as of v0.21.2 |

Open the APK on your device and allow installs from your browser or file manager when asked.

### Where to start

Not sure which translation to pick? These are good starting points, and you can download any of them from the translation menu:

* Berean Standard Bible (engbsb): Highly respected modern English translation balancing literal accuracy and smooth readability.
* World English Bible (engwebp / engwebu / engwebpb): Clear public-domain update of the ASV using standard contemporary English.
* Easy-to-Read Version (engerv): Designed specifically for ultra-clear comprehension using plain, simple English vocabulary.
* GOD'S WORD (enggw / eng-glw): Modern dynamic-equivalence translation engineered for highly natural, readable English prose.
* Free Bible Version (engfbv): Open-access translation using natural everyday English phrasing and idioms.
* NET Bible (engnet): Academically transparent Protestant translation praised for modern readability and exhaustive notes.
* Open English Bible (engoebus / engoebcw): Open-source, modern English update of the Twentieth Century New Testament and critical source texts, tailored for contemporary readers.

F-Droid packaging metadata is in `fastlane/` and [docs/RELEASING.md](docs/RELEASING.md#f-droid).

### Stay up to date with Obtainium

[Obtainium](https://github.com/ImranR98/Obtainium) installs and updates apps straight from GitHub Releases, so you don't need an app store.

1. Install Obtainium from its [releases page](https://github.com/ImranR98/Obtainium/releases/latest) (or F-Droid).
2. Open Obtainium, tap **Add App**, and enter `https://github.com/danzuep/Yeshua` as the source URL.
3. Under the app's additional options, set **Filter APKs by regular expression** to `with-WEB`. This picks the WEB-bundled APK. Leave it empty for the smaller one.
4. Tap **Add**, then **Install**. Obtainium will notify you when a new release is out.

## Features

* Free and open source software
* Reads without a network connection
* Small download and install size
* Read multiple translations at the same time
* Full-text search
* Bookmarks, highlights and your own notes
* Share verses through your favorite apps
* Adjustable font size and day / night mode

Double-tap a verse to bring up its actions, or long-press a verse to copy or share it. Notes are saved as you type.

Hundreds of other translations can be downloaded in the app from [eBible.org](https://ebible.org/download.php), or from download sites you add yourself. See the [full list](docs/TRANSLATIONS.md). The app interface is available in several languages, see [localization](docs/LOCALIZATION.md). Recent changes are in the [changelog](docs/CHANGELOG.md). Report bugs or request features on the [issue tracker](https://github.com/danzuep/Yeshua/issues).

## About this project

Thank you to [Xizhi Zhu](https://github.com/xizzhu) for creating [the original Joshua](https://github.com/xizzhu/Joshua), which this is built on. After the original appeared to be abandoned, this fork updated it to build with JDK 25 and brought the unit tests back to green. See [docs/RELEASING.md](docs/RELEASING.md) for how releases are built.

The WEB was produced by **Michael Johnson**, who also maintains many other public domain works at [eBible.org](https://ebible.org/engwebu/MRK01.htm). He wanted a Bible app for them, so this project was forked to build one: a FOSS app for his public domain translations.

License
-------
    Copyright (C) 2023 Xizhi Zhu
    Copyright (C) 2026 Daniel Collingwood

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
