#!/bin/sh
# UI interaction smoke test. Runs in the SAME emulator session as diagnose.sh.
# NOTE: android-emulator-runner executes each script LINE in its own `sh -c`,
# so all logic must stay inside a file like this one.
set -u
mkdir -p out

PKG=com.example.kuiklynotes
APK=$(find androidApp/build/outputs/apk/release -name '*.apk' ! -name '*unsigned*' | head -n 1)
LOG=out/smoke-logcat.txt

shot() {
  adb shell screencap -p /sdcard/smoke.png > /dev/null 2>&1 || true
  adb pull /sdcard/smoke.png "out/$1" > /dev/null 2>&1 || true
}
tap() { adb shell input tap "$1" "$2" || true; sleep 1; }
wait_log() {
  # wait_log <pattern> <seconds>
  n=0
  while [ "$n" -lt "${2:-12}" ]; do
    if adb logcat -d | grep -q "$1"; then return 0; fi
    sleep 1
    n=$((n + 1))
  done
  return 1
}

adb logcat -c || true
adb install -r "$APK" || true
adb shell am start -W -n "$PKG/.MainActivity" || true
sleep 8

SIZE=$(adb shell wm size | tr -d '\r' | grep -oE '[0-9]+x[0-9]+' | head -n 1)
W=${SIZE%x*}
H=${SIZE#*x}
case "$W" in ''|*[!0-9]*) W=1080 ;; esac
case "$H" in ''|*[!0-9]*) H=1920 ;; esac
echo "screen: ${W}x${H}"

echo "===== SMOKE: HOME ====="
shot 01-home.png

echo "===== SMOKE: OPEN EDITOR (FAB -> native Activity) ====="
tap $((W * 86 / 100)) $((H * 83 / 100))
sleep 3
if wait_log "MARK editorOpen" 12; then
  echo "OK: editor requested"
else
  echo "FAIL: editor did not open"
fi
sleep 2
if adb shell dumpsys activity activities | grep -q "EditorActivity"; then
  echo "OK: native EditorActivity is running"
else
  echo "FAIL: EditorActivity not found in the activity stack"
fi
shot 02-editor.png

echo "===== SMOKE: TYPE INTO NATIVE EDITTEXT ====="
tap $((W / 2)) $((H * 55 / 100))
adb shell input text 'KuiklyNativeEditor' > /dev/null 2>&1 || true
sleep 2
shot 03-typed.png

echo "===== SMOKE: BACK TO LIST ====="
adb shell input keyevent KEYCODE_BACK || true
sleep 3
if wait_log "MARK pageAppear" 12; then
  echo "OK: back to list after editor"
else
  echo "FAIL: did not return to the note list"
fi
shot 05-back.png

echo "===== SMOKE: DELETE IS TWO-STEP (intent hook) ====="
adb shell am force-stop "$PKG" || true
sleep 1
adb logcat -c || true
adb shell am start -W -n "$PKG/.MainActivity" --es delete_test 1 || true
sleep 5
if wait_log "MARK deleteArmed" 15; then
  echo "OK: first tap only arms the delete"
else
  echo "FAIL: delete was not armed"
fi
if wait_log "MARK deleted" 10; then
  echo "OK: second tap performs the delete"
else
  echo "FAIL: delete did not happen"
fi
shot 06-deleted.png

echo "===== SMOKE: TODO TAB (via intent, deterministic) ====="
adb shell am force-stop "$PKG" || true
sleep 1
adb logcat -c || true
adb shell am start -W -n "$PKG/.MainActivity" --ei tab 1 || true
sleep 4
if wait_log "MARK tab=1" 15; then
  echo "OK: todo tab selected"
else
  echo "FAIL: todo tab not selected"
fi
shot 08-todo.png

echo "===== SMOKE: NOTES TAB (via intent, deterministic) ====="
adb shell am force-stop "$PKG" || true
sleep 1
adb logcat -c || true
adb shell am start -W -n "$PKG/.MainActivity" --ei tab 0 || true
sleep 4
wait_log "MARK tab=0" 12 || true
shot 09-notes.png

echo "===== SMOKE: 500K STRESS SAMPLE (intent hook) ====="
adb shell am force-stop "$PKG" || true
sleep 1
adb logcat -c || true
adb shell am start -W -n "$PKG/.MainActivity" --ei stress 500000 || true
sleep 4
if wait_log "MARK stressRequest" 20; then
  echo "OK: stress request received"
else
  echo "FAIL: stress request not received"
fi
sleep 6
shot 10-stress.png

echo "===== SMOKE: TYPE INTO 500K DOCUMENT ====="
tap $((W / 2)) $((H * 55 / 100))
adb shell input text 'X' > /dev/null 2>&1 || true
sleep 3
shot 11-stress-typed.png

echo "===== SMOKE: SCROLL THE 500K BODY ====="
# Swipe upwards three times: a working body should scroll far more than a line.
adb shell input swipe $((W / 2)) $((H * 70 / 100)) $((W / 2)) $((H * 30 / 100)) 300 || true
sleep 1
adb shell input swipe $((W / 2)) $((H * 70 / 100)) $((W / 2)) $((H * 30 / 100)) 300 || true
sleep 1
adb shell input swipe $((W / 2)) $((H * 70 / 100)) $((W / 2)) $((H * 30 / 100)) 300 || true
sleep 2
shot 12-stress-scrolled.png
# Native EditText must report both MOVE events and a scrolling offset.
MOVE_LAST=$(adb logcat -d | grep -oE "MOVE n=[0-9]+" | tail -n 1 | grep -oE "[0-9]+")
SCROLL_LAST=$(adb logcat -d | grep -oE "SCROLL y=[0-9]+" | tail -n 1 | grep -oE "[0-9]+")
echo "move events to editor: ${MOVE_LAST:-none}, scroll offset: ${SCROLL_LAST:-none}"
if [ -n "$MOVE_LAST" ] && [ "$MOVE_LAST" -gt 5 ]; then
  echo "OK: gestures reach the native editor (n=$MOVE_LAST)"
else
  echo "FAIL: gestures are still being intercepted (n=${MOVE_LAST:-0})"
fi
if [ -n "$SCROLL_LAST" ] && [ "$SCROLL_LAST" -gt 200 ]; then
  echo "OK: long document scrolls far (y=$SCROLL_LAST)"
else
  echo "FAIL: document barely scrolled (y=${SCROLL_LAST:-0})"
fi

echo "===== SMOKE: LONG-PRESS MULTI-SELECT DELETE (intent hook) ====="
adb shell am force-stop "$PKG" || true
sleep 1
adb logcat -c || true
adb shell am start -W -n "$PKG/.MainActivity" --es select_test 1 || true
sleep 5
if wait_log "MARK selectionOn" 15; then
  echo "OK: long-press enters selection mode"
else
  echo "FAIL: selection mode did not start"
fi
if wait_log "MARK bulkDeleted" 10; then
  echo "OK: bulk delete removed the selected notes"
else
  echo "FAIL: bulk delete did not run"
fi
shot 13-bulk-deleted.png

echo "===== SMOKE: MONKEY (200 events) ====="
adb shell am force-stop "$PKG" || true
sleep 1
adb shell am start -W -n "$PKG/.MainActivity" || true
sleep 6
adb shell monkey -p "$PKG" --throttle 80 -s 20261110 \
  --pct-touch 70 --pct-motion 20 --pct-appswitch 2 --pct-anyevent 8 200 \
  > out/monkey.txt 2>&1 || true
sleep 3
shot 14-final.png

echo "===== SMOKE: CAPTURE ====="
adb logcat -d -b crash -b main -v time > "$LOG" 2>&1 || true
adb shell dumpsys activity activities > out/activities.txt 2>&1 || true
adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || true
adb pull /sdcard/ui.xml out/ui.xml > /dev/null 2>&1 || true

echo "===== SMOKE: PROCESS ====="
adb shell pidof "$PKG" || echo "PROCESS NOT RUNNING (crashed)"

echo "===== SMOKE: CRASH / ANR SIGNATURES ====="
grep -n -iE "FATAL EXCEPTION|ANR |Input dispatching timed out|Force finishing activity|has died|NoClassDefFoundError|ClassNotFoundException|handleException|必须需要且仅一个" "$LOG" | head -n 80 || echo "no crash markers"

echo "===== NATIVE EDITOR LOAD TIMING ====="
grep -a -E "HRNotesEditor.*LOAD" "$LOG" | head -n 10 || echo "no LOAD log"

echo "===== APP MARKS ====="
grep -a -oE "MARK [a-zA-Z]+[^]]*" "$LOG" | sort | uniq -c | head -n 30 || true

echo "===== MONKEY RESULT ====="
tail -n 6 out/monkey.txt

if grep -qiE "FATAL EXCEPTION|Force finishing activity|Input dispatching timed out|handleException|必须需要且仅一个" "$LOG"; then
  echo "::error::UI smoke test detected a crash/ANR/error - see SMOKE signatures above"
fi
