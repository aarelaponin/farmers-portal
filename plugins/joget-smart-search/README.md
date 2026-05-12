# Joget Smart Farmer Search Plugin

Smart Farmer Search form element with progressive criteria builder, fuzzy matching, and confidence estimation.

## Features

- **Single Text Box Search**: Auto-detects patterns (ID/Phone → instant result)
- **Progressive Criteria Builder**: Build search criteria with confidence indicator
- **Fuzzy Matching**: Trigram similarity via `pg_trgm` (finds "Tabo" → "Thabo"), Levenshtein scoring, optional Soundex
- **REST API**: Full API for search operations
- **Application-Level Logic**: All search logic in Java, database for indexing only
- **Configurable Input Patterns**: Customize ID/phone detection regex per deployment
- **Auto-Select Single Result**: Automatically select when exactly one high-confidence match found
- **Configurable Value Storage**: Choose to store national ID, internal ID, or phone
- **Recent Farmers**: Quick-access to recently selected farmers (localStorage)
- **Offline Support**: Statistics caching, works offline with recent farmers
- **Connection Status**: Visual indicator for online/offline state

## Project Structure

```
joget-smart-search/
├── pom.xml
├── src/main/java/global/govstack/smartsearch/
│   ├── Activator.java                    # OSGi bundle activator
│   ├── element/
│   │   ├── SmartSearchElement.java       # Form element
│   │   └── SmartSearchResources.java     # Static file server
│   ├── api/
│   │   └── SmartSearchApiPlugin.java     # REST API endpoints
│   └── service/
│       ├── FarmerSearchService.java      # Core search logic
│       └── FuzzyMatchService.java        # Fuzzy matching (Levenshtein/Soundex)
├── src/main/resources/
│   ├── properties/
│   │   ├── SmartSearchElement.json       # Form element config
│   │   └── SmartSearchApiPlugin.json     # API config
│   ├── sql/
│   │   ├── populate-index.sql            # Index population SQL
│   │   └── test-data.sql                 # Test data
│   ├── static/
│   │   ├── smart-search.css              # Styles
│   │   └── smart-search.js               # JavaScript
│   └── templates/
│       └── SmartSearchElement.ftl        # FreeMarker template
└── database/
    └── schema.sql                        # Database DDL (use smart-search-ddl.sql)
```

## Installation

### Prerequisites

1. Joget DX8/DX9 Enterprise Edition
2. API Builder plugin installed and configured
3. Database view created (see Database Setup below)

### Build

```bash
cd joget-smart-search
mvn clean package
```

### Deploy

1. Copy `target/joget-smart-search-8.1-SNAPSHOT.jar` to Joget's plugins folder
2. Restart Joget or upload via Plugin Manager

### Database Setup

Create the `v_farmer_search` view that the plugin queries. Adjust table names to match your Joget forms.

**PostgreSQL (Azure compatible):**
```sql
CREATE OR REPLACE VIEW v_farmer_search AS
SELECT
    bi.id,
    bi.c_national_id,
    REGEXP_REPLACE(bi.c_mobile_number, '[^0-9]', '', 'g') AS c_phone_normalized,
    bi.c_mobile_number AS c_phone_display,
    bi.c_first_name,
    bi.c_last_name,
    bi.c_gender,
    bi.c_date_of_birth,
    loc.c_district AS c_district_code,
    d.c_name AS c_district_name,
    loc.c_village,
    loc.c_communityCouncil AS c_community_council,
    bi.c_cooperative_name,
    LOWER(CONCAT_WS(' ', TRIM(bi.c_first_name), TRIM(bi.c_last_name))) AS c_search_name,
    NULL AS c_name_soundex,  -- Set to NULL if fuzzystrmatch not available
    fr.id AS c_source_record_id
FROM app_fd_farmerBasicInfo bi
INNER JOIN app_fd_farms_registry fr ON bi.c_parent_id = fr.id
LEFT JOIN app_fd_farm_location loc ON loc.c_parent_id = fr.id
LEFT JOIN app_fd_md03district d ON loc.c_district = d.c_code;
```

**Optional: Enable Soundex (requires fuzzystrmatch extension):**
```sql
CREATE EXTENSION IF NOT EXISTS fuzzystrmatch;
-- Then replace NULL AS c_name_soundex with:
-- CONCAT_WS(' ', soundex(bi.c_first_name), soundex(bi.c_last_name)) AS c_name_soundex
```

**Recommended: Enable pg_trgm for fuzzy name matching (finds "Tabo" when searching "Thabo"):**
```sql
-- Enable the extension (may require Azure portal allow-listing first)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Create index for faster fuzzy searches
CREATE INDEX idx_farmer_search_name_trgm
ON app_fd_farmerBasicInfo
USING gin((LOWER(c_first_name || ' ' || c_last_name)) gin_trgm_ops);
```

### API Builder Setup

1. Go to **Settings → API Builder** in Joget
2. Create a new API with:
   - **API ID**: `fss` (or any ID)
   - **Plugin**: Select "Smart Farmer Search API"
3. Note the API ID and Key for form element configuration

## API Endpoints

### POST /jw/api/fss/fss/search

Main search endpoint.

**Request:**
```json
{
  "criteria": {
    "nationalId": null,
    "phone": null,
    "name": "Mamosa",
    "districtCode": "BER",
    "village": "Ha Matala",
    "communityCouncil": null,
    "partialId": null,
    "partialPhone": null
  },
  "limit": 20
}
```

**Response:**
```json
{
  "success": true,
  "resultType": "CRITERIA_MATCH",
  "totalCount": 3,
  "farmers": [
    {
      "id": "TEST001",
      "nationalId": "1234567890123",
      "nationalIdMasked": "...0123",
      "firstName": "Mamosa",
      "lastName": "Motlomelo",
      "gender": "Female",
      "dateOfBirth": "1985-03-15",
      "phone": "+26622123456",
      "phoneMasked": "...3456",
      "districtCode": "BER",
      "districtName": "Berea",
      "village": "Ha Matala",
      "relevanceScore": 95
    }
  ],
  "searchTime": 45
}
```

### GET /jw/api/fss/fss/lookup/{id}

Get single farmer by index ID.

### GET /jw/api/fss/fss/villages?district={code}&q={query}

Villages autocomplete.

### GET /jw/api/fss/fss/search/byNationalId/{nationalId}

Exact match by national ID.

### GET /jw/api/fss/fss/search/byPhone/{phone}

Exact match by phone number.

## Testing

### cURL Examples

```bash
# Exact ID search
curl -X POST http://localhost:8080/jw/api/fss/fss/search \
  -H "Content-Type: application/json" \
  -H "api_id: YOUR_API_ID" \
  -H "api_key: YOUR_API_KEY" \
  -d '{"criteria":{"nationalId":"123456789"}}'

# Name search with district filter
curl -X POST http://localhost:8080/jw/api/fss/fss/search \
  -H "Content-Type: application/json" \
  -H "api_id: YOUR_API_ID" \
  -H "api_key: YOUR_API_KEY" \
  -d '{"criteria":{"name":"Mamosa","districtCode":"BER"}}'

# Fuzzy name search (will find "Mohapi" when searching "Mohape")
curl -X POST http://localhost:8080/jw/api/fss/fss/search \
  -H "Content-Type: application/json" \
  -H "api_id: YOUR_API_ID" \
  -H "api_key: YOUR_API_KEY" \
  -d '{"criteria":{"name":"Mohape"}}'

# Villages autocomplete
curl "http://localhost:8080/jw/api/fss/fss/villages?district=BER" \
  -H "api_id: YOUR_API_ID" \
  -H "api_key: YOUR_API_KEY"
```

## Implementation Phases

- [x] **Phase 1**: Project skeleton, form element, static resources
- [x] **Phase 2**: Core services (FarmerSearchService, FuzzyMatchService, REST API)
- [x] **Phase 3**: Full UI implementation (JavaScript search component)
- [x] **Phase 4**: Statistics service, confidence engine
- [x] **Phase 5**: Criteria builder, village autocomplete
- [x] **Phase 6**: Offline support, recent farmers, configurable patterns, auto-select

See `docs/PHASE*_IMPLEMENTATION.md` for detailed implementation notes.

## Plugin Configuration

The form element has several configuration sections:

### Basic Settings

| Property | Description | Default |
|----------|-------------|---------|
| Field ID | Form field identifier | `farmerId` |
| Value to Store | Which value to save (nationalId/id/phone) | `nationalId` |
| Label | Display label | `Select Farmer` |
| Required | Make selection mandatory | `false` |
| Display Mode | popup or inline | `popup` |

### Input Detection

| Property | Description | Default |
|----------|-------------|---------|
| National ID Pattern | Regex for ID detection | `^\d{9,13}$` |
| National ID Min Length | Min digits for ID | `4` |
| Phone Pattern | Regex for phone detection | `^\+?\d{8,}$` |
| Phone Min Length | Min digits for phone | `8` |

### Auto-Select Behavior

| Property | Description | Default |
|----------|-------------|---------|
| Auto-Select Single Result | Auto-select on exact match | `true` |
| Auto-Select Min Score | Minimum score for auto-select | `90` |
| Show Notification | Show toast on auto-select | `true` |

### API Configuration

| Property | Description | Default |
|----------|-------------|---------|
| API Base URL | Base URL for API | `/jw/api/fss` |
| API ID | API Builder authentication ID | (required) |
| API Key | API Builder authentication key | (required) |

## Architecture Principles

1. **Database**: Indexing only, no business logic
2. **Application**: All search logic, scoring, fuzzy matching in Java
3. **Client**: Confidence estimation, offline stats cache
4. **Configuration**: Everything domain-specific is parameterized

## Search Algorithm

1. Check for exact match fields (nationalId, phone) → instant result
2. Build parameterized SQL query with filters:
   - Name matching uses: LIKE, pg_trgm `similarity()` > 0.3, and Soundex (if available)
   - pg_trgm compares against first_name and last_name separately for better accuracy
3. Execute query, get raw results (up to 50)
4. Score and rank in application layer:
   - Base score: 50
   - Exact name match: +50
   - Levenshtein penalty: -5 per edit distance
   - Soundex match bonus: +15
   - Prefix match bonus: +20
   - Village match: +10
   - District match: +5
5. Return top 20 sorted by relevance

## License

Apache 2.0
