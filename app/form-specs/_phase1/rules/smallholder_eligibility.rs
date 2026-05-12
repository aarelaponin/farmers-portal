# smallholder_eligibility.rs — Smallholder Eligibility Reference Set
# Phase 1, Subsidy Adjustment Plan
# Scope: FARMER_APPLICATION
#
# Confirms farmer fits the policy's smallholder definition (<2 ha cultivated land).
# Policy basis: Section 4.2.1.
#
# These are MANDATORY INCLUSION rules — they hard-block applications that
# don't qualify regardless of other scoring. Use as a base layer combined
# with anti_dependency or food_security_targeting.
#
# Import via /jw/api/jre/jre/saveRuleset.

RULE "Land under 2 hectares"
  TYPE: INCLUSION
  CATEGORY: AGRICULTURAL
  MANDATORY: YES
  ORDER: 10
  WHEN total_land_ha < 2.0
  PASS MESSAGE: "Qualifies as smallholder farmer (land < 2 ha)"
  FAIL MESSAGE: "Smallholder programmes require land < 2 ha"

RULE "Active cultivation"
  TYPE: INCLUSION
  CATEGORY: AGRICULTURAL
  MANDATORY: YES
  ORDER: 20
  WHEN cultivated_land_ha > 0
  PASS MESSAGE: "Actively cultivating land"
  FAIL MESSAGE: "Must be actively cultivating land"

RULE "Adult applicant"
  TYPE: INCLUSION
  CATEGORY: DEMOGRAPHIC
  MANDATORY: YES
  ORDER: 30
  WHEN farmer_age >= 18
  PASS MESSAGE: "Of legal age"
  FAIL MESSAGE: "Applicant must be 18 or older"
