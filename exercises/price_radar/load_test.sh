#!/bin/bash
# Day 14 Load Test — run while the app is running on localhost:8080

BASE_URL="http://localhost:8080/api"
PRODUCT="iphone"

echo "========================================"
echo "  PriceRadar Load Test"
echo "========================================"

# ----------------------------------------
# Test 1: Rate limiter — 11 rapid requests
# ----------------------------------------
echo ""
echo "[Test 1] Rate limiter — send 11 rapid requests for same product"
echo "Expected: first 10 → 200, 11th → 429"
echo ""

for i in $(seq 1 11); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/search?product=$PRODUCT")
  echo "  Request $i → HTTP $STATUS"
done

# ----------------------------------------
# Test 2: Cache — two sequential requests
# ----------------------------------------
echo ""
echo "[Test 2] Cache — two sequential requests"
echo "Expected: first ~2s (miss), second <50ms (hit)"
echo ""

echo -n "  Request 1 (miss): "
TIME1=$(curl -s -o /dev/null -w "%{time_total}" "$BASE_URL/search?product=macbook")
echo "${TIME1}s"

echo -n "  Request 2 (hit):  "
TIME2=$(curl -s -o /dev/null -w "%{time_total}" "$BASE_URL/search?product=macbook")
echo "${TIME2}s"

# ----------------------------------------
# Test 3: 20 concurrent requests
# ----------------------------------------
echo ""
echo "[Test 3] 20 concurrent requests (background jobs)"
echo ""

START=$(date +%s%N)
for i in $(seq 1 20); do
  curl -s -o /dev/null "$BASE_URL/search?product=concurrent-test" &
done
wait
END=$(date +%s%N)
ELAPSED=$(( (END - START) / 1000000 ))
echo "  20 concurrent requests finished in ${ELAPSED}ms"

# ----------------------------------------
# Stats after all tests
# ----------------------------------------
echo ""
echo "[Stats] Current system stats:"
curl -s "$BASE_URL/stats" | python3 -m json.tool 2>/dev/null || curl -s "$BASE_URL/stats"

echo ""
echo "========================================"
echo "  Done"
echo "========================================"
