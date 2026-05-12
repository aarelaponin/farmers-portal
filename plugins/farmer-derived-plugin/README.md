# FarmerDerivedRefresh Plugin

**Version:** 8.1-SNAPSHOT
**Package:** `global.govstack.farmer.lib`
**Type:** ApplicationPlugin (workflow tool plugin)

Recomputes the `spFarmerDerived` snapshot for one or all farmers from raw
farmer data + program-history data. Designed to run nightly (Joget scheduler)
and on-demand (workflow tool activity at the start of subsidy_application_review).

This is **D1.E** of Phase 1 of the Lesotho Farmers Portal subsidy adjustment plan.

## Architecture

```
       ┌─────────────────────────┐
       │ Joget scheduler / WF    │
       │ tool activity           │
       └────────────┬────────────┘
                    ▼
       ┌─────────────────────────┐
       │ FarmerDerivedRefresh    │  scope = all | single
       │ .execute()              │
       └────────────┬────────────┘
                    ▼
   reads farmerBasicInfo / farmerIncomePrograms /
         farmerHousehold / householdMemberForm /
         parcelRegistration / livestockDetailsForm /
         spProgParticip
                    ▼
   writes spFarmerDerived (upsert by farmerCode)
   writes audit_log (one row per farmer)
```

## Build

```bash
cd farmer-derived-plugin
mvn clean package
ls target/farmer-derived-plugin-8.1-SNAPSHOT.jar
```

Java 11+, Maven 3.6+.

## Deploy

1. Stop Joget (or use the Manage Plugins UI)
2. Upload `target/farmer-derived-plugin-8.1-SNAPSHOT.jar` via
   **Settings → Manage Plugins → Upload Plugin**
3. Restart Joget if you used the file copy method

## Use

### As a workflow tool activity

Wrap in `MultiTools` (gs-plugins house style for custom plugins):

```json
{
  "pluginName": "org.joget.apps.app.lib.MultiTools",
  "pluginProperties": {
    "runInMultiThread": "",
    "comment": "Refresh derived attributes for the applicant",
    "tools": [{
      "className": "global.govstack.farmer.lib.FarmerDerivedRefresh",
      "properties": {
        "scope": "single",
        "farmerCode": "#variable.farmerCode#",
        "staleThresholdDays": "0",
        "dryRun": "false"
      }
    }]
  }
}
```

### As a scheduled task

Configure in **Settings → Scheduler**:

- Cron: `0 0 2 * * ?` (02:00 daily SAST)
- Plugin: `org.joget.apps.app.lib.MultiTools`
- Properties: same shape as above with `scope: all` and `staleThresholdDays: 1`

## Configuration properties

| Property | Type | Default | Description |
|---|---|---|---|
| `scope` | select | `single` | `single` = one farmer (use farmerCode); `all` = every farmer |
| `farmerCode` | text | `#variable.farmerCode#` | When scope=single, the farmer code to refresh |
| `staleThresholdDays` | number | `1` | When scope=all, skip farmers refreshed within N days |
| `dryRun` | bool | `false` | Log intended writes without persisting |

## Spec

Full design spec: `lst-frm-prj/app/form-specs/_phase1/D1.E_FarmerDerivedRefresh.plugin.spec.yaml`

## Implementation status

The Activator + plugin class scaffold is complete and builds. The
following step methods are stubbed with TODO markers — they need
implementation per the spec sections referenced in code:

| Method | Status | Spec section |
|---|---|---|
| `loadFarmerBasic`, `loadLatestIncome` | ✅ implemented | step_1 |
| `computeHouseholdAggregates` | TODO | step_2 |
| `computeLandAggregates` | TODO | step_3 |
| `computeLivestockAggregates` | TODO | step_4 |
| `computeProgramHistory` | ✅ implemented | step_5 |
| `reconcileFoodSecurity` | partial (priority 1 done; priority 2 stubbed) | step_6 |
| `computeVulnerabilityScore` | TODO (placeholder = 0) | step_7 |
| `writeAudit` | TODO (logs only — no audit_log write yet) | step_9 |

## Testing

```bash
mvn test
```

Unit tests live under `src/test/java/global/govstack/farmer/lib/`.
Recommended test cases (per the spec):

- `empty_farmer` — no spProgParticip rows → all program history fields = 0/null
- `farmer_with_one_recent_participation` — programsInLast2Seasons = 1
- `food_security_self_reported` — currentFoodSecurityStatus = self-reported
- `idempotent` — running twice produces identical output
- `scope_single_nonexistent` — graceful fail with audit log entry
