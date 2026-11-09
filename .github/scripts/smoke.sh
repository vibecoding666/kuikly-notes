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
tap() {
  adb shell input tap "$1" "$2" || true
  sleep 1
}
restart() {
  adb shell am force-stop "$PKG" || true
  sleep 1
  adb shell am start -W -n "$PKG/.MainActivity" || true
  sleep 5
}

echo "===== SMOKE: HOME ====="
shot 01-home.png

# --- New note -> type -> dismiss keyboard -> save ---
echo "===== SMOKE: NEW NOTE ====="
tap $((W / 8)) $((H - 26))
sleep 2
shot 02-editor.png

echo "===== SMOKE: TYPE TITLE ====="
tap $((W / 2)) 200
adb shell input text 'SmokeNote' > /dev/null 2>&1 || true
sleep 1
adb shell input keyevent 111 > /dev/null 2>&1 || true
sleep 1
shot 03-typed.png

echo "===== SMOKE: SAVE ====="
tap $((W / 3)) $((H - 35))
sleep 2
shot 04-after-save.png

# --- Stats overlay from a clean cold start ---
echo "===== SMOKE: STATS OVERLAY ====="
restart
tap $((W * 5 / 8)) $((H - 26))
sleep 2
shot 05-stats.png

# --- Tags overlay from a clean cold start ---
echo "===== SMOKE: TAGS OVERLAY ====="
restart
tap $((W * 3 / 8)) $((H - 26))
sleep 2
shot 06-tags.png

# --- Random exploration from a clean cold start ---
echo "===== SMOKE: MONKEY (300 events) ====="
restart
adb shell monkey -p "$PKG" --throttle 80 -s 20261109 \
  --pct-touch 70 --pct-motion 20 --pct-appswitch 2 --pct-anyevent 8 300 \
  > out/monkey.txt 2>&1 || true
sleep 3

echo "===== SMOKE: CAPTURE ====="
adb logcat -d -b crash -b main -v time > out/smoke-logcat.txt 2>&1 || true
adb shell dumpsys activity activities > out/activities.txt 2>&1 || true
shot 07-final.png

echo "===== SMOKE: PROCESS ====="
adb shell pidof "$PKG" || echo "PROCESS NOT RUNNING (crashed)"

echo "===== SMOKE: CRASH / ANR SIGNATURES ====="
grep -n -iE "FATAL EXCEPTION|AndroidRuntime|ANR |Input dispatching timed out|Force finishing activity|has died|NoClassDefFoundError|ClassNotFoundException" out/smoke-logcat.txt | head -n 120 || echo "no crash markers"

echo "===== MONKEY RESULT ====="
tail -n 12 out/monkey.txt

echo "===== SMOKE LOGCAT TAIL ====="
tail -n 100 out/smoke-logcat.txt

if grep -qiE "FATAL EXCEPTION|Force finishing activity|Input dispatching timed out" out/smoke-logcat.txt; then
  echo "::error::UI smoke test detected a crash/ANR - see SMOKE signatures above"
fi
