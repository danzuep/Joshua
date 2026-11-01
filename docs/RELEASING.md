Releasing
=========

 1. Bump the app version code in `Configurations.kt`.
 2. Update the `docs/CHANGELOG.md`.
 3. `git commit -am "Prepare for release X.Y.Z."` (where X.Y.Z is the new version)
 4. `git tag -a X.Y.Z -m "vX.Y.Z"` (where X.Y.Z is the new version)
 5. `git push && git push --tags` (use a `vX.Y.Z` tag; the `Release APK` workflow builds and publishes the APK to GitHub Releases)

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

For CI, set the optional secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. Without a keystore the release APK is signed with the debug key (installable, but not suitable for updates from a different key).

More about app signing can be found [here](https://developer.android.com/studio/publish/app-signing).

## Automated releases

- Merging a PR into `master` runs the *Auto Tag* workflow, which creates the next `vX.Y.Z` tag from the prefix of the PR title: `major: ...` bumps X, `minor: ...` bumps Y, `patch: ...` bumps Z. Any other title creates no tag and no release. After a tag is created it starts the *Release APK* workflow.
- The release workflow stamps the tag into the app (`versionName` and `versionCode` via `-PappVersion`) and publishes two APKs: `Joshua-vX.Y.Z.apk`, and `Joshua-vX.Y.Z-with-WEB.apk`, which has the WEB Bible prepackaged (installed on first launch when no translation is present; built with `-PbundledAssetsDir=<dir>` containing `bundled/list.tsv` (id, name and language separated by tabs) and `bundled/translations/<id>.zip` (USFM)`).
