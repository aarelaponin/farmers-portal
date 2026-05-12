# RPT-EXEC-001: Registry Overview Dashboard

**Priority:** P1 - Critical

**Description:** Real-time summary of farmer registration statistics with key performance indicators.

**DataList ID:** `farmersOverview`

---

## Data Sources

| Table/View | Description |
|---|---|
| `app_fd_farmerBasicInfo` | Farmer counts, gender distribution |
| `app_fd_farm_location` | Administrative hierarchy, land statistics |
| `app_fd_farmer_declaration` | Registration status breakdown |
| `app_fd_md03district` | District names (MDM lookup) |
| `app_fd_md37collectionPoint` | Resource Centre names (MDM lookup) |
| `app_fd_md04agroEcologicalZone` | Zone names (MDM lookup) |

---

## Filter Criteria

| # | Filter Field | Type | Options/Source | DB Column |
|---|---|---|---|---|
| 1 | Date Range | Date Picker | Registration date from/to | `fb.dateCreated` |
| 2 | District | Multi-select | `md03district` (10 districts) | `fl.c_district` |
| 3 | Resource Centre | Multi-select | `md37collectionPoint` (10 centres) | `fl.c_resource_center` |
| 4 | Sub-Centre | Text (contains) | Free text search | `fl.c_sub_centre` |
| 5 | Village | Text (contains) | Free text search | `fl.c_village` |
| 6 | Agro-Ecological Zone | Multi-select | `md04agroEcologicalZone` (4 zones) | `fl.c_agroEcologicalZone` |

### Filter Options Data

**md03district** (10 values):

| code | name |
|---|---|
| maseru | Maseru |
| berea | Berea |
| leribe | Leribe |
| butha-buthe | Butha-Buthe |
| mokhotlong | Mokhotlong |
| thaba-tseka | Thaba-Tseka |
| mafeteng | Mafeteng |
| qachas-nek | Qacha's Nek |
| quthing | Quthing |
| mohales-hoek | Mohale's Hoek |

**md37collectionPoint** (10 values):

| code | name | district_code |
|---|---|---|
| CP001 | Maseru Main Depot | maseru |
| CP002 | Berea Agricultural Center | berea |
| CP003 | Butha-Buthe Service Point | butha-buthe |
| CP004 | Leribe Distribution Center | leribe |
| CP005 | Mafeteng Regional Office | mafeteng |
| CP006 | Mohale's Hoek Service Center | mohales-hoek |
| CP007 | Mokhotlong Mountain Center | mokhotlong |
| CP008 | Qacha's Nek Distribution Point | qachas-nek |
| CP009 | Quthing Service Office | quthing |
| CP010 | Thaba-Tseka Highland Center | thaba-tseka |

**md04agroEcologicalZone** (4 values):

| code | name |
|---|---|
| lowlands | Lowlands |
| foothills | Foothills |
| mountains | Mountains |
| senqu_valley | Senqu River Valley |

**Sub-Centre / Village**: Free text fields in `app_fd_farm_location`. No MDM lookup — use `ILIKE '%value%'` text search in filter.

---

## Report Columns

| # | Column Name | Database Expression | Type | Notes |
|---|---|---|---|---|
| 1 | District | `d.c_name` | Text | Administrative level 1 — GROUP BY key |
| 2 | Resource Centre | `cp.c_name` | Text | Administrative level 2 — GROUP BY key |
| 3 | Sub-Centre | `fl.c_sub_centre` | Text | Administrative level 3 — GROUP BY key |
| 4 | Village | `fl.c_village` | Text | Administrative level 4 — GROUP BY key |
| 5 | Agro-Ecological Zone | `z.c_name` | Text | Ecological dimension — GROUP BY key |
| 6 | Total Registered Farmers | `COUNT(*)` | Number | Total count |
| 7 | Male Farmers | `COUNT(gender='male')` | Number | Show as `count (pct%)` |
| 8 | Female Farmers | `COUNT(gender='female')` | Number | Show as `count (pct%)` |
| 9 | Youth Farmers | `COUNT(age<35)` | Number | Show as `count (pct%)` |
| 10 | Verified | `COUNT(status='VERIFIED')` | Number | Green badge indicator |
| 11 | Pending | `COUNT(status='PENDING')` | Number | Yellow badge indicator |
| 12 | Total Land Area (Ha) | `SUM(totalAvailableLand)` | Decimal | Aggregate |
| 13 | Avg Farm Size (Ha) | `AVG(totalAvailableLand)` | Decimal | 2 decimal places |

### Column rules

- No row checkboxes — read-only report
- TOTAL summary row at bottom
- Columns 1–5 are GROUP BY keys (administrative hierarchy + zone)
- Columns 7–9 show combined value: `count (percentage%)` e.g. `28 (60.9%)`
- Columns 10–11 use coloured badge indicators (green = verified, yellow = pending)

---

## Joget Database Column Name Mapping

| Form Field | Table Alias | DB Column |
|---|---|---|
| `farmerBasicInfo.gender` | `fb` | `fb.c_gender` |
| `farmerBasicInfo.date_of_birth` | `fb` | `fb.c_date_of_birth` |
| `farmerBasicInfo.dateCreated` | `fb` | `fb.dateCreated` |
| `farm_location.district` | `fl` | `fl.c_district` |
| `farm_location.resource_center` | `fl` | `fl.c_resource_center` |
| `farm_location.sub_centre` | `fl` | `fl.c_sub_centre` |
| `farm_location.village` | `fl` | `fl.c_village` |
| `farm_location.agroEcologicalZone` | `fl` | `fl.c_agroEcologicalZone` |
| `farm_location.totalAvailableLand` | `fl` | `fl.c_totalAvailableLand` |
| `farmer_declaration.registrationStatus` | `fd` | `fd.c_registrationStatus` |
| `md03district.code` | `d` | `d.c_code` |
| `md03district.name` | `d` | `d.c_name` |
| `md37collectionPoint.code` | `cp` | `cp.c_code` |
| `md37collectionPoint.name` | `cp` | `cp.c_name` |
| `md04agroEcologicalZone.code` | `z` | `z.c_code` |
| `md04agroEcologicalZone.name` | `z` | `z.c_name` |

---

## Technical Notes

### ParamEncoder Hash

DataList ID: `farmersOverview` → prefix: `d-5814095-fn_`

### Hash Variable Pattern for JDBC SQL Filters

```sql
WHERE 1=1
  AND ('#requestParam.d-5814095-fn_date_from#' = ''
       OR fb.dateCreated >= '#requestParam.d-5814095-fn_date_from#'::timestamp)
  AND ('#requestParam.d-5814095-fn_date_to#' = ''
       OR fb.dateCreated < ('#requestParam.d-5814095-fn_date_to#'::timestamp + INTERVAL '1 day'))
  AND ('#requestParam.d-5814095-fn_district_code#' = ''
       OR fl.c_district = '#requestParam.d-5814095-fn_district_code#')
  AND ('#requestParam.d-5814095-fn_center_code#' = ''
       OR fl.c_resource_center = '#requestParam.d-5814095-fn_center_code#')
  AND ('#requestParam.d-5814095-fn_sub_centre#' = ''
       OR fl.c_sub_centre ILIKE '%' || '#requestParam.d-5814095-fn_sub_centre#' || '%')
  AND ('#requestParam.d-5814095-fn_village#' = ''
       OR fl.c_village ILIKE '%' || '#requestParam.d-5814095-fn_village#' || '%')
  AND ('#requestParam.d-5814095-fn_zone_code#' = ''
       OR fl.c_agroEcologicalZone = '#requestParam.d-5814095-fn_zone_code#')
```

### SQL GROUP BY

```sql
GROUP BY d.c_name, cp.c_name, fl.c_sub_centre, fl.c_village, z.c_name
ORDER BY d.c_name, cp.c_name, fl.c_sub_centre, fl.c_village, z.c_name
```

### Filter JSON Structure Reference

Each filter in the datalist JSON `filters` array:

```json
{
  "hidden": "",
  "name": "<column_name>",
  "id": "filter_N",
  "label": "<Display Label>",
  "type": {
    "className": "<filter plugin class>",
    "properties": { ... }
  },
  "datalist_type": "filter",
  "filterParamName": "d-5814095-fn_<column_name>"
}
```

### SelectBox Filter with Inline Options (District, Zone, Centre)

```json
{
  "className": "org.joget.plugin.enterprise.SelectBoxDataListFilterType",
  "properties": {
    "multiple": "true",
    "options": [
      { "label": "All", "value": "", "grouping": "" },
      { "label": "Maseru", "value": "maseru", "grouping": "" }
    ]
  }
}
```

### Text Filter (Sub-Centre, Village)

```json
{
  "className": "org.joget.apps.datalist.lib.TextFieldDataListFilterType",
  "properties": {}
}
```

### Date Filter

```json
{
  "className": "org.joget.plugin.enterprise.DateDataListFilterType",
  "properties": {
    "format": "yy-mm-dd",
    "formatJava": "yyyy-MM-dd",
    "yearRange": "c-10:c+2"
  }
}
```
