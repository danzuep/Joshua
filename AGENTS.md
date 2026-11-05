# Notes for AI agents

## Building and testing in the sandbox

The agent sandbox **cannot run the Gradle build or unit tests locally**:

- There is no Android SDK, and `https://dl.google.com` (Android SDK packages and the Google Maven mirror used by AGP) fails DNS/connection with no HTTP response. Gradle therefore fails resolving Google Maven dependencies.
- Maven Central, `plugins.gradle.org` and `services.gradle.org` are reachable, but that is not enough, because AGP and AndroidX come from Google.
- The sandbox JDK is 27 and the project targets JDK 25 (Temurin in CI), so a local build would not match CI anyway.

Don't spend time trying to work around this (no installing the SDK, mirrors or offline caches). Use GitHub Actions as the build and test runner instead, with `gh`:

| Goal | How |
|------|-----|
| Build and unit tests (`./gradlew testDebugUnitTest assembleDebug`, about 4 min) | Push the branch and open a PR (`gh pr create`). `Android CI` runs only when `app/`, `buildSrc/`, Gradle or workflow files change, and on pushes to `main`. It has no `workflow_dispatch`. |
| Shared module tests (`./gradlew :shared:jvmTest`, about 1 min) | `Shared tests` workflow, runs when `shared/` or Gradle files change. Tests live in `shared/src/commonTest`. Only the JVM target runs them, so they need no Android SDK. Kotlin-only changes should rely on this job, not `Android CI`. |
| Emulator smoke test of the release APK (about 4-8 min) | `gh workflow run smoke-test.yml --ref <branch>` |
| Emulator instrumented tests (`app/src/androidTest`, same workflow as the smoke test, separate `instrumented` job) | `gh workflow run smoke-test.yml --ref <branch>`. They run inside the app, so they can seed data with the shared `CoreVerse` builder and read the rendered views, against the minimal John 1-2 and Hebrews 1-2 translation. Artifact: `instrumented-results`. To position the reader, run `CoreReader` on a storage that saves through the app's singleton `BibleReadingRepository` (debug-only `DebugEntryPoints`, see `CoreReaderTest`), because the repository caches the position in memory and ignores writes made through a separate `AndroidDatabase`. |
| Release APKs (plain and with WEB) | `gh workflow run release.yml --ref <branch>`. This is a dry run unless the ref is a `v*` tag. See [docs/RELEASING.md](docs/RELEASING.md). |

A failed Android CI run uploads the `test-reports` artifact (`gh run download <id> -n test-reports`) alongside the log (`gh run view <id> --log-failed`). Check results with `gh run list --repo danzuep/eBible --branch <branch>`, `gh pr checks <n>`, and `gh run view <id> --log-failed`. Smoke-test artifacts (`logcat.txt`, `screen.png`, `ui.xml`) can be fetched with `gh run download <id> -n smoke-results`.

Before pushing, review Kotlin changes carefully (unresolved references, imports, scope), because each CI round trip costs minutes. Static checks that work locally: `git diff`, `grep`, and `bash -n` on shell scripts.

## Project map

- `app/` is the single Android module (Kotlin, Hilt, Room-less SQLite, coroutines). Source is under `app/src/main/kotlin/org/ebible/android/bible/`:
  - `core/` holds managers and repositories (`repository/local` for SQLite, `repository/remote` for downloads and USFM parsing).
  - Feature packages (`reading`, `search`, `translations`, `settings`, `annotated`, `progress`, `preview`) each have an Activity plus a `ViewModel` with a single `ViewState`, one-shot `ViewAction`s and a typed `Error`, on `infra/BaseViewModel` and `infra/BaseActivity`.
  - Strong's Numbers, cross references and the old JSON download flow were removed; translations now come from eBible.org USFM zips, with a redistributable catalog in `app/src/main/assets/ebible_translations.csv` refreshed monthly by `update-ebible-downloads.yml`.
- `buildSrc/src/main/kotlin/Configurations.kt` pins plugin and library versions. The app version comes from the release tag (`-PappVersion`), not from this file.
- `docs/RELEASING.md` covers releases and signing; `.github/scripts/` holds the smoke-test helpers; `shared/src/commonMain/.../testing` has fluent builders (`CoreVerse`, `CoreSettings`) for JVM tests.

## Repository facts

- `main` is protected. Open PRs; the maintainer merges them.
- Merging a PR auto-tags the next version from the PR title prefix (`major:`, `minor:`, `patch:`; any other title creates no tag) and starts the release. See `.github/workflows/auto-tag.yml`.
- Known-bad dependency versions that broke CI on JDK 25: Robolectric 4.17, Hilt 2.60.1, KSP 2.3.13. Keep Robolectric 4.16.1, Hilt 2.59.2, KSP 2.3.6 (`buildSrc/src/main/kotlin/Configurations.kt`).
- Some unit tests can be flaky because of leftover coroutines. `BaseUnitTest.tearDown` cancels the shared test scope; cancel any view-model scope you create in a test too.
- The reading screen accepts the launch extra `org.ebible.android.bible.KEY_OPEN_BOOK` (0-based book index) to open a book directly, which the smoke test uses.
- Branches opened by other tools can be based on an old `main`, which shows up as unresolved references for code that merged later. Rebase onto `origin/main` before debugging a CI failure.
- After a rebase, `git push --force-with-lease` is often rejected as "stale info". Use `git push --force-with-lease=<branch>:$(git ls-remote origin refs/heads/<branch> | cut -f1) origin <branch>`.
- A PR whose base is another PR's branch merges into that branch, not `main`. Check `gh pr view <n> --json baseRefName` before assuming something reached `main`.
- Commits carry the trailer `Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>`.
