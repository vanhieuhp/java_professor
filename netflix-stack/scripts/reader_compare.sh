#!/usr/bin/env bash
#
# So sanh hai reader tren cung mot counter, ngay sau khi bench_seed.sh dung
# fixture "1000 da rollup + 50 con nam trong tail":
#
#   EventuallyConsistentReader x100 -> ky vong 1000 (KHONG thay 50 event moi)
#   AccurateReader             x100 -> ky vong 1050 (THAY du 50 event moi)
#
# Do luon p50/p99 cua tung reader. Doc nhanh khong mien phi: cai tra loi trong
# ~1ms la cai tra loi SAI 50 don vi, cai tra loi dung phai quet tail trong
# Cassandra. Do chinh la thu can nhin thay canh nhau.
#
# Chay:
#   ./gradlew bootRun            # terminal khac
#   ./scripts/bench_seed.sh
#   ./scripts/reader_compare.sh
#
# Bien moi truong: BASE, COUNTER, ITERATIONS, WARMUP, EXPECTED_ROLLED_UP,
#                  EXPECTED_TOTAL, REDIS_CONTAINER
# Co: --no-cache-reset, --help

set -uo pipefail

BASE=${BASE:-http://localhost:8080}
COUNTER=${COUNTER:-bench-1}
ITERATIONS=${ITERATIONS:-100}
WARMUP=${WARMUP:-5}
REDIS_CONTAINER=${REDIS_CONTAINER:-redis}
DO_CACHE_RESET=1

for arg in "$@"; do
  case "$arg" in
    --no-cache-reset) DO_CACHE_RESET=0 ;;
    --help|-h) sed -n '2,20p' "$0" | sed 's/^# \?//'; exit 0 ;;
    *) echo "Tham so khong hieu: $arg (xem --help)" >&2; exit 2 ;;
  esac
done

if [ -t 1 ]; then
  RED=$'\033[31m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; DIM=$'\033[2m'; BOLD=$'\033[1m'; OFF=$'\033[0m'
else
  RED=''; GREEN=''; YELLOW=''; DIM=''; BOLD=''; OFF=''
fi

FAILS=0

json_num() { printf '%s' "$1" | grep -o "\"$2\":-\?[0-9][0-9]*" | head -1 | cut -d: -f2; }
json_dec() { printf '%s' "$1" | grep -o "\"$2\":-\?[0-9][0-9]*\(\.[0-9]*\)\?" | head -1 | cut -d: -f2; }
json_bool() { printf '%s' "$1" | grep -o "\"$2\":\(true\|false\)" | head -1 | cut -d: -f2; }

check() {
  if [ "$2" = "$3" ]; then
    printf "  ${GREEN}PASS${OFF}  %-32s = %s\n" "$1" "$2"
  else
    printf "  ${RED}FAIL${OFF}  %-32s = %s  ${RED}(mong doi %s)${OFF}\n" "$1" "$2" "$3"
    FAILS=$((FAILS + 1))
  fi
}

step() { printf "\n${BOLD}%s${OFF}\n" "$1"; }

# --- Preflight ------------------------------------------------------------
if ! curl -sS -m 5 -o /dev/null "$BASE/api/counters/rollup/$COUNTER/checkpoint" 2>/dev/null; then
  echo "${RED}Khong ket noi duoc $BASE${OFF}" >&2
  echo "Khoi dong app truoc:  ./gradlew bootRun" >&2
  exit 1
fi

# Lay ky vong tu chinh trang thai hien tai chu khong hard-code 1000/1050:
# script van dung neu doi EVENTS/TAIL_EVENTS khi seed.
VAL=$(curl -sS -m 60 "$BASE/api/counters/rollup/$COUNTER/value")
EXPECTED_ROLLED_UP=${EXPECTED_ROLLED_UP:-$(json_num "$VAL" rolledUpCount)}
EXPECTED_TOTAL=${EXPECTED_TOTAL:-$(json_num "$VAL" value)}
TAIL=$(json_num "$VAL" tailEvents)

if [ -z "$EXPECTED_ROLLED_UP" ] || [ "$EXPECTED_ROLLED_UP" = "0" ]; then
  echo "${YELLOW}Counter '$COUNTER' chua co checkpoint - chay ./scripts/bench_seed.sh truoc${OFF}" >&2
fi
printf "${DIM}Fixture: checkpoint=%s, tail=%s event chua rollup, gia tri that=%s${OFF}\n" \
  "$EXPECTED_ROLLED_UP" "$TAIL" "$EXPECTED_TOTAL"

# --- Reset cache ----------------------------------------------------------
# Cache khong bi invalidate khi rollup chay (TTL la thu duy nhat gioi han
# staleness), nen mot key con sot lai tu truoc luc rollup se tra ve gia tri
# cu hon ca checkpoint va lam ket qua kho doc. Xoa di de burst bat dau lanh:
# lan doc dau la MISS -> Cassandra, 99 lan sau la HIT -> Redis.
if [ "$DO_CACHE_RESET" -eq 1 ]; then
  if command -v docker >/dev/null && docker ps --format '{{.Names}}' | grep -qx "$REDIS_CONTAINER"; then
    DEL=$(docker exec "$REDIS_CONTAINER" redis-cli DEL "rollup:$COUNTER" 2>&1)
    echo "${DIM}da xoa cache key rollup:$COUNTER (redis-cli DEL -> $DEL)${OFF}"
  else
    echo "${YELLOW}canh bao: khong thay container '$REDIS_CONTAINER', bo qua xoa cache${OFF}"
  fi
fi

bench() { # bench <mode>
  curl -sS -m 300 -XPOST "$BASE/api/counters/reader/$COUNTER/bench?mode=$1&iterations=$ITERATIONS&warmup=$WARMUP"
}

report() { # report <nhan> <json>
  printf "  ${DIM}%s${OFF}\n" "$2"
  printf "  value=%s  p50=%sms  p90=%sms  p99=%sms  max=%sms  avg=%sms  tong=%sms\n" \
    "$(json_num "$2" value)" "$(json_dec "$2" p50Ms)" "$(json_dec "$2" p90Ms)" \
    "$(json_dec "$2" p99Ms)" "$(json_dec "$2" maxMs)" "$(json_dec "$2" avgMs)" "$(json_dec "$2" elapsedMs)"
}

# --- 1. Eventually consistent --------------------------------------------
step "1. EventuallyConsistentReader x$ITERATIONS (warmup=$WARMUP)"
E=$(bench eventual); report "eventual" "$E"
check "value (chi thay checkpoint)"  "$(json_num "$E" value)"      "$EXPECTED_ROLLED_UP"
check "iterations"                   "$(json_num "$E" iterations)" "$ITERATIONS"

# --- 2. Accurate ----------------------------------------------------------
step "2. AccurateReader x$ITERATIONS (warmup=$WARMUP)"
A=$(bench accurate); report "accurate" "$A"
check "value (checkpoint + tail)"    "$(json_num "$A" value)"      "$EXPECTED_TOTAL"
check "iterations"                   "$(json_num "$A" iterations)" "$ITERATIONS"

# --- Bang so sanh ---------------------------------------------------------
step "So sanh"
printf "  %-22s | %10s | %10s | %10s | %10s\n" "Reader" "value" "p50 (ms)" "p99 (ms)" "max (ms)"
printf "  %-22s-+-%10s-+-%10s-+-%10s-+-%10s\n" "----------------------" "----------" "----------" "----------" "----------"
printf "  %-22s | %10s | %10s | %10s | %10s\n" "EventuallyConsistent" \
  "$(json_num "$E" value)" "$(json_dec "$E" p50Ms)" "$(json_dec "$E" p99Ms)" "$(json_dec "$E" maxMs)"
printf "  %-22s | %10s | %10s | %10s | %10s\n" "Accurate" \
  "$(json_num "$A" value)" "$(json_dec "$A" p50Ms)" "$(json_dec "$A" p99Ms)" "$(json_dec "$A" maxMs)"
printf "\n  ${DIM}Do lech: %s event trong tail. Reader nhanh tra ve %s, reader dung tra ve %s.${OFF}\n" \
  "$TAIL" "$(json_num "$E" value)" "$(json_num "$A" value)"

# stable=false o eventual nghia la burst da cham vao ranh gioi TTL 5s:
# gia tri khong doi (checkpoint dung yen) nhung do la mot lan MISS -> day
# chinh la cai p99 dat len.
if [ "$(json_bool "$E" stable)" = "false" ] || [ "$(json_bool "$A" stable)" = "false" ]; then
  echo "  ${YELLOW}luu y: mot burst tra ve nhieu gia tri khac nhau - co rollup hoac TTI het han giua chung${OFF}"
fi

echo
if [ "$FAILS" -eq 0 ]; then
  printf "${GREEN}${BOLD}Tat ca assertion PASS${OFF}\n"
  exit 0
else
  printf "${RED}${BOLD}%d assertion FAIL${OFF}\n" "$FAILS"
  exit 1
fi
