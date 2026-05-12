#!/usr/bin/env python3
"""
Seed quality rules for the spProgramMain form (subsidy programme wizard).

Run this AFTER:
  1. The form-quality-runtime-8.1-SNAPSHOT.jar is uploaded via Joget admin.
  2. The 7 qa_* / audit_log forms have been "saved once" in App Composer
     so Joget creates the underlying app_fd_* tables.

What this script writes:
  * 1 row in qa_service       — registers the 'farmers_subsidy' service
  * 5 rows in qa_tab          — Identity / Timeline / Geography / Benefits / Monitoring
  * 5 rows in qa_rule         — starter rules of mixed severity
  * 1 row in qa_gate          — block APPROVED / ACTIVE while ERRORs exist

Idempotent: deletes any prior rows belonging to serviceId='farmers_subsidy' first.
"""

import psycopg2
import sys
import uuid

DB = dict(
    host="joget-pgsql-sa.postgres.database.azure.com",
    port=5432,
    dbname="jogetdb",
    user="jogetadmin",
    password="Joget@DB#2026!",
    sslmode="require",
)

SERVICE_ID = "farmers_subsidy"

TABS = [
    ("identity",   "Identity",        "spProgramIdentity",   100),
    ("timeline",   "Timeline & Budget","spProgramTimeline",  200),
    ("geography",  "Geography",       "spProgramGeography",  300),
    ("benefits",   "Benefits",        "spProgramBenefits",   500),
    ("monitoring", "Monitoring",      "spProgramMonitoring", 800),
]

# Each rule's ruleScript is plain SQL: returns ≥1 row when the rule FAILS.
# The post-processor substitutes #recordId# with the wizard's record id (sp_program.id).
RULES = [
    (
        "identity.programme_name_required",
        "ERROR", "identity", "programName",
        """SELECT 1 FROM app_fd_sp_program_identity
           WHERE c_parent_id = '#recordId#'
             AND (c_programname IS NULL OR TRIM(c_programname) = '')""",
        "Programme name must not be blank.",
    ),
    (
        "timeline.budget_must_be_positive",
        "ERROR", "timeline", "totalBudget",
        """SELECT 1 FROM app_fd_sp_program_timeline
           WHERE c_parent_id = '#recordId#'
             AND (c_totalbudget IS NULL
                  OR c_totalbudget = ''
                  OR CAST(c_totalbudget AS NUMERIC) <= 0)""",
        "Total budget must be greater than zero.",
    ),
    (
        "geography.at_least_one_district",
        "ERROR", "geography", "districtAllocations",
        """SELECT 1 WHERE NOT EXISTS (
             SELECT 1 FROM app_fd_sp_district_alloc da
             JOIN app_fd_sp_program_geography g ON da.c_program_id = g.id
             WHERE g.c_parent_id = '#recordId#')""",
        "Programme must declare at least one district allocation.",
    ),
    (
        "benefits.at_least_one_item",
        "ERROR", "benefits", "benefitItems",
        """SELECT 1 WHERE NOT EXISTS (
             SELECT 1 FROM app_fd_sp_benefit_item bi
             JOIN app_fd_sp_program_benefits b ON bi.c_program_id = b.id
             WHERE b.c_parent_id = '#recordId#')""",
        "Programme must declare at least one benefit item.",
    ),
    (
        "monitoring.at_least_one_kpi",
        "WARNING", "monitoring", "kpis",
        """SELECT 1 WHERE NOT EXISTS (
             SELECT 1 FROM app_fd_sp_kpi k
             JOIN app_fd_sp_program_monitor m ON k.c_program_id = m.id
             WHERE m.c_parent_id = '#recordId#')""",
        "Programme should declare at least one KPI before activation.",
    ),
]

GATE = (
    "status",                        # field
    "APPROVED,ACTIVE",               # gated values (programme can't go ACTIVE while errors exist)
    "ERROR",                         # severity threshold
    "Block APPROVED/ACTIVE transitions while ERRORs remain.",
)


def main() -> int:
    conn = psycopg2.connect(**DB)
    cur = conn.cursor()

    # Tables exist? Quick probe — surfaces actionable error if user skipped step 2.
    cur.execute("SELECT to_regclass('public.app_fd_qa_service')")
    if cur.fetchone()[0] is None:
        print("ERROR: app_fd_qa_service does not exist yet.")
        print("       Open App Composer for farmersPortal → save each qa_* form once.")
        print("       See deployment_runbook.md.")
        return 2

    # Wipe prior rows for this service (idempotent re-runs)
    for tbl in ("qa_gate", "qa_rule", "qa_tab", "qa_service"):
        cur.execute(f"DELETE FROM app_fd_{tbl} WHERE c_serviceid = %s", (SERVICE_ID,))
    print(f"  cleaned prior rows for serviceId={SERVICE_ID}")

    # qa_service
    cur.execute("""
      INSERT INTO app_fd_qa_service
        (id, datecreated, datemodified, createdby, modifiedby,
         c_serviceid, c_servicename, c_primaryformid, c_govstackversion, c_isactive, c_notes)
      VALUES (%s, NOW(), NOW(), 'admin', 'admin', %s, %s, %s, %s, %s, %s)
    """, (str(uuid.uuid4()), SERVICE_ID, "Lesotho Farmers Subsidy Programme",
          "spProgramMain", "1.1.0", "Y",
          "Day-3 Phase A3 demo. Rules cover identity / budget / geography / benefits / monitoring."))

    # qa_tab
    for code, label, form_id, order in TABS:
        cur.execute("""
          INSERT INTO app_fd_qa_tab
            (id, datecreated, datemodified, createdby, modifiedby,
             c_serviceid, c_tabcode, c_tablabel, c_tabformid, c_taborder)
          VALUES (%s, NOW(), NOW(), 'admin', 'admin', %s, %s, %s, %s, %s)
        """, (str(uuid.uuid4()), SERVICE_ID, code, label, form_id, str(order)))

    # qa_rule
    for code, severity, tab, fields, script, message in RULES:
        cur.execute("""
          INSERT INTO app_fd_qa_rule
            (id, datecreated, datemodified, createdby, modifiedby,
             c_serviceid, c_tabcode, c_rulecode, c_severity,
             c_affectedfields, c_rulescript, c_message, c_isactive)
          VALUES (%s, NOW(), NOW(), 'admin', 'admin', %s, %s, %s, %s, %s, %s, %s, %s)
        """, (str(uuid.uuid4()), SERVICE_ID, tab, code, severity,
              fields, script.strip(), message, "Y"))

    # qa_gate
    field, values, threshold, notes = GATE
    cur.execute("""
      INSERT INTO app_fd_qa_gate
        (id, datecreated, datemodified, createdby, modifiedby,
         c_serviceid, c_gatefield, c_gatevalues, c_blockedbyseverity, c_isactive, c_notes)
      VALUES (%s, NOW(), NOW(), 'admin', 'admin', %s, %s, %s, %s, %s, %s)
    """, (str(uuid.uuid4()), SERVICE_ID, field, values, threshold, "Y", notes))

    conn.commit()

    # Report
    cur.execute("SELECT COUNT(*) FROM app_fd_qa_rule WHERE c_serviceid = %s", (SERVICE_ID,))
    rule_count = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM app_fd_qa_tab WHERE c_serviceid = %s", (SERVICE_ID,))
    tab_count = cur.fetchone()[0]
    print(f"  seeded: 1 service, {tab_count} tabs, {rule_count} rules, 1 gate")

    cur.close()
    conn.close()
    print("done.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
