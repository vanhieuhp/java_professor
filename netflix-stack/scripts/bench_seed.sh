#!/usr/bin/env bash
#
# Dung fixture "1000 da rollup + 50 con trong tail" cho counter "bench-1".
#
#   1. addEvent x1000 (delta=1), event_time rai deu trong 10 giay gan nhat
#   2. rollup mot lan   -> checkpoint.count = 1000, ts = event cuoi cung
#   3. addEvent x50 (delta=1) SAU khi checkpoint da luu -> "chua kip rollup"
#
# Ket qua cuoi: checkpoint = 1000 nhung gia tri that = 1050. Do lech 50 chinh
# la thu de so sanh hai reader (xem scripts/reader_compare.sh):
#   AccurateReaderServiceImpl             -> 1050 (checkpoint + tail)
#   EventuallyConsistentReaderServiceImpl -> 1000 (chi doc checkpoint)
#
# Chay:
#   ./gradlew bootRun            # terminal khac
#   ./scripts/bench_seed.sh
#
# Bien moi truong: BASE, COUNTER, EVENTS, TAIL_EVENTS, SPREAD_SECONDS,
#                  LAG_MARGIN_SECONDS, LOOKBACK_HOURS, CONCURRENCY,
#                  CASSANDRA_CONTAINER, KEYSPACE
# Co: --no-reset (giu nguyen du lieu cu), --help

set -uo pipefail

BASE=${BASE:-http://localhost:8080}
COUNTER=${COUNTER:-bench-1}
EVENTS=${EVENTS:-1000}
TAIL_EVENTS=${TAIL_EVENTS:-50}
SPREAD_SECONDS=${SPREAD_SECONDS:-10}
# Event cuoi cung phai nam ngoai lag window cua rollup
# (netflix-stack.counter.rollup.lag-millis=1000), neu khong pass dau se bo lai
# vai event voi eventsSkippedTooFresh > 0 va checkpoint khong ra dung 1000.
LAG_MARGIN_SECONDS=${LAG_MARGIN_SECONDS:-2}
# Phai khop netflix-stack.counter.rollup.max-lookback-hours: pass dau tien doc
# tu (now - lookback), nen reset cung phai quet dung tung ay gio.
LOOKBACK_HOURS=${LOOKBACK_HOURS:-24}
CONCURRENCY=${CONCURRENCY:-16}
CASSANDRA_CONTAINER=${CASSANDRA_CONTAINER:-netflix-stack-cassandra}
KEYSPACE=${KEYSPACE:-counter_lab}
DO_RESET=1

for arg in "$@"; do
  case "$arg" in
    --no-reset) DO_RESET=0 ;;
    --help|-h) sed -n '2,24p' "$0" | sed 's/^# \?//'; exit 0 ;;
    *) echo "Tham so khong hieu: $arg (xem --help)" >&2; exit 2 ;;
  esac
done

if [ -t 1 ]; then
  RED=$'\033[31m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; DIM=$'\033[2m'; BOLD=$'\033[1m'; OFF=$'\033[0m'
else
  RED=''; GREEN=''; YELLOW=''; DIM=''; BOLD=''; OFF=''
fi

FAILS=0
WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT

# --- JSON helpers (khong phu thuoc jq) -----------------------------------
json_num() { printf '%s' "$1" | grep -o "\"$2\":-\?[0-9][0-9]*" | head -1 | cut -d: -f2; }
json_bool() { printf '%s' "$1" | grep -o "\"$2\":\(true\|false\)" | head -1 | cut -d: -f2; }
json_str() { printf '%s' "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -1 | sed 's/.*:"//; s/"$//'; }

check() { # check <nhan> <thuc te> <mong doi>
  if [ "$2" = "$3" ]; then
    printf "  ${GREEN}PASS${OFF}  %-32s = %s\n" "$1" "$2"
  else
    printf "  ${RED}FAIL${OFF}  %-32s = %s  ${RED}(mong doi %s)${OFF}\n" "$1" "$2" "$3"
    FAILS=$((FAILS + 1))
  fi
}

# So sanh moc thoi gian theo epoch millis chu khong theo chuoi: Jackson bo
# phan thap phan khi mili giay = 0, nen "...:07Z" va "...:07.000Z" la mot.
check_ts() { # check_ts <nhan> <thuc te> <mong doi>
  local a b
  a=$(date -u -d "$2" +%s%3N 2>/dev/null)
  b=$(date -u -d "$3" +%s%3N 2>/dev/null)
  if [ -n "$a" ] && [ "$a" = "$b" ]; then
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

# --- Moc thoi gian cua ca lan chay ---------------------------------------
# Tinh truoc reset vi chinh reset phai dua vao cua so nay (xem ben duoi).
# Cua so: [now - SPREAD_SECONDS, now - LAG_MARGIN_SECONDS]. Tat ca deu nam
# trong 10 giay gan nhat, nhung khong cai nao cham vao lag window 1s.
NOW_MS=$(date -u +%s%3N)
START_MS=$((NOW_MS - SPREAD_SECONDS * 1000))
END_MS=$((NOW_MS - LAG_MARGIN_SECONDS * 1000))
SPAN_MS=$((END_MS - START_MS))

# date(1) chi duoc goi mot lan cho moi giay khac nhau (~10 lan) roi ghep phan
# mili giay vao sau - goi 1000 lan se cham hon chinh phan seed.
iso_ms() { # iso_ms <epoch_millis>
  local ms_abs=$1 sec ms
  sec=$((ms_abs / 1000)); ms=$((ms_abs % 1000))
  if [ "${SEC_CACHE_KEY:-}" != "$sec" ]; then
    SEC_CACHE_KEY=$sec
    SEC_CACHE_VAL=$(date -u -d "@$sec" +%Y-%m-%dT%H:%M:%S)
  fi
  printf '%s.%03dZ' "$SEC_CACHE_VAL" "$ms"
}

# --- Reset ----------------------------------------------------------------
# Hai bang, hai moc timestamp khac nhau - va do khong phai su cau ky thua:
#
# counter_events: app ghi bang clock cua host o thoi diem INSERT, tuc "bay
#   gio tro di". Tombstone dat tai "host bay gio" la du som de moi event sap
#   ghi deu thang no, va du muon de xoa het du lieu cua lan chay truoc.
#
# counter_rollup: rollup ghi checkpoint bang USING TIMESTAMP = event_time cua
#   event cuoi cung (RollupServiceImpl.toMicros(newTs)), tuc mot moc trong QUA
#   KHU. Neu tombstone dung "host bay gio" thi no LON HON write timestamp cua
#   checkpoint sap toi, va Cassandra nuot luon checkpoint - im lang, khong
#   loi, RollupResult van bao checkpointWritten=true trong khi
#   GET /checkpoint tra ve 0. Nen tombstone phai nam DUOI event dau tien cua
#   cua so nay (START_MS - 1s) va tren checkpoint cua lan chay truoc.
#
# Phai liet ke tung time_bucket vi bucket nam trong partition key, va phai
# quet du LOOKBACK_HOURS gio chu khong chi 1-2 gio: pass rollup dau tien doc
# nguoc ve tan (now - max-lookback-hours), nen mot event sot lai tu ca chuc
# gio truoc van bi cong vao va lam checkpoint ra hon 1000.
if [ "$DO_RESET" -eq 1 ]; then
  step "0. Reset du lieu cua counter '$COUNTER'"
  if command -v docker >/dev/null && docker ps --format '{{.Names}}' | grep -qx "$CASSANDRA_CONTAINER"; then
    RESET_TS_EVENTS=$(date -u +%s%6N)
    RESET_TS_CHECKPOINT=$(( (START_MS - 1000) * 1000 ))
    RESET_CQL="DELETE FROM $KEYSPACE.counter_rollup USING TIMESTAMP $RESET_TS_CHECKPOINT WHERE counter_id='$COUNTER';"
    h=0
    while [ "$h" -le "$LOOKBACK_HOURS" ]; do
      BUCKET=$(date -u -d "@$(( NOW_MS / 1000 - h * 3600 ))" +%Y-%m-%d-%H)
      RESET_CQL="$RESET_CQL DELETE FROM $KEYSPACE.counter_events USING TIMESTAMP $RESET_TS_EVENTS WHERE counter_id='$COUNTER' AND time_bucket='$BUCKET';"
      h=$((h + 1))
    done
    RESET_ERR=$(docker exec "$CASSANDRA_CONTAINER" cqlsh -e "$RESET_CQL" 2>&1)
    if [ -n "$RESET_ERR" ]; then
      echo "  ${YELLOW}canh bao: reset that bai, ket qua co the sai${OFF}"
      echo "  ${DIM}${RESET_ERR}${OFF}"
    else
      echo "  ${DIM}da xoa $((LOOKBACK_HOURS + 1)) bucket event (TS $RESET_TS_EVENTS) + checkpoint (TS $RESET_TS_CHECKPOINT)${OFF}"
    fi
  else
    echo "  ${YELLOW}canh bao: khong thay container '$CASSANDRA_CONTAINER', bo qua reset${OFF}"
    echo "  ${YELLOW}dung --no-reset hoac doi COUNTER sang ten chua tung dung${OFF}"
  fi
fi

# --- 1. Seed 1000 event ---------------------------------------------------
step "1. addEvent x$EVENTS (delta=1), event_time rai deu trong ${SPREAD_SECONDS}s gan nhat"
: > "$WORK/bodies.txt"
i=0
while [ "$i" -lt "$EVENTS" ]; do
  if [ "$EVENTS" -gt 1 ]; then
    OFFSET_MS=$((SPAN_MS * i / (EVENTS - 1)))
  else
    OFFSET_MS=0
  fi
  TS=$(iso_ms $((START_MS + OFFSET_MS)))
  [ "$i" -eq 0 ] && FIRST_TS=$TS
  LAST_TS=$TS
  printf '{"counterId":"%s","eventTime":"%s","eventId":"b%04d","delta":1}\n' "$COUNTER" "$TS" "$i" >> "$WORK/bodies.txt"
  i=$((i + 1))
done
echo "  ${DIM}cua so: $FIRST_TS  ->  $LAST_TS${OFF}"

# -d '\n' la bat buoc, khong phai cho vui: mac dinh xargs tu dien giai dau
# nhay kep trong input, nen no an het dau nhay cua JSON va app nhan duoc
# {counterId:bench-1,...} -> HTTP 400 cho ca 1000 request. -d tat luon phan
# xu ly quote do va cat input dung theo dong.
post_bodies() { # post_bodies <file> -> in ra so request tra ve 200
  xargs -d '\n' -P "$CONCURRENCY" -I@@ -a "$1" \
    curl -sS -m 20 -o /dev/null -w '%{http_code}\n' \
      -XPOST "$BASE/api/events" -H 'Content-Type: application/json' -d '@@' \
    2>/dev/null | grep -c '^200$'
}

SEED_START=$(date -u +%s%3N)
OK=$(post_bodies "$WORK/bodies.txt")
SEED_MS=$(( $(date -u +%s%3N) - SEED_START ))
echo "  ${DIM}ghi xong trong ${SEED_MS}ms (concurrency=$CONCURRENCY)${OFF}"
check "event ghi thanh cong (HTTP 200)" "$OK" "$EVENTS"

# --- 2. rollup mot lan ----------------------------------------------------
step "2. rollup mot lan -> checkpoint.count = $EVENTS, ts = event cuoi"
P1=$(curl -sS -m 120 -XPOST "$BASE/api/counters/rollup/$COUNTER/rollup"); echo "  ${DIM}${P1}${OFF}"
check    "previousCount"          "$(json_num  "$P1" previousCount)"          "0"
check    "newCount"               "$(json_num  "$P1" newCount)"               "$EVENTS"
check    "delta"                  "$(json_num  "$P1" delta)"                  "$EVENTS"
check    "eventsAggregated"       "$(json_num  "$P1" eventsAggregated)"       "$EVENTS"
check    "eventsSkippedTooFresh"  "$(json_num  "$P1" eventsSkippedTooFresh)"  "0"
check    "checkpointWritten"      "$(json_bool "$P1" checkpointWritten)"      "true"
check_ts "newRollupTs"            "$(json_str  "$P1" newRollupTs)"            "$LAST_TS"

# Doc lai checkpoint chu khong tin RollupResult: RollupResult la con so tinh
# trong bo nho, con day moi la thu Cassandra thuc su giu lai sau khi write
# timestamp da dau voi moi thu khac trong bang.
CP=$(curl -sS -m 10 "$BASE/api/counters/rollup/$COUNTER/checkpoint"); echo "  ${DIM}${CP}${OFF}"
check    "lastRollupCount"        "$(json_num "$CP" lastRollupCount)"         "$EVENTS"
check_ts "lastRollupTs"           "$(json_str "$CP" lastRollupTs)"            "$LAST_TS"

# --- 3. Tail chua kip rollup ---------------------------------------------
# Moc thoi gian lay ngay luc nay, tuc SAU khi checkpoint da luu, nen chac chan
# lon hon high-water mark va rot ca vao tail. Khong goi rollup nua: do chinh
# la "chua kip rollup".
step "3. addEvent x$TAIL_EVENTS SAU khi checkpoint da luu (chua kip rollup)"
TAIL_NOW_MS=$(date -u +%s%3N)
: > "$WORK/tail.txt"
i=0
while [ "$i" -lt "$TAIL_EVENTS" ]; do
  TS=$(iso_ms $((TAIL_NOW_MS + i)))
  [ "$i" -eq 0 ] && TAIL_FIRST_TS=$TS
  TAIL_LAST_TS=$TS
  printf '{"counterId":"%s","eventTime":"%s","eventId":"t%04d","delta":1}\n' "$COUNTER" "$TS" "$i" >> "$WORK/tail.txt"
  i=$((i + 1))
done
echo "  ${DIM}cua so: $TAIL_FIRST_TS  ->  $TAIL_LAST_TS${OFF}"
OK_TAIL=$(post_bodies "$WORK/tail.txt")
check "tail event ghi thanh cong"  "$OK_TAIL" "$TAIL_EVENTS"

# --- Ket qua fixture ------------------------------------------------------
EXPECTED_VALUE=$((EVENTS + TAIL_EVENTS))
step "Trang thai cuoi: checkpoint=$EVENTS, gia tri that=$EXPECTED_VALUE"
VAL=$(curl -sS -m 60 "$BASE/api/counters/rollup/$COUNTER/value"); echo "  ${DIM}${VAL}${OFF}"
check "value"          "$(json_num "$VAL" value)"          "$EXPECTED_VALUE"
check "rolledUpCount"  "$(json_num "$VAL" rolledUpCount)"  "$EVENTS"
check "tailDelta"      "$(json_num "$VAL" tailDelta)"      "$TAIL_EVENTS"
check "tailEvents"     "$(json_num "$VAL" tailEvents)"     "$TAIL_EVENTS"

CP2=$(curl -sS -m 10 "$BASE/api/counters/rollup/$COUNTER/checkpoint")
check "checkpoint van dung yen"    "$(json_num "$CP2" lastRollupCount)" "$EVENTS"

echo
if [ "$FAILS" -eq 0 ]; then
  printf "${GREEN}${BOLD}Tat ca assertion PASS${OFF} - counter '%s': checkpoint=%s, tail=%s, tong=%s\n" \
    "$COUNTER" "$EVENTS" "$TAIL_EVENTS" "$EXPECTED_VALUE"
  exit 0
else
  printf "${RED}${BOLD}%d assertion FAIL${OFF}\n" "$FAILS"
  exit 1
fi
