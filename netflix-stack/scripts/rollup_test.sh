#!/usr/bin/env bash
#
# Kiem tra rollup pipeline la INCREMENTAL, khong phai re-scan toan bo lich su.
#
#   1. addEvent x5 (delta=1) cho counter "test-2"
#   2. rollup      -> checkpoint.count = 5
#   3. addEvent x3
#   4. rollup      -> checkpoint.count = 8 (khong chay lai tu 0)
#   5. verify      -> pass 2 chi doc phan duoi cua checkpoint, khong doc lai ca 8 event
#   6. rollup lan 3 -> no-op, khong ghi gi (idempotent)
#
# Chay:
#   ./gradlew bootRun            # terminal khac
#   ./scripts/rollup_test.sh
#
# Bien moi truong: BASE, COUNTER, CASSANDRA_CONTAINER, KEYSPACE
# Co: --no-reset (giu nguyen du lieu cu), --help

set -uo pipefail

BASE=${BASE:-http://localhost:8080}
COUNTER=${COUNTER:-test-2}
CASSANDRA_CONTAINER=${CASSANDRA_CONTAINER:-netflix-stack-cassandra}
KEYSPACE=${KEYSPACE:-counter_lab}
DO_RESET=1

for arg in "$@"; do
  case "$arg" in
    --no-reset) DO_RESET=0 ;;
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

# --- JSON helpers (khong phu thuoc jq) -----------------------------------
json_num() { printf '%s' "$1" | grep -o "\"$2\":-\?[0-9][0-9]*" | head -1 | cut -d: -f2; }
json_bool() { printf '%s' "$1" | grep -o "\"$2\":\(true\|false\)" | head -1 | cut -d: -f2; }
json_str() { printf '%s' "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -1 | sed 's/.*:"//; s/"$//'; }

check() { # check <nhan> <thuc te> <mong doi>
  if [ "$2" = "$3" ]; then
    printf "  ${GREEN}PASS${OFF}  %-30s = %s\n" "$1" "$2"
  else
    printf "  ${RED}FAIL${OFF}  %-30s = %s  ${RED}(mong doi %s)${OFF}\n" "$1" "$2" "$3"
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

# --- Reset ----------------------------------------------------------------
# Bat buoc, neu khong rollup lan 1 se gop ca event cu va ra 8 thay vi 5.
#
# DELETE dung USING TIMESTAMP lay tu clock cua HOST, khong de Cassandra tu
# dong dau. Ly do: cqlsh chay trong container va lay clock cua container, con
# app ghi event bang clock cua host. Neu hai dong ho lech (container o day
# nhanh hon host vai chuc ms) thi tombstone cua DELETE co timestamp LON HON
# cac INSERT ngay sau do, va Cassandra nuot luon chung - im lang, khong loi.
#
# Moc chinh xac la "host bay gio": du moi de xoa het du lieu cu (deu duoc ghi
# truoc thoi diem nay), va du som de moi addEvent sau do - cach it nhat mot
# round-trip HTTP - deu thang tombstone. Lui them vai giay se khong xoa duoc
# du lieu cua lan chay ngay truoc do, con lay thoi diem tuong lai se nuot
# chinh cac event sap ghi.
#
# Hai lenh DELETE tren counter_events vi time_bucket nam trong partition key:
# 8 event trai 10 phut co the roi vao hai bucket gio khac nhau, va Cassandra
# khong xoa duoc neu thieu partition key day du. Chinh rang buoc do la ly do
# readEventsSince() phai loop tung bucket.
if [ "$DO_RESET" -eq 1 ]; then
  step "0. Reset du lieu cua counter '$COUNTER'"
  if command -v docker >/dev/null && docker ps --format '{{.Names}}' | grep -qx "$CASSANDRA_CONTAINER"; then
    RESET_TS=$(date -u +%s%6N)
    B_NOW=$(date -u +%Y-%m-%d-%H)
    B_PREV=$(date -u -d '1 hour ago' +%Y-%m-%d-%H)
    # Gop thanh mot dong: cqlsh -e bao SyntaxException neu chuoi mo dau bang newline.
    RESET_CQL="DELETE FROM $KEYSPACE.counter_rollup USING TIMESTAMP $RESET_TS WHERE counter_id='$COUNTER';"
    RESET_CQL="$RESET_CQL DELETE FROM $KEYSPACE.counter_events USING TIMESTAMP $RESET_TS WHERE counter_id='$COUNTER' AND time_bucket='$B_NOW';"
    RESET_CQL="$RESET_CQL DELETE FROM $KEYSPACE.counter_events USING TIMESTAMP $RESET_TS WHERE counter_id='$COUNTER' AND time_bucket='$B_PREV';"
    RESET_ERR=$(docker exec "$CASSANDRA_CONTAINER" cqlsh -e "$RESET_CQL" 2>&1)
    if [ -n "$RESET_ERR" ]; then
      echo "  ${YELLOW}canh bao: reset that bai, ket qua co the sai${OFF}"
      echo "  ${DIM}${RESET_ERR}${OFF}"
    else
      echo "  ${DIM}da xoa checkpoint + event trong bucket $B_PREV, $B_NOW (USING TIMESTAMP $RESET_TS)${OFF}"
    fi
  else
    echo "  ${YELLOW}canh bao: khong thay container '$CASSANDRA_CONTAINER', bo qua reset${OFF}"
    echo "  ${YELLOW}dung --no-reset hoac doi COUNTER sang ten chua tung dung${OFF}"
  fi
fi

# --- Event timestamps -----------------------------------------------------
# Dat trong qua khu: ngoai lag window (mac dinh 1s) nhung trong lookback
# (mac dinh 24h), nen rollup goi ngay sau khi ghi la fold duoc luon, khong
# can sleep. Neu dung "now" thi pass dau se tra eventsSkippedTooFresh=5 va
# checkpointWritten=false.
ts_ago() { date -u -d "$1 minutes ago" +%Y-%m-%dT%H:%M:%SZ; }

add_event() { # add_event <eventId> <phut truoc> <delta>
  local body
  body="{\"counterId\":\"$COUNTER\",\"eventTime\":\"$(ts_ago "$2")\",\"eventId\":\"$1\",\"delta\":$3}"
  curl -sS -m 10 -XPOST "$BASE/api/events" -H 'Content-Type: application/json' -d "$body" >/dev/null || {
    echo "${RED}addEvent $1 that bai${OFF}" >&2; exit 1; }
  printf "  ${DIM}+ %-3s @ %s  delta=%s${OFF}\n" "$1" "$(ts_ago "$2")" "$3"
}

do_rollup() { curl -sS -m 30 -XPOST "$BASE/api/counters/rollup/$COUNTER/rollup"; }

# --- 1. addEvent x5 -------------------------------------------------------
step "1. addEvent x5 (delta=1)"
add_event e1 10 1
add_event e2 9  1
add_event e3 8  1
add_event e4 7  1
add_event e5 6  1

# --- 2. rollup lan 1 ------------------------------------------------------
step "2. rollup lan 1 -> checkpoint.count phai = 5"
P1=$(do_rollup); echo "  ${DIM}${P1}${OFF}"
check "newCount"            "$(json_num "$P1" newCount)"            "5"
check "delta"               "$(json_num "$P1" delta)"               "5"
check "eventsAggregated"    "$(json_num "$P1" eventsAggregated)"    "5"
check "previousRollupTs"    "$(json_str "$P1" previousRollupTs)"    "1970-01-01T00:00:00Z"
check "checkpointWritten"   "$(json_bool "$P1" checkpointWritten)"  "true"

# --- 3. addEvent x3 -------------------------------------------------------
step "3. addEvent x3 nua"
add_event e6 5 1
add_event e7 4 1
add_event e8 3 1

# --- 4 + 5. rollup lan 2 --------------------------------------------------
# eventsRead=4 chu khong phai 3: readEventsSince() la inclusive
# (event_time >= since) nen no tra ve ca e5 dang nam dung tren high-water
# mark; service loc no ra (eventsSkippedAlreadyRolled=1) roi moi cong 3
# event moi. Dieu can chung minh van dung tuyet doi: doc 4 chu khong phai 8,
# va con so do khong phu thuoc do dai lich su counter.
step "4. rollup lan 2 -> count = 8, khong chay lai tu 0"
P2=$(do_rollup); echo "  ${DIM}${P2}${OFF}"
check "previousCount"       "$(json_num "$P2" previousCount)"       "5"
check "newCount"            "$(json_num "$P2" newCount)"            "8"
check "delta"               "$(json_num "$P2" delta)"               "3"

step "5. verify: pass 2 chi doc tail, khong doc lai ca 8 event"
check "eventsRead"          "$(json_num "$P2" eventsRead)"          "4"
check "eventsAggregated"    "$(json_num "$P2" eventsAggregated)"    "3"
check "eventsSkippedAlreadyRolled" "$(json_num "$P2" eventsSkippedAlreadyRolled)" "1"

# --- Checkpoint + value ---------------------------------------------------
step "Checkpoint va value"
CP=$(curl -sS -m 10 "$BASE/api/counters/rollup/$COUNTER/checkpoint"); echo "  ${DIM}${CP}${OFF}"
check "lastRollupCount"     "$(json_num "$CP" lastRollupCount)"     "8"

VAL=$(curl -sS -m 10 "$BASE/api/counters/rollup/$COUNTER/value"); echo "  ${DIM}${VAL}${OFF}"
check "value"               "$(json_num "$VAL" value)"              "8"
check "tailEvents"          "$(json_num "$VAL" tailEvents)"         "0"

# --- 6. Idempotent --------------------------------------------------------
# Pass rong khong ghi gi: khong tombstone, khong dung toi write timestamp.
step "6. rollup lan 3 -> no-op (idempotent)"
P3=$(do_rollup); echo "  ${DIM}${P3}${OFF}"
check "delta"               "$(json_num "$P3" delta)"               "0"
check "eventsAggregated"    "$(json_num "$P3" eventsAggregated)"    "0"
check "checkpointWritten"   "$(json_bool "$P3" checkpointWritten)"  "false"

# --- Ket qua --------------------------------------------------------------
echo
if [ "$FAILS" -eq 0 ]; then
  printf "${GREEN}${BOLD}Tat ca assertion PASS${OFF}\n"
  exit 0
else
  printf "${RED}${BOLD}%d assertion FAIL${OFF}\n" "$FAILS"
  exit 1
fi
