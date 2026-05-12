# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Joget Smart Farmer Search Plugin - an OSGi bundle for Joget DX8/DX9 that provides a smart farmer lookup form element with fuzzy matching, confidence estimation, and progressive criteria building.

## Build Commands

```bash
# Build the plugin JAR
mvn clean package

# Output JAR location
target/joget-smart-search-8.1-SNAPSHOT.jar
```

**Note:** The project uses a local system path for `apibuilder_api` dependency (`${user.home}/IdeaProjects/joget/api-builder/apibuilder_api/target/apibuilder_api-8.1-SNAPSHOT.jar`). Ensure this JAR exists before building.

## Architecture

### OSGi Bundle Structure

The plugin registers three OSGi services via `Activator.java`:

1. **SmartSearchElement** - Joget form element that renders the search UI
2. **SmartSearchResources** - Static file server for CSS/JS assets
3. **SmartSearchApiPlugin** - REST API endpoints using Joget API Builder

### Package Layout

```
global.govstack.smartsearch/
├── Activator.java           # OSGi bundle activator
├── api/
│   └── SmartSearchApiPlugin # REST API (extends ApiPluginAbstract)
├── element/
│   ├── SmartSearchElement   # Form element (extends Element)
│   └── SmartSearchResources # Static file serving (PluginWebSupport)
└── service/
    ├── FarmerSearchService  # Core search logic (singleton)
    ├── FuzzyMatchService    # Levenshtein/Soundex matching (singleton)
    └── StatisticsService    # Stats caching for confidence
```

### Key Design Principles

- **Database for indexing only** - All search logic, scoring, and fuzzy matching happens in Java (FarmerSearchService, FuzzyMatchService)
- **View-based search** - Queries run against `v_farmer_search` view, not a separate index table
- **Application-layer scoring** - Raw DB results (max 50) are scored and ranked in Java, returning top 20
- **Client-side confidence** - Statistics are cached client-side for offline confidence estimation

### Search Algorithm Flow

1. Check exact match fields (nationalId, phone) → instant 100% score result
2. Build parameterized SQL with filters (district, village, cooperative, etc.)
3. Add fuzzy name matching via LIKE, pg_trgm `similarity()`, and Soundex
   - pg_trgm compares against first_name and last_name separately (threshold > 0.3)
   - Requires `pg_trgm` PostgreSQL extension
4. Execute query against `v_farmer_search` view
5. Score results in application layer using `FuzzyMatchService`:
   - Base: 50 points
   - Exact name match: +50
   - Prefix match: +20
   - Soundex match: +15
   - Levenshtein penalty: -5 per edit distance
   - Village match: +10, District match: +5

### Frontend Stack

- **FreeMarker template**: `SmartSearchElement.ftl` - renders form element and loads JS
- **JavaScript modules** (loaded in order):
  1. `confidence-engine.js` - Client-side confidence estimation
  2. `recent-farmers.js` - localStorage-based recent selections
  3. `smart-search.js` - Main UI component (FarmerSmartSearch global)
- **CSS**: `smart-search.css` - Component styles

### API Endpoints

All endpoints under `/jw/api/fss/fss/`:

| Method | Path | Description |
|--------|------|-------------|
| POST | /search | Main criteria-based search |
| GET | /lookup/{id} | Single farmer by index ID |
| GET | /search/byNationalId/{id} | Exact national ID match |
| GET | /search/byPhone/{phone} | Exact phone match |
| GET | /villages | Village autocomplete |
| GET | /community-councils | Community council autocomplete |
| GET | /cooperatives | Cooperative autocomplete |
| GET | /statistics | Stats for confidence calculation |

### Database Schema

The plugin expects:
- `v_farmer_search` view (or `app_fd_farmer_search_index` table) with columns:
  - `id`, `c_national_id`, `c_phone_normalized`, `c_phone_display`
  - `c_first_name`, `c_last_name`, `c_gender`, `c_date_of_birth`
  - `c_district_code`, `c_district_name`, `c_village`, `c_community_council`
  - `c_cooperative_name`, `c_search_name`, `c_name_soundex`, `c_source_record_id`
- `app_fd_farmer_search_stats` table for cached statistics

See `database/schema.sql` for full DDL.

## Plugin Configuration

The form element has configurable properties defined in `properties/SmartSearchElement.json`:
- `storeValue`: Which value to save (nationalId/id/phone)
- `nationalIdPattern`/`phonePattern`: Regex for input type detection
- `autoSelectSingleResult`: Auto-select on exact match
- `apiEndpoint`, `apiId`, `apiKey`: API Builder credentials
