#!/bin/bash

# Form Creator API Test Script
# This script demonstrates how to use the Form Creator API

# Configuration
JOGET_URL="http://localhost:8080"
API_ID="test_api"
API_KEY="test_key"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Form Creator API Test Script${NC}"
echo "======================================"
echo ""

# Test 1: Simple Form Creation
echo -e "${YELLOW}Test 1: Creating Simple Form${NC}"
echo "--------------------------------------"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$JOGET_URL/jw/api/formcreator/formcreator/forms" \
  -H "api-id: $API_ID" \
  -H "api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d @simple-form-request.json)

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    echo -e "${GREEN}✓ Test 1 Passed (HTTP $HTTP_CODE)${NC}"
    echo "Response: $BODY"
else
    echo -e "${RED}✗ Test 1 Failed (HTTP $HTTP_CODE)${NC}"
    echo "Response: $BODY"
fi
echo ""

# Test 2: Complete Form with API and CRUD
echo -e "${YELLOW}Test 2: Creating Form with API and CRUD${NC}"
echo "--------------------------------------"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$JOGET_URL/jw/api/formcreator/formcreator/forms" \
  -H "api-id: $API_ID" \
  -H "api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d @complete-form-request.json)

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    echo -e "${GREEN}✓ Test 2 Passed (HTTP $HTTP_CODE)${NC}"
    echo "Response: $BODY"
else
    echo -e "${RED}✗ Test 2 Failed (HTTP $HTTP_CODE)${NC}"
    echo "Response: $BODY"
fi
echo ""

# Test 3: Invalid Request (Missing Required Field)
echo -e "${YELLOW}Test 3: Testing Validation (Missing formId)${NC}"
echo "--------------------------------------"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$JOGET_URL/jw/api/formcreator/formcreator/forms" \
  -H "api-id: $API_ID" \
  -H "api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "formName": "Invalid Form",
    "tableName": "app_fd_invalid",
    "formDefinition": "{}"
  }')

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 400 ]; then
    echo -e "${GREEN}✓ Test 3 Passed (HTTP $HTTP_CODE - Expected validation error)${NC}"
    echo "Response: $BODY"
else
    echo -e "${RED}✗ Test 3 Failed (Expected HTTP 400, got $HTTP_CODE)${NC}"
    echo "Response: $BODY"
fi
echo ""

# Test 4: Invalid JSON
echo -e "${YELLOW}Test 4: Testing Invalid JSON${NC}"
echo "--------------------------------------"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$JOGET_URL/jw/api/formcreator/formcreator/forms" \
  -H "api-id: $API_ID" \
  -H "api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d 'INVALID JSON')

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 400 ]; then
    echo -e "${GREEN}✓ Test 4 Passed (HTTP $HTTP_CODE - Expected validation error)${NC}"
    echo "Response: $BODY"
else
    echo -e "${RED}✗ Test 4 Failed (Expected HTTP 400, got $HTTP_CODE)${NC}"
    echo "Response: $BODY"
fi
echo ""

echo -e "${YELLOW}======================================"
echo "Test Suite Complete"
echo "======================================${NC}"
