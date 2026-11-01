#!/usr/bin/env bash
# Start the local Android Auto bench.
#
# The bench is an AVD phone running Android Auto plus the Desktop Head Unit (DHU)
# on this machine. The DHU is the "car screen": it connects to the phone's head
# unit server over a local port forward and shows the Android Auto surface, so
# the app can be tested without a car.
#
# Usage:
#   tools/aa-bench.sh            start the phone, the forward and the DHU
#   tools/aa-bench.sh --status   report what is up
#   tools/aa-bench.sh --stop     stop the DHU and shut the phone down
#
# Requirements:
#   - The AVD "aa36" (Android 16, Google Play image) with Android Auto installed.
#   - The DHU 2.1 in $DHU_SDK; docs/building.md has the install line.
#   - The AA head unit server switched on in the phone's Android Auto settings.
#     The script detects it and names the toggle when it is off.
#
# Environment overrides: ANDROID_HOME, DHU_SDK, AVD, SERIAL, DHU_CONFIG

set -euo pipefail

ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}
DHU_SDK=${DHU_SDK:-$HOME/Android/Sdk-dhu}
AVD=${AVD:-aa36}
SERIAL=${SERIAL:-emulator-5554}
DHU_CONFIG=${DHU_CONFIG:-config/aa-1024x600.ini}

ADB="$ANDROID_HOME/platform-tools/adb"
EMULATOR="$ANDROID_HOME/emulator/emulator"
DHU_DIR="$DHU_SDK/extras/google/auto"
RUN_DIR=${XDG_RUNTIME_DIR:-/tmp}
DHU_PID_FILE="$RUN_DIR/aa-bench-dhu.pid"
DHU_LOG="$RUN_DIR/aa-bench-dhu.log"
EMULATOR_LOG="$RUN_DIR/aa-bench-emulator.log"

say() { printf '%s\n' "$*"; }
die() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

phone_connected() { "$ADB" -s "$SERIAL" get-state >/dev/null 2>&1; }

phone_booted() {
    [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]
}

server_up() { "$ADB" -s "$SERIAL" shell ss -ltn 2>/dev/null | grep -q ':5277'; }

start_phone() {
    if phone_connected && phone_booted; then
        say "phone: $SERIAL already running"
        return
    fi
    say "phone: starting AVD $AVD"
    "$EMULATOR" -avd "$AVD" -gpu host -memory 4096 -cores 4 \
        -no-snapshot -no-boot-anim >"$EMULATOR_LOG" 2>&1 &
    "$ADB" wait-for-device
    for _ in $(seq 1 90); do
        if phone_booted; then
            say "phone: booted"
            return
        fi
        sleep 2
    done
    die "the AVD did not finish booting; see $EMULATOR_LOG"
}

start_forward() {
    "$ADB" -s "$SERIAL" forward tcp:5277 tcp:5277 >/dev/null
    say "forward: localhost:5277 -> phone:5277"
}

ensure_server() {
    if server_up; then
        say "server: head unit server is on"
        return
    fi
    say "server: the head unit server is off."
    say "        On the phone: Android Auto -> gear/overflow menu -> Start head unit server."
    read -r -p "        Press Enter when it is on... " _
    for _ in $(seq 1 10); do
        if server_up; then
            say "server: head unit server is on"
            return
        fi
        sleep 2
    done
    die "port 5277 is still closed; start the head unit server and rerun"
}

start_dhu() {
    local pid
    pid=$(cat "$DHU_PID_FILE" 2>/dev/null || true)
    if [ -n "${pid:-}" ] && kill -0 "$pid" 2>/dev/null; then
        say "dhu: already running (pid $pid)"
        return
    fi
    if pgrep -f 'desktop-head-unit -a 5277' >/dev/null 2>&1; then
        say "dhu: already running (started outside this script)"
        return
    fi
    [ -x "$DHU_DIR/desktop-head-unit" ] ||
        die "DHU not found at $DHU_DIR; docs/building.md has the install line"
    say "dhu: starting with $DHU_CONFIG"
    (
        cd "$DHU_DIR"
        # Keep stdin open: the DHU quits when its stdin reaches EOF.
        nohup sleep 7200 | nohup ./desktop-head-unit -a 5277 -c "$DHU_CONFIG" \
            >"$DHU_LOG" 2>&1 &
        echo $! >"$DHU_PID_FILE"
    )
    sleep 3
    if grep -q 'failed' "$DHU_LOG" 2>/dev/null; then
        die "the DHU could not connect; see $DHU_LOG"
    fi
    say "dhu: running (pid $(cat "$DHU_PID_FILE")), log $DHU_LOG"
}

stop_all() {
    if [ -f "$DHU_PID_FILE" ]; then
        kill "$(cat "$DHU_PID_FILE")" 2>/dev/null || true
        rm -f "$DHU_PID_FILE"
        say "dhu: stopped"
    fi
    if pgrep -f 'desktop-head-unit -a 5277' >/dev/null 2>&1; then
        pkill -f 'desktop-head-unit -a 5277' 2>/dev/null || true
        say "dhu: stopped (started outside this script)"
    fi
    if phone_connected; then
        "$ADB" -s "$SERIAL" emu kill >/dev/null 2>&1 || true
        say "phone: shut down"
    fi
}

status() {
    if phone_connected && phone_booted; then
        say "phone: $SERIAL up"
    else
        say "phone: down"
    fi
    if "$ADB" forward --list 2>/dev/null | grep -q "$SERIAL"; then
        say "forward: set"
    else
        say "forward: missing"
    fi
    if phone_connected && server_up; then
        say "server: on"
    else
        say "server: off"
    fi
    if [ -f "$DHU_PID_FILE" ] && kill -0 "$(cat "$DHU_PID_FILE")" 2>/dev/null; then
        say "dhu: running (pid $(cat "$DHU_PID_FILE"))"
    elif pgrep -f 'desktop-head-unit -a 5277' >/dev/null 2>&1; then
        say "dhu: running (started outside this script)"
    else
        say "dhu: not running"
    fi
}

case "${1:-start}" in
start)
    start_phone
    start_forward
    ensure_server
    start_dhu
    say "bench ready"
    ;;
--stop | stop) stop_all ;;
--status | status) status ;;
-h | --help | help) sed -n '2,22p' "$0" ;;
*) die "unknown argument: $1" ;;
esac
