-- Phase 1bis — Create data tables + seed codes for 39 new MDs
-- Run AFTER MD52-90_form_definitions.sql.

BEGIN;

-- md52programStatus: MD.52 - Program Status
CREATE TABLE IF NOT EXISTS "app_fd_md52programstatus" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md52programstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md52programStatus-draft', NOW(), NOW(), 'system', 'system',
        'DRAFT', 'Draft', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md52programstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md52programStatus-pending_approval', NOW(), NOW(), 'system', 'system',
        'PENDING_APPROVAL', 'Pending Approval', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md52programstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md52programStatus-approved', NOW(), NOW(), 'system', 'system',
        'APPROVED', 'Approved', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md52programstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md52programStatus-rejected', NOW(), NOW(), 'system', 'system',
        'REJECTED', 'Rejected', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md52programstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md52programStatus-active', NOW(), NOW(), 'system', 'system',
        'ACTIVE', 'Active', '5', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md52programstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md52programStatus-closed', NOW(), NOW(), 'system', 'system',
        'CLOSED', 'Closed', '6', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md52programstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md52programStatus-archived', NOW(), NOW(), 'system', 'system',
        'ARCHIVED', 'Archived', '7', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md53priority: MD.53 - Priority
CREATE TABLE IF NOT EXISTS "app_fd_md53priority" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md53priority" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md53priority-high', NOW(), NOW(), 'system', 'system',
        'HIGH', 'High', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md53priority" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md53priority-normal', NOW(), NOW(), 'system', 'system',
        'NORMAL', 'Normal', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md53priority" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md53priority-low', NOW(), NOW(), 'system', 'system',
        'LOW', 'Low', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md54reportingFreq: MD.54 - Reporting Frequency
CREATE TABLE IF NOT EXISTS "app_fd_md54reportingfreq" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md54reportingfreq" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md54reportingFreq-weekly', NOW(), NOW(), 'system', 'system',
        'WEEKLY', 'Weekly', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md54reportingfreq" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md54reportingFreq-monthly', NOW(), NOW(), 'system', 'system',
        'MONTHLY', 'Monthly', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md54reportingfreq" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md54reportingFreq-quarterly', NOW(), NOW(), 'system', 'system',
        'QUARTERLY', 'Quarterly', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md54reportingfreq" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md54reportingFreq-annual', NOW(), NOW(), 'system', 'system',
        'ANNUAL', 'Annual', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md55geographicScope: MD.55 - Geographic Scope
CREATE TABLE IF NOT EXISTS "app_fd_md55geographicscope" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md55geographicscope" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md55geographicScope-national', NOW(), NOW(), 'system', 'system',
        'NATIONAL', 'National (All Districts)', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md55geographicscope" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md55geographicScope-district', NOW(), NOW(), 'system', 'system',
        'DISTRICT', 'District-Level', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md55geographicscope" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md55geographicScope-zone', NOW(), NOW(), 'system', 'system',
        'ZONE', 'Agro-Ecological Zone', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md56targetingStrat: MD.56 - Targeting Strategy
CREATE TABLE IF NOT EXISTS "app_fd_md56targetingstrat" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md56targetingstrat" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md56targetingStrat-universal', NOW(), NOW(), 'system', 'system',
        'UNIVERSAL', 'Universal', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md56targetingstrat" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md56targetingStrat-categorical', NOW(), NOW(), 'system', 'system',
        'CATEGORICAL', 'Categorical', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md56targetingstrat" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md56targetingStrat-geographic', NOW(), NOW(), 'system', 'system',
        'GEOGRAPHIC', 'Geographic', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md56targetingstrat" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md56targetingStrat-combined', NOW(), NOW(), 'system', 'system',
        'COMBINED', 'Combined', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md57priorityModel: MD.57 - Priority Model
CREATE TABLE IF NOT EXISTS "app_fd_md57prioritymodel" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md57prioritymodel" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md57priorityModel-fcfs', NOW(), NOW(), 'system', 'system',
        'FCFS', 'First-Come First-Served', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md57prioritymodel" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md57priorityModel-priority_score', NOW(), NOW(), 'system', 'system',
        'PRIORITY_SCORE', 'Priority Score Based', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md57prioritymodel" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md57priorityModel-random', NOW(), NOW(), 'system', 'system',
        'RANDOM', 'Random Selection', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md57prioritymodel" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md57priorityModel-community', NOW(), NOW(), 'system', 'system',
        'COMMUNITY', 'Community-Based Selection', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md58benefitItemType: MD.58 - Benefit Item Type
CREATE TABLE IF NOT EXISTS "app_fd_md58benefititemtype" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md58benefititemtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md58benefitItemType-input', NOW(), NOW(), 'system', 'system',
        'INPUT', 'Input', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md58benefititemtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md58benefitItemType-equipment', NOW(), NOW(), 'system', 'system',
        'EQUIPMENT', 'Equipment', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md58benefititemtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md58benefitItemType-training', NOW(), NOW(), 'system', 'system',
        'TRAINING', 'Training', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md58benefititemtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md58benefitItemType-cash', NOW(), NOW(), 'system', 'system',
        'CASH', 'Cash', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md59ruleType: MD.59 - Rule Type
CREATE TABLE IF NOT EXISTS "app_fd_md59ruletype" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md59ruletype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md59ruleType-inclusion', NOW(), NOW(), 'system', 'system',
        'INCLUSION', 'Inclusion', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md59ruletype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md59ruleType-exclusion', NOW(), NOW(), 'system', 'system',
        'EXCLUSION', 'Exclusion', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md59ruletype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md59ruleType-priority', NOW(), NOW(), 'system', 'system',
        'PRIORITY', 'Priority', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md59ruletype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md59ruleType-bonus', NOW(), NOW(), 'system', 'system',
        'BONUS', 'Bonus', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md60evalStrategy: MD.60 - Evaluation Strategy
CREATE TABLE IF NOT EXISTS "app_fd_md60evalstrategy" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md60evalstrategy" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md60evalStrategy-all_must_pass', NOW(), NOW(), 'system', 'system',
        'ALL_MUST_PASS', 'All Must Pass', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md60evalstrategy" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md60evalStrategy-score_based', NOW(), NOW(), 'system', 'system',
        'SCORE_BASED', 'Score-Based', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md60evalstrategy" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md60evalStrategy-weighted', NOW(), NOW(), 'system', 'system',
        'WEIGHTED', 'Weighted', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md61approvalFlow: MD.61 - Approval Workflow
CREATE TABLE IF NOT EXISTS "app_fd_md61approvalflow" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md61approvalflow" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md61approvalFlow-standard', NOW(), NOW(), 'system', 'system',
        'STANDARD', 'Standard', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md61approvalflow" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md61approvalFlow-express', NOW(), NOW(), 'system', 'system',
        'EXPRESS', 'Express', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md61approvalflow" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md61approvalFlow-emergency', NOW(), NOW(), 'system', 'system',
        'EMERGENCY', 'Emergency', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md62monitoringApp: MD.62 - Monitoring Approach
CREATE TABLE IF NOT EXISTS "app_fd_md62monitoringapp" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md62monitoringapp" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md62monitoringApp-continuous', NOW(), NOW(), 'system', 'system',
        'CONTINUOUS', 'Continuous', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md62monitoringapp" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md62monitoringApp-periodic', NOW(), NOW(), 'system', 'system',
        'PERIODIC', 'Periodic', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md62monitoringapp" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md62monitoringApp-sampling', NOW(), NOW(), 'system', 'system',
        'SAMPLING', 'Sampling', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md62monitoringapp" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md62monitoringApp-post_hoc', NOW(), NOW(), 'system', 'system',
        'POST_HOC', 'Post-Hoc', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md63dataCollMethod: MD.63 - Data Collection Method
CREATE TABLE IF NOT EXISTS "app_fd_md63datacollmethod" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md63datacollmethod" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md63dataCollMethod-system_auto', NOW(), NOW(), 'system', 'system',
        'SYSTEM_AUTO', 'System Automatic', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md63datacollmethod" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md63dataCollMethod-field_reports', NOW(), NOW(), 'system', 'system',
        'FIELD_REPORTS', 'Field Reports', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md63datacollmethod" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md63dataCollMethod-survey', NOW(), NOW(), 'system', 'system',
        'SURVEY', 'Survey', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md63datacollmethod" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md63dataCollMethod-mixed', NOW(), NOW(), 'system', 'system',
        'MIXED', 'Mixed Methods', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md64documentStatus: MD.64 - Document Status
CREATE TABLE IF NOT EXISTS "app_fd_md64documentstatus" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md64documentstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md64documentStatus-pending', NOW(), NOW(), 'system', 'system',
        'PENDING', 'Pending', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md64documentstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md64documentStatus-verified', NOW(), NOW(), 'system', 'system',
        'VERIFIED', 'Verified', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md64documentstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md64documentStatus-rejected', NOW(), NOW(), 'system', 'system',
        'REJECTED', 'Rejected', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md64documentstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md64documentStatus-resubmit', NOW(), NOW(), 'system', 'system',
        'RESUBMIT', 'Awaiting Resubmission', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md64documentstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md64documentStatus-expired', NOW(), NOW(), 'system', 'system',
        'EXPIRED', 'Expired', '5', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md65docRejReason: MD.65 - Document Rejection Reason
CREATE TABLE IF NOT EXISTS "app_fd_md65docrejreason" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md65docrejreason" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md65docRejReason-illegible', NOW(), NOW(), 'system', 'system',
        'ILLEGIBLE', 'Illegible', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md65docrejreason" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md65docRejReason-incomplete', NOW(), NOW(), 'system', 'system',
        'INCOMPLETE', 'Incomplete', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md65docrejreason" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md65docRejReason-wrong_type', NOW(), NOW(), 'system', 'system',
        'WRONG_TYPE', 'Wrong Document Type', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md65docrejreason" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md65docRejReason-expired', NOW(), NOW(), 'system', 'system',
        'EXPIRED', 'Document Expired', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md65docrejreason" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md65docRejReason-forged', NOW(), NOW(), 'system', 'system',
        'FORGED', 'Suspected Forgery', '5', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md65docrejreason" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md65docRejReason-mismatch', NOW(), NOW(), 'system', 'system',
        'MISMATCH', 'Information Mismatch', '6', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md65docrejreason" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md65docRejReason-other', NOW(), NOW(), 'system', 'system',
        'OTHER', 'Other', '7', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md66eligibStatus: MD.66 - Preliminary Eligibility Status
CREATE TABLE IF NOT EXISTS "app_fd_md66eligibstatus" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md66eligibstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md66eligibStatus-likely_eligible', NOW(), NOW(), 'system', 'system',
        'LIKELY_ELIGIBLE', 'Likely Eligible', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md66eligibstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md66eligibStatus-possibly_eligible', NOW(), NOW(), 'system', 'system',
        'POSSIBLY_ELIGIBLE', 'Possibly Eligible', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md66eligibstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md66eligibStatus-likely_ineligible', NOW(), NOW(), 'system', 'system',
        'LIKELY_INELIGIBLE', 'Likely Ineligible', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md66eligibstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md66eligibStatus-unknown', NOW(), NOW(), 'system', 'system',
        'UNKNOWN', 'Unknown', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md67mobileMoneyProv: MD.67 - Mobile Money Provider
CREATE TABLE IF NOT EXISTS "app_fd_md67mobilemoneyprov" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md67mobilemoneyprov" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md67mobileMoneyProv-mpesa', NOW(), NOW(), 'system', 'system',
        'MPESA', 'M-Pesa', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md67mobilemoneyprov" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md67mobileMoneyProv-ecocash', NOW(), NOW(), 'system', 'system',
        'ECOCASH', 'EcoCash', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md67mobilemoneyprov" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md67mobileMoneyProv-other', NOW(), NOW(), 'system', 'system',
        'OTHER', 'Other Provider', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md68appealType: MD.68 - Appeal Type
CREATE TABLE IF NOT EXISTS "app_fd_md68appealtype" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md68appealtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md68appealType-application_rejection', NOW(), NOW(), 'system', 'system',
        'APPLICATION_REJECTION', 'Application Rejection', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md68appealtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md68appealType-eligibility_decision', NOW(), NOW(), 'system', 'system',
        'ELIGIBILITY_DECISION', 'Eligibility Decision', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md68appealtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md68appealType-benefit_amount', NOW(), NOW(), 'system', 'system',
        'BENEFIT_AMOUNT', 'Benefit Amount', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md68appealtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md68appealType-document_rejection', NOW(), NOW(), 'system', 'system',
        'DOCUMENT_REJECTION', 'Document Rejection', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md68appealtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md68appealType-distribution_issue', NOW(), NOW(), 'system', 'system',
        'DISTRIBUTION_ISSUE', 'Distribution Issue', '5', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md68appealtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md68appealType-payment_issue', NOW(), NOW(), 'system', 'system',
        'PAYMENT_ISSUE', 'Payment Issue', '6', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md68appealtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md68appealType-enrollment_denied', NOW(), NOW(), 'system', 'system',
        'ENROLLMENT_DENIED', 'Enrollment Denied', '7', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md68appealtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md68appealType-eval_dispute', NOW(), NOW(), 'system', 'system',
        'EVAL_DISPUTE', 'Evaluation Dispute', '8', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md68appealtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md68appealType-other', NOW(), NOW(), 'system', 'system',
        'OTHER', 'Other', '9', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md69entityType: MD.69 - Related Entity Type
CREATE TABLE IF NOT EXISTS "app_fd_md69entitytype" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md69entitytype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md69entityType-application', NOW(), NOW(), 'system', 'system',
        'APPLICATION', 'Application', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md69entitytype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md69entityType-program', NOW(), NOW(), 'system', 'system',
        'PROGRAM', 'Programme', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md69entitytype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md69entityType-enrollment', NOW(), NOW(), 'system', 'system',
        'ENROLLMENT', 'Enrollment', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md69entitytype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md69entityType-entitlement', NOW(), NOW(), 'system', 'system',
        'ENTITLEMENT', 'Entitlement', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md69entitytype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md69entityType-distribution', NOW(), NOW(), 'system', 'system',
        'DISTRIBUTION', 'Distribution', '5', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md69entitytype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md69entityType-notification', NOW(), NOW(), 'system', 'system',
        'NOTIFICATION', 'Notification', '6', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md69entitytype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md69entityType-voucher', NOW(), NOW(), 'system', 'system',
        'VOUCHER', 'Voucher', '7', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md70originalDecision: MD.70 - Original Decision
CREATE TABLE IF NOT EXISTS "app_fd_md70originaldecision" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md70originaldecision" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md70originalDecision-rejected', NOW(), NOW(), 'system', 'system',
        'REJECTED', 'Rejected', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md70originaldecision" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md70originalDecision-ineligible', NOW(), NOW(), 'system', 'system',
        'INELIGIBLE', 'Found Ineligible', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md70originaldecision" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md70originalDecision-partial', NOW(), NOW(), 'system', 'system',
        'PARTIAL', 'Partially Approved', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md70originaldecision" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md70originalDecision-deducted', NOW(), NOW(), 'system', 'system',
        'DEDUCTED', 'Benefit Deducted', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md70originaldecision" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md70originalDecision-suspended', NOW(), NOW(), 'system', 'system',
        'SUSPENDED', 'Suspended', '5', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md70originaldecision" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md70originalDecision-disqualified', NOW(), NOW(), 'system', 'system',
        'DISQUALIFIED', 'Disqualified', '6', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md71appealGrounds: MD.71 - Appeal Grounds
CREATE TABLE IF NOT EXISTS "app_fd_md71appealgrounds" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md71appealgrounds" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md71appealGrounds-incorrect_info', NOW(), NOW(), 'system', 'system',
        'INCORRECT_INFO', 'Incorrect Information Used', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md71appealgrounds" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md71appealGrounds-new_evidence', NOW(), NOW(), 'system', 'system',
        'NEW_EVIDENCE', 'New Evidence Available', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md71appealgrounds" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md71appealGrounds-circumstance_change', NOW(), NOW(), 'system', 'system',
        'CIRCUMSTANCE_CHANGE', 'Change in Circumstances', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md71appealgrounds" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md71appealGrounds-procedural_error', NOW(), NOW(), 'system', 'system',
        'PROCEDURAL_ERROR', 'Procedural Error', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md71appealgrounds" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md71appealGrounds-misinterpretation', NOW(), NOW(), 'system', 'system',
        'MISINTERPRETATION', 'Misinterpretation of Rules', '5', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md71appealgrounds" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md71appealGrounds-discrimination', NOW(), NOW(), 'system', 'system',
        'DISCRIMINATION', 'Discrimination', '6', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md71appealgrounds" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md71appealGrounds-other', NOW(), NOW(), 'system', 'system',
        'OTHER', 'Other', '7', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md72appealStatus: MD.72 - Appeal Status
CREATE TABLE IF NOT EXISTS "app_fd_md72appealstatus" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md72appealstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md72appealStatus-draft', NOW(), NOW(), 'system', 'system',
        'DRAFT', 'Draft', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md72appealstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md72appealStatus-submitted', NOW(), NOW(), 'system', 'system',
        'SUBMITTED', 'Submitted', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md72appealstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md72appealStatus-acknowledged', NOW(), NOW(), 'system', 'system',
        'ACKNOWLEDGED', 'Acknowledged', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md72appealstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md72appealStatus-under_review', NOW(), NOW(), 'system', 'system',
        'UNDER_REVIEW', 'Under Review', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md72appealstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md72appealStatus-info_requested', NOW(), NOW(), 'system', 'system',
        'INFO_REQUESTED', 'Information Requested', '5', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md72appealstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md72appealStatus-escalated', NOW(), NOW(), 'system', 'system',
        'ESCALATED', 'Escalated', '6', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md72appealstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md72appealStatus-resolved', NOW(), NOW(), 'system', 'system',
        'RESOLVED', 'Resolved', '7', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md72appealstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md72appealStatus-closed', NOW(), NOW(), 'system', 'system',
        'CLOSED', 'Closed', '8', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md72appealstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md72appealStatus-withdrawn', NOW(), NOW(), 'system', 'system',
        'WITHDRAWN', 'Withdrawn', '9', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md72appealstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md72appealStatus-overdue', NOW(), NOW(), 'system', 'system',
        'OVERDUE', 'Overdue', '10', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md73resolutionDec: MD.73 - Resolution Decision
CREATE TABLE IF NOT EXISTS "app_fd_md73resolutiondec" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md73resolutiondec" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md73resolutionDec-upheld', NOW(), NOW(), 'system', 'system',
        'UPHELD', 'Appeal Upheld', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md73resolutiondec" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md73resolutionDec-partially_upheld', NOW(), NOW(), 'system', 'system',
        'PARTIALLY_UPHELD', 'Partially Upheld', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md73resolutiondec" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md73resolutionDec-dismissed_valid', NOW(), NOW(), 'system', 'system',
        'DISMISSED_VALID', 'Dismissed - Valid Original Decision', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md73resolutiondec" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md73resolutionDec-dismissed_procedural', NOW(), NOW(), 'system', 'system',
        'DISMISSED_PROCEDURAL', 'Dismissed - Procedural', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md73resolutiondec" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md73resolutionDec-dismissed_late', NOW(), NOW(), 'system', 'system',
        'DISMISSED_LATE', 'Dismissed - Late Submission', '5', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md74escalationLevel: MD.74 - Escalation Level
CREATE TABLE IF NOT EXISTS "app_fd_md74escalationlevel" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md74escalationlevel" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md74escalationLevel-l1', NOW(), NOW(), 'system', 'system',
        'L1', 'Level 1 — Officer', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md74escalationlevel" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md74escalationLevel-l2', NOW(), NOW(), 'system', 'system',
        'L2', 'Level 2 — Supervisor', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md74escalationlevel" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md74escalationLevel-l3', NOW(), NOW(), 'system', 'system',
        'L3', 'Level 3 — District', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md74escalationlevel" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md74escalationLevel-l4', NOW(), NOW(), 'system', 'system',
        'L4', 'Level 4 — Ministry', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md75particStatus: MD.75 - Participation Status
CREATE TABLE IF NOT EXISTS "app_fd_md75particstatus" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md75particstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md75particStatus-enrolled', NOW(), NOW(), 'system', 'system',
        'ENROLLED', 'Enrolled', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md75particstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md75particStatus-active', NOW(), NOW(), 'system', 'system',
        'ACTIVE', 'Active', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md75particstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md75particStatus-completed', NOW(), NOW(), 'system', 'system',
        'COMPLETED', 'Completed', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md75particstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md75particStatus-withdrawn', NOW(), NOW(), 'system', 'system',
        'WITHDRAWN', 'Withdrawn', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md75particstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md75particStatus-disqualified', NOW(), NOW(), 'system', 'system',
        'DISQUALIFIED', 'Disqualified', '5', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md75particstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md75particStatus-suspended', NOW(), NOW(), 'system', 'system',
        'SUSPENDED', 'Suspended', '6', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md75particstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md75particStatus-expired', NOW(), NOW(), 'system', 'system',
        'EXPIRED', 'Expired', '7', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md76exitReason: MD.76 - Exit Reason
CREATE TABLE IF NOT EXISTS "app_fd_md76exitreason" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md76exitreason" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md76exitReason-completed', NOW(), NOW(), 'system', 'system',
        'COMPLETED', 'Programme Completed', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md76exitreason" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md76exitReason-ineligible', NOW(), NOW(), 'system', 'system',
        'INELIGIBLE', 'Lost Eligibility', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md76exitreason" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md76exitReason-voluntary', NOW(), NOW(), 'system', 'system',
        'VOLUNTARY', 'Voluntary Withdrawal', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md76exitreason" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md76exitReason-fraud', NOW(), NOW(), 'system', 'system',
        'FRAUD', 'Fraud Detected', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md76exitreason" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md76exitReason-non_compliance', NOW(), NOW(), 'system', 'system',
        'NON_COMPLIANCE', 'Non-Compliance', '5', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md76exitreason" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md76exitReason-deceased', NOW(), NOW(), 'system', 'system',
        'DECEASED', 'Deceased', '6', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md76exitreason" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md76exitReason-relocated', NOW(), NOW(), 'system', 'system',
        'RELOCATED', 'Relocated', '7', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md76exitreason" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md76exitReason-other', NOW(), NOW(), 'system', 'system',
        'OTHER', 'Other', '8', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md77satisfactionRtg: MD.77 - Satisfaction Rating
CREATE TABLE IF NOT EXISTS "app_fd_md77satisfactionrtg" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md77satisfactionrtg" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md77satisfactionRtg-1', NOW(), NOW(), 'system', 'system',
        '1', '1 — Very Dissatisfied', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md77satisfactionrtg" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md77satisfactionRtg-2', NOW(), NOW(), 'system', 'system',
        '2', '2 — Dissatisfied', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md77satisfactionrtg" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md77satisfactionRtg-3', NOW(), NOW(), 'system', 'system',
        '3', '3 — Neutral', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md77satisfactionrtg" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md77satisfactionRtg-4', NOW(), NOW(), 'system', 'system',
        '4', '4 — Satisfied', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md77satisfactionrtg" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md77satisfactionRtg-5', NOW(), NOW(), 'system', 'system',
        '5', '5 — Very Satisfied', '5', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md78notifChannel: MD.78 - Notification Channel
CREATE TABLE IF NOT EXISTS "app_fd_md78notifchannel" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md78notifchannel" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md78notifChannel-sms', NOW(), NOW(), 'system', 'system',
        'SMS', 'SMS', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md78notifchannel" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md78notifChannel-email', NOW(), NOW(), 'system', 'system',
        'EMAIL', 'Email', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md78notifchannel" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md78notifChannel-push', NOW(), NOW(), 'system', 'system',
        'PUSH', 'Push Notification', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md78notifchannel" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md78notifChannel-ussd', NOW(), NOW(), 'system', 'system',
        'USSD', 'USSD', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md79notifStatus: MD.79 - Notification Status
CREATE TABLE IF NOT EXISTS "app_fd_md79notifstatus" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md79notifstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md79notifStatus-pending', NOW(), NOW(), 'system', 'system',
        'PENDING', 'Pending', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md79notifstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md79notifStatus-queued', NOW(), NOW(), 'system', 'system',
        'QUEUED', 'Queued', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md79notifstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md79notifStatus-sent', NOW(), NOW(), 'system', 'system',
        'SENT', 'Sent', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md79notifstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md79notifStatus-delivered', NOW(), NOW(), 'system', 'system',
        'DELIVERED', 'Delivered', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md79notifstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md79notifStatus-failed', NOW(), NOW(), 'system', 'system',
        'FAILED', 'Failed', '5', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md79notifstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md79notifStatus-expired', NOW(), NOW(), 'system', 'system',
        'EXPIRED', 'Expired', '6', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md79notifstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md79notifStatus-cancelled', NOW(), NOW(), 'system', 'system',
        'CANCELLED', 'Cancelled', '7', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md80notifCategory: MD.80 - Notification Category
CREATE TABLE IF NOT EXISTS "app_fd_md80notifcategory" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md80notifcategory" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md80notifCategory-application', NOW(), NOW(), 'system', 'system',
        'APPLICATION', 'Application', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md80notifcategory" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md80notifCategory-eligibility', NOW(), NOW(), 'system', 'system',
        'ELIGIBILITY', 'Eligibility', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md80notifcategory" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md80notifCategory-enrollment', NOW(), NOW(), 'system', 'system',
        'ENROLLMENT', 'Enrollment', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md80notifcategory" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md80notifCategory-distribution', NOW(), NOW(), 'system', 'system',
        'DISTRIBUTION', 'Distribution', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md80notifcategory" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md80notifCategory-payment', NOW(), NOW(), 'system', 'system',
        'PAYMENT', 'Payment', '5', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md80notifcategory" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md80notifCategory-reminder', NOW(), NOW(), 'system', 'system',
        'REMINDER', 'Reminder', '6', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md80notifcategory" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md80notifCategory-alert', NOW(), NOW(), 'system', 'system',
        'ALERT', 'Alert', '7', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md80notifcategory" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md80notifCategory-general', NOW(), NOW(), 'system', 'system',
        'GENERAL', 'General', '8', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md81sendTiming: MD.81 - Send Timing
CREATE TABLE IF NOT EXISTS "app_fd_md81sendtiming" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md81sendtiming" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md81sendTiming-immediate', NOW(), NOW(), 'system', 'system',
        'IMMEDIATE', 'Send Immediately', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md81sendtiming" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md81sendTiming-scheduled', NOW(), NOW(), 'system', 'system',
        'SCHEDULED', 'Send Scheduled', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md81sendtiming" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md81sendTiming-delayed', NOW(), NOW(), 'system', 'system',
        'DELAYED', 'Send Delayed', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md82yieldSource: MD.82 - Yield Source
CREATE TABLE IF NOT EXISTS "app_fd_md82yieldsource" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md82yieldsource" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md82yieldSource-baseline', NOW(), NOW(), 'system', 'system',
        'BASELINE', 'Baseline (md49)', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md82yieldsource" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md82yieldSource-farmer', NOW(), NOW(), 'system', 'system',
        'FARMER', 'Farmer-Reported', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md82yieldsource" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md82yieldSource-extension', NOW(), NOW(), 'system', 'system',
        'EXTENSION', 'Extension Officer', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md82yieldsource" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md82yieldSource-historical', NOW(), NOW(), 'system', 'system',
        'HISTORICAL', 'Historical Average', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md82yieldsource" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md82yieldSource-manual', NOW(), NOW(), 'system', 'system',
        'MANUAL', 'Manual Entry', '5', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md83yieldStatus: MD.83 - Yield/Production Status
CREATE TABLE IF NOT EXISTS "app_fd_md83yieldstatus" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md83yieldstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md83yieldStatus-excellent', NOW(), NOW(), 'system', 'system',
        'EXCELLENT', 'Excellent', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md83yieldstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md83yieldStatus-good', NOW(), NOW(), 'system', 'system',
        'GOOD', 'Good', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md83yieldstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md83yieldStatus-moderate', NOW(), NOW(), 'system', 'system',
        'MODERATE', 'Moderate', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md83yieldstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md83yieldStatus-poor', NOW(), NOW(), 'system', 'system',
        'POOR', 'Poor', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md83yieldstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md83yieldStatus-failed', NOW(), NOW(), 'system', 'system',
        'FAILED', 'Failed', '5', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md83yieldstatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md83yieldStatus-total_loss', NOW(), NOW(), 'system', 'system',
        'TOTAL_LOSS', 'Total Loss', '6', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md84seedType: MD.84 - Seed Type
CREATE TABLE IF NOT EXISTS "app_fd_md84seedtype" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md84seedtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md84seedType-improved', NOW(), NOW(), 'system', 'system',
        'IMPROVED', 'Improved', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md84seedtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md84seedType-local', NOW(), NOW(), 'system', 'system',
        'LOCAL', 'Local', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md84seedtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md84seedType-recycled', NOW(), NOW(), 'system', 'system',
        'RECYCLED', 'Recycled', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md84seedtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md84seedType-mixed', NOW(), NOW(), 'system', 'system',
        'MIXED', 'Mixed', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md84seedtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md84seedType-unknown', NOW(), NOW(), 'system', 'system',
        'UNKNOWN', 'Unknown', '5', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md85seedSource: MD.85 - Seed/Input Source
CREATE TABLE IF NOT EXISTS "app_fd_md85seedsource" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md85seedsource" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md85seedSource-subsidy', NOW(), NOW(), 'system', 'system',
        'SUBSIDY', 'Subsidy', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md85seedsource" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md85seedSource-purchased', NOW(), NOW(), 'system', 'system',
        'PURCHASED', 'Purchased', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md85seedsource" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md85seedSource-saved', NOW(), NOW(), 'system', 'system',
        'SAVED', 'Saved from Previous Season', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md85seedsource" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md85seedSource-gift', NOW(), NOW(), 'system', 'system',
        'GIFT', 'Gift', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md85seedsource" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md85seedSource-credit', NOW(), NOW(), 'system', 'system',
        'CREDIT', 'Credit', '5', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md86fertilizerType: MD.86 - Fertilizer Type
CREATE TABLE IF NOT EXISTS "app_fd_md86fertilizertype" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md86fertilizertype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md86fertilizerType-basal', NOW(), NOW(), 'system', 'system',
        'BASAL', 'Basal Application', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md86fertilizertype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md86fertilizerType-top', NOW(), NOW(), 'system', 'system',
        'TOP', 'Top Dressing', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md86fertilizertype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md86fertilizerType-foliar', NOW(), NOW(), 'system', 'system',
        'FOLIAR', 'Foliar Application', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md86fertilizertype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md86fertilizerType-organic', NOW(), NOW(), 'system', 'system',
        'ORGANIC', 'Organic', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md87irrigationType: MD.87 - Irrigation Type
CREATE TABLE IF NOT EXISTS "app_fd_md87irrigationtype" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md87irrigationtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md87irrigationType-rainfed', NOW(), NOW(), 'system', 'system',
        'RAINFED', 'Rainfed', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md87irrigationtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md87irrigationType-full', NOW(), NOW(), 'system', 'system',
        'FULL', 'Full Irrigation', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md87irrigationtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md87irrigationType-supplemental', NOW(), NOW(), 'system', 'system',
        'SUPPLEMENTAL', 'Supplemental Irrigation', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md87irrigationtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md87irrigationType-drip', NOW(), NOW(), 'system', 'system',
        'DRIP', 'Drip Irrigation', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md88harvestDispos: MD.88 - Harvest Disposition
CREATE TABLE IF NOT EXISTS "app_fd_md88harvestdispos" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md88harvestdispos" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md88harvestDispos-consumption', NOW(), NOW(), 'system', 'system',
        'CONSUMPTION', 'Household Consumption', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md88harvestdispos" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md88harvestDispos-sale', NOW(), NOW(), 'system', 'system',
        'SALE', 'Sold', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md88harvestdispos" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md88harvestDispos-seed', NOW(), NOW(), 'system', 'system',
        'SEED', 'Saved as Seed', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md88harvestdispos" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md88harvestDispos-gift', NOW(), NOW(), 'system', 'system',
        'GIFT', 'Gift', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md88harvestdispos" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md88harvestDispos-storage', NOW(), NOW(), 'system', 'system',
        'STORAGE', 'Storage', '5', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md88harvestdispos" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md88harvestDispos-lost', NOW(), NOW(), 'system', 'system',
        'LOST', 'Lost', '6', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md89shockImpactLvl: MD.89 - Shock Impact Level
CREATE TABLE IF NOT EXISTS "app_fd_md89shockimpactlvl" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md89shockimpactlvl" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md89shockImpactLvl-none', NOW(), NOW(), 'system', 'system',
        'NONE', 'None', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md89shockimpactlvl" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md89shockImpactLvl-minor', NOW(), NOW(), 'system', 'system',
        'MINOR', 'Minor', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md89shockimpactlvl" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md89shockImpactLvl-moderate', NOW(), NOW(), 'system', 'system',
        'MODERATE', 'Moderate', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md89shockimpactlvl" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md89shockImpactLvl-severe', NOW(), NOW(), 'system', 'system',
        'SEVERE', 'Severe', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md89shockimpactlvl" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md89shockImpactLvl-total', NOW(), NOW(), 'system', 'system',
        'TOTAL', 'Total', '5', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md90harvestStatus: MD.90 - Harvest Record Status
CREATE TABLE IF NOT EXISTS "app_fd_md90harveststatus" (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  datecreated TIMESTAMP, datemodified TIMESTAMP,
  createdby VARCHAR(255), createdbyname VARCHAR(255),
  modifiedby VARCHAR(255), modifiedbyname VARCHAR(255),
  c_code VARCHAR(255), c_name VARCHAR(255),
  c_description TEXT, c_displayorder VARCHAR(255),
  c_isactive VARCHAR(255)
);
INSERT INTO "app_fd_md90harveststatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md90harvestStatus-draft', NOW(), NOW(), 'system', 'system',
        'DRAFT', 'Draft', '1', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md90harveststatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md90harvestStatus-submitted', NOW(), NOW(), 'system', 'system',
        'SUBMITTED', 'Submitted', '2', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md90harveststatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md90harvestStatus-verified', NOW(), NOW(), 'system', 'system',
        'VERIFIED', 'Verified', '3', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md90harveststatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md90harvestStatus-approved', NOW(), NOW(), 'system', 'system',
        'APPROVED', 'Approved', '4', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md90harveststatus" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_displayorder, c_isactive)
VALUES ('md90harvestStatus-rejected', NOW(), NOW(), 'system', 'system',
        'REJECTED', 'Rejected', '5', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

COMMIT;
