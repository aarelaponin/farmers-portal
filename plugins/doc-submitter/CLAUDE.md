# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Joget DX8 plugin project for GovStack Registration Building Block. The plugin (`DocSubmitter`) is designed to process and send documents within Joget workflow processes. It's built as an OSGi bundle that integrates with the Joget platform.

## Build Commands

### Build the plugin
```bash
mvn clean package
```
This creates an OSGi bundle JAR in `target/doc-submitter-8.1-SNAPSHOT.jar`

### Compile only
```bash
mvn clean compile
```

### Run tests
```bash
mvn test
```

### Deploy to local repository
```bash
mvn clean install
```

## Architecture

### Plugin Structure
The project follows Joget's plugin architecture pattern:

1. **Bundle Activator** (`global.govstack.farmreg.Activator`): Registers the plugin with OSGi when the bundle starts. All plugins must be registered here for Joget to recognize them.

2. **Main Plugin Class** (`DocSubmitter`): Extends `DefaultApplicationPlugin` and implements the core business logic. Key methods:
   - `execute()`: Entry point when plugin runs in a workflow
   - `getPropertyOptions()`: Returns JSON configuration schema from `/properties/DocSubmitter.json`
   - Plugin retrieves workflow context via `WorkflowAssignment`

3. **Response Model** (`PluginResponse`): Standardized response wrapper for plugin operations with success/error states and status codes.

4. **Property Configuration**: JSON file in `src/main/resources/properties/DocSubmitter.json` defines the plugin's configurable parameters shown in Joget's UI. Current configuration includes processing modes, pairing settings, and data source configurations.

### OSGi Bundle Configuration
The Maven Bundle Plugin in `pom.xml` handles OSGi packaging:
- Bundle-Activator: `global.govstack.farmreg.Activator`
- Dynamic imports allow runtime dependency resolution
- Dependencies embedded in the bundle to avoid classpath issues

### Integration Points
- **Workflow Manager**: Accesses workflow context and process variables
- **Form Data**: Can read/write form data through Joget's DAO layer
- **Application Context**: Uses Spring context via `AppUtil.getApplicationContext()`

## Key Dependencies
- **Joget Core** (`wflow-core:8.1-SNAPSHOT`): Provided dependency, not bundled
- **JSON Processing**: Gson and Jackson libraries for data serialization
- **JUnit**: For unit testing

## Important Notes
1. The plugin currently appears to be a template - the `performWork()` method returns success without actual implementation
2. Configuration in `DocSubmitter.json` references transaction pairing functionality but implementation focuses on document sending
3. Authentication warnings from Joget repositories (401 errors) don't affect local builds if dependencies are cached
4. Plugin uses Joget's logging system via `LogUtil` for debugging
5. All plugin classes must be in packages that match the Bundle-Activator's package hierarchy