# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Joget DX8 API plugin for creating forms programmatically via REST API. It creates an OSGi bundle that integrates with the Joget platform to expose form creation capabilities through HTTP endpoints.

**Package:** `global.govstack.formcreator`
**Version:** 8.1-SNAPSHOT
**Repository:** https://github.com/aarelaponin/joget-form-creator-api

**Companion Tool:** [joget-form-generator](https://github.com/aarelaponin/joget-form-generator) - Python utility for generating form definition JSON files.

## Build Commands

```bash
# Build the plugin (produces target/form-creator-api-8.1-SNAPSHOT.jar)
mvn clean package

# Run tests
mvn test

# Build without tests
mvn clean package -DskipTests
```

## Deployment

Upload the built JAR through Joget's Manage Plugins interface (Settings > Manage Plugins) for hot deployment, or copy to `wflow/app_plugins/` and restart Joget.

## Architecture

This is a Joget API Plugin (extends `ApiPluginAbstract`) following the OSGi bundle pattern:

### Entry Points
- **`FormCreatorServiceProvider`** - API plugin handling HTTP requests at `/jw/api/formcreator/formcreator/forms`

### Services
- **`FormCreationService`** - Main orchestrator for form creation workflow
- **`FormDatabaseService`** - Direct database form registration with cache invalidation
- **`ApiBuilderService`** - Creates API endpoints via BuilderDefinitionDao
- **`CrudService`** - Coordinates datalist + userview creation
- **`DatalistService`** - Creates datalist definitions (file + database)
- **`UserviewService`** - Creates/updates userview definitions (file + database)
- **`JsonProcessingService`** - Generates JSON for forms, datalists, userviews, APIs
- **`FormCreatorBootstrapService`** - Self-bootstrapping capability

### Models
- `FormCreationRequest` / `FormCreationResponse` - Request/response DTOs
- `ApiCreationResult`, `CrudCreationResult`, `InternalFormCreationResult` - Result objects

### Utilities
- `RequestParserUtil` - JSON request parsing
- `MultipartRequestParser` - File upload handling
- `UserContextUtil` - System user context execution
- `ErrorResponseUtil` - Standardized error responses

### Request Flow
1. `FormCreatorServiceProvider.createForm()` receives HTTP POST
2. `UserContextUtil.executeAsSystemUser()` sets admin context
3. `RequestParserUtil` parses JSON or multipart request into `FormCreationRequest`
4. `FormCreationService.processFormCreationRequest()` validates and orchestrates:
   - Bootstrap check (ensures formCreator CRUD exists)
   - Form creation via `FormDatabaseService`
   - Optional API endpoint via `ApiBuilderService`
   - Optional CRUD interface via `CrudService`

## Key Implementation Notes

- Only `application/json` content type is supported (multipart/form-data fails due to Joget platform limitations)
- Uses Joget's `AppService`, `FormService`, `FormDataDao` beans
- Form definition is expected as a JSON object (not a string) in the request body
- Optional flags `createApiEndpoint` and `createCrud` trigger additional component creation
- All services use dual storage pattern (file system + database) for Joget compatibility

## Testing

```bash
# Run test script against local Joget
cd examples && ./test-api.sh

# Manual curl test
curl -X POST 'http://localhost:8080/jw/api/formcreator/formcreator/forms' \
  -H 'api-id: YOUR_API_ID' \
  -H 'api-key: YOUR_API_KEY' \
  -H 'Content-Type: application/json' \
  -d @examples/simple-form-request.json
```
