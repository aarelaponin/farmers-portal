# GIS Polygon Capture - Configuration Guide for Lesotho
**Form Builder Property Panel Reference**

This guide uses the exact labels and section names as they appear in the Joget Form Builder UI.

---

## Prerequisites

Before configuring this plugin, ensure:

1. **GIS API Plugin** (`joget-gis-api`) is installed on the Joget server
2. **API Endpoint** is registered in Joget API Builder
3. **Test data** exists in the `parcelLocation` form (for overlap checking)

### API Server Information

Based on `gis-openapi.yaml`, the GIS server provides these endpoints:

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/gis/gis/health` | GET | Health check |
| `/gis/gis/calculate` | POST | Calculate area, perimeter, centroid |
| `/gis/gis/validate` | POST | Validate geometry |
| `/gis/gis/checkOverlap` | POST | Check for overlapping parcels |
| `/gis/gis/nearbyParcels` | GET | Get nearby parcels for display |
| `/gis/gis/geocode` | GET | Search for locations |
| `/gis/gis/reverseGeocode` | GET | Get place name from coordinates |

**Authentication**: Uses `api-id` and `api-key` headers.

---

## Section: Output Field Mapping

Maps calculated values to hidden fields in your form.

| Label | Recommended Value | Description |
|-------|-------------------|-------------|
| **Area Field ID** | `area_hectares` | Hidden field to store calculated area |
| **Perimeter Field ID** | `perimeter_meters` | Hidden field to store perimeter |
| **Centroid Field ID** | `centroid_lat` | Hidden field to store center point |
| **Vertex Count Field ID** | `vertex_count` | Hidden field to store point count |

**Note**: Create matching Hidden Fields in your form with these exact IDs.

---

## Section: Capture Mode Settings

Controls how users can capture polygon boundaries.

| Label | Recommended Value | Description |
|-------|-------------------|-------------|
| **Capture Mode** | `Both (User Chooses Walk or Draw)` | Best for field testing |
| **Default Mode** | `Auto-detect (Mobile=Walk, Desktop=Draw)` | Automatically selects best mode |

**Options for Capture Mode**:
| Option | Use Case |
|--------|----------|
| `Both (User Chooses Walk or Draw)` | Recommended for testing |
| `Walk Mode Only (GPS)` | Mobile field workers only |
| `Draw Mode Only (Desktop)` | Office use only |
| `View Only (Read-only Display)` | Viewing existing polygons |

---

## Section: Map Settings

Controls the map display and initial view.

| Label | Recommended Value | Description |
|-------|-------------------|-------------|
| **Default Latitude** | `-29.5` | Center of Lesotho |
| **Default Longitude** | `28.5` | Center of Lesotho |
| **Default Zoom Level** | `14` | Good for parcel-level view |
| **Default Map Tiles** | `OpenStreetMap` | Works without API key |
| **Show Layer Switcher** | Checked | Allows switching to satellite |
| **Map Height (pixels)** | `500` | Good size for mobile |

### Lesotho District Centers

Use these coordinates for district-specific deployments:

| District | Latitude | Longitude |
|----------|----------|-----------|
| Maseru (Capital) | -29.31 | 27.48 |
| Leribe | -28.87 | 28.05 |
| Berea | -29.18 | 27.95 |
| Butha-Buthe | -28.77 | 28.25 |
| Mafeteng | -29.82 | 27.24 |
| Mohale's Hoek | -30.15 | 27.47 |
| Quthing | -30.40 | 27.70 |
| Qacha's Nek | -30.12 | 28.69 |
| Mokhotlong | -29.29 | 29.07 |
| Thaba-Tseka | -29.52 | 28.61 |

### Map Tiles Options

| Option | Description |
|--------|-------------|
| `OpenStreetMap` | Free, no API key needed |
| `Satellite (ESRI)` | Free satellite imagery |
| `Satellite (Google)` | Requires Google API key |
| `Hybrid` | Satellite with labels |
| `Terrain` | Topographic view |

---

## Section: Validation Rules

Controls polygon quality requirements.

| Label | Recommended Value | Description |
|-------|-------------------|-------------|
| **Minimum Area (hectares)** | `0.01` | 100 sq meters minimum |
| **Maximum Area (hectares)** | `1000` | Adjust to your max parcel size |
| **Minimum Vertices** | `3` | Triangle is minimum valid polygon |
| **Maximum Vertices** | `200` | Prevents overly complex shapes |
| **Allow Self-Intersecting Polygons** | Unchecked | Prevents invalid geometry |

---

## Section: Overlap Checking

Warns when captured polygon overlaps existing registered parcels.

| Label | Recommended Value | Description |
|-------|-------------------|-------------|
| **Enable Overlap Checking** | Checked | Essential for fraud prevention |
| **Overlap Check Form ID** | `parcelLocation` | Form containing existing parcels |
| **Geometry Field ID** | `parcelGeometry` | Field with GeoJSON in that form |
| **Display Fields** | `parcelCode` | Shows in overlap warning popup |
| **Filter Condition** | `status = 'REGISTERED'` | Only check registered parcels |

### API Endpoint Used

`POST /gis/gis/checkOverlap`

**Request body sent by plugin:**
```json
{
  "geometry": { /* GeoJSON Polygon */ },
  "target": {
    "formId": "parcelLocation",
    "geometryFieldId": "parcelGeometry",
    "filterCondition": "status = 'REGISTERED'"
  },
  "options": {
    "returnFields": ["parcelCode"],
    "minOverlapPercent": 1.0,
    "maxResults": 10,
    "includeOverlapGeometry": true
  }
}
```

---

## Section: Nearby Parcels Display

Shows existing parcels on map for context (fraud prevention).

| Label | Recommended Value | Description |
|-------|-------------------|-------------|
| **Show Nearby Parcels** | `Load on map initialization` | Shows parcels when map loads |
| **Parcels Form ID** | `parcelLocation` | Same form as overlap checking |
| **Parcels Geometry Field ID** | `parcelGeometry` | Field containing GeoJSON |
| **Display Fields** | `parcelCode,ownerName` | Comma-separated, shows in popup |
| **Filter Condition** | `status = 'REGISTERED'` | Only show registered parcels |
| **Fill Color** | `#808080` | Gray fill for existing parcels |
| **Fill Opacity** | `0.15` | Semi-transparent |
| **Stroke Color** | `#666666` | Dark gray border |
| **Maximum Parcels** | `100` | Limit for performance |

### Options for Show Nearby Parcels

| Option | Use Case |
|--------|----------|
| `Disabled` | Don't show existing parcels |
| `Load on map initialization` | Show immediately (recommended) |
| `Load on user request (button)` | User clicks button to load (slow networks) |

### API Endpoint Used

`GET /gis/gis/nearbyParcels?formId=...&geometryFieldId=...&bounds=...`

**Query parameters sent by plugin:**
- `formId`: Value from "Parcels Form ID"
- `geometryFieldId`: Value from "Parcels Geometry Field ID"
- `bounds`: Current map viewport (minLng,minLat,maxLng,maxLat)
- `filterCondition`: Value from "Filter Condition"
- `returnFields`: Value from "Display Fields"
- `maxResults`: Value from "Maximum Parcels"

---

## Section: GPS Settings (Walk Mode)

Controls GPS behavior for field capture on mobile devices.

| Label | Recommended Value | Description |
|-------|-------------------|-------------|
| **Use High Accuracy GPS** | Checked | Essential for accurate capture |
| **Minimum GPS Accuracy (meters)** | `15` | Warn if accuracy worse than this |
| **Auto-Close Distance (meters)** | `15` | Offer to close polygon when near start |

**For Lesotho mountain areas**: Consider increasing "Minimum GPS Accuracy" to `20` meters, as mountainous terrain affects GPS signals.

---

## Section: Appearance

Controls how the captured polygon looks on the map.

| Label | Recommended Value | Description |
|-------|-------------------|-------------|
| **Polygon Fill Color** | `#3388ff` | Blue fill (default) |
| **Polygon Fill Opacity (0-1)** | `0.2` | 20% opacity |
| **Polygon Stroke Color** | `#3388ff` | Blue border |
| **Polygon Stroke Width (pixels)** | `3` | Border thickness |

---

## Section: API Configuration

Required for overlap checking, nearby parcels, and location search features.

| Label | Recommended Value | Description |
|-------|-------------------|-------------|
| **API Base URL** | `/jw/api/gis/gis` | Path to GIS API endpoint |
| **API ID** | `[Your API ID]` | From Joget API Builder |
| **API Key** | `[Your API Key]` | Secret key from API Builder |

### How to Get API Credentials

1. Go to Joget Admin > Settings > API Builder
2. Find or create the GIS API
3. Copy the **API ID** and **API Key**

### Authentication Headers

The plugin sends these headers with every API request:
```
api-id: [Your API ID]
api-key: [Your API Key]
Content-Type: application/json
```

---

## Complete Configuration Summary

```
+-------------------------------------------------------------+
| OUTPUT FIELD MAPPING                                        |
+-------------------------------------------------------------+
| Area Field ID:           area_hectares                      |
| Perimeter Field ID:      perimeter_meters                   |
| Centroid Field ID:       centroid_lat                       |
| Vertex Count Field ID:   vertex_count                       |
+-------------------------------------------------------------+
| CAPTURE MODE SETTINGS                                       |
+-------------------------------------------------------------+
| Capture Mode:            Both (User Chooses Walk or Draw)   |
| Default Mode:            Auto-detect (Mobile=Walk...)       |
+-------------------------------------------------------------+
| MAP SETTINGS                                                |
+-------------------------------------------------------------+
| Default Latitude:        -29.5                              |
| Default Longitude:       28.5                               |
| Default Zoom Level:      14                                 |
| Default Map Tiles:       OpenStreetMap                      |
| Show Layer Switcher:     [x]                                |
| Map Height (pixels):     500                                |
+-------------------------------------------------------------+
| VALIDATION RULES                                            |
+-------------------------------------------------------------+
| Minimum Area (hectares): 0.01                               |
| Maximum Area (hectares): 1000                               |
| Minimum Vertices:        3                                  |
| Maximum Vertices:        200                                |
| Allow Self-Intersecting: [ ]                                |
+-------------------------------------------------------------+
| OVERLAP CHECKING                                            |
+-------------------------------------------------------------+
| Enable Overlap Checking: [x]                                |
| Overlap Check Form ID:   parcelLocation                     |
| Geometry Field ID:       parcelGeometry                     |
| Display Fields:          parcelCode                         |
| Filter Condition:        status = 'REGISTERED'              |
+-------------------------------------------------------------+
| NEARBY PARCELS DISPLAY                                      |
+-------------------------------------------------------------+
| Show Nearby Parcels:     Load on map initialization         |
| Parcels Form ID:         parcelLocation                     |
| Parcels Geometry Field:  parcelGeometry                     |
| Display Fields:          parcelCode                         |
| Filter Condition:        status = 'REGISTERED'              |
| Fill Color:              #808080                            |
| Fill Opacity:            0.15                               |
| Stroke Color:            #666666                            |
| Maximum Parcels:         100                                |
+-------------------------------------------------------------+
| GPS SETTINGS (WALK MODE)                                    |
+-------------------------------------------------------------+
| Use High Accuracy GPS:   [x]                                |
| Minimum GPS Accuracy:    15                                 |
| Auto-Close Distance:     15                                 |
+-------------------------------------------------------------+
| APPEARANCE                                                  |
+-------------------------------------------------------------+
| Polygon Fill Color:      #3388ff                            |
| Polygon Fill Opacity:    0.2                                |
| Polygon Stroke Color:    #3388ff                            |
| Polygon Stroke Width:    3                                  |
+-------------------------------------------------------------+
| API CONFIGURATION                                           |
+-------------------------------------------------------------+
| API Base URL:            /jw/api/gis/gis                    |
| API ID:                  [Your API ID]                      |
| API Key:                 [Your API Key]                     |
+-------------------------------------------------------------+
```

---

## Required Hidden Fields in Form

Add these Hidden Field elements to your form:

1. `area_hectares` - Stores calculated area
2. `perimeter_meters` - Stores calculated perimeter
3. `centroid_lat` - Stores centroid as GeoJSON Point
4. `vertex_count` - Stores number of polygon vertices
5. `parent_id` - For form relationships (optional)

---

## Troubleshooting

| Issue | Possible Cause | Solution |
|-------|---------------|----------|
| Map doesn't load | Tile server blocked | Check browser console, try different tile provider |
| GPS not working | Not using HTTPS | Geolocation API requires HTTPS |
| "Overlap check failed" | API authentication | Verify API ID and API Key |
| Poor GPS accuracy | Mountain terrain | Increase "Minimum GPS Accuracy" to 20m |
| Nearby parcels don't show | Wrong form/field ID | Verify form exists and has geometry data |
| No search results | Geocoding rate limit | Wait and retry, or check API health |

### Testing API Connection

Open browser console and check for:
- `401` or `403` errors = Invalid API credentials
- `404` errors = Wrong API Base URL
- Network errors = Server not reachable

### Health Check

Test API availability:
```
GET /jw/api/gis/gis/health
Headers:
  api-id: [Your API ID]
  api-key: [Your API Key]
```

Expected response:
```json
{
  "status": "healthy",
  "version": "9.0.4"
}
```

---

## Field Testing Checklist

### Before Going to Field
- [ ] Plugin installed and form configured
- [ ] API credentials working (test health endpoint)
- [ ] Test data in `parcelLocation` form
- [ ] Mobile device has GPS enabled
- [ ] Site uses HTTPS (required for GPS)

### In the Field
- [ ] Test Walk Mode - walk around a small area
- [ ] Verify GPS accuracy indicator shows
- [ ] Test auto-close when returning to start
- [ ] Check overlap warning appears (if overlapping existing parcel)
- [ ] Verify form submits successfully

### After Testing
- [ ] Review captured geometry in database
- [ ] Verify calculated metrics (area, perimeter)
- [ ] Check centroid coordinates are correct

---

*Last updated: January 2026*
*Plugin version: joget-gis-ui 8.1-SNAPSHOT*
*API version: parcelGisServer 9.0.4*
