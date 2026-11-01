#!/usr/bin/env bash
# Installs the release APK, opens John 1 in the reader and checks that the chapter is shown as flowing USFM text.
set -u

dump_ui() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml ui.xml > /dev/null 2>&1
}

# Taps the centre of the first node whose text attribute equals $1. Returns 1 if it is not on screen.
tap_text() {
  local bounds
  bounds=$(grep -o "<node[^>]* text=\"$1\"[^>]*>" ui.xml 2> /dev/null | head -n 1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')
  [ -z "$bounds" ] && return 1
  read -r x1 y1 x2 y2 <<< "$bounds"
  adb shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
  sleep 2
}

adb install app/build/outputs/apk/release/app-release.apk
adb logcat -c
adb shell am start -W -n me.xizzhu.android.joshua/.LauncherActivity

# The bundled translation is imported on first launch. The reader opens at Genesis, which is empty
# in the minimal package, so open the chapter drawer and select John 1.
# Book names in the converted translation are USFM ids, so John is "JHN".
opened=0
for _ in $(seq 1 40); do
  sleep 3
  dump_ui
  if tap_text "JHN"; then
    dump_ui
    if tap_text "1"; then
      opened=1
      break
    fi
  elif grep -q 'text="OT"' ui.xml 2> /dev/null; then
    # The chapter drawer is open but John is off screen, so scroll its book list.
    adb shell input swipe 120 560 120 200 400
  else
    # Open the chapter drawer from the left edge, staying clear of the system back gesture.
    adb shell input swipe 4 600 600 600 300
    sleep 2
  fi
done

sleep 3
dump_ui
found=0
if grep -q "In the beginning was the Word" ui.xml 2> /dev/null; then
  found=1
fi

adb logcat -d > logcat.txt
adb exec-out screencap -p > screen.png

status=0
if [ "$found" -ne 1 ]; then
  echo "FAIL: John 1 text was not shown (navigation reached: $opened)"
  status=1
fi
# The paragraph / heading markers must never leak into the UI.
if grep -qP '\x{2028}|\x{2029}' ui.xml 2> /dev/null; then
  echo "FAIL: USFM structure markers leaked into the UI"
  status=1
fi
# The chapter flows in paragraphs rather than one row per verse, so later verses share the same view.
if ! grep -q "The Word became flesh" ui.xml 2> /dev/null; then
  echo "FAIL: expected flowing paragraph text (John 1:14 missing)"
  status=1
fi
if grep -q "FATAL EXCEPTION" logcat.txt; then
  echo "FAIL: the app crashed"
  grep -A 20 "FATAL EXCEPTION" logcat.txt | head -40
  status=1
fi
[ "$status" -eq 0 ] && echo "PASS"
exit "$status"
