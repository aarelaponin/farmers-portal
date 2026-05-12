# food_security_targeting.rs — Food Security Targeting Reference Set
# Phase 1, Subsidy Adjustment Plan — closes Gap 11
# Scope: FARMER_APPLICATION
#
# Prioritises food-insecure households for poverty-targeted subsidies.
# Policy basis: Section 2.2.2 — food insecurity is "a primary justification".
#
# Use this rule set in:
#   - Block Farming
#   - Emergency Drought Relief
#   - Vulnerable Group Programmes
#
# Import via /jw/api/jre/jre/saveRuleset (see anti_dependency.rs for shape).

RULE "Food insecure household"
  TYPE: PRIORITY
  CATEGORY: FOOD_SECURITY
  ORDER: 10
  SCORE: 35
  WHEN food_security_status IN ("MODERATE", "SEVERE", "CRISIS")
  PASS MESSAGE: "Qualifies for food-security targeting"
  FAIL MESSAGE: "Household reports food-secure status — programme prioritises insecure households"

RULE "High vulnerability"
  TYPE: PRIORITY
  CATEGORY: ECONOMIC
  ORDER: 20
  SCORE: 20
  WHEN vulnerability_score >= 50
  PASS MESSAGE: "Vulnerability above threshold"
  FAIL MESSAGE: "Vulnerability below 50 — programme targets vulnerable households"

RULE "Female-headed bonus"
  TYPE: BONUS
  CATEGORY: HOUSEHOLD
  ORDER: 30
  SCORE: 10
  WHEN female_headed_household = true
  PASS MESSAGE: "Bonus weight for female-headed household"
