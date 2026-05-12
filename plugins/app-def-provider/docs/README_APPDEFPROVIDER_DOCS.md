# AppDefProvider Documentation Suite

## 📚 Complete Documentation Package

This directory contains a complete documentation suite for creating the **AppDefProvider** Joget plugin architecture and design specification.

---

## 🎯 START HERE

### For Claude Code (Command Line Interface) - RECOMMENDED

**Read this first:** [AppDefProvider_File_Usage_Matrix.md](AppDefProvider_File_Usage_Matrix.md)

**Then use:** [AppDefProvider_ClaudeCode_Kickoff.md](AppDefProvider_ClaudeCode_Kickoff.md)

```bash
claude-code "Follow the instructions in AppDefProvider_ClaudeCode_Kickoff.md"
```

### For Web-Based AI (Claude.ai, ChatGPT)

**Use:** [AppDefProvider_AI_Assistance_Prompt.md](AppDefProvider_AI_Assistance_Prompt.md)

Copy entire file contents and paste into web interface.

---

## 📁 File Directory

### Core Prompt Files

| File | Lines | Purpose | Use Case |
|------|-------|---------|----------|
| **AppDefProvider_ClaudeCode_Kickoff.md** | ~80 | Concise entry point | Claude Code CLI |
| **AppDefProvider_AI_Assistance_Prompt.md** | ~950 | Complete requirements | Web AI interfaces |

### Reference Guides

| File | Purpose |
|------|---------|
| **AppDefProvider_ClaudeCode_Guide.md** | Detailed guide for using with Claude Code |
| **AppDefProvider_Prompt_Usage_Guide.md** | Detailed guide for using with web AI |
| **AppDefProvider_Quick_Reference.md** | One-page architecture reference |
| **AppDefProvider_File_Usage_Matrix.md** | File selection guide (START HERE) |
| **README_APPDEFPROVIDER_DOCS.md** | This file - documentation index |

---

## 🚀 Quick Start (Choose Your Path)

### Path A: Claude Code (CLI) ✅ Recommended

1. **Prepare project directory:**
   ```bash
   mkdir -p ~/Projects/AppDefProvider
   cd ~/Projects/AppDefProvider
   ```

2. **Copy prompt files:**
   ```bash
   cp /path/to/outputs/AppDefProvider_ClaudeCode_Kickoff.md .
   cp /path/to/outputs/AppDefProvider_AI_Assistance_Prompt.md .
   ```

3. **Start Claude Code:**
   ```bash
   claude-code "Follow the instructions in AppDefProvider_ClaudeCode_Kickoff.md"
   ```

4. **Claude will generate:**
   - Architecture specification documents
   - Component design specifications
   - OpenAPI 3.0 API specification
   - Implementation plan (6 phases)
   - Java code skeletons

### Path B: Web AI (claude.ai, ChatGPT)

1. **Open AI interface:**
   - Claude: https://claude.ai
   - ChatGPT: https://chat.openai.com

2. **Copy prompt:**
   - Open `AppDefProvider_AI_Assistance_Prompt.md`
   - Copy entire file contents

3. **Paste into AI:**
   - Start new conversation
   - Paste the complete prompt
   - Wait for comprehensive architecture response

---

## 📖 Detailed Documentation

### What's Included in Each File

#### 1. AppDefProvider_ClaudeCode_Kickoff.md (Entry Point for CLI)
- Brief overview of the plugin
- Reference to detailed requirements
- List of deliverables expected
- Project context references
- Development environment specs
- Success criteria

**Use when:** You're using Claude Code from command line

#### 2. AppDefProvider_AI_Assistance_Prompt.md (Complete Specification)
- **Section 1:** Executive Summary & Business Context
- **Section 2:** Technical Architecture Context
- **Section 3:** API Design Requirements
- **Section 4:** Plugin Architecture Specification Needed
- **Section 5:** Implementation Considerations
- **Section 6:** Specific Architecture Questions (25+)
- **Section 7:** Deliverables Requested
- **Section 8:** Reference Materials
- **Section 9:** Success Criteria
- **Section 10:** Additional Context
- **Section 11:** Request Summary

**Use when:** You're using web-based AI or need complete context

#### 3. AppDefProvider_ClaudeCode_Guide.md (How-To Guide)
Complete guide for using prompts with Claude Code:
- Best practices
- Command examples
- Workflow recommendations
- Troubleshooting tips
- Example session walkthrough

#### 4. AppDefProvider_Prompt_Usage_Guide.md (Web AI Guide)
Complete guide for using prompts with web AI:
- Usage instructions
- Expected outputs
- Follow-up prompt examples
- Customization options

#### 5. AppDefProvider_Quick_Reference.md (Cheat Sheet)
One-page reference containing:
- Architecture pattern overview
- API endpoints table
- Core components (11 total)
- GovStack ↔ Joget mapping
- Security requirements
- Implementation phases
- Key code snippets

#### 6. AppDefProvider_File_Usage_Matrix.md (Decision Guide)
Helps you choose which file to use:
- File comparison matrix
- Usage scenarios
- Command references
- File relationships diagram

---

## 🎨 Architecture Overview

### What AppDefProvider Does

**Purpose:** Export Joget application definitions via REST API for deployment to multiple organizational instances

**Architecture Pattern:**
```
DEFINITION SERVER (Central DP)
         ↓
   AppDefProvider Plugin (REST API)
         ↓
    JSON Definitions
         ↓
CONSUMER INSTANCES (Multiple Orgs)
         ↓
   FormCreator Plugin (Import)
```

### Key Components (11 Total)

**Extractors (6):**
1. FormDefinitionExtractor
2. ProcessDefinitionExtractor
3. DatalistDefinitionExtractor
4. UserviewDefinitionExtractor
5. ApiBuilderExtractor
6. EnvironmentVariableExtractor

**Assemblers (2):**
7. RegistrationAssembler
8. ServiceAssembler

**Utilities (3):**
9. AuthenticationService
10. RateLimitService
11. CatalogService

### API Endpoints

- `GET /api/v1/catalog` - List all services
- `GET /api/v1/services/{serviceId}/export` - Export complete service
- `GET /api/v1/registrations/{registrationId}` - Export registration
- `GET /api/v1/components/forms/{formId}` - Export form
- `GET /api/v1/components/processes/{processId}` - Export process

---

## ✅ What You'll Receive

When you use these prompts, the AI will generate:

### 1. Architecture Documentation
- System context diagram (Mermaid)
- Component architecture diagram (Mermaid)
- Sequence diagrams for each endpoint (Mermaid)
- Data flow diagrams

### 2. Component Specifications
- Detailed design for all 11 components
- Interface definitions with method signatures
- Data models
- Error handling strategies

### 3. API Specification
- Complete OpenAPI 3.0 YAML file
- All endpoints with schemas
- Example requests/responses
- Error code catalog

### 4. Implementation Plan
- 6-week phase breakdown
- Detailed tasks with estimates
- Code templates
- Testing strategy

### 5. Java Code Skeletons
- Main plugin class (`AppDefinitionProvider.java`)
- All extractor service classes
- All assembler service classes
- Exception hierarchy
- Maven `pom.xml`

---

## 📋 Implementation Phases

| Phase | Duration | Focus |
|-------|----------|-------|
| **1** | Weeks 1-2 | Core Export + Authentication |
| **2** | Week 3 | Process Flow + GovStack Mapping |
| **3** | Week 4 | Registration Assembly |
| **4** | Week 5 | Service Export + Validation |
| **5** | Week 6 | Integration + Testing |

---

## 🔒 GovStack Compliance

This plugin implements:
- **GovStack Registration Building Block**
- **Section 6.3.2.10 (REQUIRED)**
- **Import/Export of Service Descriptions**

Reference: https://registration.govstack.global/6-functional-requirements

---

## 🛠️ Technology Stack

- **Platform:** Joget DX 8.1 Enterprise Edition
- **Language:** Java 11+
- **Build Tool:** Maven 3.6+
- **Database:** MySQL 8.0
- **IDE:** IntelliJ IDEA
- **OS:** macOS (dev), Linux (prod)

---

## 📚 Related Project Files

These files are referenced in the prompts and available in your project:

- `FormCreator.java` - Existing consumer plugin
- `BaseServiceProvider.java` - Template pattern for API plugins
- `RegistrationServiceProvider.java` - Example implementation
- `app-provider-api-design.md` - API design documentation
- `app-provider-implementation-guide.md` - Implementation guidelines
- `app-provider-plan.md` - Overall architecture plan
- `METADATA_MANUAL.md` - MDM system architecture

---

## 💡 Tips for Success

### When Using Claude Code

1. **Start with kickoff prompt** - it's optimized for CLI
2. **Let Claude read files** - don't paste large content
3. **Request specifics** - "generate FormDefinitionExtractor"
4. **Iterate gradually** - component by component

### When Using Web AI

1. **Paste complete prompt** - all context at once
2. **Ask follow-ups** - "expand on security architecture"
3. **Request code** - "show me the Java implementation"
4. **Save responses** - copy to files for reference

### General Tips

1. **Review generated architecture** before implementation
2. **Validate against GovStack requirements**
3. **Test with FormCreator** for compatibility
4. **Iterate based on feedback**

---

## 🔍 Troubleshooting

### Issue: Not sure which file to use
**Solution:** Read [AppDefProvider_File_Usage_Matrix.md](AppDefProvider_File_Usage_Matrix.md)

### Issue: Architecture too high-level
**Solution:** Ask AI: "Add implementation details with code examples for FormDefinitionExtractor"

### Issue: Missing integration points
**Solution:** Ask AI: "Review section 4.3 and ensure all integration points are addressed"

### Issue: Need specific code
**Solution:** Ask AI: "Generate complete Java code for AppDefinitionProvider.java"

---

## 📞 Support & Questions

If you need clarification or modifications:
1. Review the appropriate guide file
2. Check the Quick Reference for architecture decisions
3. Ask the AI for specific elaborations
4. Refer to project knowledge files for context

---

## 🎯 Success Criteria

The architecture specification will enable:

1. ✅ **Immediate Implementation** - Clear, actionable design
2. ✅ **GovStack Compliance** - Section 6.3.2.10 satisfied
3. ✅ **FormCreator Integration** - Seamless export/import
4. ✅ **Production Ready** - Security, performance, error handling
5. ✅ **Maintainable** - Clean architecture, testable
6. ✅ **Scalable** - Handles 100+ forms, concurrent requests
7. ✅ **Integration Friendly** - Works with Python utilities

---

## 📅 Document Version

- **Version:** 1.0
- **Created:** October 30, 2025
- **Platform:** Joget DX 8.1 Enterprise Edition
- **Purpose:** AppDefProvider Plugin Development

---

## 🚀 Next Steps

1. **Choose your path** (Claude Code or Web AI)
2. **Use the appropriate prompt file**
3. **Generate the architecture**
4. **Review and validate**
5. **Begin implementation**

**Ready to start?** Pick your preferred AI interface and use the corresponding prompt file!

---

**Good luck with your AppDefProvider plugin development! 🎉**
