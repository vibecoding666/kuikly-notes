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

echo "===== SMOKE: OPEN EDITOR (FAB) ====="
tap $((W * 86 / 100)) $((H * 83 / 100))
sleep 2
if wait_log "MARK editorOpen" 12; then
  echo "OK: editor opened"
else
  echo "FAIL: editor did not open"
fi
shot 02-editor.png

echo "===== SMOKE: TYPE INTO NATIVE EDITTEXT ====="
tap $((W / 2)) $((H * 55 / 100))
adb shell input text 'KuiklyNativeEditor' > /dev/null 2>&1 || true
sleep 2
if wait_log "MARK meta length=" 12; then
  echo "OK: native editor reported meta"
else
  echo "FAIL: no meta event from native editor"
fi
shot 03-typed.png

echo "===== SMOKE: EDITOR THREE-DOT MENU ====="
adb logcat -c || true
tap $((W * 93 / 100)) $((H * 5 / 100))
sleep 1
tap $((W * 95 / 100)) $((H * 4 / 100))
sleep 2
if wait_log "MARK menuOpen=true" 10; then
  echo "OK: editor three-dot menu opened"
else
  echo "FAIL: editor three-dot menu did not open"
fi
shot 04-editor-menu.png

echo "===== SMOKE: BACK TO LIST ====="
tap $((W * 8 / 100)) $((H * 8 / 100))
sleep 2
if ! wait_log "MARK editorClose" 6; then
  echo "retry: system back key"
  adb shell input keyevent KEYCODE_BACK || true
  sleep 2
fi
if wait_log "MARK editorClose" 12; then
  echo "OK: editor closed"
else
  echo "FAIL: editor did not close (back key missed)"
fi
shot 05-back.png

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
shot 06-todo.png

echo "===== SMOKE: NOTES TAB (via intent, deterministic) ====="
adb shell am force-stop "$PKG" || true
sleep 1
adb logcat -c || true
adb shell am start -W -n "$PKG/.MainActivity" --ei tab 0 || true
sleep 4
wait_log "MARK tab=0" 12 || true
shot 07-notes.png

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
shot 08-stress.png

echo "===== SMOKE: TYPE INTO 500K DOCUMENT ====="
tap $((W / 2)) $((H * 55 / 100))
adb shell input text 'X' > /dev/null 2>&1 || true
sleep 3
shot 09-stress-typed.png

echo "===== SMOKE: MONKEY (200 events) ====="
adb shell am force-stop "$PKG" || true
sleep 1
adb shell am start -W -n "$PKG/.MainActivity" || true
sleep 6
adb shell monkey -p "$PKG" --throttle 80 -s 20261110 \
  --pct-touch 70 --pct-motion 20 --pct-appswitch 2 --pct-anyevent 8 200 \
  > out/monkey.txt 2>&1 || true
sleep 3
shot 10-final.png

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
