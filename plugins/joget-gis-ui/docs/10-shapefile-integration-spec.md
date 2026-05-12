# Shapefile Integration - Architecture & Implementation Plan

## Document Information

| Attribute | Value |
|-----------|-------|
| Document Type | Architecture & Implementation Plan |
| Components | joget-gis-server, joget-gis-ui |
| Version | 1.0 |
| Status | Draft |
| Date | 2026-02-04 |
| Related Documents | 07-gis-ui-ux-spec.md, 08-gis-backend-spec.md, gis-nearby-parcels-spec.md |
| Platform | Joget DX8 Enterprise Edition |

---

## 1. OVERVIEW

### 1.1 Purpose

This specification defines the architecture and implementation plan for integrating **ESRI Shapefile** support into the existing GIS plugin suite (`joget-gis-ui` and `joget-gis-server`). The integration enables:

1. **Boundary Reference Layers** — Display administrative boundaries (districts, agro-ecological zones, community councils) as read-only overlays on the polygon capture map
2. **Bulk Geometry Import** — Upload shapefiles to create or update records in Joget forms (e.g., import surveyed field boundaries)
3. **Geometry Export** — Export captured GeoJSON polygons to shapefile format for interoperability with QGIS, ArcGIS, and other GIS tools

### 1.2 Context: Lesotho MOA

The Lesotho Ministry of Agriculture maintains geospatial reference data as ESRI Shapefiles:

| Data | Expected CRS | Approximate Size | Usage |
|------|-------------|-----------------|-------|
| District boundaries (10 districts) | WGS84 or Hartebeeshoek94 | ~2 MB | Reference layer, auto-center validation |
| Agro-ecological zones (4 zones) | WGS84 or Hartebeeshoek94 | ~1 MB | Reference layer, zone classification |
| Community council boundaries | WGS84 or Hartebeeshoek94 | ~5 MB | Reference layer |
| Existing farm/field boundaries | Unknown | Variable | Bulk import into parcelGeometry form |

> **Note on CRS**: Southern Africa commonly uses Hartebeeshoek94 (EPSG:4148) or Cape datum. The system must support automatic reprojection to WGS84 (EPSG:4326).

### 1.3 Design Principles

| Principle | Description |
|-----------|-------------|
| **Generic** | Shapefile operations are domain-agnostic — no assumptions about boundary types |
| **GeoJSON-Centric** | Shapefiles are converted to GeoJSON at ingest; all internal processing uses GeoJSON |
| **Configurable** | Boundary layers controlled via plugin properties and/or admin forms |
| **Read-Only Display** | Boundary layers cannot be edited during polygon capture sessions |
| **Joget-Native Storage** | Converted GeoJSON stored via Joget forms and/or filesystem |

### 1.4 Relationship to Existing Components

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  JOGET FORM (e.g., Parcel Registration)                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  GIS POLYGON CAPTURE PLUGIN (joget-gis-ui)                             │ │
│  │  ─────────────────────────────────────────                             │ │
│  │  EXISTING:                                                              │ │
│  │  • Polygon capture (Draw + Walk)                                       │ │
│  │  • Nearby parcels display         (READ-ONLY)                          │ │
│  │  • Overlap checking                                                     │ │
│  │                                                                          │ │
│  │  NEW:                                                                    │ │
│  │  • Boundary layer display          (READ-ONLY)  ◄── This spec          │ │
│  │  • Layer toggle controls                                                │ │
│  └───────────────────────────────────────────┬────────────────────────────┘ │
│                                               │                              │
│  ┌────────────────────────────────────────────┴───────────────────────────┐ │
│  │  GIS SERVER PLUGIN (joget-gis-server)                                   │ │
│  │  ─────────────────────────────────────                                 │ │
│  │  EXISTING:                                                              │ │
│  │  • /gis/calculate, /gis/validate, /gis/checkOverlap                    │ │
│  │  • /gis/nearbyParcels, /gis/geocode                                    │ │
│  │                                                                          │ │
│  │  NEW:                                                                    │ │
│  │  • /gis/layers                     — List available layers              │ │
│  │  • /gis/layers/{code}              — Get GeoJSON for a layer            │ │
│  │  • /gis/shapefile/import           — Upload & convert shapefile         │ │
│  │  • /gis/shapefile/export           — Export GeoJSON to shapefile        │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  BOUNDARY LAYER ADMIN (Joget form-based)                                │ │
│  │  ─────────────────────────────────────                                 │ │
│  │  • mdBoundaryLayer form — layer metadata (code, name, status)          │ │
│  │  • File upload for shapefiles                                           │ │
│  │  • Converted GeoJSON stored on filesystem                               │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. USE CASES

### 2.1 UC-01: Display Boundary Reference Layers

**Actor**: Extension officer registering a parcel

**Flow**:
1. Officer opens parcel registration form with GIS Polygon Capture element
2. Map loads with default tile layer (OSM/Satellite)
3. Officer toggles "District Boundaries" layer ON via layer control
4. District boundary polygons appear as dashed outlines with labels
5. Officer draws parcel boundary within the correct district
6. Boundary layers remain read-only throughout the session

**Acceptance Criteria**:
- Boundary layers load within 3 seconds (cached after first load)
- Boundaries render as non-interactive, non-editable overlays
- Layer labels visible on hover/click
- Layer control allows toggling individual layers on/off
- Boundary layers do not interfere with polygon capture interactions

### 2.2 UC-02: Admin Uploads Boundary Shapefile

**Actor**: System administrator

**Flow**:
1. Admin navigates to Boundary Layer management form
2. Admin uploads a shapefile bundle (.shp + .dbf + .shx + .prj as ZIP)
3. System validates the shapefile structure and CRS
4. System converts to GeoJSON (reprojecting to WGS84 if needed)
5. System stores GeoJSON and creates/updates the layer record
6. Layer becomes available in GIS Polygon Capture element

**Acceptance Criteria**:
- Supports .zip bundle containing .shp, .dbf, .shx, .prj files
- Validates shapefile structure before conversion
- Automatic CRS detection and reprojection to EPSG:4326
- Reports feature count and bounding box after conversion
- Rejects files exceeding configurable size limit (default: 50 MB)

### 2.3 UC-03: Bulk Import Farm Boundaries

**Actor**: Data migration specialist

**Flow**:
1. User uploads a shapefile containing farm boundary polygons
2. System converts each feature to GeoJSON
3. User maps shapefile attribute columns to Joget form fields
4. System creates form records with geometry and mapped attributes
5. System reports import results (success count, error count, skipped)

**Acceptance Criteria**:
- Attribute mapping UI shows shapefile columns and target form fields
- Handles attribute type conversion (string, numeric, date)
- Validates each geometry before import
- Provides dry-run mode (preview without committing)
- Generates import report with record IDs

### 2.4 UC-04: Export Parcels to Shapefile

**Actor**: GIS analyst

**Flow**:
1. User selects records to export (or applies a filter)
2. System queries geometries and attributes from Joget form
3. System generates shapefile bundle with .shp, .dbf, .shx, .prj
4. User downloads the resulting .zip file

**Acceptance Criteria**:
- Exports selected form fields as shapefile attributes
- Generates valid .prj file with WGS84 definition
- Handles null geometries gracefully (skips with warning)
- Supports up to 10,000 records per export

---

## 3. BACKEND: joget-gis-server CHANGES

### 3.1 New Dependency: GeoTools

Add GeoTools libraries for shapefile read/write and CRS reprojection:

```xml
<!-- pom.xml additions -->
<properties>
    <geotools.version>29.2</geotools.version>
</properties>

<repositories>
    <!-- Already present -->
    <repository>
        <id>osgeo</id>
        <name>OSGeo Release Repository</name>
        <url>https://repo.osgeo.org/repository/release/</url>
    </repository>
</repositories>

<dependencies>
    <!-- Shapefile reading/writing -->
    <dependency>
        <groupId>org.geotools</groupId>
        <artifactId>gt-shapefile</artifactId>
        <version>${geotools.version}</version>
    </dependency>

    <!-- GeoJSON conversion -->
    <dependency>
        <groupId>org.geotools</groupId>
        <artifactId>gt-geojson-core</artifactId>
        <version>${geotools.version}</version>
    </dependency>

    <!-- CRS / coordinate reference system support -->
    <dependency>
        <groupId>org.geotools</groupId>
        <artifactId>gt-epsg-hsql</artifactId>
        <version>${geotools.version}</version>
    </dependency>

    <!-- Reprojection support -->
    <dependency>
        <groupId>org.geotools</groupId>
        <artifactId>gt-referencing</artifactId>
        <version>${geotools.version}</version>
    </dependency>
</dependencies>
```

**OSGi Bundle Configuration** — update `maven-bundle-plugin` instructions:

```xml
<Embed-Dependency>
    jts-core,jts-io-common,json-simple,
    gt-shapefile,gt-geojson-core,gt-epsg-hsql,gt-referencing,
    gt-metadata,gt-api,gt-main,gt-http,
    hsqldb;inline=true
</Embed-Dependency>
```

> **Risk**: GeoTools has a large dependency tree. The gt-epsg-hsql bundle alone is ~8 MB. OSGi embedding needs careful testing to avoid class loading issues. Consider a separate plugin bundle if the jar becomes too large (>30 MB).

### 3.2 New Service: ShapefileService

```
src/main/java/global/govstack/gisserver/
├── service/
│   ├── ShapefileService.java          ◄── NEW
│   ├── BoundaryLayerService.java      ◄── NEW
│   ├── OverlapService.java            (existing)
│   ├── NearbyParcelsService.java      (existing)
│   └── GeocodingService.java          (existing)
├── model/
│   ├── ShapefileImportResult.java     ◄── NEW
│   ├── BoundaryLayer.java             ◄── NEW
│   └── ... (existing)
└── lib/
    └── GisApiProvider.java            (add new endpoints)
```

#### 3.2.1 ShapefileService

**Responsibilities**:
- Parse shapefile bundles from uploaded ZIP files
- Detect and validate CRS from .prj file
- Reproject geometries to WGS84 (EPSG:4326) if needed
- Convert features to GeoJSON FeatureCollection
- Generate shapefile bundles from GeoJSON for export
- Validate geometry during conversion

**Key Methods**:

| Method | Input | Output | Description |
|--------|-------|--------|-------------|
| `convertToGeoJson(File zipFile)` | ZIP file | GeoJSON string + metadata | Parse shapefile, reproject, return GeoJSON FeatureCollection |
| `getShapefileMetadata(File zipFile)` | ZIP file | Metadata object | Extract CRS, feature count, attribute schema, bounding box |
| `exportToShapefile(String geoJson, Map<String,String> attributes)` | GeoJSON + attribute config | ZIP file | Generate shapefile bundle from GeoJSON |
| `validateShapefileBundle(File zipFile)` | ZIP file | Validation result | Check all required files present (.shp, .dbf, .shx) |

**CRS Handling**:

```
Input CRS Detection:
  1. Read .prj file → parse CRS definition
  2. If .prj missing → assume WGS84 with warning
  3. If CRS is not WGS84 → reproject using GeoTools MathTransform
  4. Common Southern Africa CRS to handle:
     - EPSG:4326  (WGS84)            → no reprojection needed
     - EPSG:4148  (Hartebeeshoek94)  → reproject
     - EPSG:4222  (Cape)             → reproject
     - EPSG:2046  (Hartebeeshoek94 / Lo29) → reproject
```

#### 3.2.2 BoundaryLayerService

**Responsibilities**:
- Manage boundary layer metadata and GeoJSON cache
- Serve GeoJSON for individual layers
- Handle layer lifecycle (upload, activate, deactivate, delete)

**Storage Strategy** (hybrid):

| Data | Storage | Reason |
|------|---------|--------|
| Layer metadata (code, name, CRS, feature count, active status) | Joget form: `mdBoundaryLayer` | Queryable, admin UI |
| Converted GeoJSON files | Filesystem: `{wflow}/app_data/gis-layers/{code}.geojson` | Large files (>1 MB), fast file read |
| GeoJSON file path | Joget form field: `geojson_path` | Links metadata to file |

### 3.3 New API Endpoints

#### 3.3.1 GET /gis/layers

List all available boundary layers.

**Response**:
```json
{
    "status": "success",
    "layers": [
        {
            "code": "LSO_DISTRICTS",
            "name": "Lesotho Districts",
            "featureCount": 10,
            "geometryType": "MultiPolygon",
            "sourceCrs": "EPSG:4326",
            "boundingBox": {
                "minLng": 27.01,
                "minLat": -30.67,
                "maxLng": 29.46,
                "maxLat": -28.57
            },
            "isActive": true,
            "uploadedAt": "2026-02-04T10:00:00Z"
        }
    ]
}
```

#### 3.3.2 GET /gis/layers/{code}

Get GeoJSON for a specific boundary layer.

**Query Parameters**:

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `simplify` | Double | No | 0 | Simplification tolerance (degrees). 0 = no simplification |
| `bounds` | String | No | - | Bounding box filter: `minLng,minLat,maxLng,maxLat` |
| `properties` | String | No | all | Comma-separated list of properties to include |

**Response**: GeoJSON FeatureCollection

**Caching**: Response should include `Cache-Control: max-age=3600` header. Boundary layers are static and rarely change.

#### 3.3.3 POST /gis/shapefile/import

Upload a shapefile and optionally import features into a Joget form.

**Request**: `multipart/form-data`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `file` | File | Yes | ZIP file containing shapefile bundle |
| `targetFormId` | String | No | Joget form to import features into |
| `geometryFieldId` | String | No | Target form field for GeoJSON geometry |
| `attributeMapping` | JSON | No | Shapefile column → form field mapping |
| `dryRun` | Boolean | No | Preview results without committing (default: false) |
| `layerCode` | String | No | If provided, register as a boundary layer instead of importing |
| `layerName` | String | No | Display name for boundary layer |

**Response** (import mode):
```json
{
    "status": "success",
    "totalFeatures": 150,
    "imported": 148,
    "skipped": 2,
    "errors": [
        { "featureIndex": 42, "error": "Empty geometry" },
        { "featureIndex": 99, "error": "Self-intersection detected" }
    ],
    "sourceCrs": "EPSG:4148",
    "reprojected": true
}
```

**Response** (boundary layer mode):
```json
{
    "status": "success",
    "layerCode": "LSO_DISTRICTS",
    "featureCount": 10,
    "geometryType": "MultiPolygon",
    "sourceCrs": "EPSG:4148",
    "reprojected": true,
    "boundingBox": { "minLng": 27.01, "minLat": -30.67, "maxLng": 29.46, "maxLat": -28.57 }
}
```

#### 3.3.4 POST /gis/shapefile/export

Export GeoJSON records to shapefile format.

**Request**:
```json
{
    "formId": "parcelGeometry",
    "geometryFieldId": "geometry",
    "returnFields": ["area_hectares", "perimeter_meters"],
    "filterCondition": "district = 'BEREA'",
    "maxRecords": 5000
}
```

**Response**: `application/zip` binary download with shapefile bundle.

### 3.4 MDM Form: mdBoundaryLayer

| Field ID | Label | Type | Required | Description |
|----------|-------|------|----------|-------------|
| code | Layer Code | TextField | Yes | Unique identifier (e.g., `LSO_DISTRICTS`) |
| name | Layer Name | TextField | Yes | Display name |
| description | Description | TextArea | No | Layer description |
| geometry_type | Geometry Type | SelectBox | Auto | Polygon / MultiPolygon / LineString |
| source_crs | Source CRS | TextField | Auto | Original CRS (e.g., `EPSG:4148`) |
| feature_count | Feature Count | TextField | Auto | Number of features |
| geojson_path | GeoJSON File Path | HiddenField | Auto | Filesystem path to converted GeoJSON |
| file_size_mb | File Size (MB) | TextField | Auto | Size of GeoJSON file |
| bbox_min_lng | Min Longitude | HiddenField | Auto | Bounding box west |
| bbox_min_lat | Min Latitude | HiddenField | Auto | Bounding box south |
| bbox_max_lng | Max Longitude | HiddenField | Auto | Bounding box east |
| bbox_max_lat | Max Latitude | HiddenField | Auto | Bounding box north |
| default_style | Default Style | TextArea | No | JSON style config |
| is_active | Active | CheckBox | Yes | Whether layer is available |
| uploaded_at | Upload Date | TextField | Auto | ISO timestamp |

---

## 4. FRONTEND: joget-gis-ui CHANGES

### 4.1 New Module: Boundary Layer Display

Add a new module section to `gis-capture.js` alongside the existing NEARBY PARCELS MODULE:

```
gis-capture.js modules:
├── CORE METHODS                    (existing)
├── UI METHODS                      (existing)
├── MAP METHODS                     (existing)
├── DRAWING METHODS                 (existing)
├── VALIDATION MODULE               (existing)
├── OVERLAP MODULE                  (existing)
├── NEARBY PARCELS MODULE           (existing)
├── BOUNDARY LAYER MODULE           ◄── NEW
│   ├── initBoundaryLayers()        — Setup layer control, load config
│   ├── loadBoundaryLayer(config)   — Fetch GeoJSON, create Leaflet layer
│   ├── toggleBoundaryLayer(code)   — Show/hide a specific layer
│   ├── styleBoundaryFeature()      — Apply layer-specific styling
│   └── destroyBoundaryLayers()     — Cleanup on component destroy
├── AUTO-CENTER MODULE              (existing)
└── CLEANUP                         (existing)
```

### 4.2 Layer Control UI

A collapsible panel positioned below the existing tile layer switcher:

```
┌──────────────────────────────────────────────────────┐
│  🗺 Map                                     [─][□]  │
├──────────────────────────────────────────────────────┤
│                                                      │
│  ┌──────────────────┐                                │
│  │ ≡ Layers         │  ◄── Leaflet control (top-right│
│  │ ☑ Districts      │      below tile switcher)      │
│  │ ☐ Agro Zones     │                                │
│  │ ☐ Councils       │                                │
│  └──────────────────┘                                │
│                                                      │
│              [Map area with polygon]                  │
│                                                      │
│  ┌─────────────────────────────────────────────────┐ │
│  │ Draw Mode | Walk Mode                           │ │
│  └─────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────┘
```

**Behaviour**:
- Layers with `showByDefault: true` are loaded on map init
- Other layers load on first toggle (lazy loading)
- Loaded layers are cached in memory for the session
- Layer GeoJSON is fetched once and reused (no re-fetching on toggle)

### 4.3 Boundary Layer Styling

Default styles for boundary layers (overridable via configuration):

| Layer Type | Stroke Color | Stroke Width | Dash Pattern | Fill | Fill Opacity | Label |
|------------|-------------|-------------|-------------|------|-------------|-------|
| District | `#666666` | 2 | `8,4` | `#666666` | 0.03 | District name |
| Agro Zone | `#228B22` | 2 | `5,5` | `#228B22` | 0.05 | Zone name |
| Council | `#8B4513` | 1 | `3,6` | none | 0 | Council name |

**Interaction**:
- Hover: Highlight boundary stroke (increase opacity to 0.8, width +1)
- Click: Show tooltip with feature name and properties
- No drag, no edit, no selection — strictly read-only

### 4.4 Plugin Properties Addition

Add a new property group to `GisPolygonCaptureElement.json`:

```json
[
    {
        "title": "Boundary Layers",
        "properties": [
            {
                "name": "enableBoundaryLayers",
                "label": "Enable Boundary Layers",
                "type": "checkbox",
                "value": ""
            },
            {
                "name": "boundaryLayers",
                "label": "Layers to Display",
                "description": "Configure which boundary layers to show on the map",
                "type": "grid",
                "columns": [
                    {
                        "key": "layerCode",
                        "label": "Layer Code",
                        "description": "Code from Boundary Layer management (e.g., LSO_DISTRICTS)"
                    },
                    {
                        "key": "displayName",
                        "label": "Display Name",
                        "description": "Label shown in layer control"
                    },
                    {
                        "key": "strokeColor",
                        "label": "Border Color",
                        "description": "Hex color (e.g., #666666)"
                    },
                    {
                        "key": "showByDefault",
                        "label": "Show by Default",
                        "type": "checkbox"
                    }
                ]
            },
            {
                "name": "boundaryLayerLabelField",
                "label": "Feature Label Property",
                "description": "GeoJSON property to use as label (e.g., 'name', 'district_name')",
                "type": "textfield",
                "value": "name"
            }
        ]
    }
]
```

### 4.5 Config JSON Extension

Add boundary layer config to `GisConfigBuilder.java`:

```java
// In GisConfigBuilder:
public GisConfigBuilder withBoundaryLayers(String layersJson, String labelField) {
    // Parse grid config into JSON array for frontend
    config.put("boundaryLayers", new JSONArray(layersJson));
    config.put("boundaryLayerLabelField", labelField);
    return this;
}
```

**Config JSON output example**:
```json
{
    "boundaryLayers": [
        {
            "layerCode": "LSO_DISTRICTS",
            "displayName": "Districts",
            "strokeColor": "#666666",
            "showByDefault": true
        },
        {
            "layerCode": "LSO_AGRO_ZONES",
            "displayName": "Agro-Ecological Zones",
            "strokeColor": "#228B22",
            "showByDefault": false
        }
    ],
    "boundaryLayerLabelField": "name"
}
```

---

## 5. DATA FLOW DIAGRAMS

### 5.1 Boundary Layer Upload Flow

```
Admin                    Joget Form              GisApiProvider           ShapefileService
  │                         │                         │                         │
  │  Upload ZIP file        │                         │                         │
  │────────────────────────►│                         │                         │
  │                         │  POST /gis/shapefile/   │                         │
  │                         │  import?layerCode=...   │                         │
  │                         │────────────────────────►│                         │
  │                         │                         │  validateBundle()       │
  │                         │                         │────────────────────────►│
  │                         │                         │  ◄── OK / errors        │
  │                         │                         │                         │
  │                         │                         │  convertToGeoJson()     │
  │                         │                         │────────────────────────►│
  │                         │                         │  ◄── GeoJSON + metadata │
  │                         │                         │                         │
  │                         │                         │  Write GeoJSON to file  │
  │                         │                         │  Create mdBoundaryLayer │
  │                         │                         │  record                 │
  │                         │                         │                         │
  │  ◄── Success + metadata │  ◄── Response           │                         │
  │                         │                         │                         │
```

### 5.2 Boundary Layer Display Flow

```
Browser (gis-capture.js)      GisApiProvider         BoundaryLayerService    Filesystem
  │                               │                         │                    │
  │  Map init / layer toggle      │                         │                    │
  │                               │                         │                    │
  │  GET /gis/layers/{code}       │                         │                    │
  │──────────────────────────────►│                         │                    │
  │                               │  getLayer(code)         │                    │
  │                               │────────────────────────►│                    │
  │                               │                         │  Read GeoJSON file │
  │                               │                         │───────────────────►│
  │                               │                         │  ◄── file content  │
  │                               │  ◄── GeoJSON            │                    │
  │  ◄── GeoJSON FeatureCollection│                         │                    │
  │                               │                         │                    │
  │  L.geoJSON(data, style)       │                         │                    │
  │  Add to map + layer control   │                         │                    │
  │                               │                         │                    │
```

### 5.3 Bulk Import Flow

```
User                     GisApiProvider         ShapefileService        FormDataDao
  │                           │                       │                     │
  │  POST /shapefile/import   │                       │                     │
  │  (file + mapping)         │                       │                     │
  │──────────────────────────►│                       │                     │
  │                           │  convertToGeoJson()   │                     │
  │                           │──────────────────────►│                     │
  │                           │  ◄── FeatureCollection│                     │
  │                           │                       │                     │
  │                           │  For each feature:                          │
  │                           │  ┌──────────────────────────────────┐       │
  │                           │  │ 1. Extract geometry → GeoJSON    │       │
  │                           │  │ 2. Map attributes per mapping    │       │
  │                           │  │ 3. Validate geometry (JTS)       │       │
  │                           │  │ 4. Calculate area/perimeter      │       │
  │                           │  │ 5. Create FormRow                │       │
  │                           │  │ 6. formDataDao.saveOrUpdate()    │──────►│
  │                           │  └──────────────────────────────────┘       │
  │                           │                                             │
  │  ◄── Import report        │                                             │
  │  (counts, errors)         │                                             │
```

---

## 6. PERFORMANCE CONSIDERATIONS

### 6.1 GeoJSON File Size

| Layer | Est. Features | Est. Raw Size | Simplified (0.0001°) | Notes |
|-------|--------------|--------------|----------------------|-------|
| Districts | 10 | 2-5 MB | 200-500 KB | Moderate detail |
| Agro Zones | 4 | 0.5-2 MB | 100-300 KB | Simpler boundaries |
| Councils | ~80 | 5-15 MB | 1-3 MB | Many features |

### 6.2 Optimization Strategy

| Technique | Where | Benefit |
|-----------|-------|---------|
| **Server-side simplification** | `/gis/layers/{code}?simplify=0.0001` | Reduce GeoJSON size by 60-80% |
| **HTTP caching** | `Cache-Control: max-age=3600` | Avoid repeated fetches |
| **Lazy loading** | Frontend: load on first toggle | Only fetch layers user needs |
| **Client-side caching** | `sessionStorage` or JS variable | Avoid re-fetches during session |
| **Bounding box filter** | `/gis/layers/{code}?bounds=...` | Only return features in viewport |
| **Streaming JSON** | Server: stream large files | Avoid loading entire file in memory |

### 6.3 Size Limits

| Constraint | Limit | Rationale |
|------------|-------|-----------|
| Upload file size | 50 MB | Prevent server memory exhaustion |
| Maximum features per shapefile | 50,000 | Practical GeoJSON rendering limit |
| GeoJSON response size | 10 MB | Browser memory constraint |
| Bulk import batch size | 1,000 | FormDataDao transaction limit |

---

## 7. SECURITY CONSIDERATIONS

### 7.1 File Upload Security

| Risk | Mitigation |
|------|------------|
| Malicious ZIP (zip bomb) | Check uncompressed size before extraction; limit to 200 MB uncompressed |
| Path traversal in ZIP entries | Validate all entry names; reject `..` and absolute paths |
| Non-shapefile content | Validate file extensions; only extract .shp/.dbf/.shx/.prj/.cpg |
| Oversized uploads | Enforce max upload size at servlet level (50 MB) |

### 7.2 Access Control

| Endpoint | Required Role | Rationale |
|----------|--------------|-----------|
| GET /gis/layers | Authenticated user | Read-only, used during form usage |
| GET /gis/layers/{code} | Authenticated user | Read-only, GeoJSON data |
| POST /gis/shapefile/import | Admin | Writes to filesystem and/or forms |
| POST /gis/shapefile/export | Admin | Bulk data access |

### 7.3 SQL Injection Prevention

Shapefile attribute values must be sanitized before use in Joget `FormDataDao.save()`. Apply the same `InputValidator` patterns already used in `OverlapService` and `NearbyParcelsService`.

---

## 8. IMPLEMENTATION PHASES

### Phase 1: Boundary Layer Display (Priority: HIGH)

**Goal**: Display pre-converted GeoJSON boundary layers on the polygon capture map.

**Scope**: No shapefile parsing yet — admin manually converts shapefiles to GeoJSON (using QGIS or `ogr2ogr`) and places files on the server. This phase validates the UI/UX before building the full pipeline.

| Task | Plugin | Effort | Description |
|------|--------|--------|-------------|
| 1.1 | gis-server | S | Create `BoundaryLayerService` — reads GeoJSON from filesystem |
| 1.2 | gis-server | S | Add `GET /gis/layers` endpoint |
| 1.3 | gis-server | S | Add `GET /gis/layers/{code}` endpoint with simplification + caching headers |
| 1.4 | gis-server | S | Create `mdBoundaryLayer` Joget form (manual data entry for now) |
| 1.5 | gis-ui | M | Add Boundary Layer Module to `gis-capture.js` |
| 1.6 | gis-ui | S | Add layer toggle control UI (Leaflet control) |
| 1.7 | gis-ui | S | Add plugin properties for boundary layer configuration |
| 1.8 | gis-ui | S | Add `withBoundaryLayers()` to `GisConfigBuilder.java` |
| 1.9 | gis-ui | S | Update `GisPolygonCaptureElement.java` — extract boundary layer properties |
| 1.10 | both | M | Integration testing with Lesotho district boundaries |

**Deliverable**: Field agents see district boundaries on the map during parcel registration.

**Estimated Duration**: 1-2 weeks

### Phase 2: Shapefile Upload & Conversion (Priority: MEDIUM)

**Goal**: Admin can upload shapefiles via Joget UI and system converts to GeoJSON automatically.

| Task | Plugin | Effort | Description |
|------|--------|--------|-------------|
| 2.1 | gis-server | L | Add GeoTools dependencies, test OSGi bundle packaging |
| 2.2 | gis-server | M | Implement `ShapefileService.validateShapefileBundle()` |
| 2.3 | gis-server | L | Implement `ShapefileService.convertToGeoJson()` with CRS reprojection |
| 2.4 | gis-server | M | Implement `ShapefileService.getShapefileMetadata()` |
| 2.5 | gis-server | M | Add `POST /gis/shapefile/import` endpoint (boundary layer mode) |
| 2.6 | gis-server | S | File upload security (ZIP validation, size limits, path traversal) |
| 2.7 | gis-server | M | Admin form: enhance `mdBoundaryLayer` with file upload workflow |
| 2.8 | gis-server | M | CRS testing with Hartebeeshoek94 and Cape datum samples |

**Deliverable**: Admin uploads shapefile ZIP → system auto-converts to boundary layer.

**Estimated Duration**: 2-3 weeks

### Phase 3: Bulk Import to Forms (Priority: MEDIUM-LOW)

**Goal**: Import shapefile features as records into Joget forms.

| Task | Plugin | Effort | Description |
|------|--------|--------|-------------|
| 3.1 | gis-server | M | Implement attribute mapping logic |
| 3.2 | gis-server | L | Implement bulk import with FormDataDao batch writes |
| 3.3 | gis-server | M | Add `POST /gis/shapefile/import` endpoint (form import mode) |
| 3.4 | gis-server | S | Implement dry-run mode |
| 3.5 | gis-server | M | Import report generation |
| 3.6 | gis-server | M | Admin UI: attribute mapping form |
| 3.7 | gis-server | L | Testing with real farm boundary shapefiles from MOA |

**Deliverable**: Data migration team can import legacy farm boundaries into Farmers' Registry.

**Estimated Duration**: 2-3 weeks

### Phase 4: Shapefile Export (Priority: LOW)

**Goal**: Export Joget form records as shapefiles for GIS tools.

| Task | Plugin | Effort | Description |
|------|--------|--------|-------------|
| 4.1 | gis-server | L | Implement `ShapefileService.exportToShapefile()` using GeoTools |
| 4.2 | gis-server | M | Add `POST /gis/shapefile/export` endpoint |
| 4.3 | gis-server | S | Generate .prj with WGS84 definition |
| 4.4 | gis-server | M | Admin UI or datalist action for triggering export |
| 4.5 | gis-server | M | Testing with QGIS and ArcGIS import |

**Deliverable**: GIS analysts can download farm boundaries as shapefiles for analysis.

**Estimated Duration**: 1-2 weeks

---

## 9. TESTING STRATEGY

### 9.1 Unit Tests

| Test Class | Coverage |
|------------|----------|
| `ShapefileServiceTest` | ZIP validation, GeoJSON conversion, CRS detection, reprojection |
| `BoundaryLayerServiceTest` | Layer CRUD, file read, caching |
| `GisApiProviderTest` | New endpoint request/response validation |

### 9.2 Integration Tests

| Scenario | Description |
|----------|-------------|
| District boundary display | Upload Lesotho district shapefile → display on map → verify 10 polygons |
| CRS reprojection | Upload Hartebeeshoek94 shapefile → verify coordinates are WGS84 |
| Large file handling | Upload 20 MB shapefile → verify no timeout, correct feature count |
| Bulk import | Import 100 farm boundaries → verify FormRow records created |
| Export round-trip | Export GeoJSON → import shapefile → compare geometries |

### 9.3 Test Data

| File | Source | Purpose |
|------|--------|---------|
| Lesotho district boundaries | GADM (gadm.org) or OCHA HDX | Phase 1 testing |
| Lesotho agro-ecological zones | MOA / FAO | Phase 1 testing |
| Sample Hartebeeshoek94 data | Generated | CRS reprojection testing |
| Synthetic farm parcels | Generated with QGIS | Bulk import testing |

### 9.4 Test Data Sources

Publicly available Lesotho boundaries can be obtained for development:

- **GADM**: https://gadm.org/download_country.html (Lesotho admin boundaries)
- **OCHA HDX**: https://data.humdata.org/ (search "Lesotho boundaries")
- **OpenStreetMap**: Extract via Overpass API or download from GeoFabrik

---

## 10. RISKS AND MITIGATIONS

| Risk | Severity | Probability | Mitigation |
|------|----------|-------------|------------|
| GeoTools dependency size bloats OSGi bundle | Medium | High | Consider separate plugin bundle; lazy-load EPSG database |
| OSGi class loading conflicts with GeoTools | High | Medium | Test thoroughly; use `DynamicImport-Package: *`; consider shade plugin |
| Unknown CRS in MOA shapefiles | Medium | Medium | Support manual CRS override; default to WGS84 with warning |
| Large shapefile causes browser memory issues | Medium | Medium | Server-side simplification; paginated loading; warn for >5 MB |
| Joget FormDataDao bulk write performance | Low | Medium | Batch writes in transactions of 100-500 records |
| Shapefile attribute encoding (non-UTF8) | Low | Medium | Read .cpg file for encoding; default to UTF-8 with ISO-8859-1 fallback |

---

## 11. ALTERNATIVES CONSIDERED

### 11.1 GeoServer Integration

**Approach**: Deploy GeoServer alongside Joget, serve WMS/WFS tiles.

**Pros**: Industry standard, handles huge datasets, built-in styling (SLD), mature caching (GeoWebCache).

**Cons**: Additional infrastructure to manage; separate authentication; overkill for <100 boundary features; deployment complexity.

**Decision**: Rejected for Phase 1. May revisit if data volumes exceed direct GeoJSON serving capabilities (>50,000 features or >50 MB).

### 11.2 Client-Side Shapefile Parsing

**Approach**: Use JavaScript libraries (shpjs, shapefile.js) to parse shapefiles in the browser.

**Pros**: No server changes; immediate processing; works offline.

**Cons**: No CRS reprojection in browser; limited file size handling; no server-side validation; security concerns with client-side file parsing.

**Decision**: Rejected. Server-side conversion provides CRS handling, validation, and a single source of truth.

### 11.3 Pre-Convert Only (No Runtime Upload)

**Approach**: All shapefiles manually converted to GeoJSON by developer/admin using QGIS.

**Pros**: Simplest implementation; no GeoTools dependency; minimal risk.

**Cons**: Requires GIS expertise for every update; no self-service for MOA staff.

**Decision**: **Adopted for Phase 1** as bootstrap approach. Phase 2 adds self-service upload.

---

## 12. GLOSSARY

| Term | Definition |
|------|-----------|
| **Shapefile** | ESRI format for geospatial vector data; actually a bundle of files (.shp, .dbf, .shx, .prj, .cpg) |
| **CRS** | Coordinate Reference System — defines how coordinates map to locations on Earth |
| **WGS84** | World Geodetic System 1984 (EPSG:4326) — the GPS coordinate system, used as standard in this project |
| **Hartebeeshoek94** | South African datum (EPSG:4148) — commonly used in Southern Africa |
| **GeoJSON** | Open standard format for representing geographic features using JSON |
| **GeoTools** | Java library for geospatial data (shapefile I/O, CRS, projections) |
| **JTS** | Java Topology Suite — geometry engine already used in joget-gis-server |
| **Simplification** | Douglas-Peucker algorithm to reduce vertex count while preserving shape |
| **GADM** | Database of Global Administrative Areas — source for boundary shapefiles |
| **Feature** | A single geographic entity (e.g., one district polygon with attributes) |
| **FeatureCollection** | GeoJSON container for multiple features |
