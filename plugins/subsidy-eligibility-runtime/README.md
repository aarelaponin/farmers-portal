# EligibilityRuntime Plugin

**Version:** 8.1-SNAPSHOT
**Package:** `global.govstack.subsidy.lib`
**Type:** ApplicationPlugin (workflow tool plugin)

Per-applicant eligibility evaluation that bridges
[joget-rules-api](../joget-rules-api/) with workflow execution.

This is **D1.G** of Phase 1 of the Lesotho Farmers Portal subsidy adjustment plan.

## Why this exists

`joget-rules-api` already does the heavy lifting:
- Parses Rules Script DSL via ANTLR (rules-grammar)
- Compiles rules to SQL (eligibility filter, scoring, full per-rule pass/fail)
- Stores rulesets in `jreRuleset`

But it's a **population-level** API — its compiled queries return *all*
eligible records or scores. Workflows need a **per-applicant** answer:
"is *this one* application eligible?"

This plugin fills that gap. For each application:

1. Loads the ruleset bound to the target programme (via JRE REST)
2. Compiles it to SQL (via JRE REST — `/jre/compile`)
3. Runs the SQL **filtered to one applicant** (`WHERE id = ?`)
4. Persists per-rule pass/fail to `spEligRuleResult`, overall to `spEligResult`
5. Sets workflow variable `eligibilityDecision` = `PASS` | `FAIL` | `ERROR`

## Architecture

```
   ┌──────────────────┐  workflow tool activity
   │ subsidy_app_     │  
   │ review process   │  
   └────────┬─────────┘  
            ▼            
   ┌──────────────────────────────┐    HTTP POST   ┌─────────────────────┐
   │ EligibilityRuntime.execute() ├───────────────▶│ joget-rules-api     │
   │  - load ruleset (HTTP GET)   │  /jre/compile  │  - parse DSL        │
   │  - compile (HTTP POST)       │◀───────────────┤  - emit SQL         │
   │  - exec SQL for one farmer   │   {compiled:   │                     │
   │  - persist + set wf var      │     {fullEli-  └─────────────────────┘
   └────────┬─────────────────────┘     gibility-                          
            │                            Query, sco-                       
            ▼                            ringQuery}}                       
   spEligResult, spEligRuleResult                                          
   workflow var: eligibilityDecision                                       
```

The HTTP-REST coupling means **no compile-time dependency on rules-api**.
The runtime can be built and deployed independently. Pay one HTTP roundtrip
per evaluation (~5-10ms locally) for clean module boundaries.

## Build

```bash
cd subsidy-eligibility-runtime
mvn clean package
ls target/subsidy-eligibility-runtime-8.1-SNAPSHOT.jar
```

Java 11+, Maven 3.6+.

## Deploy

1. Ensure the `joget-rules-api` plugin is deployed and the `jre` API Builder
   app is configured with an API key (note the key — you'll need it).
2. Upload `target/subsidy-eligibility-runtime-8.1-SNAPSHOT.jar` via
   **Settings → Manage Plugins → Upload Plugin**.

## Use

Wrap in `MultiTools` for a workflow tool activity:

```json
{
  "pluginName": "org.joget.apps.app.lib.MultiTools",
  "pluginProperties": {
    "runInMultiThread": "",
    "comment": "Evaluate eligibility for this application",
    "tools": [{
      "className": "global.govstack.subsidy.lib.EligibilityRuntime",
      "properties": {
        "applicationId": "#variable.applicationId#",
        "applicantFarmerCode": "#variable.farmerCode#",
        "programCode": "#variable.programCode#",
        "jreBaseUrl": "http://localhost:8080/jw",
        "jreApiKey": "abc-XXX-jre-key",
        "writeAuditTrail": "true"
      }
    }]
  }
}
```

## Configuration properties

| Property | Required | Default | Description |
|---|---|---|---|
| `applicationId` | yes | `#variable.applicationId#` | spApplication.id |
| `applicantFarmerCode` | yes | `#variable.farmerCode#` | Farmer to evaluate |
| `programCode` | yes | `#variable.programCode#` | Target programme |
| `rulesetCode` | no | (empty) | Override which ruleset to use; if blank, looks up by programme binding |
| `jreBaseUrl` | yes | `http://localhost:8080/jw` | URL to the Joget instance hosting joget-rules-api |
| `jreApiKey` | yes | — | API key for the `jre` API Builder app |
| `writeAuditTrail` | no | `true` | Append entry to audit_log per evaluation |

## Outputs

### Database tables

- **`app_fd_speligresult`** — one row per application (upsert on applicationId):
  `applicationId`, `farmerCode`, `programCode`, `rulesetCode`, `totalScore`,
  `isEligible`, `decision`, `decisionReason`, `evaluatedAt`

- **`app_fd_speligruleresult`** — one row per rule per application:
  `applicationId`, `ruleName`, `passed`

### Workflow variables

- `eligibilityDecision` = `PASS` | `FAIL` | `ERROR`
- `eligibilityScore` = numeric total

### Plugin return value

```json
{
  "status": "OK",
  "decision": "PASS",
  "totalScore": 75.0,
  "isEligible": true,
  "ruleCount": 3,
  "durationMs": 412
}
```

## Spec

Full design spec: `lst-frm-prj/app/form-specs/_phase1/D1.G_EligibilityRuntime.plugin.spec.yaml`

## Implementation status

Activator + plugin class scaffold is complete and builds. Core logic is
implemented. The following items are marked TODO in code:

| Method | Status | Notes |
|---|---|---|
| `execute()` | ✅ implemented | full happy path + error handling |
| `loadRuleset` (HTTP GET) | ✅ implemented | supports both rulesetCode and programme binding |
| `compileScript` (HTTP POST) | ✅ implemented | calls /jre/compile |
| `runEligibilityForOne` | ✅ implemented | wraps full SQL with WHERE id = ? |
| `applyProgrammeThreshold` | ✅ implemented | reads spProgramEligibility for minimumScore |
| `persistResults` (spEligResult) | ✅ implemented | upsert by applicationId |
| `persistResults` (spEligRuleResult cleanup) | TODO | uses upsert by deterministic ID — could leave stale rows if rule names change between runs |
| `writeAuditLog` | TODO | logs only; full audit_log row write deferred |

## Testing

```bash
mvn test
```

Recommended test cases (per the spec):

- `ruleset_not_found` — programmeCode with no binding → ERROR
- `applicant_not_in_main_table` → ERROR
- `smallholder_passes` — REF-SMALLHOLDER + farmer with land < 2ha → PASS
- `smallholder_fails_mandatory` — land = 3ha → FAIL with mandatoryFailed
- `food_security_scoring` — REF-FOOD-TARGET + SEVERE farmer → PASS, score 65
- `idempotent` — evaluate twice → 1 spEligResult row, N spEligRuleResult rows

Integration test sequence (against the dev DB):

1. Apply D1.A, D1.B, D1.C, D1.D, D1.H seeds
2. Deploy joget-rules-api, FarmerDerivedRefresh, EligibilityRuntime
3. Run D1.I `load_reference_rulesets.sh` to register the 3 rulesets
4. SQL-bind REF-FOOD-TARGET to a test programme:
   ```sql
   UPDATE app_fd_jreruleset
     SET c_contexttype='PROGRAM', c_contextcode='PROG-TEST-001'
     WHERE c_rulesetcode='REF-FOOD-TARGET';
   ```
5. Run FarmerDerivedRefresh `scope=all`
6. Submit a test application from FR-T00003 to PROG-TEST-001
7. Trigger this plugin → verify spEligResult, spEligRuleResult, workflow var
