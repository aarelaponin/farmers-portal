# Self-Overlap Filtering in Edit Mode

## Overview

When editing an existing parcel, the overlap detection API returns the original parcel as an "overlap" because the modified geometry intersects with the stored geometry in the database. This creates **false positive warnings** that confuse users.

This document describes the client-side filtering logic that removes these self-overlaps while preserving legitimate overlap warnings for other parcels.

> **IMPORTANT**: This logic is critical for a good user experience. Any changes to the overlap filtering code in `gis-capture.js` should be tested against ALL scenarios described below.

---

## The Problem

### Scenario: User edits their own parcel

1. User opens form to edit parcel `ABC-123` (stored area: 74 ha)
2. User drags a vertex to adjust the boundary
3. System calls `POST /gis/checkOverlap` with the new geometry
4. Server finds overlap with parcel `ABC-123` (the same parcel being edited!)
5. **Without filtering**: User sees "Overlap Detected" warning for their own parcel

### Why Server-Side Exclusion Isn't Enough

The API supports `excludeRecordId` parameter, but it may not always work because:
- The same parcel geometry might exist under multiple record IDs (versioning/audit history)
- Data migration issues could create duplicate geometries
- The API exclusion might fail silently

Therefore, we implement **robust client-side filtering** as a safety net.

---

## The Solution: Five-Strategy Filter

Located in `gis-capture.js` lines **3017-3160**, inside the overlap response handler.

The filter checks each overlap against 5 strategies **in order**. If ANY strategy matches, the overlap is filtered out (considered self-overlap).

### Filter Flow Diagram

```
For each overlap returned by API:
│
├─▶ Strategy 1: SHRUNK? (overlap% >= 95% AND initialArea > currentArea)
│   └─▶ YES → Filter out
│
├─▶ Strategy 2: SAME-SIZE? (overlap% >= 99% AND overlapArea ≈ currentArea)
│   └─▶ YES → Filter out
│
├─▶ Strategy 3: EXPANDED? (currentArea > initialArea AND contains(current, initial))
│   └─▶ YES → Filter out
│
├─▶ Strategy 4: SHIFTED? (overlap geometry ≈ initial geometry)
│   ├─▶ Condition A: 85%+ mutual coverage → Filter out
│   └─▶ Condition B: 95%+ of overlap inside initial → Filter out
│
├─▶ Strategy 5: FALLBACK? (overlapArea ≈ initialArea within 15%)
│   └─▶ YES → Filter out
│
└─▶ None matched → KEEP this overlap (show warning to user)
```

---

## Strategy Details

### Strategy 1: Shrunk Polygon Detection

**Location**: Lines 3035-3042

**Condition**: `overlapPercentage >= 95% AND initialArea > currentArea`

**Scenario**: User shrinks the parcel by dragging vertices inward. The new smaller polygon fits entirely inside the original stored geometry.

```
Original (74 ha):  ┌─────────────┐
                   │             │
                   └─────────────┘

New (60 ha):       ┌─────────┐
                   │         │
                   └─────────┘
                   (entirely inside original)

API returns: overlap = 100% (entire new polygon overlaps with stored)
```

**Code**:
```javascript
if (overlap.overlapPercentage >= 95.0 && initialArea && initialArea > currentArea) {
    console.log('[GIS] Polygon was shrunk... filtering as self-overlap');
    return false; // Filter out
}
```

**Console output**: `[GIS] Polygon was shrunk (initial=74.0778 > current=60.0000) and overlap is 100.0% - filtering as self-overlap`

---

### Strategy 2: Same-Size Polygon Detection

**Location**: Lines 3044-3052

**Condition**: `overlapPercentage >= 99% AND |overlapArea - currentArea| < 2%`

**Scenario**: User makes minor adjustments without significantly changing the area. The overlap area equals the current polygon area.

```
Original (74 ha):  ┌─────────────┐
                   │             │
                   └─────────────┘

New (74 ha):         ┌─────────────┐
                     │             │
                     └─────────────┘
                   (shifted slightly, same area)

API returns: overlap ≈ 99%, overlapArea ≈ currentArea
```

**Code**:
```javascript
if (overlap.overlapPercentage >= 99.0) {
    var areaDiff = Math.abs(overlap.overlapArea - currentArea);
    var areaThreshold = currentArea * 0.02; // 2% tolerance
    if (areaDiff <= areaThreshold) {
        console.log('[GIS] Overlap area ≈ current area - filtering as self-overlap');
        return false; // Filter out
    }
}
```

**Console output**: `[GIS] Overlap area ≈ current area - filtering as self-overlap`

---

### Strategy 3: Expanded Polygon Detection (Spatial Containment)

**Location**: Lines 3054-3084

**Condition**: `currentArea > initialArea AND turf.booleanContains(current, initial) AND |overlapArea - initialArea| < 10%`

**Scenario**: User expands the parcel by dragging vertices outward. The new larger polygon fully contains the original stored geometry.

```
Original (74 ha):  ┌─────────────┐
                   │             │
                   └─────────────┘

New (100 ha):      ┌───────────────────┐
                   │   ┌─────────┐     │
                   │   │ original│     │
                   │   └─────────┘     │
                   └───────────────────┘

API returns: overlap ≈ 74% (74/100), overlapArea ≈ 74 ha (the initial area)
```

**Code**:
```javascript
if (isEditMode && self.state.initialGeometry && currentArea > initialArea) {
    var currentGeojson = self._toGeoJSON();
    var initialGeojson = self.state.initialGeometry;

    if (turf.booleanContains(currentGeojson, initialGeojson)) {
        var areaDiffFromInitial = Math.abs(overlap.overlapArea - initialArea);
        var initialThreshold = initialArea * 0.10; // 10% tolerance
        if (areaDiffFromInitial <= initialThreshold) {
            console.log('[GIS] Self-overlap filter: expanded polygon contains original geometry...');
            return false; // Filter out
        }
    }
}
```

**Console output**: `[GIS] Self-overlap filter: expanded polygon contains original geometry, overlap area (73.9121 ha) ≈ initial area (74.0778 ha), filtering out`

**Fallback**: If `turf.booleanContains()` throws an error, falls back to 5% area tolerance check.

---

### Strategy 4: Shifted Polygon Detection (Geometry Comparison)

**Location**: Lines 3086-3133

**Condition**: EITHER of:
1. `intersectionOfOverlap >= 85% AND intersectionOfInitial >= 85%` (mutual coverage)
2. `intersectionOfOverlap >= 95%` (overlap is subset of initial)

**Scenario**: User shifts/reshapes the parcel - expanding some sides while shrinking others. The result may be larger, smaller, or same size, but parts have moved outside the original boundary.

```
Original (74 ha):  ┌─────────────┐
                   │             │
                   └─────────────┘

New (52 ha):             ┌───────┐
                         │       │
                         └───────┘
                   (shifted right and shrunk)

Overlap geometry from API: the intersection area (~28 ha)
This 28 ha overlap is 100% contained within our initial 74 ha
```

**Why this is needed**: Strategies 1-3 fail when the polygon is shifted:
- Strategy 1 fails: overlap% is only 54% (28/52), not 95%+
- Strategy 2 fails: overlap% is only 54%, not 99%+
- Strategy 3 fails: currentArea (52) < initialArea (74), so it's not "expanded"

**Code**:
```javascript
if (isEditMode && self.state.initialGeometry && overlap.geometry) {
    var initialGeojson = self.state.initialGeometry;
    var overlapGeojson = overlap.geometry;

    var intersection = turf.intersect(
        turf.feature(overlapGeojson),
        turf.feature(initialGeojson)
    );

    if (intersection) {
        var intersectionArea = turf.area(intersection) / 10000;
        var overlapGeoArea = turf.area(overlapGeojson) / 10000;

        var intersectionOfOverlap = intersectionArea / overlapGeoArea;
        var intersectionOfInitial = intersectionArea / initialArea;

        // Condition 1: geometries are essentially the same
        if (intersectionOfOverlap >= 0.85 && intersectionOfInitial >= 0.85) {
            return false; // Filter out
        }
        // Condition 2: overlap is entirely within our initial (it's a subset of our parcel)
        if (intersectionOfOverlap >= 0.95) {
            return false; // Filter out
        }
    }
}
```

**Why Condition 2 works**: If 95%+ of the overlap geometry lies within our initial geometry, the overlap IS a subset of our parcel. A **different** parcel would have significant portions **outside** our initial boundary. This is the key insight that catches shifted polygons.

**Console output**:
```
[GIS] Strategy 4 check: intersection=28.3876 ha, overlapGeo=28.3876 ha, initial=74.8778 ha, coverage: 100.0% of overlap, 37.9% of initial
[GIS] Self-overlap filter: overlap geometry is 95%+ contained within initial geometry (overlap IS our parcel), filtering out
```

---

### Strategy 5: Area-Based Fallback

**Location**: Lines 3135-3144

**Condition**: `|overlapArea - initialArea| < 15%`

**Scenario**: Fallback when Strategy 4 can't run (e.g., no overlap geometry in API response).

**Code**:
```javascript
if (isEditMode && initialArea) {
    var areaDiffFromInitial = Math.abs(overlap.overlapArea - initialArea);
    var initialThreshold = initialArea * 0.15; // 15% tolerance
    if (areaDiffFromInitial <= initialThreshold) {
        console.log('[GIS] Self-overlap filter: overlap area ≈ initial area within 15%, filtering out');
        return false; // Filter out
    }
}
```

**Console output**: `[GIS] Self-overlap filter: overlap area (72.5000 ha) ≈ initial area (74.0778 ha) within 15%, filtering out`

**Warning**: This is the least precise strategy. It could theoretically filter out a legitimate overlap with a different parcel that happens to have similar area. Use with caution.

---

## State Requirements

For the filter to work, the following state must be captured when loading an existing parcel:

| State Variable | Description | Set In |
|---------------|-------------|--------|
| `state.initialGeometry` | Original GeoJSON Polygon from database | `_loadExistingValue()` line 4055/4058 |
| `state.initialAreaHectares` | Original area in hectares (via Turf.js) | `_loadExistingValue()` line 4066 |
| `options.recordId` | The record ID being edited | Passed from FreeMarker template |

**Code in `_loadExistingValue()` (lines 4052-4071)**:
```javascript
// Store initial geometry for self-overlap detection in edit mode
if (geojson.type === 'Polygon') {
    this.state.initialGeometry = geojson;
} else if (geojson.type === 'Feature' && geojson.geometry.type === 'Polygon') {
    this.state.initialGeometry = geojson.geometry;
}

if (this.state.initialGeometry) {
    // Calculate and store initial area using Turf.js
    var initialArea = turf.area(this.state.initialGeometry) / 10000; // m² to hectares
    this.state.initialAreaHectares = initialArea;
    console.log('[GIS] Stored initial geometry for self-overlap detection, area=' + initialArea.toFixed(4) + ' ha');
}
```

---

## Console Logging

The filter logs detailed information for debugging. Example full output:

```
[GIS] Self-overlap filter check:
[GIS]   isEditMode=true (recordId=eea39e76-016c-4a4a-8c54-3688f0be5029)
[GIS]   initialArea=74.8778 ha
[GIS]   currentArea=52.0000 ha
[GIS]   Polygon state: SHRUNK
[GIS] Checking overlap: id=078122ec-9575-4b87-a831-fd062bbf025, percentage=54.6%, overlapArea=28.3876 ha
[GIS] Strategy 4 check: intersection=28.3876 ha, overlapGeo=28.3876 ha, initial=74.8778 ha, coverage: 100.0% of overlap, 37.9% of initial
[GIS] Self-overlap filter: overlap geometry is 95%+ contained within initial geometry (overlap IS our parcel), filtering out
[GIS] Self-overlap filter removed 1 overlap(s)
```

---

## Testing Checklist

When modifying overlap-related code, verify ALL scenarios:

### 1. Shrink Scenario
- [ ] Open existing parcel in edit mode
- [ ] Drag vertex **inward** to reduce area significantly (e.g., 74 ha → 50 ha)
- [ ] Verify NO "Overlap Detected" warning for same parcel
- [ ] Console shows: `Polygon was shrunk... filtering as self-overlap`

### 2. Same-Size Scenario
- [ ] Open existing parcel in edit mode
- [ ] Slightly adjust vertices without significant area change
- [ ] Verify NO "Overlap Detected" warning for same parcel
- [ ] Console shows: `Overlap area ≈ current area - filtering as self-overlap`

### 3. Expand Scenario
- [ ] Open existing parcel in edit mode
- [ ] Drag vertex **outward** to increase area (e.g., 74 ha → 100 ha)
- [ ] Verify NO "Overlap Detected" warning for same parcel
- [ ] Console shows: `expanded polygon contains original geometry... filtering out`

### 4. Shift Scenario (CRITICAL)
- [ ] Open existing parcel in edit mode
- [ ] Drag one vertex **outward** (expand one side)
- [ ] Drag another vertex **inward** (shrink another side)
- [ ] Net area may be smaller, same, or larger than original
- [ ] Make **multiple successive edits** (this was the bug case)
- [ ] Verify NO "Overlap Detected" warning for same parcel
- [ ] Console shows: `Strategy 4 check:` followed by `overlap geometry is 95%+ contained`

### 5. Legitimate Overlap (Control Test)
- [ ] Create or edit a parcel that genuinely overlaps a **different** parcel
- [ ] Verify "Overlap Detected" warning **IS** shown
- [ ] Verify the overlapping parcel details are correct
- [ ] Verify the overlap geometry shown on map is correct

---

## Tolerance Values Summary

| Strategy | Tolerance | Rationale |
|----------|-----------|-----------|
| Strategy 1 (Shrunk) | 95% overlap percentage | Allows for minor GPS/editing variance |
| Strategy 2 (Same-size) | 99% overlap, 2% area diff | Very tight - only exact matches |
| Strategy 3 (Expanded) | 10% area diff from initial | Wider tolerance since spatial containment check is robust |
| Strategy 3 (Fallback) | 5% area diff from initial | Tighter when spatial check fails |
| Strategy 4 (Shifted) | 85% mutual OR 95% containment | Geometry comparison is robust |
| Strategy 5 (Fallback) | 15% area diff from initial | Last resort, least precise |

---

## Edge Cases

### Multiple Overlaps
If the modified polygon overlaps with BOTH the original parcel AND a different parcel:
- Self-overlap with original parcel → filtered out by one of the strategies
- Overlap with different parcel → kept, warning shown to user

### Overlap Geometry Not Available
If the API doesn't return `overlap.geometry`:
- Strategy 4 is skipped
- Strategy 5 (area-based fallback) handles it

### Invalid Geometry
If `turf.booleanContains()` or `turf.intersect()` fails:
- Logged as warning
- Falls back to simpler area comparison
- Filter continues to next strategy

### Missing Initial State
If `initialGeometry` or `initialAreaHectares` not set:
- Filter logs: `Self-overlap filter skipped: not in edit mode` or `no current metrics`
- All overlaps are shown (safe default - better to show false positive than miss real overlap)

### Different Record IDs for Same Parcel
If the same parcel exists under multiple record IDs (e.g., versioning):
- `excludeRecordId` only excludes one ID
- Strategy 4 catches this by comparing **geometries**, not IDs

---

## Related Files

| File | Lines | Description |
|------|-------|-------------|
| `gis-capture.js` | 3017-3160 | Self-overlap filter implementation |
| `gis-capture.js` | 4036-4090 | `_loadExistingValue()` - sets initial state |
| `gis-capture.js` | 274-275 | State variable declarations |
| `GisPolygonCaptureElement.java` | ~224 | Passes `recordId` to template |
| `GisPolygonCaptureElement.ftl` | ~45 | Template passes recordId to JS |

---

## Debugging Tips

1. **Check if initial state is set**: Look for console log `[GIS] Stored initial geometry for self-overlap detection, area=XX.XXXX ha`

2. **Check polygon state**: The filter logs `Polygon state: EXPANDED/SHRUNK/SAME SIZE` to show which direction the area changed

3. **Check Strategy 4 output**: Look for `[GIS] Strategy 4 check:` - the coverage percentages tell you why it passed/failed

4. **If filter isn't working**: Verify `options.recordId` is being passed from template (check `isEditMode=true` in logs)

---

## History

| Date | Change | Reason |
|------|--------|--------|
| 2026-01-28 | Added Strategy 4 Condition 2 (95% containment) | Fix false positive when polygon is significantly shifted |
| 2026-01-28 | Added Strategy 4 (geometry comparison) & Strategy 5 (area fallback) | Fix false positive when polygon is shifted (expanded + shrunk) |
| 2026-01-28 | Added Strategy 3 (spatial containment) | Fix false positive when expanding polygons |
| 2026-01-XX | Added Strategy 1 & 2 | Initial self-overlap filtering for shrink/same-size |

---

## Architecture Notes

### Why Client-Side Filtering?

The server-side `excludeRecordId` parameter should theoretically prevent self-overlap detection. However, client-side filtering provides:

1. **Defense in depth**: Works even if server exclusion fails
2. **Handles duplicate records**: Same geometry under different IDs
3. **No server changes needed**: Fix can be deployed via plugin update
4. **Detailed logging**: Easier to debug issues

### Performance Considerations

- Strategies are checked in order; first match wins
- `turf.intersect()` in Strategy 4 is the most expensive operation
- For typical parcels (<100 vertices), performance is negligible
- Filter only runs when API returns overlaps (not on every edit)

### Future Improvements

Potential enhancements if needed:
1. Add server-side geometry comparison to `excludeRecordId`
2. Cache intersection results if same overlap checked multiple times
3. Add configurable tolerance values via plugin properties
