# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the project
mvn clean package

# Run tests
mvn test

# Run integration tests
mvn integration-test

# Install to local repository
mvn install

# Skip tests during build
mvn clean package -DskipTests=true
```

## Project Architecture

This is a Joget OSGi plugin for GovStack farmer registration processing. The codebase implements a workflow-based API for processing farmer applications.

### Core Components

**Bundle Activator**: `global.govstack.Activator` - OSGi bundle entry point that registers service providers

**Base Service Provider**: `BaseServiceProvider` - Abstract base class providing generic request/response handling, error management, and user context management for all API plugins

**Main API Endpoint**: `RegistrationServiceProvider` - Extends `BaseServiceProvider` and serves as the main REST API interface for the farmer registration system

**Service Layer Architecture**:
- `ApiRequestProcessor` - Generic interface for request processing
- `RegistrationService` / `RegistrationServiceFactory` - Handles farmer registration logic
- `FormSubmissionManager` - Manages form submissions to Joget workflow
- `WorkflowService` - Interfaces with Joget's workflow engine
- `ConfigurationService` - Manages server configuration loading
- `FormDataProcessor` - Processes form data before submission

**Configuration**: Server configuration is stored in `src/main/resources/server-config.json` which defines:
- Form ID and process definition
- User roles (admin, farmer, chief reviewer)
- Activity IDs for submission and review
- Field definitions with types and mandatory flags

### Exception Hierarchy

Custom exceptions in `org.joget.govstack.processing.exception`:
- `ApiProcessingException` - Base exception
- `ConfigurationException` - Configuration errors
- `FormSubmissionException` - Form submission failures
- `RegistrationException` - Registration process errors
- `ValidationException` - Data validation errors
- `WorkflowProcessingException` - Workflow execution errors
- `InvalidRequestException` - Invalid API requests

### Plugin Integration

This plugin integrates with Joget's platform APIs:
- Uses Joget's workflow engine for process automation
- Leverages form builder for data collection
- Integrates with user management for role-based access
- Requires `wflow-core` and `apibuilder_api` as provided dependencies

## Development Notes

- Java 8+ required (configured in pom.xml)
- Uses Maven Bundle Plugin for OSGi packaging
- All Joget dependencies are marked as `provided` scope
- External dependencies (Gson, Jackson) are embedded in the bundle