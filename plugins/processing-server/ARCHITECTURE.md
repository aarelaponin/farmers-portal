# Architecture: Transport-Layer Only Design

## Overview

The GovStack Registration Building Block plugins (DocSubmitter and ProcessingServer) implement a **transport-layer only** architecture. They are responsible for data transformation and transmission between Joget instances using GovStack standards, but NOT for business validation.

## Design Philosophy

### What These Plugins Do

✅ **Transform data formats**
- Convert between Joget form data and GovStack JSON
- Handle field mapping via configuration (services.yml)
- Transform data types (dates, booleans, master data lookups)
- Process grid/array relationships

✅ **Validate structure and metadata**
- JSON is parseable
- Metadata version compatibility (sender/receiver sync)
- Configuration matches database schema
- Required configuration fields present

✅ **Transport data**
- HTTP API endpoint for receiving data
- Send data from sender to receiver
- Handle connection/network errors

### What These Plugins Do NOT Do

❌ **Business validation**
- Required fields
- Conditional requirements
- Data format validation
- Business logic rules
- Cross-field dependencies

❌ **Functional logic**
- Approval workflows
- State transitions
- Authorization/permissions
- Audit logging (except transport-level)

## Why This Separation Matters

### 1. Generic Building Block Pattern

The plugins must support ANY service type (farmers, students, patients, products, etc.).

**Problem with business validation in plugin:**
```java
// BAD: Service-specific hardcoded rules
if (serviceId.equals("farmers_registry")) {
    if (cropProduction.equals("yes") && crops.isEmpty()) {
        throw new ValidationException("Farmers need crops!");
    }
}
```

**Solution: Transport-layer only:**
```java
// GOOD: Generic, service-agnostic
JSONObject data = parseJSON(requestBody);
Map<String, Object> mappedData = dataMapper.mapToMultipleForms(data);
multiFormManager.saveToMultipleForms(mappedData);
```

### 2. Maintainability

**Before (Business Validation in Plugin)**:
- 9 validation files (~1500 lines)
- Hardcoded farmer-specific rules
- Adding new service requires code changes
- 3 hours debugging after minor refactoring
- Tight coupling between transport and business logic

**After (Transport-Only)**:
- 3 validation files (structural only)
- No service-specific code
- Adding new service = update YAML config
- Changes isolated to configuration
- Clean separation of concerns

### 3. User Experience

**Development Platform (Joget) Is for Business Logic:**
- Form designers can see validation rules visually
- Business analysts can modify rules without coding
- Testing validation doesn't require plugin rebuild
- Joget's native validators are mature and tested

**Plugins Are for Integration:**
- Developers configure field mappings once
- Minimal code changes when business rules change
- Focus on reliability of data transmission
- Let each layer do what it's good at

## Architecture Layers

```
┌─────────────────────────────────────────────────────────┐
│                  APPLICATION LAYER                       │
│  (Joget Forms - Business Validation & Logic)            │
│  - Required fields                                       │
│  - Conditional requirements                              │
│  - Data format validation                                │
│  - Business rules                                        │
│  - Approval workflows                                    │
└────────────────────┬────────────────────────────────────┘
                     │
                     ↓
┌─────────────────────────────────────────────────────────┐
│                   TRANSPORT LAYER                        │
│  (DocSubmitter & ProcessingServer Plugins)              │
│  - Data format transformation                            │
│  - Field mapping (services.yml)                         │
│  - Metadata version checking                             │
│  - HTTP transmission                                     │
│  - Configuration validation                              │
└────────────────────┬────────────────────────────────────┘
                     │
                     ↓
┌─────────────────────────────────────────────────────────┐
│                    DATA LAYER                            │
│  (Database)                                              │
└─────────────────────────────────────────────────────────┘
```

## Validation Strategy

### Application Layer (Joget Forms)

**Required Fields:**
```
Form Designer → Field Properties → Required: Yes
```

**Conditional Requirements:**
```
Form Designer → Visibility Rules
If "cropProduction" = "yes", show "Crops Grid" (required)
```

**Data Format:**
```
Form Designer → Field Validators
- Email validator
- Number range validator
- Date format validator
- Custom regex validator
```

**Business Logic:**
```
Form Designer → Form Validators
- Cross-field validation
- Complex business rules
- External API calls
```

### Transport Layer (Plugins)

**Structural Validation (ProcessingServer):**
```java
// JSON parseable?
if (!requestBody.trim().startsWith("{")) {
    throw new FormSubmissionException("Invalid JSON format");
}

// Metadata version compatible?
if (!isCompatible(clientVersion, serverVersion)) {
    LogUtil.warn("Metadata version mismatch");
}
```

**Configuration Validation (Startup):**
```java
// services.yml matches database schema?
ServiceMetadataValidator validator = new ServiceMetadataValidator(dataSource);
ValidationResult result = validator.validate();

if (!result.valid) {
    LogUtil.error("Configuration errors: " + result.getReport());
}
```

## Example: Farmer Registration

### Application Layer (Joget Form)

**Form: Farmer Basic Info**
- National ID: Required, 10-12 digits
- First Name: Required
- Last Name: Required
- Birth Date: Required, must be 18+ years old
- Gender: Required, radio button (Male/Female)

**Form: Crop Management (Grid)**
- Shown only if "Crop Production" = Yes
- At least 1 row required
- Crop Type: Required, dropdown from master data
- Area: Required, number > 0

**Joget Configuration:**
```
Field: national_id
- Type: Text Field
- Validators:
  - Required: Yes
  - Min Length: 10
  - Max Length: 12
  - Pattern: [0-9]+

Field: cropProduction
- Type: Radio Button
- Options: Yes/No

Grid: cropManagement
- Visibility Rule: Show if cropProduction = "Yes"
- Min Rows: 1 (if visible)
```

### Transport Layer (services.yml)

```yaml
formMappings:
  farmerBasicInfo:
    formId: farmerBasicInfo
    tableName: app_fd_farmer_basic_data
    fields:
      - joget: national_id
        govstack: identifiers[0].value
        # NO required: true - that's in Joget form!

      - joget: first_name
        govstack: name.given[0]

      - joget: last_name
        govstack: name.family

  farmerCropsLivestock:
    formId: farmerCropsLivestock
    fields:
      - joget: cropManagement
        govstack: extension.agriculture.crops
        transform: grid
        gridTableName: app_fd_crop_management
```

## Benefits of Transport-Only Design

### 1. Simplicity

**Lines of Code:**
- Before: ~4,500 lines (including validation)
- After: ~3,000 lines (transport only)
- 33% reduction in complexity

**Conceptual Model:**
- Before: "Plugins validate AND transform"
- After: "Plugins only transform"
- Easier to understand and maintain

### 2. Flexibility

**Change Request: "Add new required field"**
- Before: Update Java code, rebuild plugin, redeploy to ALL environments
- After: Update Joget form (1 minute), done

**Change Request: "Support new service type"**
- Before: Add service-specific validation logic, rebuild
- After: Create services.yml config, deploy

### 3. Reliability

**Testing Scope:**
- Before: Test transport + business validation
- After: Test transport only
- Faster test cycles, fewer edge cases

**Debugging:**
- Before: "Is bug in transport or validation?"
- After: "Bug is in form? → Edit form. Bug is in mapping? → Edit YAML."
- Clear ownership of issues

### 4. Scalability

**Multiple Services:**
- Before: Each service adds validation code
- After: Each service adds YAML config only
- Linear growth in complexity

**Multiple Teams:**
- Before: All changes require plugin developer
- After: Form changes → Business analyst, Mapping changes → Configuration engineer
- Better separation of responsibilities

## Migration Path

For systems with existing business validation in plugins:

### Phase 1: Identify Validation Types
Audit existing validation code:
- **Structural** (keep in plugin) - JSON format, version compatibility
- **Business** (move to forms) - Required fields, conditional logic

### Phase 2: Move Business Rules to Forms
For each business validation rule:
1. Implement in Joget form designer
2. Test in form submission
3. Remove from plugin code
4. Deploy form changes
5. Deploy plugin update

### Phase 3: Document Architecture
- Update README.md
- Create ARCHITECTURE.md (this file)
- Train team on separation of concerns

## Common Questions

### Q: What if validation logic is complex?

**A:** Use Joget's Custom Validators or Form Validators. You can write Java/JavaScript validators that run in the form context. This keeps validation logic with the form, not in the transport layer.

### Q: What about performance? Won't Joget validators be slower?

**A:** No. Validators run during form submission before any API call. Transport-layer validation would happen AFTER network transmission, which is slower. Joget validators are also highly optimized.

### Q: What if different services need different validation?

**A:** Perfect! Each service has its own Joget forms with service-specific validators. The transport plugins remain generic. This is the whole point of separation.

### Q: How do we ensure sender and receiver validate the same way?

**A:** You don't. The sender validates business rules in its forms. The receiver trusts the sender (or validates again in its forms if needed). The transport layer just moves data. This is standard distributed systems architecture.

### Q: What if we need to validate data format (email, phone, date)?

**A:** Data format is a gray area:
- **Strict format (e.g., ISO8601 date)**: Transport layer can validate format
- **Business format (e.g., "phone must be local")**: Application layer validates

General rule: If validation depends on business context, it's application layer.

## Related Documentation

- [README.md](README.md) - Project overview and setup
- [TODO.md](TODO.md) - Future improvements
- [CONFIGURATION_SYNC.md](CONFIGURATION_SYNC.md) - Configuration synchronization
- [GovStack Registration BB Specification](https://registration.govstack.global/) - Standard specification

## Version History

**October 28, 2025**: Initial version - Transport-layer only architecture documented
- Removed business validation from plugins
- Clarified separation of concerns
- Documented validation strategy

---

**Key Takeaway**: Let forms do business validation. Let plugins do data transformation. Keep it simple.
