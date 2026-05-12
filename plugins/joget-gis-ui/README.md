# Joget GIS UI Plugin

A Joget DX8 form element plugin that provides a Leaflet.js-based interactive map for capturing geographic polygon boundaries. Designed for land registration, farm boundary mapping, and any polygon-based geographic data capture.

## Features

### Capture Modes

- **Draw Mode** (Desktop): Click-to-draw polygon capture optimized for office use with satellite imagery
- **Walk Mode** (Mobile): GPS-based capture for field work - walk the boundary while marking corners

### Core Functionality

- Interactive Leaflet.js map with multiple tile layers (Street, Satellite, Hybrid)
- Real-time polygon metrics: area (hectares), perimeter (meters), centroid, vertex count
- GeoJSON output compatible with PostGIS and standard GIS tools
- Automatic output to configurable form fields

### Edit Mode

- Drag vertices to adjust polygon shape
- Drag midpoint handles to add new vertices
- Click vertex + Delete key to remove vertices
- Undo/redo support

### Validation

- Real-time self-intersection detection with visual highlighting
- Configurable min/max area warnings
- Vertex count limits with 90% threshold warnings
- Optional overlap checking against existing records via backend API

### Accessibility (WCAG 2.1)

- Full keyboard navigation (Tab, Enter, Escape, Arrow keys, +/-)
- Ctrl+Z/Cmd+Z undo support
- ARIA labels on all interactive elements
- Screen reader announcements for actions
- Location search with keyboard-navigable results

### Walk Mode Features

- GPS accuracy indicator with Excellent/Good/Fair/Poor status
- Visual accuracy bar with real-time updates
- GPS average tracking displayed in completion summary
- Auto-close detection when returning to start point

## Requirements

- Joget DX8 Enterprise Edition (8.1+)
- Backend GIS API service (joget-gis-server) for:
  - Overlap checking
  - Location search (geocoding)

## Installation

1. Build the plugin:
   ```bash
   mvn clean package
   ```

2. Deploy `target/joget-gis-ui-8.1-SNAPSHOT.jar` to Joget:
   - Navigate to **Settings > Manage Plugins**
   - Upload the JAR file
   - The plugin appears in the "GovStack" category in Form Builder

## Configuration

Add the "GIS Polygon Capture" element to your form and configure:

### Basic Settings

| Property | Description |
|----------|-------------|
| Label | Field label displayed above the map |
| Field ID | Form field ID for storing GeoJSON geometry |

### Output Field Mapping

| Property | Description |
|----------|-------------|
| Area Field ID | Field for calculated area (hectares) |
| Perimeter Field ID | Field for calculated perimeter (meters) |
| Centroid Field ID | Field for centroid point (GeoJSON) |
| Vertex Count Field ID | Field for number of vertices |

### Map Settings

| Property | Default | Description |
|----------|---------|-------------|
| Default Latitude | 0 | Initial map center latitude |
| Default Longitude | 0 | Initial map center longitude |
| Default Zoom | 10 | Initial zoom level |
| Map Height | 400 | Map height in pixels |

### Validation

| Property | Default | Description |
|----------|---------|-------------|
| Min Area (ha) | 0 | Minimum area warning threshold |
| Max Area (ha) | - | Maximum area error threshold |
| Max Vertices | 100 | Maximum allowed vertices |
| Allow Self-Intersection | No | Whether to allow self-intersecting polygons |

### Overlap Checking

| Property | Description |
|----------|-------------|
| Enable Overlap Check | Enable checking against existing records |
| API Base URL | Backend GIS API URL |
| Overlap Endpoint | API endpoint for overlap checking |
| Current Record ID Field | Field containing current record ID (to exclude from overlap check) |

## Output Format

The plugin outputs a GeoJSON Polygon:

```json
{
  "type": "Polygon",
  "coordinates": [
    [
      [28.123456, -29.123456],
      [28.234567, -29.234567],
      [28.345678, -29.345678],
      [28.123456, -29.123456]
    ]
  ]
}
```

## Development

### Project Structure

```
src/main/
├── java/global/govstack/gisui/
│   ├── Activator.java              # OSGi bundle activator
│   └── element/
│       ├── GisPolygonCaptureElement.java  # Main form element
│       └── GisResourcesPlugin.java        # Static file server
└── resources/
    ├── properties/
    │   └── GisPolygonCaptureElement.json  # Plugin properties
    ├── static/
    │   ├── gis-capture.js          # Frontend JavaScript
    │   └── gis-capture.css         # Component styles
    └── templates/
        └── GisPolygonCaptureElement.ftl   # FreeMarker template
```

### Build Commands

```bash
# Build OSGi bundle
mvn clean package

# Install to local Maven repository
mvn clean install
```

### Dependencies

- Leaflet.js 1.9.4 (loaded from CDN)
- Turf.js 6.x (loaded from CDN)
- Joget wflow-core 8.1-SNAPSHOT (provided)

## Documentation

- [UI/UX Specification](docs/07-gis-ui-ux-spec.md) - Complete UI/UX design specification
- [Backend API Specification](docs/08-gis-backend-spec.md) - Backend service API documentation
- [Implementation Plan](docs/09-implementation-plan.md) - Development phases and tasks

## License

This project is part of the GovStack initiative.

## Related Projects

- [joget-gis-server](https://github.com/aarelaponin/joget-gis-server) - Backend GIS API services
