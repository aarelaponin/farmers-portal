# anti_dependency.rs — Anti-Dependency Reference Set
# Phase 1, Subsidy Adjustment Plan — closes Gap 4
# Scope: FARMER_APPLICATION
#
# Discourages repeat beneficiaries across programmes.
# Policy basis: Section 1.2.iv — "dependency syndrome" warning.
#
# These are scoring rules (not mandatory) — programmes that prioritise
# new beneficiaries should weight these high; programmes responding to
# acute crises (drought relief, emergency) should weight them low.
#
# Import via:
#   POST /jw/api/jre/jre/saveRuleset
#   { "rulesetCode": "REF-ANTI-DEP",
#     "rulesetName": "Reference: Anti-Dependency",
#     "fieldScopeCode": "FARMER_APPLICATION",
#     "contextType": "TEMPLATE",
#     "script": <contents of this file> }

RULE "Programs in last 2 seasons cap"
  TYPE: INCLUSION
  CATEGORY: PROGRAM_HISTORY
  MANDATORY: NO
  ORDER: 10
  SCORE: 30
  WHEN programs_last_2_seasons <= 1
  PASS MESSAGE: "Within recent-program cap"
  FAIL MESSAGE: "Received subsidies from too many recent programmes"

RULE "Lifetime benefits cap"
  TYPE: INCLUSION
  CATEGORY: PROGRAM_HISTORY
  MANDATORY: NO
  ORDER: 20
  SCORE: 25
  WHEN total_benefits_lifetime <= 10000
  PASS MESSAGE: "Lifetime benefits within cap"
  FAIL MESSAGE: "Lifetime benefits exceed LSL 10,000 cap"
