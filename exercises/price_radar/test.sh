#!/bin/bash

MAX_CONCURRENT=10
TOTAL=100
count=0

for i in $(seq 1 $TOTAL); do
  curl --silent --location --request GET 'http://localhost:8080/api/search?product=iphonessssss' &
  count=$((count + 1))

  # Limit concurrent requests
  if [ $count -ge $MAX_CONCURRENT ]; then
    wait
    count=0
  fi
done

wait
echo "Done: $TOTAL requests completed"