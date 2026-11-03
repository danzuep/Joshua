#!/usr/bin/env bash
# Installs the release APK, launches it straight into John 1 and checks that the chapter is shown as flowing USFM text,
# then searches "jhn1" and checks the search screen stays responsive.
set -u

# Taps the center of the node whose content-desc contains $1 in ui.xml; returns 1 if there is none.
tap_desc() {
  local xy
  xy=$(DESC="$1" python3 - << 'PY'
import os, re
xml = open("ui.xml", encoding="utf-8").read()
node = re.search(r'<node[^>]*content-desc="[^"]*' + os.environ["DESC"] + r'[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
if node:
    x1, y1, x2, y2 = map(int, node.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2)
PY
)
  [ -n "$xy" ] && adb shell input tap $xy
}

dump_ui() {
  rm -f ui.xml
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml ui.xml > /dev/null 2>&1
}

adb install app/build/outputs/apk/release/app-release.apk
adb logcat -c
# Open Genesis first (importing the bundled translation takes a while), then jump to another book. A page must
# never be left on the loading spinner after the jump.
adb shell am start -W --activity-single-top -n me.xizzhu.android.joshua/.LauncherActivity --ei me.xizzhu.android.joshua.KEY_OPEN_BOOK 0
sleep 20
# Open straight to the requested book (0-based index, default 42 = John) instead of navigating the UI.
adb shell am start -W --activity-single-top -n me.xizzhu.android.joshua/.LauncherActivity --ei me.xizzhu.android.joshua.KEY_OPEN_BOOK "${SMOKE_BOOK_INDEX:-42}"

# The bundled translation is imported on first launch, so give it time to appear.
sleep 10
dump_ui
found=0
if grep -q "In the beginning was the Word" ui.xml 2> /dev/null; then
  found=1
fi

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
if ! grep -q "The light shines in the darkness" ui.xml 2> /dev/null; then
  echo "FAIL: expected flowing paragraph text (John 1:1-5 not in one paragraph)"
  status=1
fi
# Long-press a verse to open the action mode and capture it, so the verse menu icons can be checked.
verse_xy=$(python3 - << 'PY'
import re
xml = open("ui.xml", encoding="utf-8").read()
node = re.search(r'<node[^>]*text="[^"]*In the beginning was the Word[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
if node:
    x1, y1, x2, y2 = map(int, node.groups())
    print((x1 + x2) // 2, y1 + 40)
PY
)
if [ -n "$verse_xy" ]; then
  adb shell input swipe $verse_xy $verse_xy 1500
  sleep 2
  adb exec-out screencap -p > screen-actionmode.png
  rm -f ui-actionmode.xml
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml ui-actionmode.xml > /dev/null 2>&1
  for desc in "Highlight" "Copy" "Share" "Bookmark"; do
    if ! grep -q "content-desc=\"[^\"]*$desc" ui-actionmode.xml 2> /dev/null; then
      echo "FAIL: verse action mode is missing the $desc icon"
      status=1
    fi
  done
  # Bookmark the verse: the heart goes at the end of verse 1 (in the second paragraph), not after its first part.
  cp ui-actionmode.xml ui.xml
  if tap_desc "Bookmark"; then
    sleep 2
    adb exec-out screencap -p > screen-bookmarked.png
    # Tapping bookmark on a bookmarked verse removes the bookmark.
    adb shell input swipe $verse_xy $verse_xy 1500
    sleep 2
    dump_ui
    tap_desc "Bookmark" || { echo "FAIL: no Bookmark icon the second time"; status=1; }
    sleep 2
    adb exec-out screencap -p > screen-unbookmarked.png
  else
    echo "FAIL: could not tap the Bookmark icon"
    status=1
  fi
  adb shell input keyevent KEYCODE_BACK
  sleep 1
else
  echo "FAIL: could not find a verse to long-press"
  status=1
fi
# Regression test: submitting "jhn1" used to relaunch SearchActivity in an endless loop, freezing the screen after the results showed.
adb shell am start -W -n me.xizzhu.android.joshua/.search.SearchActivity > /dev/null
sleep 3
adb shell input text jhn1
# Instant search waits for a typing pause before running.
sleep 5
adb shell input keyevent KEYCODE_ENTER
sleep 8
dump_ui
cp ui.xml ui-search.xml 2> /dev/null
if ! grep -q "In the beginning was the Word" ui-search.xml 2> /dev/null; then
  echo "FAIL: searching jhn1 did not show John 1 (or the UI dump timed out because the UI is frozen)"
  status=1
fi

# The clear button must still work, which fails if the main thread is stuck.
clear_xy=$(python3 - << 'PY'
import re
xml = open("ui-search.xml", encoding="utf-8").read()
node = re.search(r'<node[^>]*content-desc="Clear query"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
if node:
    x1, y1, x2, y2 = map(int, node.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2)
PY
)
if [ -z "$clear_xy" ]; then
  echo "FAIL: no clear button in the search screen"
  status=1
else
  adb shell input tap $clear_xy
  sleep 3
  dump_ui
  if grep -q "In the beginning was the Word" ui.xml 2> /dev/null; then
    echo "FAIL: the clear button did not clear the search results"
    status=1
  fi
fi

adb shell input keyevent KEYCODE_BACK
sleep 2
adb shell input keyevent KEYCODE_BACK
sleep 3
if adb shell dumpsys activity activities | grep -E "mResumedActivity|ResumedActivity" | grep -q "SearchActivity"; then
  echo "FAIL: back did not leave the search screen"
  status=1
fi

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
