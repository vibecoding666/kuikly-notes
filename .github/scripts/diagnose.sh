#!/bin/sh
# Runs inside reactivecircus/android-emulator-runner.
# NOTE: that action executes each script LINE in its own `sh -c`, so all
# logic must live in a single file like this one (no multi-line if/then).
set -u
mkdir -p out

APK=$(find androidApp/build/outputs/apk/release -name '*.apk' ! -name '*unsigned*' | head -n 1)
echo "APK=$APK"

echo "===== APK BADGING (icon / launcher) ====="
"$ANDROID_HOME/build-tools/34.0.0/aapt2" dump badging "$APK" 2>/dev/null | grep -iE "^package:|application:|application-icon|launchable-activity:" || true

adb logcat -c || true
echo "===== INSTALL ====="
adb install -r "$APK" || true
adb shell pm list packages | grep kuiklynotes || true

echo "===== LAUNCH ====="
adb shell am start -W -n com.example.kuiklynotes/.MainActivity || true
sleep 12

echo "===== PROCESS STATE ====="
adb shell pidof com.example.kuiklynotes || echo "PROCESS NOT RUNNING (crashed)"

echo "===== CAPTURE ====="
adb logcat -d -b crash -b main -v time > out/logcat.txt 2>&1 || true
adb shell dumpsys package com.example.kuiklynotes > out/package.txt 2>&1 || true
adb shell screencap -p /sdcard/screen.png > /dev/null 2>&1 || true
adb pull /sdcard/screen.png out/screen.png > /dev/null 2>&1 || true

echo "===== CRASH SIGNATURES ====="
grep -n -iE "FATAL EXCEPTION|AndroidRuntime|Caused by|Unable to instantiate|ClassNotFoundException|NoSuchMethod|NullPointerException|IllegalStateException|Kuikly|has died|Force finishing activity" out/logcat.txt | head -n 150 || echo "no crash markers"

echo "===== LOGCAT TAIL (last 200 lines) ====="
tail -n 200 out/logcat.txt

if grep -qiE "FATAL EXCEPTION|Force finishing activity" out/logcat.txt; then
  echo "::error::App crashed - see CRASH SIGNATURES above"
fi
