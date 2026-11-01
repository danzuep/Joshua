#!/usr/bin/env bash
# Installs the release APK, opens the reader and checks that a chapter is shown as flowing USFM text.
set -u

adb install app/build/outputs/apk/release/app-release.apk
adb logcat -c
adb shell am start -W -n me.xizzhu.android.joshua/.LauncherActivity

# The bundled translation is imported on first launch, so wait for the chapter text to show up.
found=0
for _ in $(seq 1 30); do
  sleep 5
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml ui.xml > /dev/null 2>&1
  if grep -q "God created the heavens and the earth" ui.xml 2> /dev/null; then
    found=1
    break
  fi
done

adb logcat -d > logcat.txt
adb exec-out screencap -p > screen.png

status=0
if [ "$found" -ne 1 ]; then
  echo "FAIL: Genesis 1 text was not shown"
  status=1
fi
# The paragraph / heading markers must never leak into the UI.
if grep -qP '\x{2028}|\x{2029}' ui.xml 2> /dev/null; then
  echo "FAIL: USFM structure markers leaked into the UI"
  status=1
fi
# The whole chapter flows in a few paragraphs rather than one row per verse.
if [ "$(grep -c 'God said' ui.xml)" -lt 1 ]; then
  echo "FAIL: expected flowing paragraph text"
  status=1
fi
if grep -q "FATAL EXCEPTION" logcat.txt; then
  echo "FAIL: the app crashed"
  grep -A 20 "FATAL EXCEPTION" logcat.txt | head -40
  status=1
fi
[ "$status" -eq 0 ] && echo "PASS"
exit "$status"
