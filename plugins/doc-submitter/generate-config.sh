#!/bin/bash
#
# Configuration Generator Script
# Generates services.yml and validation-rules.yaml from minimal input files
#
# Usage:
#   ./generate-config.sh farmer-mapping-hints.yaml farmer-business-rules.yaml
#   ./generate-config.sh student-mapping-hints.yaml student-business-rules.yaml
#

set -e  # Exit on error

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Default file paths
FORM_STRUCTURE="src/main/resources/docs-metadata/form_structure.yaml"
MAPPING_HINTS="${1:-mapping-hints.yaml}"
BUSINESS_RULES="${2:-business-rules.yaml}"
SERVICES_OUTPUT="${3:-services.yml}"
VALIDATION_OUTPUT="${4:-validation-rules.yaml}"

echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}   GovStack Configuration Generator${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo ""

# Check input files exist
if [ ! -f "$FORM_STRUCTURE" ]; then
    echo -e "${RED}✗ Error: form_structure.yaml not found at: $FORM_STRUCTURE${NC}"
    exit 1
fi

if [ ! -f "$MAPPING_HINTS" ]; then
    echo -e "${RED}✗ Error: mapping hints file not found: $MAPPING_HINTS${NC}"
    echo ""
    echo "Usage: $0 <mapping-hints.yaml> <business-rules.yaml> [services.yml] [validation-rules.yaml]"
    echo ""
    echo "Example:"
    echo "  $0 farmer-mapping-hints.yaml farmer-business-rules.yaml"
    exit 1
fi

if [ ! -f "$BUSINESS_RULES" ]; then
    echo -e "${RED}✗ Error: business rules file not found: $BUSINESS_RULES${NC}"
    exit 1
fi

echo -e "${YELLOW}Input Files:${NC}"
echo "  Form structure:  $FORM_STRUCTURE"
echo "  Mapping hints:   $MAPPING_HINTS"
echo "  Business rules:  $BUSINESS_RULES"
echo ""
echo -e "${YELLOW}Output Files:${NC}"
echo "  Services config: $SERVICES_OUTPUT"
echo "  Validation rules: $VALIDATION_OUTPUT"
echo ""

# Step 1: Compile if needed
echo -e "${BLUE}[1/3] Compiling generators...${NC}"
if ! mvn compile -q; then
    echo -e "${RED}✗ Compilation failed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Compilation successful${NC}"
echo ""

# Step 2: Generate services.yml
echo -e "${BLUE}[2/3] Generating services.yml...${NC}"
mvn exec:java -q \
    -Dexec.mainClass="global.govstack.farmreg.registration.util.ServicesYamlGenerator" \
    -Dexec.args="--form-structure $FORM_STRUCTURE --mapping-hints $MAPPING_HINTS --output $SERVICES_OUTPUT"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Generated: $SERVICES_OUTPUT${NC}"
    echo "  $(wc -l < "$SERVICES_OUTPUT") lines, $(wc -w < "$SERVICES_OUTPUT") words"
else
    echo -e "${RED}✗ Failed to generate services.yml${NC}"
    exit 1
fi
echo ""

# Step 3: Generate validation-rules.yaml
echo -e "${BLUE}[3/3] Generating validation-rules.yaml...${NC}"
mvn exec:java -q \
    -Dexec.mainClass="global.govstack.farmreg.registration.util.ValidationRulesGenerator" \
    -Dexec.args="--form-structure $FORM_STRUCTURE --business-rules $BUSINESS_RULES --output $VALIDATION_OUTPUT"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Generated: $VALIDATION_OUTPUT${NC}"
    echo "  $(wc -l < "$VALIDATION_OUTPUT") lines, $(wc -w < "$VALIDATION_OUTPUT") words"
else
    echo -e "${RED}✗ Failed to generate validation-rules.yaml${NC}"
    exit 1
fi
echo ""

# Summary
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  ✓ Configuration generation completed successfully!${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo ""
echo "Next steps:"
echo "  1. Review generated files:"
echo "     less $SERVICES_OUTPUT"
echo "     less $VALIDATION_OUTPUT"
echo ""
echo "  2. Deploy to plugin resources:"
echo "     cp $SERVICES_OUTPUT src/main/resources/docs-metadata/"
echo "     cp $VALIDATION_OUTPUT ../processing-server/src/main/resources/docs-metadata/"
echo ""
echo "  3. Build and deploy plugins:"
echo "     mvn clean package"
echo "     cd ../processing-server && mvn clean package"
echo ""
