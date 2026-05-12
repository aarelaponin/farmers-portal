#!/bin/bash

################################################################################
# GovStack Service YAML Validator
# Purpose: Validates YAML syntax and required fields for service configurations
# Version: 1.0.0
# Date: 2025-10-29
################################################################################

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Counters
ERRORS=0
WARNINGS=0
CHECKS=0

# Helper functions
print_header() {
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║       GovStack Service YAML Validator v1.0.0                  ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
    ((CHECKS++))
}

print_error() {
    echo -e "${RED}✗${NC} $1"
    ((ERRORS++))
    ((CHECKS++))
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
    ((WARNINGS++))
}

print_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

print_section() {
    echo ""
    echo -e "${BLUE}━━━ $1 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

# Check if yq is installed
check_yq() {
    if ! command -v yq &> /dev/null; then
        print_warning "yq (YAML processor) not found. Installing syntax check will be limited."
        print_info "Install yq for better validation: brew install yq"
        return 1
    fi
    return 0
}

# Validate YAML syntax
validate_syntax() {
    local file=$1
    print_section "Syntax Validation"

    if check_yq; then
        if yq eval '.' "$file" > /dev/null 2>&1; then
            print_success "YAML syntax is valid"
        else
            print_error "YAML syntax errors detected"
            yq eval '.' "$file" 2>&1 | head -n 5
            return 1
        fi
    else
        # Basic check without yq
        if python3 -c "import yaml; yaml.safe_load(open('$file'))" 2>/dev/null; then
            print_success "YAML syntax is valid (basic check)"
        else
            print_error "YAML syntax errors detected"
            python3 -c "import yaml; yaml.safe_load(open('$file'))" 2>&1 | head -n 5
            return 1
        fi
    fi
}

# Check required fields
check_required_fields() {
    local file=$1
    print_section "Required Fields"

    # Check service.id
    if check_yq; then
        SERVICE_ID=$(yq eval '.service.id' "$file" 2>/dev/null)
        if [ "$SERVICE_ID" != "null" ] && [ -n "$SERVICE_ID" ]; then
            print_success "service.id present: $SERVICE_ID"
        else
            print_error "service.id is missing or empty"
        fi

        # Check service.serviceConfig.parentFormId
        PARENT_FORM=$(yq eval '.service.serviceConfig.parentFormId' "$file" 2>/dev/null)
        if [ "$PARENT_FORM" != "null" ] && [ -n "$PARENT_FORM" ]; then
            print_success "serviceConfig.parentFormId present: $PARENT_FORM"
        else
            print_error "serviceConfig.parentFormId is missing"
        fi

        # Check parentReferenceFields
        REF_FIELDS=$(yq eval '.service.serviceConfig.parentReferenceFields | length' "$file" 2>/dev/null)
        if [ "$REF_FIELDS" != "null" ] && [ "$REF_FIELDS" -gt 0 ]; then
            print_success "serviceConfig.parentReferenceFields present ($REF_FIELDS fields)"
        else
            print_error "serviceConfig.parentReferenceFields is missing or empty"
        fi

        # Check sectionToFormMap
        SECTIONS=$(yq eval '.service.serviceConfig.sectionToFormMap | length' "$file" 2>/dev/null)
        if [ "$SECTIONS" != "null" ] && [ "$SECTIONS" -gt 0 ]; then
            print_success "serviceConfig.sectionToFormMap present ($SECTIONS sections)"
        else
            print_error "serviceConfig.sectionToFormMap is missing or empty"
        fi

        # Check formMappings
        MAPPINGS=$(yq eval '.formMappings | keys | .[]' "$file" 2>/dev/null | wc -l)
        if [ "$MAPPINGS" -gt 0 ]; then
            print_success "formMappings present ($MAPPINGS sections)"
        else
            print_error "formMappings is missing or empty"
        fi
    else
        print_warning "Skipping detailed field checks (yq not available)"
    fi
}

# Check naming conventions
check_naming_conventions() {
    local file=$1
    print_section "Naming Conventions"

    if check_yq; then
        SERVICE_ID=$(yq eval '.service.id' "$file" 2>/dev/null)

        # Check service ID format (lowercase, underscores)
        if [[ "$SERVICE_ID" =~ ^[a-z][a-z0-9_]*$ ]]; then
            print_success "Service ID follows naming convention"
        else
            print_warning "Service ID should be lowercase with underscores only: $SERVICE_ID"
        fi

        # Check for consistent formId naming
        FORM_IDS=$(yq eval '.formMappings | .[] | .formId' "$file" 2>/dev/null)
        if [ -n "$FORM_IDS" ]; then
            while IFS= read -r form_id; do
                if [[ "$form_id" =~ ^[a-z][a-zA-Z0-9]*$ ]]; then
                    continue
                else
                    print_warning "Form ID doesn't follow camelCase convention: $form_id"
                fi
            done <<< "$FORM_IDS"
        fi
    fi
}

# Check field mappings
check_field_mappings() {
    local file=$1
    print_section "Field Mappings"

    if check_yq; then
        # Get all form mapping sections
        SECTIONS=$(yq eval '.formMappings | keys | .[]' "$file" 2>/dev/null)

        if [ -z "$SECTIONS" ]; then
            print_error "No form mappings found"
            return
        fi

        while IFS= read -r section; do
            # Check if section has fields array
            FIELD_COUNT=$(yq eval ".formMappings.$section.fields | length" "$file" 2>/dev/null)
            TYPE=$(yq eval ".formMappings.$section.type" "$file" 2>/dev/null)

            if [ "$FIELD_COUNT" != "null" ] && [ "$FIELD_COUNT" -gt 0 ]; then
                print_success "Section '$section' has $FIELD_COUNT field(s) mapped"

                # Check each field has required properties
                for ((i=0; i<$FIELD_COUNT; i++)); do
                    JOGET=$(yq eval ".formMappings.$section.fields[$i].joget" "$file" 2>/dev/null)
                    GOVSTACK=$(yq eval ".formMappings.$section.fields[$i].govstack" "$file" 2>/dev/null)

                    if [ "$JOGET" = "null" ] || [ -z "$JOGET" ]; then
                        print_warning "Field $i in section '$section' missing 'joget' property"
                    fi

                    if [ "$GOVSTACK" = "null" ] || [ -z "$GOVSTACK" ]; then
                        print_warning "Field $i in section '$section' missing 'govstack' property"
                    fi
                done
            else
                if [ "$TYPE" != "array" ]; then
                    print_warning "Section '$section' has no fields defined"
                fi
            fi
        done <<< "$SECTIONS"
    fi
}

# Check grid mappings
check_grid_mappings() {
    local file=$1
    print_section "Grid Mappings"

    if check_yq; then
        # Check if gridMappings exists
        GRID_COUNT=$(yq eval '.service.serviceConfig.gridMappings | length' "$file" 2>/dev/null)

        if [ "$GRID_COUNT" = "null" ] || [ "$GRID_COUNT" -eq 0 ]; then
            print_info "No grid mappings defined (grids are optional)"
            return
        fi

        print_success "Grid mappings found ($GRID_COUNT grid(s))"

        # Check each grid has required properties
        GRIDS=$(yq eval '.service.serviceConfig.gridMappings | keys | .[]' "$file" 2>/dev/null)
        while IFS= read -r grid; do
            FORM_ID=$(yq eval ".service.serviceConfig.gridMappings.$grid.formId" "$file" 2>/dev/null)
            PARENT_FIELD=$(yq eval ".service.serviceConfig.gridMappings.$grid.parentField" "$file" 2>/dev/null)

            if [ "$FORM_ID" = "null" ] || [ -z "$FORM_ID" ]; then
                print_error "Grid '$grid' missing formId"
            else
                print_success "Grid '$grid' → formId: $FORM_ID"
            fi

            if [ "$PARENT_FIELD" = "null" ] || [ -z "$PARENT_FIELD" ]; then
                print_error "Grid '$grid' missing parentField"
            else
                print_success "Grid '$grid' → parentField: $PARENT_FIELD"
            fi
        done <<< "$GRIDS"
    fi
}

# Generate summary
print_summary() {
    echo ""
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║                     Validation Summary                        ║${NC}"
    echo -e "${BLUE}╠════════════════════════════════════════════════════════════════╣${NC}"

    if [ $ERRORS -eq 0 ] && [ $WARNINGS -eq 0 ]; then
        echo -e "${BLUE}║${NC}  ${GREEN}✓ All checks passed!${NC}                                          ${BLUE}║${NC}"
    elif [ $ERRORS -eq 0 ]; then
        echo -e "${BLUE}║${NC}  ${GREEN}✓ No errors${NC} | ${YELLOW}$WARNINGS warning(s)${NC}                                    ${BLUE}║${NC}"
    else
        echo -e "${BLUE}║${NC}  ${RED}✗ $ERRORS error(s)${NC} | ${YELLOW}$WARNINGS warning(s)${NC}                                 ${BLUE}║${NC}"
    fi

    echo -e "${BLUE}║${NC}  Total checks: $CHECKS                                             ${BLUE}║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"
    echo ""

    if [ $ERRORS -eq 0 ]; then
        echo -e "${GREEN}✓ YAML file is ready for deployment${NC}"
        return 0
    else
        echo -e "${RED}✗ Please fix errors before deploying${NC}"
        return 1
    fi
}

################################################################################
# Main execution
################################################################################

print_header

# Check arguments
if [ $# -eq 0 ]; then
    echo -e "${RED}Error:${NC} No YAML file specified"
    echo ""
    echo "Usage: $0 <yaml-file>"
    echo ""
    echo "Example:"
    echo "  $0 student_enrollment.yml"
    echo "  $0 ~/govstack-services/receiver-configs/farmers_registry.yml"
    exit 1
fi

YAML_FILE=$1

# Check if file exists
if [ ! -f "$YAML_FILE" ]; then
    echo -e "${RED}Error:${NC} File not found: $YAML_FILE"
    exit 1
fi

print_info "Validating file: $YAML_FILE"
echo ""

# Run all validations
validate_syntax "$YAML_FILE" || true
check_required_fields "$YAML_FILE" || true
check_naming_conventions "$YAML_FILE" || true
check_field_mappings "$YAML_FILE" || true
check_grid_mappings "$YAML_FILE" || true

# Print summary and exit
print_summary
exit $?
