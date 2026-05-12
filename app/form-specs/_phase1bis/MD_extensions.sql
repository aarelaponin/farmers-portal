-- Phase 1bis — Extensions to existing MDs (new codes only)

BEGIN;

-- md11hazard extensions
INSERT INTO "app_fd_md11hazard" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_isactive)
VALUES ('md11hazard-ext-theft', NOW(), NOW(), 'system', 'system', 'THEFT', 'Theft', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md11hazard" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_isactive)
VALUES ('md11hazard-ext-other', NOW(), NOW(), 'system', 'system', 'OTHER', 'Other Hazard', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md11hazard" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_isactive)
VALUES ('md11hazard-ext-none', NOW(), NOW(), 'system', 'system', 'NONE', 'No Hazard', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md18registrationChan extensions
INSERT INTO "app_fd_md18registrationchan" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_isactive)
VALUES ('md18registrationChan-ext-portal', NOW(), NOW(), 'system', 'system', 'portal', 'Online Portal', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md18registrationchan" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_isactive)
VALUES ('md18registrationChan-ext-phone', NOW(), NOW(), 'system', 'system', 'phone', 'Phone Submission', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md18registrationchan" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_isactive)
VALUES ('md18registrationChan-ext-sms', NOW(), NOW(), 'system', 'system', 'sms', 'SMS', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md18registrationchan" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_isactive)
VALUES ('md18registrationChan-ext-campaign', NOW(), NOW(), 'system', 'system', 'campaign', 'Outreach Campaign', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md18registrationchan" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_isactive)
VALUES ('md18registrationChan-ext-field_visit', NOW(), NOW(), 'system', 'system', 'field_visit', 'Field Visit', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md18registrationchan" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_isactive)
VALUES ('md18registrationChan-ext-mobile_app', NOW(), NOW(), 'system', 'system', 'mobile_app', 'Mobile App', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md18registrationchan" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_isactive)
VALUES ('md18registrationChan-ext-email', NOW(), NOW(), 'system', 'system', 'email', 'Email', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

-- md34notificationType extensions
INSERT INTO "app_fd_md34notificationtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_isactive)
VALUES ('md34notificationType-ext-manual', NOW(), NOW(), 'system', 'system', 'MANUAL', 'Manual Trigger', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md34notificationtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_isactive)
VALUES ('md34notificationType-ext-app_under_review', NOW(), NOW(), 'system', 'system', 'APP_UNDER_REVIEW', 'Application Under Review', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md34notificationtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_isactive)
VALUES ('md34notificationType-ext-app_docs_required', NOW(), NOW(), 'system', 'system', 'APP_DOCS_REQUIRED', 'Documents Required', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md34notificationtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_isactive)
VALUES ('md34notificationType-ext-enrolled', NOW(), NOW(), 'system', 'system', 'ENROLLED', 'Enrolled in Programme', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md34notificationtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_isactive)
VALUES ('md34notificationType-ext-benefit_issued', NOW(), NOW(), 'system', 'system', 'BENEFIT_ISSUED', 'Benefit Issued', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md34notificationtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_isactive)
VALUES ('md34notificationType-ext-distribution_open', NOW(), NOW(), 'system', 'system', 'DISTRIBUTION_OPEN', 'Distribution Opened', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md34notificationtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_isactive)
VALUES ('md34notificationType-ext-overdue', NOW(), NOW(), 'system', 'system', 'OVERDUE', 'Overdue Notification', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();
INSERT INTO "app_fd_md34notificationtype" (id, datecreated, datemodified, createdby, modifiedby, c_code, c_name, c_isactive)
VALUES ('md34notificationType-ext-custom', NOW(), NOW(), 'system', 'system', 'CUSTOM', 'Custom Event', 'Y')
ON CONFLICT (id) DO UPDATE SET c_name=EXCLUDED.c_name, datemodified=NOW();

COMMIT;
