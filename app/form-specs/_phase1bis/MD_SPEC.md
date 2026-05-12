# Phase 1bis — MD Lookup Specification

This is the canonical specification for all MD lookups touched by the
component-03 (subsidy) refactor. Generated 2026-04-26 after the user-approved
consolidations:

- md18registrationChan absorbs all 3 "channel" fields across spAppeal,
  spProgParticip, spSeasonHarvest (with code extensions)
- mdYieldStatus absorbs productionStatus (single 6-code severity scale)

## Reuse summary

| MD ID | Action | Affected fields |
|---|---|---|
| `md02language` | Re-point as-is | spAppeal.preferredLanguage, spNotification.language |
| `md11hazard` | **Extend** with THEFT, OTHER, NONE | spCropHarvestItem.lossFactors |
| `md15areaUnits` | Re-point as-is | spCropHarvestItem.areaUnit |
| `md18registrationChan` | **Extend** with portal, phone, sms, campaign, field_visit, mobile_app, email | spAppeal.preferredContact, spAppeal.submissionChannel, spProgParticip.enrollmentChannel, spSeasonHarvest.collectionMethod |
| `md22applicationStatu` | Re-point with code alignment | spApplication.status |
| `md34notificationType` | **Extend** with MANUAL, APP_UNDER_REVIEW, APP_DOCS_REQUIRED, ENROLLED, BENEFIT_ISSUED, DISTRIBUTION_OPEN, OVERDUE, CUSTOM | spNotifTemplate.triggerEvent |

## New MDs (39 total — md52 through md90)

| ID | Form ID | Codes | Used by |
|---|---|---|---|
| md52 | md52programStatus | DRAFT, PENDING_APPROVAL, APPROVED, REJECTED, ACTIVE, CLOSED, ARCHIVED | spProgramApproval.status (after architectural move) |
| md53 | md53priority | HIGH, NORMAL, LOW | spAppeal.priority, spNotification.priority, spNotifTemplate.priority |
| md54 | md54reportingFreq | WEEKLY, MONTHLY, QUARTERLY, ANNUAL | spKpiRow.frequency, spProgramMonitoring.reportingFrequency |
| md55 | md55geographicScope | NATIONAL, DISTRICT, ZONE | spProgramGeography.geographicScope |
| md56 | md56targetingStrat | UNIVERSAL, CATEGORICAL, GEOGRAPHIC, COMBINED | spProgramBeneficiary.targetingStrategy |
| md57 | md57priorityModel | FCFS, PRIORITY_SCORE, RANDOM, COMMUNITY | spProgramBeneficiary.priorityModel |
| md58 | md58benefitItemType | INPUT, EQUIPMENT, TRAINING, CASH | spBenefitItemRow.itemType |
| md59 | md59ruleType | INCLUSION, EXCLUSION, PRIORITY, BONUS | spEligCriterionRow.ruleType (transitional) |
| md60 | md60evalStrategy | ALL_MUST_PASS, SCORE_BASED, WEIGHTED | spProgramEligibility.evaluationStrategy (transitional) |
| md61 | md61approvalFlow | STANDARD, EXPRESS, EMERGENCY | spProgramApproval.approvalWorkflow |
| md62 | md62monitoringApp | CONTINUOUS, PERIODIC, SAMPLING, POST_HOC | spProgramMonitoring.monitoringApproach |
| md63 | md63dataCollMethod | SYSTEM_AUTO, FIELD_REPORTS, SURVEY, MIXED | spProgramMonitoring.dataCollectionMethod |
| md64 | md64documentStatus | PENDING, VERIFIED, REJECTED, RESUBMIT, EXPIRED | spApplicationDoc.status |
| md65 | md65docRejReason | ILLEGIBLE, INCOMPLETE, WRONG_TYPE, EXPIRED, FORGED, MISMATCH, OTHER | spApplicationDoc.rejectionReason |
| md66 | md66eligibStatus | LIKELY_ELIGIBLE, POSSIBLY_ELIGIBLE, LIKELY_INELIGIBLE, UNKNOWN | spApplication.eligibilityStatus |
| md67 | md67mobileMoneyProv | MPESA, ECOCASH, OTHER | spApplication.mobileMoneyProvider |
| md68 | md68appealType | APPLICATION_REJECTION, ELIGIBILITY_DECISION, BENEFIT_AMOUNT, DOCUMENT_REJECTION, DISTRIBUTION_ISSUE, PAYMENT_ISSUE, ENROLLMENT_DENIED, EVAL_DISPUTE, OTHER | spAppeal.appealType |
| md69 | md69entityType | APPLICATION, PROGRAM, ENROLLMENT, ENTITLEMENT, DISTRIBUTION, NOTIFICATION, VOUCHER | spAppeal.relatedEntityType, spNotification.relatedEntityType |
| md70 | md70originalDecision | REJECTED, INELIGIBLE, PARTIAL, DEDUCTED, SUSPENDED, DISQUALIFIED | spAppeal.originalDecision |
| md71 | md71appealGrounds | INCORRECT_INFO, NEW_EVIDENCE, CIRCUMSTANCE_CHANGE, PROCEDURAL_ERROR, MISINTERPRETATION, DISCRIMINATION, OTHER | spAppeal.appealGrounds (multi) |
| md72 | md72appealStatus | DRAFT, SUBMITTED, ACKNOWLEDGED, UNDER_REVIEW, INFO_REQUESTED, ESCALATED, RESOLVED, CLOSED, WITHDRAWN, OVERDUE | spAppeal.status |
| md73 | md73resolutionDec | UPHELD, PARTIALLY_UPHELD, DISMISSED_VALID, DISMISSED_PROCEDURAL, DISMISSED_LATE | spAppeal.resolutionDecision |
| md74 | md74escalationLevel | L1, L2, L3, L4 | spAppeal.escalationLevel |
| md75 | md75particStatus | ENROLLED, ACTIVE, COMPLETED, WITHDRAWN, DISQUALIFIED, SUSPENDED, EXPIRED | spProgParticip.status |
| md76 | md76exitReason | COMPLETED, INELIGIBLE, VOLUNTARY, FRAUD, NON_COMPLIANCE, DECEASED, RELOCATED, OTHER | spProgParticip.exitReason |
| md77 | md77satisfactionRtg | 1, 2, 3, 4, 5 | spProgParticip.satisfactionRating |
| md78 | md78notifChannel | SMS, EMAIL, PUSH, USSD | spNotification.channel |
| md79 | md79notifStatus | PENDING, QUEUED, SENT, DELIVERED, FAILED, EXPIRED, CANCELLED | spNotification.status |
| md80 | md80notifCategory | APPLICATION, ELIGIBILITY, ENROLLMENT, DISTRIBUTION, PAYMENT, REMINDER, ALERT, GENERAL | spNotifTemplate.category |
| md81 | md81sendTiming | IMMEDIATE, SCHEDULED, DELAYED | spNotifTemplate.sendImmediately |
| md82 | md82yieldSource | BASELINE, FARMER, EXTENSION, HISTORICAL, MANUAL | spCropHarvItem.expectedYieldSource, spCropHarvestItem.expectedYieldSource |
| md83 | md83yieldStatus | EXCELLENT, GOOD, MODERATE, POOR, FAILED, TOTAL_LOSS | spCropHarvItem.yieldStatus, spCropHarvestItem.productionStatus, spSeasonHarvest.productionStatus |
| md84 | md84seedType | IMPROVED, LOCAL, RECYCLED, MIXED, UNKNOWN | spCropHarvItem.seedType, spCropHarvestItem.seedType |
| md85 | md85seedSource | SUBSIDY, PURCHASED, SAVED, GIFT, CREDIT | spCropHarvItem.seedSource, spCropHarvestItem.seedSource, spCropHarvestItem.fertilizerSource, spSeasonHarvest.fertilizerSource |
| md86 | md86fertilizerType | BASAL, TOP, FOLIAR, ORGANIC | spCropHarvestItem.fertilizerType (multi) |
| md87 | md87irrigationType | RAINFED, FULL, SUPPLEMENTAL, DRIP | spCropHarvestItem.irrigationType |
| md88 | md88harvestDispos | CONSUMPTION, SALE, SEED, GIFT, STORAGE, LOST | spCropHarvestItem.harvestDisposition (multi) |
| md89 | md89shockImpactLvl | NONE, MINOR, MODERATE, SEVERE, TOTAL | spSeasonHarvest.shockImpactLevel |
| md90 | md90harvestStatus | DRAFT, SUBMITTED, VERIFIED, APPROVED, REJECTED | spSeasonHarvest.status |

## Notes

- md59ruleType + md60evalStrategy are tagged "transitional" — Tab 6 is being
  retrofitted with the JRE rule editor in Phase 2; these MDs (and the forms
  that use them) become obsolete then.
- All form IDs respect Joget's 24-char cap on form IDs.
- All MDs follow the standard md-lookup pattern: `c_code`, `c_name`,
  `c_description`, `c_displayorder`, `c_isactive` ('Y'/'N'), with timestamp
  audit columns added by Joget.

## Deliverable files

- `MD52-90_form_definitions.sql` — INSERT statements for 39 new app_form rows
- `MD52-90_data_seed.sql` — INSERT statements for codes/labels in each new MD
- `MD_extensions.sql` — INSERT statements adding new codes to md11, md18, md22, md34
- `form_patches/` — patched f03.* JSON files (16 forms with LOV-to-MD swaps)
