#!/bin/sh
# UI interaction smoke test. Runs in the SAME emulator session as diagnose.sh.
# NOTE: android-emulator-runner executes each script LINE in its own `sh -c`,
# so all logic must stay inside a file like this one.
set -u
mkdir -p out

PKG=com.example.kuiklynotes
APK=$(find androidApp/build/outputs/apk/release -name '*.apk' ! -name '*unsigned*' | head -n 1)

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

shot() {
  adb shell screencap -p /sdcard/smoke.png > /dev/null 2>&1 || true
  adb pull /sdcard/smoke.png "out/$1" > /dev/null 2>&1 || true
}
tap() { adb shell input tap "$1" "$2" || true; sleep 1; }
restart() {
  adb shell am force-stop "$PKG" || true
  sleep 1
  adb shell am start -W -n "$PKG/.MainActivity" || true
  sleep 5
}

echo "===== SMOKE: HOME (notes grid) ====="
shot 01-home.png

echo "===== SMOKE: FAB -> EDITOR ====="
tap $((W * 86 / 100)) $((H * 83 / 100))
sleep 3
shot 02-editor.png

echo "===== SMOKE: TYPE INTO NATIVE EDITTEXT ====="
tap $((W / 2)) $((H * 55 / 100))
adb shell input text 'HelloKuiklyNativeEditor' > /dev/null 2>&1 || true
sleep 2
shot 03-typed.png

echo "===== SMOKE: BACK TO LIST ====="
tap $((W * 6 / 100)) $((H * 5 / 100))
sleep 2
shot 04-back.png

echo "===== SMOKE: TODO TAB ====="
tap $((W * 72 / 100)) $((H * 94 / 100))
sleep 2
shot 05-todo.png

echo "===== SMOKE: EDITOR THREE-DOT MENU ====="
restart
tap $((W * 86 / 100)) $((H * 83 / 100))
sleep 2
tap $((W * 93 / 100)) $((H * 5 / 100))
sleep 2
shot 06-editor-menu.png

echo "===== SMOKE: MONKEY (200 events) ====="
restart
adb shell monkey -p "$PKG" --throttle 80 -s 20261110 \
  --pct-touch 70 --pct-motion 20 --pct-appswitch 2 --pct-anyevent 8 200 \
  > out/monkey.txt 2>&1 || true
sleep 3

echo "===== SMOKE: CAPTURE ====="
adb logcat -d -b crash -b main -v time > out/smoke-logcat.txt 2>&1 || true
adb shell dumpsys activity activities > out/activities.txt 2>&1 || true
shot 07-final.png

echo "===== SMOKE: PROCESS ====="
adb shell pidof "$PKG" || echo "PROCESS NOT RUNNING (crashed)"

echo "===== SMOKE: CRASH / ANR SIGNATURES ====="
grep -n -iE "FATAL EXCEPTION|AndroidRuntime|ANR |Input dispatching timed out|Force finishing activity|has died|NoClassDefFoundError|ClassNotFoundException|handleException" out/smoke-logcat.txt | head -n 120 || echo "no crash markers"

echo "===== NATIVE EDITOR PRESENT? ====="
grep -a -iE "HRNotesEditor|NotesPage|LaunchData" out/smoke-logcat.txt | head -n 20 || echo "no editor markers"

echo "===== MONKEY RESULT ====="
tail -n 8 out/monkey.txt

echo "===== SMOKE LOGCAT TAIL ====="
tail -n 80 out/smoke-logcat.txt

if grep -qiE "FATAL EXCEPTION|Force finishing activity|Input dispatching timed out|handleException" out/smoke-logcat.txt; then
  echo "::error::UI smoke test detected a crash/ANR/error - see SMOKE signatures above"
fi
