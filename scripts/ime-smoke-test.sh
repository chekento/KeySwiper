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
  grep -E "AndroidRuntime|FATAL EXCEPTION|ANR in|failed to complete startup|$PACKAGE|InputMethod|TestRunner"     "$OUT_DIR/logcat.txt" > "$OUT_DIR/logcat-keyswiper.txt" 2>/dev/null || true
}

if [[ ! -f "$APK_PATH" ]]; then
  echo "APK not found: $APK_PATH" >&2
  exit 2
fi

adb wait-for-device

log "Waiting for Android framework readiness"
BOOTED=""
for _ in $(seq 1 90); do
  BOOTED="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  [[ "$BOOTED" == "1" ]] && break
  sleep 1
done

if [[ "$BOOTED" != "1" ]]; then
  echo "Android did not report sys.boot_completed=1." >&2
  exit 1
fi

adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true

# Automated test images can surface unrelated framework crash/ANR dialogs while
# background services settle. They steal focus from the text host and make IME
# visibility assertions meaningless, so suppress system error dialogs in CI.
adb shell settings put global hide_error_dialogs 1 >/dev/null 2>&1 || true
adb shell settings put global show_first_crash_dialog 0 >/dev/null 2>&1 || true
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true

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

# Start from a stopped package before selecting it as the active IME. Force-stopping
# an already-selected IME makes Android fall back to another keyboard.
adb shell am force-stop "$PACKAGE" || true

log "Enabling KeySwiper IME"
adb shell ime enable "$SERVICE" | tee "$OUT_DIR/ime-enable.txt"
adb shell ime set "$SERVICE" | tee "$OUT_DIR/ime-set.txt"

SELECTED=""
for _ in $(seq 1 30); do
  SELECTED="$(adb shell settings get secure default_input_method | tr -d '\r' || true)"
  [[ "$SELECTED" == "$SERVICE" ]] && break
  sleep 1
done

if [[ "$SELECTED" != "$SERVICE" ]]; then
  echo "KeySwiper was not selected. Current default IME: $SELECTED" >&2
  exit 1
fi

log "Launching debug-only focused text host"
set +e
HOST_LAUNCH="$(adb shell am start -W -n "$HOST" --ez run_input_checks true 2>&1 | tr -d '\r')"
HOST_LAUNCH_CODE=$?
set -e
printf '%s\n' "$HOST_LAUNCH" | tee "$OUT_DIR/host-launch.txt"
printf '%s\n' "$HOST_LAUNCH_CODE" > "$OUT_DIR/host-launch-exit-code.txt"

HOST_READY=0
for attempt in $(seq 1 30); do
  ACTIVITY_STATE="$(adb shell dumpsys activity activities 2>/dev/null | tr -d '\r' || true)"
  printf '%s\n' "$ACTIVITY_STATE" > "$OUT_DIR/dumpsys-activity-live.txt"

  if grep -Fq "$HOST" <<<"$ACTIVITY_STATE" &&
     grep -Eq "topResumedActivity=.*$PACKAGE|ResumedActivity:.*$PACKAGE" <<<"$ACTIVITY_STATE"; then
    HOST_READY=1
    break
  fi

  # Reassert the host if a transient system window took focus during emulator startup.
  if (( attempt % 5 == 0 )); then
    adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
    adb shell am start -n "$HOST" >/dev/null 2>&1 || true
  fi

  sleep 1
done

if [[ "$HOST_READY" -ne 1 ]]; then
  echo "Smoke host never became the resumed activity." >&2
  exit 1
fi

VISIBLE=0
for attempt in $(seq 1 60); do
  STATE="$(adb shell dumpsys input_method 2>/dev/null | tr -d '\r' || true)"
  printf '%s\n' "$STATE" > "$OUT_DIR/dumpsys-input_method-live.txt"

  PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
  CURRENT_OK=0
  VISIBLE_OK=0

  if grep -Fq "mCurId=$SERVICE" <<<"$STATE" ||
     grep -Fq "mCurMethodId=$SERVICE" <<<"$STATE"; then
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

  # The debug host independently retries showSoftInput. Reassert focus occasionally
  # so the smoke test survives benign emulator focus churn without masking a real
  # KeySwiper process crash.
  if (( attempt % 10 == 0 )); then
    adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
    adb shell am start -n "$HOST" >/dev/null 2>&1 || true
  fi

  sleep 1
done

if [[ "$VISIBLE" -ne 1 ]]; then
  echo "KeySwiper never became a live, current and visible IME." >&2
  exit 1
fi

INPUT_CHECKS_OK=0
for _ in $(seq 1 40); do
  adb logcat -d -s KeySwiperInputChecks:I '*:S' > "$OUT_DIR/input-regression-checks.txt"
  if grep -Fq "PASS: pen and finger taps/swipes, menu selection, palm contact, boundaries, handwriting" "$OUT_DIR/input-regression-checks.txt" &&
     grep -Fq "PASS: attached pen/finger long-press alternatives" "$OUT_DIR/input-regression-checks.txt" &&
     grep -Fq "PASS: word spacing, completion, composition, selection and cursor position" "$OUT_DIR/input-regression-checks.txt" &&
     grep -Fq "PASS: Shift caps lock, command layout, clipboard input isolation and personal spelling" "$OUT_DIR/input-regression-checks.txt" &&
     grep -Fq "PASS: held key release/cancel and clipboard delete/undo/persistence" "$OUT_DIR/input-regression-checks.txt" &&
     grep -Fq "PASS: period beside M and held punctuation" "$OUT_DIR/input-regression-checks.txt"; then
    INPUT_CHECKS_OK=1
    break
  fi
  sleep 0.25
done

if [[ "$INPUT_CHECKS_OK" -ne 1 ]]; then
  echo "Android input, attached popup or word-spacing regression checks did not pass." >&2
  adb logcat -d -s AndroidRuntime:E KeySwiperInputChecks:I '*:S'
  exit 1
fi

log "PASS: KeySwiper is alive, selected and visible; pen, finger, edge, long-press and word-spacing checks passed on Android 15."

# Preserve the actual rendered keyboard for layout review.
# Let the final attached test views disappear and the focused IME draw a frame.
sleep 1
adb exec-out screencap -p > "$OUT_DIR/keyboard.png"
