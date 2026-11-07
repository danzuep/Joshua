Releasing
=========

Releases are automated, see [Automated releases](#automated-releases) below. In short: merge a PR whose title starts with `major:`, `minor:` or `patch:`, and the next `vX.Y.Z` tag and GitHub Release are created for you. The app version is taken from the tag, so there is nothing to bump in `Configurations.kt`.

To release by hand instead:

 1. Update `docs/CHANGELOG.md` and merge it.
 2. `git tag -a vX.Y.Z -m "vX.Y.Z" && git push origin vX.Y.Z`. The `Release APK` workflow builds and publishes the APKs to GitHub Releases.
 3. To re-run a release for an existing tag: `gh workflow run release.yml --ref vX.Y.Z`.

To build locally: `./gradlew clean assembleRelease`.

Debug in a dev container:
```shell
# ./gradlew :app:kaptDebugKotlin --stacktrace --no-daemon -q
# ./gradlew :app:dependencyInsight --dependency kotlin-stdlib --configuration debugCompileClasspath --no-daemon
./gradlew assembleDebug --stacktrace --full-stacktrace --no-daemon --console=plain 2>&1 | tee build/full.log
./gradlew clean assembleDebug --stacktrace --full-stacktrace --no-daemon --console=plain 2>&1 | tee build/full.log
cat build/full.log | awk 'BEGIN{r=0} /KaptBaseError/{k=NR} {a[NR]=$0} END{for(i=k-1;i>=1;i--) if (a[i] ~ /Caused by: /) {start=i; break} if(!start) start=k-200; for(i=start;i<=k+20;i++) if(a[i]) print a[i]}' build/full.log
./gradlew :app:testDebugUnitTest --tests "*.BibleReadingManagerTest" --tests "*.ChapterListViewTest"
./gradlew --rerun-tasks test
```

We use [Semantic Versioning](https://semver.org/).

Prerequisites
-------------

Create a `keystore.properties` file at project's root folder:
```
KEYSTORE_FILE=<keystore file>
KEYSTORE_PASSWORD=<keystore password>
KEY_ALIAS=<key alias>
KEY_PASSWORD=<key password>
```

### Configure CI signing

Install the JDK (`keytool`) and GitHub CLI (`gh`), and authenticate with `gh auth login` using an account with repository administration access. From the repository root, run in Bash (including Git Bash on Windows):

```bash
bash .github/scripts/setup-release-secrets.sh
```

Enter one password of at least 6 characters at the hidden prompt. The [setup script](../.github/scripts/setup-release-secrets.sh) detects the repository from the Git `origin` remote using `gh` (not the upstream or CLI default repository), derives `KEY_ALIAS` from its lowercase name (`Yeshua` becomes `yeshua`), and creates `../yeshua-release.keystore` in PKCS12 format if it does not exist. It uses the same password for `KEYSTORE_PASSWORD` and `KEY_PASSWORD`, generates `KEYSTORE_BASE64` directly into the upload without a temporary Base64 file, and sets all four secrets in the tag-only `release` environment. Passwords are not passed as command-line arguments.

By default, an existing local keystore is reused, not replaced; it must contain the derived alias and use the same password for the keystore and key. Existing JKS keystores do not need conversion. To choose another repository or an existing keystore path:

```bash
bash .github/scripts/setup-release-secrets.sh danzuep/Yeshua ../yeshua-release.keystore
```

To deliberately generate a new signing key and overwrite the local keystore and all four CI secrets, add `--overwrite`:

```bash
bash .github/scripts/setup-release-secrets.sh --overwrite
```

The flag also works with explicit repository and keystore arguments. Back up the original keystore first. The local file is replaced only after key generation succeeds, but CI uploads are not transactional: if an upload fails, rerun without `--overwrite` to upload the same new key.

For a published app, always reuse its original signing key: a new key cannot sign updates to existing installations. If CI already has `KEYSTORE_BASE64` but the local keystore is missing, the script refuses to generate a replacement unless `--overwrite` is explicitly supplied. Keep the keystore and password securely backed up; do not commit or share them.

Branch runs of `release.yml` use the secret-less `dry-run` environment. Without a keystore the release APK is signed with the debug key (installable, but not suitable for updates from a different key).

More about app signing can be found [here](https://developer.android.com/studio/publish/app-signing).

## Automated releases

- Merging a PR into `main` runs the *Auto Tag* workflow, which creates the next `vX.Y.Z` tag from the prefix of the PR title: `major: ...` bumps X, `minor: ...` bumps Y, `patch: ...` bumps Z. Any other title creates no tag and no release. After a tag is created it starts the *Release APK* workflow.
- The release workflow stamps the tag into the app (`versionName` and `versionCode` via `-PappVersion`) and publishes two APKs: `Yeshua-vX.Y.Z.apk`, and `Yeshua-vX.Y.Z-with-WEB.apk`, which has the WEB Bible prepackaged (installed on first launch when no translation is present). The workflow parses the WEB USFM zip during the build with the `BundledDatabaseGenerator` unit test (`BUNDLE_DIR=<dir>` containing `list.tsv` and `translations/<id>.zip`), and builds with `-PbundledAssetsDir=<dir>` containing the resulting `bundled/bundled.db`, which the app copies into its database on first launch. As a fallback the app also accepts `bundled/list.tsv` (id, name and language separated by tabs) and `bundled/translations/<id>.zip` (USFM), which it parses on first launch.


F-Droid
-------

Store listing text, icon and changelogs live in `fastlane/metadata/android/en-US/`. Add `changelogs/<versionCode>.txt` (versionCode is `major*10000 + minor*100 + patch`, e.g. `2701` for v0.27.1) for each release. To get listed, open a merge request to [fdroiddata](https://gitlab.com/fdroid/fdroiddata) using `docs/com.github.danzuep.yeshua.android.yml` as the metadata file. F-Droid builds from the tag and signs with its own key, so users can't update between the GitHub APK and the F-Droid build without reinstalling, unless F-Droid reproducible builds are set up with the release keystore.
