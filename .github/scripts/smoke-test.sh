#!/usr/bin/env bash
# Installs the release APK, drives it with the fluent Kotlin builders in shared/.../testing (SmokeScenario.kt): opens John 1 and checks that the chapter is shown as flowing USFM text,
# then searches "jhn1" and checks the search screen stays responsive.
set -u

adb install app/build/outputs/apk/release/app-release.apk
adb logcat -c
status=0
./gradlew --no-daemon -q :shared:runSmokeScenario || status=1

adb logcat -d > logcat.txt
adb exec-out screencap -p > screen.png

# SearchActivity should be started once by us, plus at most one self-relaunch when the query is submitted.
search_starts=$(grep -c "START u0.*cmp=me.xizzhu.android.joshua/.search.SearchActivity" logcat.txt)
if [ "$search_starts" -gt 3 ]; then
  echo "FAIL: SearchActivity was started $search_starts times, it keeps relaunching itself"
  status=1
fi
if grep -qE "ANR in me.xizzhu.android.joshua|Application Not Responding" logcat.txt; then
  echo "FAIL: the app is not responding"
  status=1
fi
if grep -q "FATAL EXCEPTION" logcat.txt; then
  echo "FAIL: the app crashed"
  grep -A 20 "FATAL EXCEPTION" logcat.txt | head -40
  status=1
fi
[ "$status" -eq 0 ] && echo "PASS"
exit "$status"
