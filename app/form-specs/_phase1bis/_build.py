#!/usr/bin/env python3
"""
Phase 1bis builder — generates:
  - 39 new MD form-definition JSON files (md52..md90)
  - SQL: app_form INSERTs for the 39 new MD form definitions
  - SQL: app_datalist INSERTs for the 39 new list_md*
  - SQL: data seed for the 39 new MDs (codes + labels)
  - SQL: extensions for md11, md18, md22, md34 (new codes only)
  - Patched form JSONs for each violation, swapping static `options`
    arrays for `optionsBinder` pointing at the right MD.

Run from the project root. Outputs land under app/form-specs/_phase1bis/.
"""
from __future__ import annotations
import json, os, re, sys
from copy import deepcopy

ROOT = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.abspath(os.path.join(ROOT, '..', '..'))
COMPONENT03 = os.path.join(PROJECT, '_forms', 'component03')
OUT_FORMS = os.path.join(ROOT, 'md_forms')
OUT_PATCHED = os.path.join(ROOT, 'form_patches')
os.makedirs(OUT_FORMS, exist_ok=True)
os.makedirs(OUT_PATCHED, exist_ok=True)

# ============================================================================
# MD definitions (codes, labels)
# ============================================================================
MDS = {
    'md52programStatus':    ('MD.52 - Program Status', [
        ('DRAFT','Draft'),('PENDING_APPROVAL','Pending Approval'),
        ('APPROVED','Approved'),('REJECTED','Rejected'),
        ('ACTIVE','Active'),('CLOSED','Closed'),('ARCHIVED','Archived')]),
    'md53priority':         ('MD.53 - Priority', [
        ('HIGH','High'),('NORMAL','Normal'),('LOW','Low')]),
    'md54reportingFreq':    ('MD.54 - Reporting Frequency', [
        ('WEEKLY','Weekly'),('MONTHLY','Monthly'),
        ('QUARTERLY','Quarterly'),('ANNUAL','Annual')]),
    'md55geographicScope':  ('MD.55 - Geographic Scope', [
        ('NATIONAL','National (All Districts)'),
        ('DISTRICT','District-Level'),('ZONE','Agro-Ecological Zone')]),
    'md56targetingStrat':   ('MD.56 - Targeting Strategy', [
        ('UNIVERSAL','Universal'),('CATEGORICAL','Categorical'),
        ('GEOGRAPHIC','Geographic'),('COMBINED','Combined')]),
    'md57priorityModel':    ('MD.57 - Priority Model', [
        ('FCFS','First-Come First-Served'),
        ('PRIORITY_SCORE','Priority Score Based'),
        ('RANDOM','Random Selection'),
        ('COMMUNITY','Community-Based Selection')]),
    'md58benefitItemType':  ('MD.58 - Benefit Item Type', [
        ('INPUT','Input'),('EQUIPMENT','Equipment'),
        ('TRAINING','Training'),('CASH','Cash')]),
    'md59ruleType':         ('MD.59 - Rule Type', [
        ('INCLUSION','Inclusion'),('EXCLUSION','Exclusion'),
        ('PRIORITY','Priority'),('BONUS','Bonus')]),
    'md60evalStrategy':     ('MD.60 - Evaluation Strategy', [
        ('ALL_MUST_PASS','All Must Pass'),
        ('SCORE_BASED','Score-Based'),('WEIGHTED','Weighted')]),
    'md61approvalFlow':     ('MD.61 - Approval Workflow', [
        ('STANDARD','Standard'),('EXPRESS','Express'),
        ('EMERGENCY','Emergency')]),
    'md62monitoringApp':    ('MD.62 - Monitoring Approach', [
        ('CONTINUOUS','Continuous'),('PERIODIC','Periodic'),
        ('SAMPLING','Sampling'),('POST_HOC','Post-Hoc')]),
    'md63dataCollMethod':   ('MD.63 - Data Collection Method', [
        ('SYSTEM_AUTO','System Automatic'),
        ('FIELD_REPORTS','Field Reports'),
        ('SURVEY','Survey'),('MIXED','Mixed Methods')]),
    'md64documentStatus':   ('MD.64 - Document Status', [
        ('PENDING','Pending'),('VERIFIED','Verified'),
        ('REJECTED','Rejected'),('RESUBMIT','Awaiting Resubmission'),
        ('EXPIRED','Expired')]),
    'md65docRejReason':     ('MD.65 - Document Rejection Reason', [
        ('ILLEGIBLE','Illegible'),('INCOMPLETE','Incomplete'),
        ('WRONG_TYPE','Wrong Document Type'),('EXPIRED','Document Expired'),
        ('FORGED','Suspected Forgery'),('MISMATCH','Information Mismatch'),
        ('OTHER','Other')]),
    'md66eligibStatus':     ('MD.66 - Preliminary Eligibility Status', [
        ('LIKELY_ELIGIBLE','Likely Eligible'),
        ('POSSIBLY_ELIGIBLE','Possibly Eligible'),
        ('LIKELY_INELIGIBLE','Likely Ineligible'),('UNKNOWN','Unknown')]),
    'md67mobileMoneyProv':  ('MD.67 - Mobile Money Provider', [
        ('MPESA','M-Pesa'),('ECOCASH','EcoCash'),('OTHER','Other Provider')]),
    'md68appealType':       ('MD.68 - Appeal Type', [
        ('APPLICATION_REJECTION','Application Rejection'),
        ('ELIGIBILITY_DECISION','Eligibility Decision'),
        ('BENEFIT_AMOUNT','Benefit Amount'),
        ('DOCUMENT_REJECTION','Document Rejection'),
        ('DISTRIBUTION_ISSUE','Distribution Issue'),
        ('PAYMENT_ISSUE','Payment Issue'),
        ('ENROLLMENT_DENIED','Enrollment Denied'),
        ('EVAL_DISPUTE','Evaluation Dispute'),
        ('OTHER','Other')]),
    'md69entityType':       ('MD.69 - Related Entity Type', [
        ('APPLICATION','Application'),('PROGRAM','Programme'),
        ('ENROLLMENT','Enrollment'),('ENTITLEMENT','Entitlement'),
        ('DISTRIBUTION','Distribution'),('NOTIFICATION','Notification'),
        ('VOUCHER','Voucher')]),
    'md70originalDecision': ('MD.70 - Original Decision', [
        ('REJECTED','Rejected'),('INELIGIBLE','Found Ineligible'),
        ('PARTIAL','Partially Approved'),('DEDUCTED','Benefit Deducted'),
        ('SUSPENDED','Suspended'),('DISQUALIFIED','Disqualified')]),
    'md71appealGrounds':    ('MD.71 - Appeal Grounds', [
        ('INCORRECT_INFO','Incorrect Information Used'),
        ('NEW_EVIDENCE','New Evidence Available'),
        ('CIRCUMSTANCE_CHANGE','Change in Circumstances'),
        ('PROCEDURAL_ERROR','Procedural Error'),
        ('MISINTERPRETATION','Misinterpretation of Rules'),
        ('DISCRIMINATION','Discrimination'),('OTHER','Other')]),
    'md72appealStatus':     ('MD.72 - Appeal Status', [
        ('DRAFT','Draft'),('SUBMITTED','Submitted'),
        ('ACKNOWLEDGED','Acknowledged'),('UNDER_REVIEW','Under Review'),
        ('INFO_REQUESTED','Information Requested'),
        ('ESCALATED','Escalated'),('RESOLVED','Resolved'),
        ('CLOSED','Closed'),('WITHDRAWN','Withdrawn'),
        ('OVERDUE','Overdue')]),
    'md73resolutionDec':    ('MD.73 - Resolution Decision', [
        ('UPHELD','Appeal Upheld'),
        ('PARTIALLY_UPHELD','Partially Upheld'),
        ('DISMISSED_VALID','Dismissed - Valid Original Decision'),
        ('DISMISSED_PROCEDURAL','Dismissed - Procedural'),
        ('DISMISSED_LATE','Dismissed - Late Submission')]),
    'md74escalationLevel':  ('MD.74 - Escalation Level', [
        ('L1','Level 1 — Officer'),('L2','Level 2 — Supervisor'),
        ('L3','Level 3 — District'),('L4','Level 4 — Ministry')]),
    'md75particStatus':     ('MD.75 - Participation Status', [
        ('ENROLLED','Enrolled'),('ACTIVE','Active'),
        ('COMPLETED','Completed'),('WITHDRAWN','Withdrawn'),
        ('DISQUALIFIED','Disqualified'),('SUSPENDED','Suspended'),
        ('EXPIRED','Expired')]),
    'md76exitReason':       ('MD.76 - Exit Reason', [
        ('COMPLETED','Programme Completed'),
        ('INELIGIBLE','Lost Eligibility'),
        ('VOLUNTARY','Voluntary Withdrawal'),
        ('FRAUD','Fraud Detected'),
        ('NON_COMPLIANCE','Non-Compliance'),
        ('DECEASED','Deceased'),('RELOCATED','Relocated'),
        ('OTHER','Other')]),
    'md77satisfactionRtg':  ('MD.77 - Satisfaction Rating', [
        ('1','1 — Very Dissatisfied'),('2','2 — Dissatisfied'),
        ('3','3 — Neutral'),('4','4 — Satisfied'),
        ('5','5 — Very Satisfied')]),
    'md78notifChannel':     ('MD.78 - Notification Channel', [
        ('SMS','SMS'),('EMAIL','Email'),
        ('PUSH','Push Notification'),('USSD','USSD')]),
    'md79notifStatus':      ('MD.79 - Notification Status', [
        ('PENDING','Pending'),('QUEUED','Queued'),
        ('SENT','Sent'),('DELIVERED','Delivered'),
        ('FAILED','Failed'),('EXPIRED','Expired'),
        ('CANCELLED','Cancelled')]),
    'md80notifCategory':    ('MD.80 - Notification Category', [
        ('APPLICATION','Application'),('ELIGIBILITY','Eligibility'),
        ('ENROLLMENT','Enrollment'),('DISTRIBUTION','Distribution'),
        ('PAYMENT','Payment'),('REMINDER','Reminder'),
        ('ALERT','Alert'),('GENERAL','General')]),
    'md81sendTiming':       ('MD.81 - Send Timing', [
        ('IMMEDIATE','Send Immediately'),
        ('SCHEDULED','Send Scheduled'),
        ('DELAYED','Send Delayed')]),
    'md82yieldSource':      ('MD.82 - Yield Source', [
        ('BASELINE','Baseline (md49)'),
        ('FARMER','Farmer-Reported'),
        ('EXTENSION','Extension Officer'),
        ('HISTORICAL','Historical Average'),
        ('MANUAL','Manual Entry')]),
    'md83yieldStatus':      ('MD.83 - Yield/Production Status', [
        ('EXCELLENT','Excellent'),('GOOD','Good'),
        ('MODERATE','Moderate'),('POOR','Poor'),
        ('FAILED','Failed'),('TOTAL_LOSS','Total Loss')]),
    'md84seedType':         ('MD.84 - Seed Type', [
        ('IMPROVED','Improved'),('LOCAL','Local'),
        ('RECYCLED','Recycled'),('MIXED','Mixed'),
        ('UNKNOWN','Unknown')]),
    'md85seedSource':       ('MD.85 - Seed/Input Source', [
        ('SUBSIDY','Subsidy'),('PURCHASED','Purchased'),
        ('SAVED','Saved from Previous Season'),
        ('GIFT','Gift'),('CREDIT','Credit')]),
    'md86fertilizerType':   ('MD.86 - Fertilizer Type', [
        ('BASAL','Basal Application'),('TOP','Top Dressing'),
        ('FOLIAR','Foliar Application'),('ORGANIC','Organic')]),
    'md87irrigationType':   ('MD.87 - Irrigation Type', [
        ('RAINFED','Rainfed'),('FULL','Full Irrigation'),
        ('SUPPLEMENTAL','Supplemental Irrigation'),
        ('DRIP','Drip Irrigation')]),
    'md88harvestDispos':    ('MD.88 - Harvest Disposition', [
        ('CONSUMPTION','Household Consumption'),
        ('SALE','Sold'),('SEED','Saved as Seed'),
        ('GIFT','Gift'),('STORAGE','Storage'),('LOST','Lost')]),
    'md89shockImpactLvl':   ('MD.89 - Shock Impact Level', [
        ('NONE','None'),('MINOR','Minor'),
        ('MODERATE','Moderate'),('SEVERE','Severe'),
        ('TOTAL','Total')]),
    'md90harvestStatus':    ('MD.90 - Harvest Record Status', [
        ('DRAFT','Draft'),('SUBMITTED','Submitted'),
        ('VERIFIED','Verified'),('APPROVED','Approved'),
        ('REJECTED','Rejected')]),
}

# ============================================================================
# Extensions to existing MDs
# ============================================================================
EXTENSIONS = {
    'md11hazard': [
        ('THEFT','Theft'),('OTHER','Other Hazard'),('NONE','No Hazard')],
    'md18registrationChan': [
        ('portal','Online Portal'),('phone','Phone Submission'),
        ('sms','SMS'),('campaign','Outreach Campaign'),
        ('field_visit','Field Visit'),('mobile_app','Mobile App'),
        ('email','Email')],
    'md22applicationStatu': [
        # No new codes — form codes will be aligned to existing 12
    ],
    'md34notificationType': [
        ('MANUAL','Manual Trigger'),
        ('APP_UNDER_REVIEW','Application Under Review'),
        ('APP_DOCS_REQUIRED','Documents Required'),
        ('ENROLLED','Enrolled in Programme'),
        ('BENEFIT_ISSUED','Benefit Issued'),
        ('DISTRIBUTION_OPEN','Distribution Opened'),
        ('OVERDUE','Overdue Notification'),('CUSTOM','Custom Event')],
}

# ============================================================================
# Generate canonical MD form JSON (template — same shape as md03district etc.)
# ============================================================================
def md_form_json(form_id, name, table_name=None):
    if table_name is None: table_name = form_id
    # Mimic the standard MD form structure: code, name, description, displayOrder, isActive
    fields = [
        {"className":"org.joget.apps.form.lib.TextField",
         "properties":{"id":"code","label":"Code","maxlength":"50","required":"True",
                       "validator":{"className":"org.joget.apps.form.lib.DefaultValidator",
                                    "properties":{"mandatory":"true"}}}},
        {"className":"org.joget.apps.form.lib.TextField",
         "properties":{"id":"name","label":"Name","maxlength":"200","required":"True",
                       "validator":{"className":"org.joget.apps.form.lib.DefaultValidator",
                                    "properties":{"mandatory":"true"}}}},
        {"className":"org.joget.apps.form.lib.TextArea",
         "properties":{"id":"description","label":"Description","rows":"3"}},
        {"className":"org.joget.apps.form.lib.TextField",
         "properties":{"id":"displayOrder","label":"Display Order","size":"5",
                       "storeNumeric":"true"}},
        {"className":"org.joget.apps.form.lib.Radio",
         "properties":{"id":"isActive","label":"Active",
                       "options":[{"value":"Y","label":"Yes"},
                                  {"value":"N","label":"No"}],
                       "value":"Y"}},
    ]
    return {
        "elements": [{
            "elements": [{
                "elements": fields,
                "className": "org.joget.apps.form.model.Column",
                "properties": {"width":"100%"}
            }],
            "className": "org.joget.apps.form.model.Section",
            "properties": {"id":"section1","label":"Lookup Entry"}
        }],
        "className": "org.joget.apps.form.model.Form",
        "properties": {
            "id": form_id, "name": name, "tableName": table_name,
            "loadBinder":{"className":"org.joget.apps.form.lib.WorkflowFormBinder","properties":{}},
            "storeBinder":{"className":"org.joget.apps.form.lib.WorkflowFormBinder","properties":{}}
        }
    }

# Write each MD form definition JSON
for form_id, (name, _codes) in MDS.items():
    fdef = md_form_json(form_id, name)
    with open(os.path.join(OUT_FORMS, form_id + '.json'), 'w') as f:
        json.dump(fdef, f, indent=4)

print(f"  wrote {len(MDS)} MD form-definition JSONs to mdapp/forms/")

# ============================================================================
# SQL: app_form INSERTs for the 39 new MD form definitions
# ============================================================================
def sql_escape(s): return s.replace("'","''")

with open(os.path.join(ROOT, 'MD52-90_form_definitions.sql'), 'w') as f:
    f.write("-- Phase 1bis — INSERT 39 new MD form definitions into app_form\n")
    f.write("-- Idempotent via ON CONFLICT.\n\n")
    f.write("BEGIN;\n\n")
    for form_id, (name, _codes) in MDS.items():
        fdef_json = json.dumps(md_form_json(form_id, name))
        f.write(f"INSERT INTO app_form (appid, appversion, formid, name, tablename, json, datecreated, datemodified)\n")
        f.write(f"VALUES ('farmersPortal', 1, '{form_id}', '{sql_escape(name)}', '{form_id}',\n")
        f.write(f"        $${fdef_json}$$, NOW(), NOW())\n")
        f.write(f"ON CONFLICT (appid, appversion, formid) DO UPDATE\n")
        f.write(f"  SET name=EXCLUDED.name, tablename=EXCLUDED.tablename, json=EXCLUDED.json, datemodified=NOW();\n\n")
    f.write("COMMIT;\n")
print("  wrote MD52-90_form_definitions.sql")

# ============================================================================
# SQL: data tables + data seed for the 39 new MDs
# ============================================================================
with open(os.path.join(ROOT, 'MD52-90_data_seed.sql'), 'w') as f:
    f.write("-- Phase 1bis — Create data tables + seed codes for 39 new MDs\n")
    f.write("-- Run AFTER MD52-90_form_definitions.sql.\n\n")
    f.write("BEGIN;\n\n")
    for form_id, (name, codes) in MDS.items():
        tbl = 'app_fd_' + form_id.lower()
        f.write(f"-- {form_id}: {name}\n")
        f.write(f"CREATE TABLE IF NOT EXISTS \"{tbl}\" (\n")
        f.write(f"  id VARCHAR(255) NOT NULL PRIMARY KEY,\n")
        f.write(f"  datecreated TIMESTAMP, datemodified TIMESTAMP,\n")
        f.write(f"  createdby VARCHAR(255), createdbyname VARCHAR(255),\n")
        f.write(f"  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),\n")
        f.write(f"  c_code VARCHAR(255), c_name VARCHAR(255),\n")
        f.write(f"  c_description TEXT, c_displayorder VARCHAR(255),\n")
        f.write(f"  c_isactive VARCHAR(255)\n")
        f.write(f");\n")
        for i, (code, label) in enumerate(codes, start=1):
            row_id = f"{form_id}-{code.lower()}"
            f.write(f"INSERT INTO \"{tbl}\" (id, datecreated, datemodified, createdby, modifiedby, "
                    f"c_code, c_name, c_displayorder, c_isactive)\n")
            f.write(f"VALUES ('{row_id}', NOW(), NOW(), 'system', 'system',\n")
            f.write(f"        '{sql_escape(code)}', '{sql_escape(label)}', '{i}', 'Y')\n")
            f.write(f"ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();\n")
        f.write("\n")
    f.write("COMMIT;\n")
print("  wrote MD52-90_data_seed.sql")

# ============================================================================
# SQL: app_datalist INSERTs for list_md52..md90
# ============================================================================
with open(os.path.join(ROOT, 'MD52-90_datalists.sql'), 'w') as f:
    f.write("-- Phase 1bis — Create list_md52..md90 datalists (thin form-row binders)\n\n")
    f.write("BEGIN;\n\n")
    for form_id, (name, _codes) in MDS.items():
        list_id = 'list_' + form_id
        list_name = 'List: ' + name
        list_json = {
            "id": list_id, "name": list_name,
            "binder": {
                "className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder",
                "properties": {"formDefId": form_id}
            },
            "columns": [
                {"name":"code","id":"column_0","label":"Code"},
                {"name":"name","id":"column_1","label":"Name"},
                {"name":"description","id":"column_2","label":"Description"},
                {"name":"displayOrder","id":"column_3","label":"Order"},
                {"name":"isActive","id":"column_4","label":"Active"}
            ],
            "filters": [],"actions": [],"rowActions": [],
            "useSession":"false","showPageSizeSelector":"true",
            "pageSize":0,"pageSizeSelectorOptions":"10,20,30,40,50,100",
            "buttonPosition":"bothLeft","checkboxPosition":"left",
            "orderBy":"displayOrder","order":""
        }
        json_str = json.dumps(list_json)
        f.write(f"INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)\n")
        f.write(f"VALUES ('farmersPortal', 1, '{list_id}', '{sql_escape(list_name)}',\n")
        f.write(f"        $${json_str}$$, NOW(), NOW())\n")
        f.write(f"ON CONFLICT (appid, appversion, id) DO UPDATE\n")
        f.write(f"  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();\n\n")
    f.write("COMMIT;\n")
print("  wrote MD52-90_datalists.sql")

# ============================================================================
# SQL: extensions to md11, md18, md34
# ============================================================================
with open(os.path.join(ROOT, 'MD_extensions.sql'), 'w') as f:
    f.write("-- Phase 1bis — Extensions to existing MDs (new codes only)\n\n")
    f.write("BEGIN;\n\n")
    EXT_TABLES = {
        'md11hazard': 'app_fd_md11hazard',
        'md18registrationChan': 'app_fd_md18registrationchan',
        'md34notificationType': 'app_fd_md34notificationtype',
    }
    for form_id, codes in EXTENSIONS.items():
        if not codes: continue
        tbl = EXT_TABLES[form_id]
        f.write(f"-- {form_id} extensions\n")
        for code, label in codes:
            row_id = f"{form_id}-ext-{code.lower()}"
            f.write(f"INSERT INTO \"{tbl}\" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_isactive)\n")
            f.write(f"VALUES ('{row_id}', NOW(), NOW(), 'system', 'system', '{sql_escape(code)}', '{sql_escape(label)}', 'Y')\n")
            f.write(f"ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();\n")
        f.write("\n")
    f.write("COMMIT;\n")
print("  wrote MD_extensions.sql")

# ============================================================================
# Form patches — replace static options with optionsBinder per the mapping
# ============================================================================
# Mapping: (form_filename, field_id) -> (md_form_id, idColumn, labelColumn)
# idColumn=='code' for all our MDs (the c_code column)
PATCH_MAP = {
    ('f03.03-spProgramGeography.json','geographicScope'):  ('md55geographicScope','code','name'),
    ('f03.04-spProgramBeneficiary.json','targetingStrategy'):('md56targetingStrat','code','name'),
    ('f03.04-spProgramBeneficiary.json','priorityModel'):  ('md57priorityModel','code','name'),
    ('f03.05-1-spBenefitItemRow.json','itemType'):         ('md58benefitItemType','code','name'),
    ('f03.06-1-spEligCriterionRow.json','ruleType'):       ('md59ruleType','code','name'),
    ('f03.06-spProgramEligibility.json','evaluationStrategy'):('md60evalStrategy','code','name'),
    ('f03.07-spProgramApproval.json','approvalWorkflow'):  ('md61approvalFlow','code','name'),
    ('f03.08-1-spKpiRow.json','frequency'):                ('md54reportingFreq','code','name'),
    ('f03.08-spProgramMonitoring.json','monitoringApproach'):('md62monitoringApp','code','name'),
    ('f03.08-spProgramMonitoring.json','reportingFrequency'):('md54reportingFreq','code','name'),
    ('f03.08-spProgramMonitoring.json','dataCollectionMethod'):('md63dataCollMethod','code','name'),
    ('f03.09-1-spApplicationDoc.json','status'):           ('md64documentStatus','code','name'),
    ('f03.09-1-spApplicationDoc.json','rejectionReason'):  ('md65docRejReason','code','name'),
    ('f03.09-spApplication.json','eligibilityStatus'):     ('md66eligibStatus','code','name'),
    ('f03.09-spApplication.json','mobileMoneyProvider'):   ('md67mobileMoneyProv','code','name'),
    ('f03.09-spApplication.json','status'):                ('md22applicationStatu','code','name'),
    # acceptDeclaration is a different fix — convert to Radio Y/N
    ('f03.10-spAppeal.json','appealType'):                 ('md68appealType','code','name'),
    ('f03.10-spAppeal.json','relatedEntityType'):          ('md69entityType','code','name'),
    ('f03.10-spAppeal.json','originalDecision'):           ('md70originalDecision','code','name'),
    ('f03.10-spAppeal.json','appealGrounds'):              ('md71appealGrounds','code','name'),
    ('f03.10-spAppeal.json','preferredContact'):           ('md18registrationChan','code','name'),
    ('f03.10-spAppeal.json','preferredLanguage'):          ('md02language','code','name'),
    ('f03.10-spAppeal.json','submissionChannel'):          ('md18registrationChan','code','name'),
    ('f03.10-spAppeal.json','status'):                     ('md72appealStatus','code','name'),
    ('f03.10-spAppeal.json','priority'):                   ('md53priority','code','name'),
    ('f03.10-spAppeal.json','resolutionDecision'):         ('md73resolutionDec','code','name'),
    ('f03.10-spAppeal.json','escalationLevel'):            ('md74escalationLevel','code','name'),
    ('f03.11-spProgParticip.json','enrollmentChannel'):    ('md18registrationChan','code','name'),
    ('f03.11-spProgParticip.json','status'):               ('md75particStatus','code','name'),
    ('f03.11-spProgParticip.json','exitReason'):           ('md76exitReason','code','name'),
    ('f03.11-spProgParticip.json','satisfactionRating'):   ('md77satisfactionRtg','code','name'),
    ('f03.12-spNotification.json','channel'):              ('md78notifChannel','code','name'),
    ('f03.12-spNotification.json','language'):             ('md02language','code','name'),
    ('f03.12-spNotification.json','relatedEntityType'):    ('md69entityType','code','name'),
    ('f03.12-spNotification.json','status'):               ('md79notifStatus','code','name'),
    ('f03.12-spNotification.json','priority'):             ('md53priority','code','name'),
    ('f03.13-spNotifTemplate.json','category'):            ('md80notifCategory','code','name'),
    ('f03.13-spNotifTemplate.json','triggerEvent'):        ('md34notificationType','code','name'),
    ('f03.13-spNotifTemplate.json','sendImmediately'):     ('md81sendTiming','code','name'),
    ('f03.13-spNotifTemplate.json','priority'):            ('md53priority','code','name'),
    ('f03.harvest-01-spCropHarvItem.json','expectedYieldSource'):('md82yieldSource','code','name'),
    ('f03.harvest-01-spCropHarvItem.json','yieldStatus'):  ('md83yieldStatus','code','name'),
    ('f03.harvest-01-spCropHarvItem.json','seedType'):     ('md84seedType','code','name'),
    ('f03.harvest-01-spCropHarvItem.json','seedSource'):   ('md85seedSource','code','name'),
    ('f03.harvest-02-spCropHarvestItem.json','areaUnit'):  ('md15areaUnits','code','name'),
    ('f03.harvest-02-spCropHarvestItem.json','expectedYieldSource'):('md82yieldSource','code','name'),
    ('f03.harvest-02-spCropHarvestItem.json','productionStatus'):('md83yieldStatus','code','name'),
    ('f03.harvest-02-spCropHarvestItem.json','seedType'):  ('md84seedType','code','name'),
    ('f03.harvest-02-spCropHarvestItem.json','seedSource'):('md85seedSource','code','name'),
    ('f03.harvest-02-spCropHarvestItem.json','fertilizerType'):('md86fertilizerType','code','name'),
    ('f03.harvest-02-spCropHarvestItem.json','fertilizerSource'):('md85seedSource','code','name'),
    ('f03.harvest-02-spCropHarvestItem.json','lossFactors'):('md11hazard','code','name'),
    ('f03.harvest-02-spCropHarvestItem.json','irrigationType'):('md87irrigationType','code','name'),
    ('f03.harvest-02-spCropHarvestItem.json','harvestDisposition'):('md88harvestDispos','code','name'),
    ('f03.harvest-03-spSeasonHarvest.json','collectionMethod'):('md18registrationChan','code','name'),
    ('f03.harvest-03-spSeasonHarvest.json','productionStatus'):('md83yieldStatus','code','name'),
    ('f03.harvest-03-spSeasonHarvest.json','shockImpactLevel'):('md89shockImpactLvl','code','name'),
    ('f03.harvest-03-spSeasonHarvest.json','fertilizerSource'):('md85seedSource','code','name'),
    ('f03.harvest-03-spSeasonHarvest.json','status'):       ('md90harvestStatus','code','name'),
}

def patch_form(form, patches_for_this_form):
    """Walk the form, for each field whose id is in patches_for_this_form,
    convert static options → optionsBinder."""
    patched = []
    def walk(node):
        if isinstance(node, dict):
            cn = node.get('className','')
            props = node.get('properties',{}) or {}
            fid = props.get('id','')
            if cn in ('org.joget.apps.form.lib.Radio',
                      'org.joget.apps.form.lib.SelectBox',
                      'org.joget.apps.form.lib.CheckBox') and fid in patches_for_this_form:
                md_form_id, id_col, label_col = patches_for_this_form[fid]
                # Replace options with optionsBinder
                props['options'] = []
                props['optionsBinder'] = {
                    "className": "org.joget.apps.form.lib.FormOptionsBinder",
                    "properties": {
                        "formDefId": md_form_id,
                        "idColumn": id_col,
                        "labelColumn": label_col,
                        "addEmptyOption": "true",
                        "groupingColumn": "",
                        "useAjax": "",
                        "extraCondition": "e.customProperties.isActive = 'Y'",
                        "cacheInterval": "",
                        "emptyLabel": ""
                    }
                }
                patched.append(fid)
            for v in node.values():
                if isinstance(v,(dict,list)): walk(v)
        elif isinstance(node, list):
            for v in node: walk(v)
    walk(form)
    return patched

# Group PATCH_MAP by file
by_file = {}
for (fname, fid), val in PATCH_MAP.items():
    by_file.setdefault(fname, {})[fid] = val

patched_forms = []
for fname, patches in by_file.items():
    src_path = os.path.join(COMPONENT03, fname)
    if not os.path.exists(src_path):
        print(f"  SKIP {fname} (not found)")
        continue
    form = json.load(open(src_path))
    applied = patch_form(form, patches)
    if applied:
        out_path = os.path.join(OUT_PATCHED, fname)
        with open(out_path, 'w') as f:
            json.dump(form, f, indent=4)
        patched_forms.append((fname, applied))

print(f"\n  wrote {len(patched_forms)} patched form JSONs to form_patches/")
for fname, applied in patched_forms:
    print(f"    {fname}: patched fields {applied}")

print("\nDone.")
