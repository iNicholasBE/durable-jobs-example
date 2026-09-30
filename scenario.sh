#!/usr/bin/env bash
# Replays the invoice incident against a freshly started app (mvn spring-boot:run).
#
#   ./scenario.sh phase1   a healthy invoice run
#   ./scenario.sh phase2   one durable job fails on charge-card and recovers on the retry
#
# Everything here runs on JobRunr open source.
set -euo pipefail

APP=${APP:-http://localhost:8080}
DASH=${DASH:-http://localhost:8000/api}

count() { curl -s "$DASH/jobs?state=$1&limit=1" | python3 -c 'import sys,json;print(json.load(sys.stdin)["total"])'; }
state_of() { curl -s "$DASH/jobs/$1" | python3 -c 'import sys,json;print(json.load(sys.stdin)["jobHistory"][-1]["state"])'; }
states() { for s in AWAITING ENQUEUED SCHEDULED PROCESSING SUCCEEDED FAILED; do printf "%s=%s " "$s" "$(count "$s")"; done; echo; }

phase1() {
  local customers=${1:-150}
  curl -s -X POST "$APP/invoices/run?period=2026-08&customers=$customers"; echo
  until [ "$(count ENQUEUED)" = 0 ] && [ "$(count PROCESSING)" = 0 ] && [ "$(count SUCCEEDED)" -ge "$customers" ]; do sleep 3; done
  states
}

phase2() {
  curl -s -X POST "$APP/invoices/payment-provider/outage?enabled=true"; echo
  local job
  job=$(curl -s -X POST "$APP/invoices/single?customerId=${1:-CUST-0042}&period=2026-09")
  echo "job $job"
  until [ "$(state_of "$job")" = SCHEDULED ]; do sleep 1; done   # attempt 1 failed on charge-card
  echo "attempt 1 failed, retry scheduled. Bringing the payment provider back."
  curl -s -X POST "$APP/invoices/payment-provider/outage?enabled=false"; echo
  until [ "$(state_of "$job")" = SUCCEEDED ]; do sleep 2; done
  echo "$job succeeded. Open http://localhost:8000/dashboard/jobs/$job and switch History to Chart."
}

"${@:-phase2}"
