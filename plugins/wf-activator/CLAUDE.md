# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview
This is a Joget DX8 plugin project implementing a Workflow Activator - a Post Processing Tool plugin that automatically invokes workflow processes after form submission.

## Build Commands

### Compile and Package
```bash
# Clean and build the OSGi bundle
mvn clean package

# Build without running tests
mvn clean package -DskipTests=true

# Install to local Maven repository
mvn clean install
```

### Testing
```bash
# Run unit tests
mvn test

# Run integration tests
mvn integration-test
```

## Architecture

### Plugin Structure
The project follows Joget's OSGi plugin architecture:

- **Activator** (`global.govstack.workflow.Activator`): OSGi bundle activator that registers the plugin service
- **WorkflowActivator** (`global.govstack.workflow.activator.lib.WorkflowActivator`): Main plugin implementation extending `DefaultApplicationPlugin`
- **Plugin Configuration**: JSON-based property definitions in `/properties/workflowActivator.json`
- **Bundle Configuration**: Maven Bundle Plugin creates OSGi bundle with embedded dependencies

### Key Integration Points
- **Joget WorkflowManager**: Used for starting workflow processes (`processStart()` method)
- **Form Data Integration**: Automatically passes form submission data as workflow variables
- **Execution Modes**: Supports both synchronous and asynchronous workflow execution
- **Post Processing Tool**: Integrates with Joget's Form Builder as a post-submission processor

### Configuration Properties
The plugin is configured through a JSON property file that defines:
- Process Definition ID or Process Name
- Execution mode (sync/async)
- Data mapping options (form data pass-through, custom variables)
- Participant assignment
- Error handling strategies

## Development Guidelines

### Java Version
Target Java 11 (configured in Maven compiler plugin)

### Dependencies
- Joget wflow-core (version matches project version, currently 8.1-SNAPSHOT)
- Jackson and Gson for JSON processing
- All dependencies are embedded in the OSGi bundle

### Package Naming
Current base package: `global.govstack.workflow`
Bundle activator configured in pom.xml must match the actual Activator class location

### Plugin Registration
New plugins must be registered in the `Activator.start()` method using OSGi service registration