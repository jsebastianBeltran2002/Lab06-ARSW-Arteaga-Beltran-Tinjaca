#!/usr/bin/env bash
# Manual smoke-test script for the Collaborative Board REST API.
# Requires the app to already be running (mvn spring-boot:run) on http://localhost:8080.
#
# Usage:
#   bash scripts/demo.sh

set -euo pipefail
BASE_URL="http://localhost:8080/api/boards"

echo "=================================================="
echo "  DEMO - Collaborative Architecture Board API"
echo "=================================================="

echo -e "\n[1] Create a board (POST)"
CREATE_RESP=$(curl -s -X POST "$BASE_URL" -H "Content-Type: application/json" -d '{"name":"Architecture Session"}')
echo "$CREATE_RESP"
BOARD_ID=$(echo "$CREATE_RESP" | grep -oE '"id":"[^"]+"' | cut -d'"' -f4)
echo "-> generated id: $BOARD_ID"

echo -e "\n[2] Read that board (GET)"
curl -s "$BASE_URL/$BOARD_ID"

echo -e "\n\n[3] Replace the board, adding one element (PUT)"
curl -s -X PUT "$BASE_URL/$BOARD_ID" -H "Content-Type: application/json" \
  -d '{"name":"Renamed","elements":[{"id":"el-1","type":"RECTANGLE","x":1,"y":2,"width":10,"height":5,"text":""}]}'

echo -e "\n\n[4] Read a board that does not exist -> 404 BOARD_NOT_FOUND"
curl -s "$BASE_URL/no-existe"

echo -e "\n\n[5] Send an invalid nested element (negative width) -> 400 INVALID_INPUT, not a raw 500"
curl -s -X PUT "$BASE_URL/$BOARD_ID" -H "Content-Type: application/json" \
  -d '{"name":"Renamed","elements":[{"id":"el-1","type":"RECTANGLE","x":1,"y":2,"width":-10,"height":5,"text":""}]}'

echo -e "\n\n=================================================="
echo "  DEMO COMPLETE"
echo "=================================================="
