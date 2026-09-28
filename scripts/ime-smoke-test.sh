#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK_PATH="${1:-$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk}"
OUT_DIR="${IME_SMOKE_OUT:-$ROOT_DIR/artifacts/ime-smoke}"

PACKAGE="cloud.kosch.keyswiper"
SERVICE="$PACKAGE/.KeySwiperImeService"
HOST="$PACKAGE/.debug.ImeSmokeHostActivity"

mkdir -p "$OUT_DIR"

log() {
  printf '[ime-smoke] %s\n' "$*"
}

capture_diagnostics() {
  adb shell dumpsys input_method > "$OUT_DIR/dumpsys-input_method.txt" 2>&1 || true
  adb shell dumpsys window > "$OUT_DIR/dumpsys-window.txt" 2>&1 || true
  adb shell dumpsys activity activities > "$OUT_DIR/dumpsys-activity.txt" 2>&1 || true
  adb logcat -d -v threadtime > "$OUT_DIR/logcat.txt" 2>&1 || true
  grep -E "AndroidRuntime|FATAL EXCEPTION|$PACKAGE|InputMethod|TestRunner"     "$OUT_DIR/logcat.txt" > "$OUT_DIR/logcat-keyswiper.txt" 2>/dev/null || true
}

if [[ ! -f "$APK_PATH" ]]; then
  echo "APK not found: $APK_PATH" >&2
  exit 2
fi

adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true

log "Installing debug APK"
adb install -r "$APK_PATH" | tee "$OUT_DIR/install.txt"

PREVIOUS_IME="$(adb shell settings get secure default_input_method | tr -d '\r' || true)"

cleanup() {
  set +e
  capture_diagnostics
  if [[ -n "$PREVIOUS_IME" && "$PREVIOUS_IME" != "null" && "$PREVIOUS_IME" != "$SERVICE" ]]; then
    adb shell ime set "$PREVIOUS_IME" > "$OUT_DIR/restore-ime.txt" 2>&1 || true
  elif [[ -z "$PREVIOUS_IME" || "$PREVIOUS_IME" == "null" ]]; then
    adb shell ime reset > "$OUT_DIR/restore-ime.txt" 2>&1 || true
  fi
}
trap cleanup EXIT

adb logcat -c || true

log "Enabling KeySwiper IME"
adb shell ime enable "$SERVICE" | tee "$OUT_DIR/ime-enable.txt"
adb shell ime set "$SERVICE" | tee "$OUT_DIR/ime-set.txt"

SELECTED=""
for _ in $(seq 1 20); do
  SELECTED="$(adb shell settings get secure default_input_method | tr -d '\r' || true)"
  [[ "$SELECTED" == "$SERVICE" ]] && break
  sleep 1
done

if [[ "$SELECTED" != "$SERVICE" ]]; then
  echo "KeySwiper was not selected. Current default IME: $SELECTED" >&2
  exit 1
fi

# Starting from a stopped package catches cold-start and service rebinding problems.
adb shell am force-stop "$PACKAGE" || true

log "Launching debug-only focused text host"
HOST_LAUNCH="$(adb shell am start -W -n "$HOST" 2>&1 | tr -d '\r')"
printf '%s\n' "$HOST_LAUNCH" | tee "$OUT_DIR/host-launch.txt"
if ! grep -q "Status: ok" "$OUT_DIR/host-launch.txt"; then
  echo "Smoke host did not launch successfully." >&2
  exit 1
fi

VISIBLE=0
for _ in $(seq 1 45); do
  STATE="$(adb shell dumpsys input_method 2>/dev/null | tr -d '\r' || true)"
  printf '%s\n' "$STATE" > "$OUT_DIR/dumpsys-input_method-live.txt"

  PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
  CURRENT_OK=0
  VISIBLE_OK=0

  if grep -Fq "mCurId=$SERVICE" <<<"$STATE" || grep -Fq "mCurMethodId=$SERVICE" <<<"$STATE"; then
    CURRENT_OK=1
  fi

  if grep -Fq "mInputShown=true" <<<"$STATE" ||
     grep -Fq "mIsInputViewShown=true" <<<"$STATE" ||
     grep -Eq "mImeWindowVis=0x[13579bBdDfF]" <<<"$STATE"; then
    VISIBLE_OK=1
  fi

  if [[ -n "$PID" && "$CURRENT_OK" -eq 1 && "$VISIBLE_OK" -eq 1 ]]; then
    VISIBLE=1
    printf '%s\n' "$PID" > "$OUT_DIR/keyswiper-pid.txt"
    break
  fi

  sleep 1
done

if [[ "$VISIBLE" -ne 1 ]]; then
  echo "KeySwiper never became a live, current and visible IME." >&2
  exit 1
fi

log "PASS: KeySwiper is alive, selected and visible on Android 15."
