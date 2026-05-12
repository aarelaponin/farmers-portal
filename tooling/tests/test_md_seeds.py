"""
Layer 1 — Master-data seed integrity.

Asserts:
- Every MD lookup table has at least one row.
- Critical FK references from transactional tables resolve to live MD rows.
- Every Lesotho district has at least one Resource Centre.
- Every active programme has a budget envelope.

The MD table names follow Joget's truncated-to-24-chars convention with
no underscore separators (e.g. md03district, not md03_district). Always
inspect the live schema before adding entries to MD_TABLES below.
"""
import pytest


# ---------------------------------------------------------------------------
# MD inventory — verified against the live dev DB (May 2026).
# Adding rows here is how you extend coverage when a new MD form is added.
# ---------------------------------------------------------------------------

MD_TABLES = [
    # (table_name, business_meaning, expected_min_rows)
    ("app_fd_md01maritalstatus",   "Marital status",            1),
    ("app_fd_md03district",        "Districts (Lesotho has 10)", 10),
    ("app_fd_md04agroecologicalzo","Agro-ecological zones",     1),
    ("app_fd_md12relationship",    "Household relationships",   1),
    ("app_fd_md16livestocktype",   "Livestock types",           1),
    ("app_fd_md191cropcategory",   "Crop categories",           1),
    ("app_fd_md19crops",           "Crops",                     1),
    ("app_fd_md27input",           "Input catalogue",           1),
    ("app_fd_md37collectionpoint", "Resource Centres (≥60 expected)", 60),
    ("app_fd_md_donor",            "Donors",                    1),
    ("app_fd_md_input_unit",       "Units (BAG_25KG, etc.)",    1),
    ("app_fd_md_voucher_status",   "Voucher status codes",      1),
    ("app_fd_md_supplier_type",    "Supplier types",            1),
]

# FK-resolution checks: pairs of (left table.column → right table.column).
# A row in the left side references a code that should exist on the right.
FK_CHECKS = [
    ("app_fd_subsidy_app_2025", "c_applied_programme",
     "app_fd_mm_registration", "c_code", "applied_programme → mm_registration.code"),
    ("app_fd_im_voucher", "c_programme_code",
     "app_fd_mm_registration", "c_code", "im_voucher.programme_code → mm_registration.code"),
    ("app_fd_im_voucher", "c_input_code",
     "app_fd_md27input", "c_code", "im_voucher.input_code → md27input.code"),
    ("app_fd_im_voucher", "c_point_code",
     "app_fd_md37collectionpoint", "c_code", "im_voucher.point_code → md37collectionpoint.code"),
    ("app_fd_programme_funding", "c_programme_code",
     "app_fd_mm_registration", "c_code", "programme_funding.programme_code → mm_registration.code"),
    ("app_fd_programme_funding", "c_donor_code",
     "app_fd_md_donor", "c_code", "programme_funding.donor_code → md_donor.code"),
]


@pytest.mark.parametrize("table,meaning,expected_min", MD_TABLES,
                         ids=[t[0] for t in MD_TABLES])
def test_md_table_has_rows(cur, table, meaning, expected_min):
    """Every MD lookup table should have at least the documented minimum
    number of rows. Catches "lookup never seeded" bugs that silently
    produce empty dropdowns in operator UI."""
    try:
        cur.execute(f"SELECT count(*) FROM {table}")
        n = cur.fetchone()[0]
    except Exception as e:
        pytest.skip(f"Table {table} not present: {e}")
        return
    assert n >= expected_min, (
        f"{table} ({meaning}) has {n} rows; expected ≥{expected_min}. "
        f"Empty / under-seeded MD tables produce empty dropdowns."
    )


@pytest.mark.parametrize("ltable,lcol,rtable,rcol,desc", FK_CHECKS,
                         ids=[c[4] for c in FK_CHECKS])
def test_fk_references_resolve(cur, ltable, lcol, rtable, rcol, desc):
    """Every transactional row's FK should point at a live row.
    Catches stale codes — e.g. a programme deleted while live applications
    still reference it."""
    try:
        cur.execute(
            f"""
            SELECT count(*) FROM {ltable} l
            WHERE l.{lcol} IS NOT NULL
              AND l.{lcol} != ''
              AND NOT EXISTS (SELECT 1 FROM {rtable} r WHERE r.{rcol} = l.{lcol})
            """
        )
        orphans = cur.fetchone()[0]
    except Exception as e:
        pytest.skip(f"FK check skipped (table absent): {e}")
        return
    assert orphans == 0, (
        f"{desc}: {orphans} row(s) in {ltable}.{lcol} reference a code "
        f"that does not exist in {rtable}.{rcol}."
    )


def test_resource_centres_one_per_district(cur):
    """Each Lesotho district should have at least one Resource Centre.
    A district with zero centres means farmers in that district cannot
    collect inputs."""
    cur.execute("SELECT c_code FROM app_fd_md03district")
    districts = {r[0] for r in cur.fetchall() if r[0]}
    if not districts:
        pytest.skip("md03district is empty — covered by test_md_table_has_rows")
        return
    cur.execute(
        "SELECT DISTINCT c_district_code FROM app_fd_md37collectionpoint "
        "WHERE c_district_code IS NOT NULL AND c_district_code != ''"
    )
    covered = {r[0] for r in cur.fetchall()}
    missing = districts - covered
    assert not missing, (
        f"{len(missing)} district(s) with no Resource Centre: {sorted(missing)}. "
        f"Farmers in these districts cannot collect inputs."
    )


def test_active_programmes_have_envelopes(cur):
    """Every programme in mm_registration should have a budget envelope.
    Programmes without envelopes silently fail at voucher-issuance time
    because the COMMITMENT event has nowhere to post."""
    cur.execute("SELECT c_code FROM app_fd_mm_registration WHERE c_code IS NOT NULL")
    programmes = {r[0] for r in cur.fetchall()}
    if not programmes:
        pytest.skip("No mm_registration rows — bootstrapping scenario")
        return
    cur.execute(
        "SELECT DISTINCT c_programme_code FROM app_fd_budget_envelope "
        "WHERE c_programme_code IS NOT NULL"
    )
    enveloped = {r[0] for r in cur.fetchall()}
    missing = programmes - enveloped
    if missing:
        pytest.fail(
            f"{len(missing)} programme(s) have no budget envelope: "
            f"{sorted(missing)}. Voucher issuance will fail silently."
        )
