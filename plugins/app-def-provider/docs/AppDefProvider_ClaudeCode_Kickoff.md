# AppDefProvider Plugin - Architecture & Design Request

## Quick Start for Claude Code

I need your help creating a comprehensive architecture and design specification for the **AppDefProvider** Joget plugin.

**Please read the complete detailed requirements from this file:**
`AppDefProvider_AI_Assistance_Prompt.md`

This file contains:
- Complete business context and GovStack compliance requirements
- Technical architecture with existing FormCreator integration
- All API design requirements
- 11 core components that need design
- Implementation considerations (6-week plan)
- Specific architecture questions (25+)
- Detailed deliverables specification

## What I Need You to Produce

Based on the detailed requirements in the main prompt file, please create:

1. **Architecture Specification Document** (`AppDefProvider_Architecture_Spec.md`)
   - System context diagram (Mermaid)
   - Component architecture diagram (Mermaid)
   - Sequence diagrams for each API endpoint (Mermaid)
   - Data flow diagrams

2. **Component Design Specifications** (`AppDefProvider_Component_Specs.md`)
   - Detailed design for all 11 components
   - Interface definitions with method signatures
   - Data models
   - Error handling strategies

3. **API Specification** (`AppDefProvider_OpenAPI.yaml`)
   - Complete OpenAPI 3.0 specification
   - All endpoints with request/response schemas
   - Error responses
   - Authentication requirements

4. **Implementation Plan** (`AppDefProvider_Implementation_Plan.md`)
   - Phase-by-phase breakdown (6 weeks)
   - Detailed tasks with estimates
   - Code templates and skeletons
   - Testing strategy

5. **Code Skeletons** (Java files in proper package structure)
   - `AppDefinitionProvider.java` - Main plugin class
   - All extractor service skeletons
   - All assembler service skeletons
   - Exception hierarchy
   - Maven `pom.xml`

## Project Context Available

You have access to these reference files in the project:
- `FormCreator.java` - Existing consumer plugin
- `BaseServiceProvider.java` - Template pattern
- `RegistrationServiceProvider.java` - Example implementation
- `app-provider-api-design.md` - API design docs
- `app-provider-implementation-guide.md` - Implementation guide
- `METADATA_MANUAL.md` - MDM system architecture

## Development Environment

- **IDE**: IntelliJ IDEA
- **Build**: Maven 3.6+
- **Java**: OpenJDK 11
- **Platform**: Joget DX 8.1 Enterprise Edition
- **Database**: MySQL 8.0
- **OS**: macOS (development), Linux (production)

## Key Success Criteria

The architecture must:
1. ✅ Enable immediate implementation (clear, actionable)
2. ✅ Comply with GovStack Registration BB section 6.3.2.10
3. ✅ Integrate seamlessly with existing FormCreator plugin
4. ✅ Be production-ready (security, performance, error handling)
5. ✅ Support multi-tenant distributed deployment

## First Step

Please start by:
1. Reading the complete requirements from `AppDefProvider_AI_Assistance_Prompt.md`
2. Confirming you understand the scope and context
3. Then proceed to create the architecture specification documents

Let me know when you're ready to begin, or if you need any clarification on the requirements!
