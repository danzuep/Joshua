# Notes for AI agents

## Building and testing in the sandbox

The agent sandbox **cannot run the Gradle build or unit tests locally**:

- There is no Android SDK, and `https://dl.google.com` (Android SDK packages and the Google Maven mirror used by AGP) fails DNS/connection with no HTTP response. Gradle therefore fails resolving Google Maven dependencies.
- Maven Central, `plugins.gradle.org` and `services.gradle.org` are reachable, but that is not enough, because AGP and AndroidX come from Google.
- The sandbox JDK is 27 and the project targets JDK 25 (Temurin in CI), so a local build would not match CI anyway.

Don't spend time trying to work around this (no installing the SDK, mirrors or offline caches). Use GitHub Actions as the build and test runner instead, with `gh`:

| Goal | How |
|------|-----|
| Build and unit tests (`./gradlew testDebugUnitTest assembleDebug`, about 4 min) | Push the branch and open a PR (`gh pr create`). `Android CI` runs on pull requests and on pushes to `master`. It has no `workflow_dispatch`. |
| Emulator smoke test of the release APK (about 4-8 min) | `gh workflow run smoke-test.yml --ref <branch>` |
| Release APKs (plain and with WEB) | `gh workflow run release.yml --ref <branch>`. This is a dry run unless the ref is a `v*` tag. See [docs/RELEASING.md](docs/RELEASING.md). |

Check results with `gh run list --repo danzuep/Joshua --branch <branch>`, `gh pr checks <n>`, and `gh run view <id> --log-failed`. Smoke-test artifacts (`logcat.txt`, `screen.png`, `ui.xml`) can be fetched with `gh run download <id> -n smoke-results`.

Before pushing, review Kotlin changes carefully (unresolved references, imports, scope), because each CI round trip costs minutes. Static checks that work locally: `git diff`, `grep`, and `bash -n` on shell scripts.

## Repository facts

- `master` is protected. Open PRs; the maintainer merges them.
- Merging a PR auto-tags the next version from the PR title prefix (`major:`, `minor:`, `patch:`; any other title creates no tag) and starts the release. See `.github/workflows/auto-tag.yml`.
- Known-bad dependency versions that broke CI on JDK 25: Robolectric 4.17, Hilt 2.60.1, KSP 2.3.13. Keep Robolectric 4.16.1, Hilt 2.59.2, KSP 2.3.6 (`buildSrc/src/main/kotlin/Configurations.kt`).
- Some unit tests can be flaky because of leftover coroutines. `BaseUnitTest.tearDown` cancels the shared test scope; cancel any view-model scope you create in a test too.
- The reading screen accepts the launch extra `me.xizzhu.android.joshua.KEY_OPEN_BOOK` (0-based book index) to open a book directly, which the smoke test uses.
- Commits carry the trailer `Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>`.
