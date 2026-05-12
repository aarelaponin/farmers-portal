#!/usr/bin/env bash
# =============================================================================
# D1.I — Load three reference rulesets into the JRE via REST API
# Phase 1, Subsidy Adjustment Plan (revised — DSL-based, not criterion rows)
#
# Pre-requisites:
#   - joget-rules-api plugin deployed
#   - jreFieldScope.FARMER_APPLICATION + 18 jreFieldDefinition rows seeded (D1.D)
#   - JRE API exposed at /jw/api/jre/jre/saveRuleset (configure API Builder app id 'jre')
#
# Each rule file is a Rules Script DSL document. The /jre/saveRuleset endpoint
# stores it in app_fd_jreruleset.c_script.
#
# Re-run safe: saveRuleset upserts on rulesetCode.
# =============================================================================
set -euo pipefail

JOGET_BASE="${JOGET_BASE:-http://20.87.213.78:8080/jw}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RULES_DIR="$SCRIPT_DIR/rules"

# JRE API key — set this via env var. Get it from the API Builder app's API Key menu.
JRE_API_KEY="${JRE_API_KEY:?Set JRE_API_KEY env var to the jre app's API key}"

post_ruleset() {
    local code="$1"
    local name="$2"
    local file="$3"
    local context_type="${4:-TEMPLATE}"

    if [[ ! -f "$file" ]]; then
        echo "ERROR: rule file not found: $file" >&2
        return 1
    fi

    # Read the DSL file content
    local script
    script=$(cat "$file")

    echo "Loading $code from $(basename "$file")..."

    # Build JSON payload using jq (escapes the script body correctly)
    local payload
    payload=$(jq -n \
        --arg rulesetCode "$code" \
        --arg rulesetName "$name" \
        --arg fieldScopeCode "FARMER_APPLICATION" \
        --arg contextType "$context_type" \
        --arg script "$script" \
        '{rulesetCode: $rulesetCode, rulesetName: $rulesetName,
          fieldScopeCode: $fieldScopeCode, contextType: $contextType,
          script: $script}')

    curl -s -X POST \
        -H "Content-Type: application/json" \
        -H "api_key: $JRE_API_KEY" \
        -d "$payload" \
        "$JOGET_BASE/api/jre/jre/saveRuleset" \
        | jq '.'
    echo ""
}

# ============================================================================
# Three reference rulesets
# ============================================================================
post_ruleset "REF-ANTI-DEP"     "Reference: Anti-Dependency"           "$RULES_DIR/anti_dependency.rs"
post_ruleset "REF-FOOD-TARGET"  "Reference: Food Security Targeting"   "$RULES_DIR/food_security_targeting.rs"
post_ruleset "REF-SMALLHOLDER"  "Reference: Smallholder Eligibility"   "$RULES_DIR/smallholder_eligibility.rs"

# ============================================================================
# Optional: publish them so they're visible to the rule editor's "templates" view
# ============================================================================
for code in REF-ANTI-DEP REF-FOOD-TARGET REF-SMALLHOLDER; do
    echo "Publishing $code..."
    curl -s -X POST \
        -H "Content-Type: application/json" \
        -H "api_key: $JRE_API_KEY" \
        -d "{\"rulesetCode\": \"$code\"}" \
        "$JOGET_BASE/api/jre/jre/publishRuleset" \
        | jq '.'
    echo ""
done

echo "Done. Verify with:"
echo "  curl -H 'api_key: \$JRE_API_KEY' '$JOGET_BASE/api/jre/jre/loadRuleset?rulesetCode=REF-ANTI-DEP'"
