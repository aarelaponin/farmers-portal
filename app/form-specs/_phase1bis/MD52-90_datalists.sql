-- Phase 1bis — Create list_md52..md90 datalists (thin form-row binders)

BEGIN;

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md52programStatus', 'List: MD.52 - Program Status',
        $${"id": "list_md52programStatus", "name": "List: MD.52 - Program Status", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md52programStatus"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md53priority', 'List: MD.53 - Priority',
        $${"id": "list_md53priority", "name": "List: MD.53 - Priority", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md53priority"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md54reportingFreq', 'List: MD.54 - Reporting Frequency',
        $${"id": "list_md54reportingFreq", "name": "List: MD.54 - Reporting Frequency", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md54reportingFreq"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md55geographicScope', 'List: MD.55 - Geographic Scope',
        $${"id": "list_md55geographicScope", "name": "List: MD.55 - Geographic Scope", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md55geographicScope"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md56targetingStrat', 'List: MD.56 - Targeting Strategy',
        $${"id": "list_md56targetingStrat", "name": "List: MD.56 - Targeting Strategy", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md56targetingStrat"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md57priorityModel', 'List: MD.57 - Priority Model',
        $${"id": "list_md57priorityModel", "name": "List: MD.57 - Priority Model", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md57priorityModel"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md58benefitItemType', 'List: MD.58 - Benefit Item Type',
        $${"id": "list_md58benefitItemType", "name": "List: MD.58 - Benefit Item Type", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md58benefitItemType"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md59ruleType', 'List: MD.59 - Rule Type',
        $${"id": "list_md59ruleType", "name": "List: MD.59 - Rule Type", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md59ruleType"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md60evalStrategy', 'List: MD.60 - Evaluation Strategy',
        $${"id": "list_md60evalStrategy", "name": "List: MD.60 - Evaluation Strategy", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md60evalStrategy"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md61approvalFlow', 'List: MD.61 - Approval Workflow',
        $${"id": "list_md61approvalFlow", "name": "List: MD.61 - Approval Workflow", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md61approvalFlow"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md62monitoringApp', 'List: MD.62 - Monitoring Approach',
        $${"id": "list_md62monitoringApp", "name": "List: MD.62 - Monitoring Approach", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md62monitoringApp"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md63dataCollMethod', 'List: MD.63 - Data Collection Method',
        $${"id": "list_md63dataCollMethod", "name": "List: MD.63 - Data Collection Method", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md63dataCollMethod"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md64documentStatus', 'List: MD.64 - Document Status',
        $${"id": "list_md64documentStatus", "name": "List: MD.64 - Document Status", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md64documentStatus"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md65docRejReason', 'List: MD.65 - Document Rejection Reason',
        $${"id": "list_md65docRejReason", "name": "List: MD.65 - Document Rejection Reason", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md65docRejReason"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md66eligibStatus', 'List: MD.66 - Preliminary Eligibility Status',
        $${"id": "list_md66eligibStatus", "name": "List: MD.66 - Preliminary Eligibility Status", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md66eligibStatus"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md67mobileMoneyProv', 'List: MD.67 - Mobile Money Provider',
        $${"id": "list_md67mobileMoneyProv", "name": "List: MD.67 - Mobile Money Provider", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md67mobileMoneyProv"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md68appealType', 'List: MD.68 - Appeal Type',
        $${"id": "list_md68appealType", "name": "List: MD.68 - Appeal Type", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md68appealType"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md69entityType', 'List: MD.69 - Related Entity Type',
        $${"id": "list_md69entityType", "name": "List: MD.69 - Related Entity Type", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md69entityType"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md70originalDecision', 'List: MD.70 - Original Decision',
        $${"id": "list_md70originalDecision", "name": "List: MD.70 - Original Decision", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md70originalDecision"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md71appealGrounds', 'List: MD.71 - Appeal Grounds',
        $${"id": "list_md71appealGrounds", "name": "List: MD.71 - Appeal Grounds", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md71appealGrounds"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md72appealStatus', 'List: MD.72 - Appeal Status',
        $${"id": "list_md72appealStatus", "name": "List: MD.72 - Appeal Status", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md72appealStatus"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md73resolutionDec', 'List: MD.73 - Resolution Decision',
        $${"id": "list_md73resolutionDec", "name": "List: MD.73 - Resolution Decision", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md73resolutionDec"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md74escalationLevel', 'List: MD.74 - Escalation Level',
        $${"id": "list_md74escalationLevel", "name": "List: MD.74 - Escalation Level", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md74escalationLevel"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md75particStatus', 'List: MD.75 - Participation Status',
        $${"id": "list_md75particStatus", "name": "List: MD.75 - Participation Status", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md75particStatus"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md76exitReason', 'List: MD.76 - Exit Reason',
        $${"id": "list_md76exitReason", "name": "List: MD.76 - Exit Reason", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md76exitReason"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md77satisfactionRtg', 'List: MD.77 - Satisfaction Rating',
        $${"id": "list_md77satisfactionRtg", "name": "List: MD.77 - Satisfaction Rating", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md77satisfactionRtg"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md78notifChannel', 'List: MD.78 - Notification Channel',
        $${"id": "list_md78notifChannel", "name": "List: MD.78 - Notification Channel", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md78notifChannel"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md79notifStatus', 'List: MD.79 - Notification Status',
        $${"id": "list_md79notifStatus", "name": "List: MD.79 - Notification Status", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md79notifStatus"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md80notifCategory', 'List: MD.80 - Notification Category',
        $${"id": "list_md80notifCategory", "name": "List: MD.80 - Notification Category", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md80notifCategory"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md81sendTiming', 'List: MD.81 - Send Timing',
        $${"id": "list_md81sendTiming", "name": "List: MD.81 - Send Timing", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md81sendTiming"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md82yieldSource', 'List: MD.82 - Yield Source',
        $${"id": "list_md82yieldSource", "name": "List: MD.82 - Yield Source", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md82yieldSource"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md83yieldStatus', 'List: MD.83 - Yield/Production Status',
        $${"id": "list_md83yieldStatus", "name": "List: MD.83 - Yield/Production Status", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md83yieldStatus"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md84seedType', 'List: MD.84 - Seed Type',
        $${"id": "list_md84seedType", "name": "List: MD.84 - Seed Type", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md84seedType"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md85seedSource', 'List: MD.85 - Seed/Input Source',
        $${"id": "list_md85seedSource", "name": "List: MD.85 - Seed/Input Source", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md85seedSource"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md86fertilizerType', 'List: MD.86 - Fertilizer Type',
        $${"id": "list_md86fertilizerType", "name": "List: MD.86 - Fertilizer Type", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md86fertilizerType"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md87irrigationType', 'List: MD.87 - Irrigation Type',
        $${"id": "list_md87irrigationType", "name": "List: MD.87 - Irrigation Type", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md87irrigationType"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md88harvestDispos', 'List: MD.88 - Harvest Disposition',
        $${"id": "list_md88harvestDispos", "name": "List: MD.88 - Harvest Disposition", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md88harvestDispos"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md89shockImpactLvl', 'List: MD.89 - Shock Impact Level',
        $${"id": "list_md89shockImpactLvl", "name": "List: MD.89 - Shock Impact Level", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md89shockImpactLvl"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

INSERT INTO app_datalist (appid, appversion, id, name, json, datecreated, datemodified)
VALUES ('farmersPortal', 1, 'list_md90harvestStatus', 'List: MD.90 - Harvest Record Status',
        $${"id": "list_md90harvestStatus", "name": "List: MD.90 - Harvest Record Status", "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder", "properties": {"formDefId": "md90harvestStatus"}}, "columns": [{"name": "code", "id": "column_0", "label": "Code"}, {"name": "name", "id": "column_1", "label": "Name"}, {"name": "description", "id": "column_2", "label": "Description"}, {"name": "displayOrder", "id": "column_3", "label": "Order"}, {"name": "isActive", "id": "column_4", "label": "Active"}], "filters": [], "actions": [], "rowActions": [], "useSession": "false", "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100", "buttonPosition": "bothLeft", "checkboxPosition": "left", "orderBy": "displayOrder", "order": ""}$$, NOW(), NOW())
ON CONFLICT (appid, appversion, id) DO UPDATE
  SET name=EXCLUDED.name, json=EXCLUDED.json, datemodified=NOW();

COMMIT;
