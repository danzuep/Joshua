#!/usr/bin/env bash
# Installs the release APK, launches it straight into John 1 and checks that the chapter is shown as flowing USFM text.
set -u

dump_ui() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml ui.xml > /dev/null 2>&1
}

adb install app/build/outputs/apk/release/app-release.apk
adb logcat -c
# Open straight to the requested book (0-based index, default 42 = John) instead of navigating the UI.
adb shell am start -W -n me.xizzhu.android.joshua/.LauncherActivity --ei me.xizzhu.android.joshua.KEY_OPEN_BOOK "${SMOKE_BOOK_INDEX:-42}"

# The bundled translation is imported on first launch, so give it time to appear.
sleep 20
dump_ui
found=0
if grep -q "In the beginning was the Word" ui.xml 2> /dev/null; then
  found=1
fi

adb logcat -d > logcat.txt
adb exec-out screencap -p > screen.png

status=0
if [ "$found" -ne 1 ]; then
  echo "FAIL: John 1 text was not shown"
  status=1
fi
# The paragraph / heading markers must never leak into the UI.
if grep -qP '\x{2028}|\x{2029}' ui.xml 2> /dev/null; then
  echo "FAIL: USFM structure markers leaked into the UI"
  status=1
fi
# The chapter flows in paragraphs rather than one row per verse, so verse 5 shares a view with verse 1.
if ! grep -q "5 The light shines in the darkness" ui.xml 2> /dev/null; then
  echo "FAIL: expected flowing paragraph text (John 1:1-5 not in one paragraph)"
  status=1
fi
if grep -q "FATAL EXCEPTION" logcat.txt; then
  echo "FAIL: the app crashed"
  grep -A 20 "FATAL EXCEPTION" logcat.txt | head -40
  status=1
fi
[ "$status" -eq 0 ] && echo "PASS"
exit "$status"
