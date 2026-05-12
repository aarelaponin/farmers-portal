# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Joget GIS UI Plugin - A Joget DX form element plugin that provides a Leaflet.js-based map for capturing geographic polygon boundaries. Supports two capture modes:
- **Walk Mode**: GPS-based capture for field use (mobile devices)
- **Draw Mode**: Click-to-draw for office use (desktop)

The plugin outputs GeoJSON polygons with calculated metrics (area in hectares, perimeter in meters, centroid, vertex count).

## Build Commands

```bash
# Build the OSGi bundle
mvn clean package

# Install to local Maven repository
mvn clean install
```

The built bundle JAR will be in `target/joget-gis-ui-8.1-SNAPSHOT.jar`.

## Architecture

### OSGi Bundle Structure

This is a Joget plugin packaged as an OSGi bundle using maven-bundle-plugin:
- **Bundle Activator**: `global.govstack.gisui.Activator` - Registers plugin services
- **Packaging**: `bundle` (Felix maven-bundle-plugin)

### Key Components

1. **GisPolygonCaptureElement** (`element/GisPolygonCaptureElement.java`)
   - Joget form element implementing `Element` and `FormBuilderPaletteElement`
   - Renders to FreeMarker template, passing config JSON to JavaScript
   - Validates GeoJSON on form submission
   - Appears in "GovStack" category in Form Builder

2. **GisResourcesPlugin** (`element/GisResourcesPlugin.java`)
   - Implements `PluginWebSupport` for serving static files
   - Endpoint: `/jw/web/json/plugin/global.govstack.gisui.element.GisResourcesPlugin/service?file=<filename>`
   - Serves JS/CSS from `/static/` classpath resources

3. **Frontend** (`resources/static/`)
   - `gis-capture.js`: Main JavaScript component (Leaflet.js + Turf.js integration)
   - `gis-capture.css`: Component styles
   - Dependencies loaded from CDN: Leaflet 1.9.4, Turf.js 6.x

### Resource Structure

```
resources/
├── properties/
│   └── GisPolygonCaptureElement.json  # Plugin property definitions for Form Builder
├── static/
│   ├── gis-capture.js                  # JavaScript map component
│   └── gis-capture.css                 # Component styles
└── templates/
    └── GisPolygonCaptureElement.ftl    # FreeMarker template
```

### Data Flow

1. Form element renders FreeMarker template with config JSON
2. Template loads Leaflet, Turf.js, then gis-capture.js
3. `GISCapture.init()` creates map instance with configuration
4. User captures polygon via GPS or click-to-draw
5. GeoJSON stored in hidden textarea field for form submission
6. Output fields (area, perimeter, centroid) populated automatically

## Joget Integration Notes

- Requires `wflow-core` 8.1-SNAPSHOT (provided scope)
- Plugin properties defined in JSON format (not XML)
- Form element template uses `FormUtil.generateElementHtml()` for rendering
- Backend GIS API services are in a separate plugin (`joget-gis-api`)

## Critical Implementation Notes

### Self-Overlap Filtering (IMPORTANT - DO NOT BREAK)

When editing an existing parcel, the overlap detection API returns the original parcel as an "overlap" (because the modified geometry intersects with the stored geometry). The client-side code filters these out using **five strategies**:

1. **Strategy 1 (Shrunk)**: Polygon area decreased, 95%+ overlap → filter out
2. **Strategy 2 (Same-size)**: 99%+ overlap, area ≈ current area → filter out
3. **Strategy 3 (Expanded)**: Area increased, `turf.booleanContains()` confirms expanded polygon contains original → filter out
4. **Strategy 4 (Shifted)**: Overlap geometry is 95%+ contained within initial geometry → filter out (catches cases where polygon is moved/reshaped)
5. **Strategy 5 (Fallback)**: Overlap area ≈ initial area within 15% → filter out

**Location**: `gis-capture.js` lines 3017-3160

**Full documentation**: `docs/self-overlap-filtering.md`

**Before modifying overlap-related code**, read the documentation and test ALL scenarios:
- Shrink (drag inward)
- Same-size (minor adjustments)
- Expand (drag outward)
- **Shift** (expand one side, shrink another - this was a bug case)
- Legitimate overlap with different parcel (must still show warning)

### Self-Intersection Detection

Uses dual-algorithm approach for robustness:
1. Primary: `turf.kinks()` (fast, handles most cases)
2. Fallback: `sweepline-intersections` library (Bentley-Ottmann algorithm)
3. Final fallback: Manual O(n²) edge intersection check

**Documentation**: `docs/polygon-self-intersection-research.md`
