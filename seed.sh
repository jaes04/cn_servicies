#!/usr/bin/env bash
# Seed: 5 atletas + 40 pruebas
# Requiere: curl, jq
# Uso: bash seed.sh

BASE="http://localhost:8080"
JQ=$(find /c/Users -name "jq.exe" 2>/dev/null | grep WinGet | head -1)
[ -z "$JQ" ] && JQ="jq"
set -e

echo "==> Login..."
TOKEN=$(curl -s -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password123"}' | $JQ -r '.accessToken')

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo "ERROR: no se pudo obtener el token."
  exit 1
fi

AUTH="Authorization: Bearer $TOKEN"
echo "    Token OK"

# ─── ATLETAS ────────────────────────────────────────────────────────────────

create_athlete() {
  RESP=$(curl -s -X POST "$BASE/api/athletes" \
    -H "Content-Type: application/json" \
    -H "$AUTH" \
    -d "$1")
  echo "$RESP" | $JQ -r '.id'
}

echo ""
echo "==> Creando atletas..."

A1=$(create_athlete '{"firstName":"Carlos","lastName":"Garcia Ruiz","birthDate":"2001-03-14","dni":"12345678A"}')
echo "    [1] Carlos Garcia Ruiz  -> $A1"

A2=$(create_athlete '{"firstName":"Maria","lastName":"Lopez Serrano","birthDate":"2003-07-22","dni":"23456789B"}')
echo "    [2] Maria Lopez Serrano -> $A2"

A3=$(create_athlete '{"firstName":"Pablo","lastName":"Martinez Vega","birthDate":"2000-11-05","dni":"34567890C"}')
echo "    [3] Pablo Martinez Vega -> $A3"

A4=$(create_athlete '{"firstName":"Ana","lastName":"Fernandez Diaz","birthDate":"2002-01-30","dni":"45678901D"}')
echo "    [4] Ana Fernandez Diaz  -> $A4"

A5=$(create_athlete '{"firstName":"Jorge","lastName":"Sanchez Mora","birthDate":"1999-09-18","dni":"56789012E"}')
echo "    [5] Jorge Sanchez Mora  -> $A5"

# ─── PRUEBAS ────────────────────────────────────────────────────────────────

pr() {
  ATHLETE_ID=$1; DATE=$2; DIST=$3; STROKE=$4; POOL=$5; TIME=$6
  curl -s -X POST "$BASE/api/competition-results" \
    -H "Content-Type: application/json" \
    -H "$AUTH" \
    -d "{\"athleteId\":\"$ATHLETE_ID\",\"competitionDate\":\"$DATE\",\"distanceMeters\":$DIST,\"stroke\":\"$STROKE\",\"poolLength\":$POOL,\"resultTimeMillis\":$TIME,\"partial\":false}" \
    | $JQ -r '"        -> id: " + .id'
}

echo ""
echo "==> Creando pruebas..."

echo "  Carlos Garcia Ruiz (8 pruebas)"
pr $A1 "2025-10-05"  50  FREESTYLE    50  24310
pr $A1 "2025-10-05" 100  FREESTYLE    50  53820
pr $A1 "2025-11-12" 200  FREESTYLE    25 115640
pr $A1 "2025-11-12"  50  BUTTERFLY    50  26980
pr $A1 "2026-01-18" 100  BACKSTROKE   50  61450
pr $A1 "2026-01-18" 200  FREESTYLE    50 117200
pr $A1 "2026-03-22"  50  FREESTYLE    25  23870
pr $A1 "2026-03-22" 400  FREESTYLE    50 248500

echo "  Maria Lopez Serrano (8 pruebas)"
pr $A2 "2025-09-14"  50  BACKSTROKE   50  30120
pr $A2 "2025-09-14" 100  BACKSTROKE   50  65340
pr $A2 "2025-10-20" 200  BACKSTROKE   25 138900
pr $A2 "2025-10-20"  50  BREASTSTROKE 50  34560
pr $A2 "2026-02-08" 100  BREASTSTROKE 50  72810
pr $A2 "2026-02-08" 200  MEDLEY       25 143200
pr $A2 "2026-04-11"  50  BACKSTROKE   25  29750
pr $A2 "2026-04-11" 400  MEDLEY       50 289600

echo "  Pablo Martinez Vega (8 pruebas)"
pr $A3 "2025-08-30" 100  FREESTYLE    50  50340
pr $A3 "2025-08-30" 200  BUTTERFLY    50 130200
pr $A3 "2025-11-01"  50  BUTTERFLY    25  27650
pr $A3 "2025-11-01" 400  FREESTYLE    50 243100
pr $A3 "2026-01-25" 800  FREESTYLE    50 512400
pr $A3 "2026-01-25" 100  BUTTERFLY    50  58920
pr $A3 "2026-03-15" 200  FREESTYLE    25 113800
pr $A3 "2026-03-15"  50  FREESTYLE    50  23140

echo "  Ana Fernandez Diaz (8 pruebas)"
pr $A4 "2025-09-28"  50  BREASTSTROKE 50  33450
pr $A4 "2025-09-28" 100  BREASTSTROKE 50  71230
pr $A4 "2025-10-18" 200  BREASTSTROKE 25 152600
pr $A4 "2025-10-18" 200  MEDLEY       50 141800
pr $A4 "2026-02-14"  50  FREESTYLE    50  25960
pr $A4 "2026-02-14" 400  MEDLEY       50 282400
pr $A4 "2026-04-05" 100  BREASTSTROKE 25  70540
pr $A4 "2026-04-05" 200  BREASTSTROKE 50 151200

echo "  Jorge Sanchez Mora (8 pruebas)"
pr $A5 "2025-10-12" 400  FREESTYLE    50 241300
pr $A5 "2025-10-12" 800  FREESTYLE    50 505800
pr $A5 "2025-11-22" 1500 FREESTYLE    50 983600
pr $A5 "2025-11-22" 200  FREESTYLE    50 112900
pr $A5 "2026-01-11" 100  FREESTYLE    50  50870
pr $A5 "2026-01-11" 400  FREESTYLE    25 239700
pr $A5 "2026-03-29" 800  FREESTYLE    25 501200
pr $A5 "2026-03-29"  50  FREESTYLE    50  22980

echo ""
echo "==> Seed completado: 5 atletas, 40 pruebas."