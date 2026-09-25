# Manual smoke-test script for the Collaborative Board REST API.
# Requires the app to already be running (mvn spring-boot:run) on http://localhost:8080.
#
# Usage:
#   .\scripts\demo.ps1

$ErrorActionPreference = "Stop"
$baseUrl = "http://localhost:8080/api/boards"

Write-Host "=================================================="
Write-Host "  DEMO - Collaborative Architecture Board API"
Write-Host "=================================================="

Write-Host "`n[1] Create a board (POST)" -ForegroundColor Cyan
'{"name":"Architecture Session"}' | Set-Content -Path "$env:TEMP\post-body.json" -Encoding utf8 -NoNewline
$resp = curl.exe -s -X POST $baseUrl -H "Content-Type: application/json" -d "@$env:TEMP\post-body.json"
Write-Host $resp
$id = ($resp | ConvertFrom-Json).id
Write-Host "-> generated id: $id" -ForegroundColor Yellow

Write-Host "`n[2] Read that board (GET)" -ForegroundColor Cyan
curl.exe -s "$baseUrl/$id"

Write-Host "`n`n[3] Replace the board, adding one element (PUT)" -ForegroundColor Cyan
'{"name":"Renamed","elements":[{"id":"el-1","type":"RECTANGLE","x":1,"y":2,"width":10,"height":5,"text":""}]}' | Set-Content -Path "$env:TEMP\put-body.json" -Encoding utf8 -NoNewline
curl.exe -s -X PUT "$baseUrl/$id" -H "Content-Type: application/json" -d "@$env:TEMP\put-body.json"

Write-Host "`n`n[4] Read a board that does not exist -> 404 BOARD_NOT_FOUND" -ForegroundColor Cyan
curl.exe -s "$baseUrl/no-existe"

Write-Host "`n`n[5] Send an invalid nested element (negative width) -> 400 INVALID_INPUT, not a raw 500" -ForegroundColor Cyan
'{"name":"Renamed","elements":[{"id":"el-1","type":"RECTANGLE","x":1,"y":2,"width":-10,"height":5,"text":""}]}' | Set-Content -Path "$env:TEMP\bad-body.json" -Encoding utf8 -NoNewline
curl.exe -s -X PUT "$baseUrl/$id" -H "Content-Type: application/json" -d "@$env:TEMP\bad-body.json"

Write-Host "`n`n=================================================="
Write-Host "  DEMO COMPLETE"
Write-Host "=================================================="
