# Parcel-form dynamic zone-based map centring — implementation plan

**Status:** scoped, not yet implemented. Post-demo work, scheduled for the week of 2026-05-12.
**Decided:** 2026-05-07 (D49). Option 1 of three considered (see "Why this and not the others" below).

## What we're building

When a citizen fills the **Parcel Location** tab of the parcel registration wizard
(picking *district* + *agro-ecological zone*) and moves to the **Parcel Geometry**
tab, the GIS map should already be centred on the centroid of that
(district, zone) polygon — using the customer-supplied Bureau of Statistics
shapefile data already loaded into `md_district_eco_zone` (MD.95).

Today the GIS widget falls back to its hardcoded default `(-29.6, 28.2)`
or to district-name geocoding via the `autoCenterDistrictFieldId` mechanism.
The new behaviour replaces that fallback with the precise polygon centroid
from MD.95 whenever the (district, zone) pair has a match.

## Architecture

A **server-side `DefaultApplicationPlugin`** post-processor wired to the
`parcelLocation` form's save event. It runs in JVM, never touches the
browser, and pre-populates two HiddenFields on the sibling `parcelGeometry`
record so the existing GIS widget renders correctly without modification.

```
Citizen saves parcelLocation (district + agroEcologicalZone picked)
   ↓ Joget fires postProcessor on parcelLocation
ParcelZoneCentroidPostProcessor.execute(props)
   ├── reads district + agroEcologicalZone from the just-saved row
   ├── FormDataDao.find("md_district_eco_zone", ...) by (district, agro_zone)
   ├── extracts centroid_lat, centroid_lon
   └── FormDataDao.saveOrUpdate("parcelGeometry", row{auto_center_lat, auto_center_lon})
       keyed by parent_id (same wizard-record id as parcelLocation)

User clicks Parcel Geometry tab
   ↓
Form load binder reads parcelGeometry record
   ↓
HiddenFields auto_center_lat / auto_center_lon already populated
   ↓
GisPolygonCaptureElement reads its configured latFieldId / lonFieldId
   ↓
Map centres on (centroid_lat, centroid_lon) at autoCenterZoom (14)
```

**No client-side JavaScript. No GIS widget modification. No CustomHTML.**

## Implementation outline

### 1. New plugin module: `plugins/parcel-zone-centring/`

Mirror the structure of `plugins/form-quality-runtime/`. Smallest viable
shape:

```
parcel-zone-centring/
├── pom.xml                               # OSGi bundle, mirrors form-quality-runtime
├── deploy/
│   └── repack.sh                          # build script (copy from form-quality-runtime)
├── src/main/java/global/govstack/parcelzonecentring/
│   ├── Activator.java                    # registers the post-processor with OSGi
│   └── hook/
│       └── ParcelZoneCentroidPostProcessor.java   # extends DefaultApplicationPlugin
└── src/main/resources/properties/
    └── ParcelZoneCentroidPostProcessor.json       # configurable properties shown in App Composer
```

### 2. The post-processor class — outline

```java
public class ParcelZoneCentroidPostProcessor extends DefaultApplicationPlugin {
    @Override
    public Object execute(Map props) {
        AppDefinition appDef = (AppDefinition) props.get("appDef");
        String recordId = (String) props.get("recordId");

        // Read the parcelLocation row that just saved
        FormRowSet locRows = formDataDao.find("parcelLocation", "parcelLocation",
                "WHERE e.id = ?", new Object[]{recordId}, null, false, null, null);
        if (locRows.isEmpty()) return null;
        FormRow loc = locRows.get(0);
        String district = loc.getProperty("district");
        String zone = loc.getProperty("agroEcologicalZone");
        String parentId = loc.getProperty("parent_id");
        if (district == null || zone == null || parentId == null) return null;

        // Look up the centroid
        FormRowSet zoneRows = formDataDao.find("md_district_eco_zone", "md_district_eco_zone",
                "WHERE e.customProperties.district = ? AND e.customProperties.agro_zone = ?",
                new Object[]{district, zone}, null, false, null, null);
        if (zoneRows.isEmpty()) return null;
        FormRow zoneRow = zoneRows.get(0);
        String lat = zoneRow.getProperty("centroid_lat");
        String lon = zoneRow.getProperty("centroid_lon");

        // Find the sibling parcelGeometry row (linked by parent_id)
        FormRowSet geomRows = formDataDao.find("parcelGeometry", "parcelGeometry",
                "WHERE e.customProperties.parent_id = ?", new Object[]{parentId}, null, false, null, null);
        FormRow geomRow = geomRows.isEmpty() ? new FormRow() : geomRows.get(0);
        if (geomRows.isEmpty()) {
            geomRow.setId(UuidGenerator.getInstance().getUuid());
            geomRow.setProperty("parent_id", parentId);
        }
        geomRow.setProperty("auto_center_lat", lat);
        geomRow.setProperty("auto_center_lon", lon);

        // Use AppService.storeFormData (NOT FormDataDao.saveOrUpdate directly — see CLAUDE.md)
        FormRowSet rs = new FormRowSet();
        rs.add(geomRow);
        appService.storeFormData("parcelGeometry", "parcelGeometry", rs, geomRow.getId());

        return null;
    }
}
```

**Critical: use `AppService.storeFormData` not `FormDataDao.saveOrUpdate`** —
per CLAUDE.md, the latter writes NULL `datecreated` / `datemodified` /
operator-attribution columns. `RowWriter.save()` from reg-bb-engine is
the helper that wraps this correctly; either reuse that helper or replicate
the pattern.

### 3. Wiring on parcelLocation

After deploying the JAR, the parcelLocation form gets:

```json
"postProcessor": {
  "className": "global.govstack.parcelzonecentring.hook.ParcelZoneCentroidPostProcessor",
  "properties": {}
},
"postProcessorRunOn": "both"
```

`runOn = both` so it fires on both create and update (the citizen might change district/zone later; we want centring to update accordingly).

### 4. Build + deploy

Same pattern as the other 27 custom plugins in `plugins/`:

```bash
cd plugins/parcel-zone-centring
./deploy/repack.sh                 # builds JAR, bumps Build.NUMBER
# upload via Joget admin → Manage Plugins → Upload
```

### 5. Test plan

1. Pre-existing parcel: open in edit mode, confirm map still works (no-op fallback path).
2. New parcel: pick district + zone on Location tab → save → flip to Geometry tab → map should be at the (district, zone) centroid.
3. Change district+zone on the same parcel → save → map recentres on next Geometry-tab view.
4. (district, zone) combination with no match in MD.95 (e.g. lowlands_qachas-nek which doesn't exist) → centroid lookup returns nothing → auto_center_lat/lon left blank → GIS widget falls back to district-name geocoding (existing behaviour, unchanged).
5. Run the parcel-related regression suite (test_im_e2e step 6 covers parcel save flow).

## Why this approach and not the others

Three options were considered on 2026-05-07 (after a failed first attempt
using a CustomHTML-element with inline `<script>`, which Joget strips on
form rendering):

**Option 1 — server-side post-processor on parcelLocation (this plan).**
Lowest risk: doesn't touch the GIS widget, doesn't add client-side code,
uses the existing `auto_center_lat/lon` HiddenFields the widget already
watches. ~2-3 hrs effort. Pattern proven by `FormQualityPostProcessor`
in this codebase.

**Option 2 — custom Element plugin emitting Freemarker `<script>`.**
Element-plugin output is not stripped (CustomHTML output is — the failed
tonight attempt). ~3 hrs effort. Adds a new draggable element. Higher
moving parts than Option 1.

**Option 3 — modify the GIS widget directly.** Add `autoCenterZoneFieldId`,
`autoCenterZoneLookupFormId` properties to GisConfigBuilder + zone-lookup
logic to gis-capture.js. Most "native" path but **changes a widget that
every parcel save in the country depends on**. Effort 5+ hrs, high blast
radius, weak risk-reward.

Trade-off named: Option 1 over Option 3 because we have a working widget
we don't want to disturb; Option 1 over Option 2 because server-side
runs once per save instead of every form render, and there's no
client-side state-machine to debug.

## Reference

Tonight's failed first attempt is documented in the decision log as
D49a. Backup of the original `parcelGeometry` form definition is at
`_backups/parcelGeometry.v1.20260507-182913.pretty.json`. Useful to
keep as a reference of the form's stable shape.

The new MD form `md_district_eco_zone` (MD.95), the 25-row centroid seed,
and the supporting `dl_parcel_zone_centroid` datalist all exist in the
live system today. The post-processor reuses them without further changes.

The Bureau of Statistics shapefile is at
`_02_Analysis/_eco-zones/Ecological_Zones.shp` (with companion files);
GeoJSON conversion at `_02_Analysis/_eco-zones/lesotho_eco_zones.geojson`;
seed data in `district_zone_centroids.csv` and `.json`.
