# Using AppDefProvider Prompts with Claude Code

## Overview

You have **two prompt files** for different use cases:

1. **AppDefProvider_ClaudeCode_Kickoff.md** (Concise) - For Claude Code CLI
2. **AppDefProvider_AI_Assistance_Prompt.md** (Detailed) - Full requirements reference

## Recommended Approach for Claude Code

### ✅ Best Practice: Use the Kickoff Prompt

The kickoff prompt is specifically designed for Claude Code because it:
- Is concise (~80 lines vs 950 lines)
- References the detailed prompt file
- Clearly states deliverables
- Works efficiently with the command-line interface

## How to Use with Claude Code

### Option 1: Reference the File (Recommended)

```bash
# Navigate to your project directory where the prompts are located
cd /path/to/your/project

# Start Claude Code and reference the kickoff prompt
claude-code "Please read and follow the instructions in AppDefProvider_ClaudeCode_Kickoff.md"
```

**What happens:**
1. Claude Code reads the kickoff prompt
2. The kickoff prompt instructs Claude to read the detailed prompt file
3. Claude reads both files and has complete context
4. Claude begins creating the architecture documents

### Option 2: Pipe the File Content

```bash
# Pipe the file content directly
cat AppDefProvider_ClaudeCode_Kickoff.md | claude-code
```

### Option 3: Use as Initial Context

```bash
# Start with the file as context
claude-code --files AppDefProvider_ClaudeCode_Kickoff.md "Create the AppDefProvider architecture specification"
```

## Recommended Workflow

### Step 1: Set Up Your Project Structure

```bash
# Create a new project directory
mkdir -p ~/Projects/AppDefProvider
cd ~/Projects/AppDefProvider

# Copy the prompt files
cp /path/to/AppDefProvider_ClaudeCode_Kickoff.md .
cp /path/to/AppDefProvider_AI_Assistance_Prompt.md .

# Create directories for output
mkdir -p docs
mkdir -p src/main/java/com/fiscaladmin/gam/appdefinitionprovider
```

### Step 2: Start Claude Code

```bash
# Method A: Simple reference
claude-code "Follow the instructions in AppDefProvider_ClaudeCode_Kickoff.md"

# Method B: Be more specific
claude-code "Read AppDefProvider_ClaudeCode_Kickoff.md and create the architecture specification as outlined"

# Method C: Include reference files context
claude-code --files AppDefProvider_ClaudeCode_Kickoff.md,AppDefProvider_AI_Assistance_Prompt.md \
  "Create the complete architecture and design specification for AppDefProvider plugin"
```

### Step 3: Interact as Claude Works

Claude Code will work autonomously, but you can guide it:

```bash
# If Claude asks for clarification
> "Yes, proceed with the architecture specification"

# To focus on specific deliverable
> "Start with the OpenAPI specification first"

# To request more detail
> "Expand the FormDefinitionExtractor component design with implementation details"
```

### Step 4: Review and Iterate

```bash
# After Claude generates initial docs
> "Review the sequence diagram for /services/{serviceId}/export endpoint - add error handling flows"

> "Generate the complete Java skeleton for AppDefinitionProvider.java with all methods"

> "Create unit test templates for FormDefinitionExtractor"
```

## What Claude Code Will Create

Based on the prompts, Claude Code will create these files in your project:

### Documentation
```
docs/
├── AppDefProvider_Architecture_Spec.md
├── AppDefProvider_Component_Specs.md
├── AppDefProvider_Implementation_Plan.md
├── AppDefProvider_OpenAPI.yaml
└── diagrams/
    ├── system-context.mmd
    ├── component-architecture.mmd
    ├── sequence-catalog.mmd
    ├── sequence-service-export.mmd
    └── sequence-component-export.mmd
```

### Source Code Skeletons
```
src/main/java/com/fiscaladmin/gam/appdefinitionprovider/
├── lib/
│   └── AppDefinitionProvider.java
├── service/
│   ├── extractor/
│   │   ├── FormDefinitionExtractor.java
│   │   ├── ProcessDefinitionExtractor.java
│   │   ├── DatalistDefinitionExtractor.java
│   │   ├── UserviewDefinitionExtractor.java
│   │   ├── ApiBuilderExtractor.java
│   │   └── EnvironmentVariableExtractor.java
│   ├── assembler/
│   │   ├── RegistrationAssembler.java
│   │   ├── ServiceAssembler.java
│   │   └── GovStackMapper.java
│   ├── security/
│   │   ├── AuthenticationService.java
│   │   └── RateLimitService.java
│   └── util/
│       ├── ExportValidationService.java
│       └── CatalogService.java
├── model/
│   ├── ServiceExport.java
│   ├── RegistrationExport.java
│   ├── ComponentExport.java
│   └── GovStackMetadata.java
└── exception/
    ├── ExportException.java
    ├── AuthenticationException.java
    └── ValidationException.java
```

### Build Configuration
```
pom.xml
.gitignore
README.md
```

## Tips for Effective Claude Code Usage

### 1. Start Broad, Then Narrow

```bash
# First pass: Get overall architecture
claude-code "Read AppDefProvider_ClaudeCode_Kickoff.md and create the high-level architecture specification"

# Second pass: Deep dive on specific component
claude-code "Based on the architecture, implement FormDefinitionExtractor with complete code, error handling, and unit tests"
```

### 2. Use Checkpoints

```bash
# After major milestone
> "Review what we've created so far and create a progress summary"

# Before moving to next phase
> "Validate that the API specification matches the component designs"
```

### 3. Request Specific Artifacts

```bash
> "Generate the complete OpenAPI 3.0 YAML file with all endpoints"

> "Create the Maven pom.xml with all Joget dependencies"

> "Write the implementation guide for Phase 1 (Core Export)"
```

### 4. Iterative Refinement

```bash
# Review and improve
> "The authentication service needs more detail on API key hashing - expand that section"

> "Add example requests/responses to the OpenAPI spec"

> "Create integration test scenarios for the export flow"
```

## Advantages of Using File Reference

### ✅ **Efficiency**
- Don't paste 950 lines into terminal
- Claude Code reads files faster than parsing pasted text
- Easier to update prompt and retry

### ✅ **Maintainability**
- Prompt stays in version control
- Easy to update and iterate
- Shareable with team members

### ✅ **Context Management**
- Claude Code maintains file context throughout session
- Can reference multiple files simultaneously
- Better handling of large requirements

### ✅ **Reproducibility**
- Exact same prompt every time
- No copy-paste errors
- Consistent results

## Common Commands Reference

### Starting a Session
```bash
# Basic start
claude-code "Follow AppDefProvider_ClaudeCode_Kickoff.md"

# With multiple reference files
claude-code --files AppDefProvider_ClaudeCode_Kickoff.md,FormCreator.java,BaseServiceProvider.java \
  "Create the architecture"

# With specific output directory
claude-code --output-dir ./generated "Follow AppDefProvider_ClaudeCode_Kickoff.md"
```

### During Session
```bash
# Request specific deliverable
> "Create the AppDefinitionProvider.java skeleton now"

# Ask for clarification
> "Explain the request routing pattern you chose"

# Request changes
> "Use strategy pattern instead of if-else chains for routing"

# Add more context
> "Consider that we need to support Joget 8.0, 8.1, and 8.2"
```

### Ending Session
```bash
# Get summary
> "Create a summary document of everything we've created"

# Checkpoint for next session
> "What should we work on next time?"

# Generate README
> "Create a README.md explaining the architecture and how to build it"
```

## Troubleshooting

### Issue: Claude doesn't read the detailed prompt

**Solution:**
```bash
# Be explicit
claude-code "First, read AppDefProvider_AI_Assistance_Prompt.md completely. Then read AppDefProvider_ClaudeCode_Kickoff.md and follow its instructions."
```

### Issue: Output is too high-level

**Solution:**
```bash
# Request more detail
> "The component specifications need more detail - include method signatures, parameter types, return types, and implementation notes for each method"
```

### Issue: Missing reference to existing code

**Solution:**
```bash
# Add reference files
claude-code --files FormCreator.java,BaseServiceProvider.java,AppDefProvider_ClaudeCode_Kickoff.md \
  "Create the architecture, following the patterns in FormCreator and BaseServiceProvider"
```

### Issue: Architecture doesn't address integration

**Solution:**
```bash
> "Review section 4.3 of AppDefProvider_AI_Assistance_Prompt.md about integration points and ensure the architecture addresses all items"
```

## Example Full Session

```bash
# Start
cd ~/Projects/AppDefProvider
claude-code --files AppDefProvider_ClaudeCode_Kickoff.md,FormCreator.java "Create the AppDefProvider architecture"

# Claude generates initial architecture
> "Good start. Now create the complete OpenAPI 3.0 specification with all endpoints"

# Claude generates OpenAPI spec
> "Now generate the Java skeleton for AppDefinitionProvider.java"

# Claude generates main class
> "Create the FormDefinitionExtractor with complete implementation"

# Claude implements extractor
> "Add comprehensive error handling and logging"

# Claude adds error handling
> "Create unit tests for FormDefinitionExtractor"

# Claude creates tests
> "Create a summary document of our progress"

# End session
```

## Next Steps After Architecture is Complete

1. **Review the Generated Artifacts**
   ```bash
   # List what was created
   find docs/ src/ -type f -name "*.md" -o -name "*.java" -o -name "*.yaml"
   ```

2. **Set Up Maven Project**
   ```bash
   # Use generated pom.xml
   mvn clean install
   ```

3. **Implement Phase 1**
   ```bash
   # Start implementing with Claude Code
   claude-code "Based on the architecture, let's implement Phase 1: Core Export. Start with the catalog endpoint."
   ```

4. **Iterate**
   - Implement component by component
   - Test each component
   - Integrate with FormCreator
   - Deploy to test environment

## Summary

**For Claude Code, use the two-file approach:**

1. **Start with**: `AppDefProvider_ClaudeCode_Kickoff.md` (concise entry point)
2. **References**: `AppDefProvider_AI_Assistance_Prompt.md` (detailed requirements)

**Command:**
```bash
claude-code "Follow the instructions in AppDefProvider_ClaudeCode_Kickoff.md"
```

This approach gives Claude Code all the context it needs while being efficient and maintainable!
