#!/bin/bash

# Test script for Generic Configuration Support
# This script helps test both V2 (hardcoded) and V3 (configuration-driven) modes

echo "========================================="
echo "GovStack Processing Server Plugin"
echo "Generic Configuration Support Test"
echo "========================================="
echo ""

# Configuration
JAR_FILE="target/processing-server-8.1-SNAPSHOT.jar"
TEST_DATA="src/main/resources/docs-metadata/test-data.json"
VALIDATION_DIR="/Users/aarelaponin/PycharmProjects/dev/gam/joget_validator"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check if JAR exists
if [ ! -f "$JAR_FILE" ]; then
    echo -e "${RED}ERROR: JAR file not found at $JAR_FILE${NC}"
    echo "Please run: mvn clean package -Dmaven.test.skip=true"
    exit 1
fi

echo -e "${GREEN}✓ JAR file found${NC}: $JAR_FILE"
echo ""

# Show JAR info
echo "JAR Information:"
echo "----------------"
ls -lh "$JAR_FILE"
echo ""

# Check for V3 classes in JAR
echo "Checking for V3 (Generic) Support Classes:"
echo "-------------------------------------------"
jar tf "$JAR_FILE" | grep -E "(GovStackDataMapperV3|GovStackRegistrationServiceV3)" | head -5
echo ""

# Check services.yml configuration
echo "Checking services.yml configuration:"
echo "------------------------------------"
jar tf "$JAR_FILE" | grep -E "services.*yml" | head -5
echo ""

echo -e "${YELLOW}DEPLOYMENT INSTRUCTIONS:${NC}"
echo "========================="
echo ""
echo "1. Copy the JAR to Joget:"
echo "   cp $JAR_FILE /path/to/joget/wflow/app_plugins/"
echo ""
echo "2. Restart Joget server"
echo ""
echo "3. Configure the plugin in Joget Admin:"
echo ""
echo "   For V2 Mode (Default - Hardcoded Farmer):"
echo "   ----------------------------------------"
echo "   - useGovStack: true"
echo "   - useV3: false (or leave empty)"
echo "   - serviceId: farmers_registry"
echo ""
echo "   For V3 Mode (Configuration-Driven):"
echo "   -----------------------------------"
echo "   - useGovStack: true"
echo "   - useV3: true"
echo "   - serviceId: farmers_registry (or your service)"
echo ""

echo -e "${YELLOW}TESTING INSTRUCTIONS:${NC}"
echo "===================="
echo ""
echo "1. Test API submission:"
echo "   curl -X POST http://localhost:8080/jw/api/govstack/farmers_registry/process \\"
echo "     -H \"Content-Type: application/json\" \\"
echo "     -d @$TEST_DATA"
echo ""
echo "2. Run validation (after API submission):"
echo "   cd $VALIDATION_DIR"
echo "   python3 run_diagnostic_validation.py --spec generated/test-validation.yml"
echo ""

echo -e "${YELLOW}WHAT TO VERIFY:${NC}"
echo "==============="
echo ""
echo "V2 Mode (Default):"
echo "-----------------"
echo "✓ Should work exactly as before"
echo "✓ Logs should show: \"Using GovStackRegistrationServiceV2 (hardcoded defaults)\""
echo "✓ All forms should populate correctly"
echo "✓ Grid data should save with proper parent relationships"
echo ""
echo "V3 Mode (Configuration-Driven):"
echo "-------------------------------"
echo "✓ Should work identically to V2 for farmers"
echo "✓ Logs should show: \"Using GovStackRegistrationServiceV3 (configuration-driven)\""
echo "✓ Logs should show: \"Loaded section to form map from configuration\""
echo "✓ All forms should populate correctly"
echo "✓ Grid data should save with proper parent relationships"
echo ""

echo -e "${GREEN}Key Differences to Watch:${NC}"
echo "========================="
echo "1. V2 uses hardcoded mappings in Java code"
echo "2. V3 reads mappings from services.yml serviceConfig section"
echo "3. Both should produce IDENTICAL results for farmer registration"
echo "4. V3 can support other services by changing services.yml"
echo ""

echo "========================================="
echo "Ready for testing!"
echo "========================================="