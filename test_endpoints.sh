#!/bin/bash
# test_endpoints.sh - Test script for ADC Individual Evaluation REST API
# Usage: ./test_endpoints.sh [BASE_URL]
# Example: ./test_endpoints.sh http://localhost:8080
#          ./test_endpoints.sh https://YOUR-PROJECT-ID.appspot.com

BASE_URL="${1:-http://localhost:8080}"
REST_URL="$BASE_URL/rest"

echo "============================================="
echo "ADC Individual Evaluation - REST API Tests"
echo "Base URL: $REST_URL"
echo "============================================="
echo ""

# ---- Op1: Create Account (ADMIN) ----
echo ">>> Op1: Create ADMIN Account"
curl -s -X POST "$REST_URL/createaccount" \
  -H "Content-Type: application/json" \
  -d '{
    "input": {
      "username": "admin1",
      "password": "admin123",
      "confirmation": "admin123",
      "email": "admin@test.com",
      "phone": "912345678",
      "address": "Lisbon",
      "role": "ADMIN"
    }
  }' | python3 -m json.tool 2>/dev/null || echo "(raw output above)"
echo ""

# ---- Op1: Create Account (USER) ----
echo ">>> Op1: Create USER Account"
curl -s -X POST "$REST_URL/createaccount" \
  -H "Content-Type: application/json" \
  -d '{
    "input": {
      "username": "user1",
      "password": "user123",
      "confirmation": "user123",
      "email": "user1@test.com",
      "phone": "919876543",
      "address": "Porto",
      "role": "USER"
    }
  }' | python3 -m json.tool 2>/dev/null || echo "(raw output above)"
echo ""

# ---- Op1: Create Account (BOFFICER) ----
echo ">>> Op1: Create BOFFICER Account"
curl -s -X POST "$REST_URL/createaccount" \
  -H "Content-Type: application/json" \
  -d '{
    "input": {
      "username": "officer1",
      "password": "officer123",
      "confirmation": "officer123",
      "email": "officer@test.com",
      "phone": "913456789",
      "address": "Faro",
      "role": "BOFFICER"
    }
  }' | python3 -m json.tool 2>/dev/null || echo "(raw output above)"
echo ""

# ---- Op1: Test duplicate account ----
echo ">>> Op1: Test Duplicate Account (should fail)"
curl -s -X POST "$REST_URL/createaccount" \
  -H "Content-Type: application/json" \
  -d '{
    "input": {
      "username": "admin1",
      "password": "admin123",
      "confirmation": "admin123",
      "email": "admin@test.com",
      "role": "ADMIN"
    }
  }' | python3 -m json.tool 2>/dev/null || echo "(raw output above)"
echo ""

# ---- Op2: Login as ADMIN ----
echo ">>> Op2: Login as ADMIN"
ADMIN_RESPONSE=$(curl -s -X POST "$REST_URL/login" \
  -H "Content-Type: application/json" \
  -d '{
    "input": {
      "username": "admin1",
      "password": "admin123"
    }
  }')
echo "$ADMIN_RESPONSE" | python3 -m json.tool 2>/dev/null
# Extract token fields
ADMIN_TOKEN_ID=$(echo "$ADMIN_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data']['token']['tokenId'])" 2>/dev/null)
ADMIN_USER_ID=$(echo "$ADMIN_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data']['token']['userId'])" 2>/dev/null)
ADMIN_ROLE=$(echo "$ADMIN_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data']['token']['role'])" 2>/dev/null)
ADMIN_ISSUED=$(echo "$ADMIN_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data']['token']['issuedAt'])" 2>/dev/null)
ADMIN_EXPIRES=$(echo "$ADMIN_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data']['token']['expiresAt'])" 2>/dev/null)
echo "Token ID: $ADMIN_TOKEN_ID"
echo ""

# ---- Op2: Login as USER ----
echo ">>> Op2: Login as USER"
USER_RESPONSE=$(curl -s -X POST "$REST_URL/login" \
  -H "Content-Type: application/json" \
  -d '{
    "input": {
      "username": "user1",
      "password": "user123"
    }
  }')
echo "$USER_RESPONSE" | python3 -m json.tool 2>/dev/null
USER_TOKEN_ID=$(echo "$USER_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data']['token']['tokenId'])" 2>/dev/null)
USER_USER_ID=$(echo "$USER_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data']['token']['userId'])" 2>/dev/null)
USER_ROLE=$(echo "$USER_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data']['token']['role'])" 2>/dev/null)
USER_ISSUED=$(echo "$USER_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data']['token']['issuedAt'])" 2>/dev/null)
USER_EXPIRES=$(echo "$USER_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data']['token']['expiresAt'])" 2>/dev/null)
echo ""

# ---- Op3: Show Users (as ADMIN) ----
echo ">>> Op3: Show Users (ADMIN)"
curl -s -X POST "$REST_URL/showusers" \
  -H "Content-Type: application/json" \
  -d "{
    \"input\": {},
    \"token\": {
      \"tokenId\": \"$ADMIN_TOKEN_ID\",
      \"userId\": \"$ADMIN_USER_ID\",
      \"role\": \"$ADMIN_ROLE\",
      \"issuedAt\": $ADMIN_ISSUED,
      \"expiresAt\": $ADMIN_EXPIRES
    }
  }" | python3 -m json.tool 2>/dev/null
echo ""

# ---- Op3: Show Users (as USER - should fail) ----
echo ">>> Op3: Show Users (USER - should fail UNAUTHORIZED)"
curl -s -X POST "$REST_URL/showusers" \
  -H "Content-Type: application/json" \
  -d "{
    \"input\": {},
    \"token\": {
      \"tokenId\": \"$USER_TOKEN_ID\",
      \"userId\": \"$USER_USER_ID\",
      \"role\": \"$USER_ROLE\",
      \"issuedAt\": $USER_ISSUED,
      \"expiresAt\": $USER_EXPIRES
    }
  }" | python3 -m json.tool 2>/dev/null
echo ""

# ---- Op6: Show Authenticated Sessions (ADMIN) ----
echo ">>> Op6: Show Authenticated Sessions (ADMIN)"
curl -s -X POST "$REST_URL/showauthsessions" \
  -H "Content-Type: application/json" \
  -d "{
    \"input\": {},
    \"token\": {
      \"tokenId\": \"$ADMIN_TOKEN_ID\",
      \"userId\": \"$ADMIN_USER_ID\",
      \"role\": \"$ADMIN_ROLE\",
      \"issuedAt\": $ADMIN_ISSUED,
      \"expiresAt\": $ADMIN_EXPIRES
    }
  }" | python3 -m json.tool 2>/dev/null
echo ""

# ---- Op7: Show User Role (ADMIN checking user1) ----
echo ">>> Op7: Show User Role"
curl -s -X POST "$REST_URL/showuserrole" \
  -H "Content-Type: application/json" \
  -d "{
    \"input\": { \"userId\": \"user1\" },
    \"token\": {
      \"tokenId\": \"$ADMIN_TOKEN_ID\",
      \"userId\": \"$ADMIN_USER_ID\",
      \"role\": \"$ADMIN_ROLE\",
      \"issuedAt\": $ADMIN_ISSUED,
      \"expiresAt\": $ADMIN_EXPIRES
    }
  }" | python3 -m json.tool 2>/dev/null
echo ""

# ---- Op8: Change User Role (ADMIN changes user1 to BOFFICER) ----
echo ">>> Op8: Change User Role (user1 -> BOFFICER)"
curl -s -X POST "$REST_URL/changeuserrole" \
  -H "Content-Type: application/json" \
  -d "{
    \"input\": { \"userId\": \"user1\", \"newRole\": \"BOFFICER\" },
    \"token\": {
      \"tokenId\": \"$ADMIN_TOKEN_ID\",
      \"userId\": \"$ADMIN_USER_ID\",
      \"role\": \"$ADMIN_ROLE\",
      \"issuedAt\": $ADMIN_ISSUED,
      \"expiresAt\": $ADMIN_EXPIRES
    }
  }" | python3 -m json.tool 2>/dev/null
echo ""

# ---- Op5: Modify Account Attributes (ADMIN modifies user1 email) ----
echo ">>> Op5: Modify Account Attributes (change user1 email)"
curl -s -X POST "$REST_URL/modaccount" \
  -H "Content-Type: application/json" \
  -d "{
    \"input\": {
      \"userId\": \"user1\",
      \"attributes\": { \"email\": \"newemail@test.com\" }
    },
    \"token\": {
      \"tokenId\": \"$ADMIN_TOKEN_ID\",
      \"userId\": \"$ADMIN_USER_ID\",
      \"role\": \"$ADMIN_ROLE\",
      \"issuedAt\": $ADMIN_ISSUED,
      \"expiresAt\": $ADMIN_EXPIRES
    }
  }" | python3 -m json.tool 2>/dev/null
echo ""

# ---- Op9: Change User Password ----
echo ">>> Op9: Change User Password (user1)"
curl -s -X POST "$REST_URL/changeuserpwd" \
  -H "Content-Type: application/json" \
  -d "{
    \"input\": {
      \"userId\": \"user1\",
      \"oldPassword\": \"user123\",
      \"newPassword\": \"newuser456\"
    },
    \"token\": {
      \"tokenId\": \"$ADMIN_TOKEN_ID\",
      \"userId\": \"$ADMIN_USER_ID\",
      \"role\": \"$ADMIN_ROLE\",
      \"issuedAt\": $ADMIN_ISSUED,
      \"expiresAt\": $ADMIN_EXPIRES
    }
  }" | python3 -m json.tool 2>/dev/null
echo ""

# ---- Op10: Logout (USER) ----
echo ">>> Op10: Logout (USER)"
curl -s -X POST "$REST_URL/logout" \
  -H "Content-Type: application/json" \
  -d "{
    \"input\": { \"userID\": \"user1\" },
    \"token\": {
      \"tokenId\": \"$USER_TOKEN_ID\",
      \"userId\": \"$USER_USER_ID\",
      \"role\": \"$USER_ROLE\",
      \"issuedAt\": $USER_ISSUED,
      \"expiresAt\": $USER_EXPIRES
    }
  }" | python3 -m json.tool 2>/dev/null
echo ""

# ---- Op4: Delete Account (ADMIN deletes officer1) ----
echo ">>> Op4: Delete Account (officer1)"
curl -s -X POST "$REST_URL/deleteaccount" \
  -H "Content-Type: application/json" \
  -d "{
    \"input\": { \"userId\": \"officer1\" },
    \"token\": {
      \"tokenId\": \"$ADMIN_TOKEN_ID\",
      \"userId\": \"$ADMIN_USER_ID\",
      \"role\": \"$ADMIN_ROLE\",
      \"issuedAt\": $ADMIN_ISSUED,
      \"expiresAt\": $ADMIN_EXPIRES
    }
  }" | python3 -m json.tool 2>/dev/null
echo ""

# ---- Op10: Logout (ADMIN) ----
echo ">>> Op10: Logout (ADMIN)"
curl -s -X POST "$REST_URL/logout" \
  -H "Content-Type: application/json" \
  -d "{
    \"input\": { \"userID\": \"admin1\" },
    \"token\": {
      \"tokenId\": \"$ADMIN_TOKEN_ID\",
      \"userId\": \"$ADMIN_USER_ID\",
      \"role\": \"$ADMIN_ROLE\",
      \"issuedAt\": $ADMIN_ISSUED,
      \"expiresAt\": $ADMIN_EXPIRES
    }
  }" | python3 -m json.tool 2>/dev/null
echo ""

echo "============================================="
echo "All tests completed."
echo "============================================="
