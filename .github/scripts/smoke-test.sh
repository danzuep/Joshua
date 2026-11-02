#!/usr/bin/env bash
# Installs the release APK, opens the search screen, opens its overflow menu and saves a screenshot.
set -u

adb install app/build/outputs/apk/release/app-release.apk
adb logcat -c
# Launch once so the bundled translation is imported, then open search.
adb shell am start -W -n me.xizzhu.android.joshua/.LauncherActivity
sleep 25
adb shell am start -W -n me.xizzhu.android.joshua/.search.SearchActivity
sleep 3
adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
adb pull /sdcard/ui.xml ui.xml > /dev/null 2>&1

overflow_xy=$(python3 - << 'PY'
import re
xml = open("ui.xml", encoding="utf-8").read()
node = re.search(r'<node[^>]*content-desc="More options"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
if node:
    x1, y1, x2, y2 = map(int, node.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2)
PY
)
[ -n "$overflow_xy" ] && adb shell input tap $overflow_xy
sleep 2
adb exec-out screencap -p > screen-search-menu.png
adb logcat -d > logcat.txt
[ -n "$overflow_xy" ] || { echo "FAIL: no overflow button found"; exit 1; }
