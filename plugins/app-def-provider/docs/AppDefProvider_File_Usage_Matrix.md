# AppDefProvider Documentation Files - Usage Matrix

## 📁 Files Overview

| File | Size | Purpose | Use With |
|------|------|---------|----------|
| **AppDefProvider_ClaudeCode_Kickoff.md** | ~80 lines | Concise entry point for CLI | Claude Code |
| **AppDefProvider_AI_Assistance_Prompt.md** | ~950 lines | Complete detailed requirements | Web Claude / ChatGPT |
| **AppDefProvider_ClaudeCode_Guide.md** | Reference guide | How to use with Claude Code | Documentation |
| **AppDefProvider_Prompt_Usage_Guide.md** | Reference guide | How to use with web AI | Documentation |
| **AppDefProvider_Quick_Reference.md** | 1 page | Quick lookup reference | Your desk |

---

## 🎯 Choose Your Approach

### If Using Claude Code (CLI) ✅ RECOMMENDED FOR YOU

**Use:** `AppDefProvider_ClaudeCode_Kickoff.md`

```bash
cd /path/to/your/project
claude-code "Follow the instructions in AppDefProvider_ClaudeCode_Kickoff.md"
```

**Why:**
- ✅ Concise (~80 lines vs 950)
- ✅ Designed for command-line interface
- ✅ References the detailed prompt automatically
- ✅ Optimized for file-based workflow

**What you get:**
- Architecture specification documents
- Component design specifications
- OpenAPI 3.0 API specification
- Implementation plan
- Java code skeletons

---

### If Using Web Claude (claude.ai)

**Use:** `AppDefProvider_AI_Assistance_Prompt.md`

1. Open https://claude.ai
2. Start new conversation
3. Copy entire content of `AppDefProvider_AI_Assistance_Prompt.md`
4. Paste as your first message

**Why:**
- Complete context in one message
- Works well with web interface
- Comprehensive single document

---

### If Using ChatGPT / Other AI

**Use:** `AppDefProvider_AI_Assistance_Prompt.md`

Same as web Claude - copy and paste the complete prompt.

---

## 📋 Quick Command Reference

### Claude Code - Initial Architecture Request

```bash
# Option 1: Simple (recommended)
claude-code "Follow the instructions in AppDefProvider_ClaudeCode_Kickoff.md"

# Option 2: With explicit file reference
claude-code --files AppDefProvider_ClaudeCode_Kickoff.md \
  "Create the complete AppDefProvider architecture"

# Option 3: With multiple reference files
claude-code --files AppDefProvider_ClaudeCode_Kickoff.md,FormCreator.java,BaseServiceProvider.java \
  "Create architecture following existing patterns"
```

### Claude Code - Specific Component Implementation

```bash
# After architecture is complete, implement specific components
claude-code "Based on the architecture, implement FormDefinitionExtractor with complete code and tests"

claude-code "Generate the complete AppDefinitionProvider.java main class"

claude-code "Create the OpenAPI 3.0 specification with all endpoints"
```

---

## 🎨 File Relationships

```
AppDefProvider_ClaudeCode_Kickoff.md (Entry Point)
            │
            ├─→ References: AppDefProvider_AI_Assistance_Prompt.md
            │                      │
            │                      └─→ Contains: Complete requirements
            │
            └─→ Produces:
                ├─ Architecture_Spec.md
                ├─ Component_Specs.md
                ├─ OpenAPI.yaml
                ├─ Implementation_Plan.md
                └─ Java code skeletons

AppDefProvider_ClaudeCode_Guide.md
            │
            └─→ How to use the kickoff prompt effectively

AppDefProvider_Prompt_Usage_Guide.md
            │
            └─→ How to use the detailed prompt with web AI

AppDefProvider_Quick_Reference.md
            │
            └─→ Quick lookup for architecture decisions
```

---

## ✅ Recommended Workflow for You

### Step 1: Prepare Your Project
```bash
mkdir -p ~/Projects/AppDefProvider
cd ~/Projects/AppDefProvider

# Copy the prompts
cp /path/to/outputs/AppDefProvider_ClaudeCode_Kickoff.md .
cp /path/to/outputs/AppDefProvider_AI_Assistance_Prompt.md .
```

### Step 2: Start Claude Code
```bash
claude-code "Follow the instructions in AppDefProvider_ClaudeCode_Kickoff.md"
```

### Step 3: Let Claude Work
Claude will:
1. Read the kickoff prompt
2. Read the detailed prompt (referenced in kickoff)
3. Read your reference files (FormCreator.java, etc.)
4. Generate all architecture documents
5. Create code skeletons

### Step 4: Iterate
```bash
# Request specific components
> "Generate complete FormDefinitionExtractor implementation"

# Add details
> "Add comprehensive error handling to the extractor"

# Create tests
> "Create unit tests for FormDefinitionExtractor"
```

---

## 📊 File Content Comparison

### Kickoff Prompt (AppDefProvider_ClaudeCode_Kickoff.md)
```
✓ Brief overview
✓ Pointer to detailed requirements
✓ List of deliverables
✓ Development environment
✓ Success criteria
✓ Clear next steps

Size: ~80 lines
Format: Markdown
Optimized: CLI/File reference
```

### Detailed Prompt (AppDefProvider_AI_Assistance_Prompt.md)
```
✓ Executive summary
✓ Complete business context
✓ Technical architecture (11 sections)
✓ GovStack compliance details
✓ 25+ specific questions
✓ Reference materials
✓ Success criteria
✓ Development philosophy

Size: ~950 lines
Format: Markdown
Optimized: Single comprehensive document
```

---

## 💡 Pro Tips

### For Claude Code

1. **Start with the kickoff prompt** - it's designed for CLI
2. **Let Claude reference files** - don't paste everything
3. **Add reference files gradually** - use `--files` flag
4. **Request specific outputs** - be clear about what you want

### For Web AI

1. **Use the detailed prompt** - paste it all at once
2. **Scroll through response** - architecture will be comprehensive
3. **Ask follow-ups** - "expand on FormDefinitionExtractor"
4. **Request code** - "generate the Java skeleton now"

---

## 🚀 Quick Start (30 seconds)

```bash
# Clone or copy files to your project
cd ~/Projects/AppDefProvider

# Start Claude Code with the kickoff prompt
claude-code "Follow the instructions in AppDefProvider_ClaudeCode_Kickoff.md"

# Done! Claude will read both prompts and start generating architecture
```

---

## 📞 Summary

**For Command Line (Claude Code):**
- ✅ Use: `AppDefProvider_ClaudeCode_Kickoff.md`
- ✅ Command: `claude-code "Follow AppDefProvider_ClaudeCode_Kickoff.md"`
- ✅ It automatically references the detailed prompt

**For Web Interface:**
- ✅ Use: `AppDefProvider_AI_Assistance_Prompt.md`
- ✅ Copy entire file and paste into web chat

**Both approaches produce the same comprehensive architecture - choose based on your interface!**
