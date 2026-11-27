#!/usr/bin/env bash
./gradlew connectedDebugAndroidTest --no-daemon 2>&1 | tee gradle.log
status=${PIPESTATUS[0]}
mkdir -p layar
for f in $(adb shell ls /data/local/tmp/ | tr -d '\r' | grep '^layar-'); do
  adb pull "/data/local/tmp/$f" "layar/$f"
done
exit $status
