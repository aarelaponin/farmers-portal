# GovStack Multi-Service Plugin - Complete Service Creation Guide

**Version**: 1.0.0
**Last Updated**: 2025-10-29
**Author**: GovStack Registration Building Block Team
**Estimated Reading Time**: 4-6 hours (with hands-on practice)

---

## 📚 Table of Contents

### Part 1: Quick Start (20 pages)
1. [Introduction](#1-introduction)
2. [Prerequisites](#2-prerequisites)
3. [Your First Service in 5 Minutes](#3-your-first-service-in-5-minutes)
4. [Architecture Overview](#4-architecture-overview)
5. [Key Concepts](#5-key-concepts)

### Part 2: Planning Your Service (15 pages)
6. [Service Design Principles](#6-service-design-principles)
7. [Data Modeling](#7-data-modeling)
8. [Form Structure Planning](#8-form-structure-planning)
9. [Field Mapping Strategies](#9-field-mapping-strategies)

### Part 3: Complete Tutorial - Student Enrollment (80 pages)
10. [Tutorial Overview](#10-tutorial-overview)
11. [Step 1: Design the Data Model](#11-step-1-design-the-data-model)
12. [Step 2: Create Receiver Forms](#12-step-2-create-receiver-forms)
13. [Step 3: Configure Receiver YAML](#13-step-3-configure-receiver-yaml)
14. [Step 4: Configure Sender YAML](#14-step-4-configure-sender-yaml)
15. [Step 5: Build and Deploy](#15-step-5-build-and-deploy)
16. [Step 6: Create Sender Process](#16-step-6-create-sender-process)
17. [Step 7: Test and Verify](#17-step-7-test-and-verify)

### Part 4: Advanced Topics (30 pages)
18. [Complex Data Structures](#18-complex-data-structures)
19. [Data Transformations](#19-data-transformations)
20. [Error Handling](#20-error-handling)
21. [Performance Optimization](#21-performance-optimization)

### Part 5: YAML Reference (20 pages)
22. [Complete YAML Schema](#22-complete-yaml-schema)
23. [Required vs Optional Fields](#23-required-vs-optional-fields)
24. [Field Mapping Syntax](#24-field-mapping-syntax)
25. [Transform Functions](#25-transform-functions)

### Part 6: Troubleshooting (20 pages)
26. [Common Errors and Solutions](#26-common-errors-and-solutions)
27. [Debugging Techniques](#27-debugging-techniques)
28. [Log Analysis](#28-log-analysis)
29. [Database Verification](#29-database-verification)

### Part 7: Examples and Templates (15 pages)
30. [Minimal Contact Form](#30-minimal-contact-form)
31. [Simple Job Application](#31-simple-job-application)
32. [Business License Application](#32-business-license-application)
33. [Service Templates](#33-service-templates)

### Appendices
- [Appendix A: YAML Validator Tool](#appendix-a-yaml-validator-tool)
- [Appendix B: Service Generator Wizard](#appendix-b-service-generator-wizard)
- [Appendix C: Testing Automation](#appendix-c-testing-automation)
- [Appendix D: Quick Reference Cards](#appendix-d-quick-reference-cards)

---

# PART 1: QUICK START

---

## 1. Introduction

### 1.1 What is the GovStack Multi-Service Plugin System?

The GovStack Multi-Service Plugin System is a **truly generic** Joget DX8 plugin architecture that enables government services to be deployed through **YAML configuration alone**, with **zero Java code changes** required for each new service type.

**Key Benefits:**
- Add new services (farmers, students, business licenses, etc.) in hours, not weeks
- No programming required for new services
- Consistent data flow across all services
- Single codebase maintains all services
- Easy to test and validate

### 1.2 Who Should Use This Guide?

This guide is designed for:

**🟢 Beginners (No prior Joget experience):**
- Start with Part 1 (Quick Start) and Part 3 (Complete Tutorial)
- Follow screenshot-by-screenshot instructions
- Use the minimal examples to learn concepts

**🟡 Intermediate Users (Some Joget/YAML knowledge):**
- Review Part 1 for architecture understanding
- Jump to Part 2 (Planning) before Part 3 (Tutorial)
- Reference Part 5 (YAML Reference) as needed

**🔴 Advanced Users (Experienced Joget developers):**
- Skim Part 1 for key concepts
- Use Part 5 (YAML Reference) and Part 7 (Examples)
- Implement complex services with Part 4 (Advanced Topics)

### 1.3 How to Use This Guide

**For Learning:**
1. Read Part 1 completely (30 minutes)
2. Follow Part 3 tutorial hands-on (3-4 hours)
3. Implement your own service using templates (2-3 hours)

**For Reference:**
- Use Table of Contents to jump to specific topics
- Check Part 6 (Troubleshooting) when issues arise
- Reference Part 5 (YAML Reference) for syntax questions

**For Implementation:**
1. Use Part 2 (Planning) to design your service
2. Follow Part 3 (Tutorial) structure for implementation
3. Validate with tools in Appendices

### 1.4 What You'll Learn

By the end of this guide, you will be able to:

✅ Understand the multi-service architecture
✅ Design data models for government services
✅ Create Joget forms for data collection
✅ Write YAML configurations for both sender and receiver
✅ Build, deploy, and test new services
✅ Troubleshoot common issues
✅ Implement advanced features like grids and transformations

### 1.5 Architecture at a Glance

```
┌─────────────────────────────────────────────────────────────────┐
│                    GovStack Multi-Service System                 │
└─────────────────────────────────────────────────────────────────┘

Sender Side (Data Origin)                Receiver Side (Data Storage)
┌──────────────────────────┐            ┌──────────────────────────┐
│  Joget Instance (9999)   │            │  Joget Instance (8080)   │
│                          │            │                          │
│  ┌────────────────────┐  │            │  ┌────────────────────┐  │
│  │ Data Entry Form    │  │            │  │ Parent Form        │  │
│  │ (User fills out)   │  │            │  │ (Auto-created)     │  │
│  └────────┬───────────┘  │            │  └────────┬───────────┘  │
│           │              │            │           │              │
│  ┌────────▼───────────┐  │            │  ┌────────▼───────────┐  │
│  │ DocSubmitter       │  │ HTTP POST  │  │ ProcessingServer   │  │
│  │ Plugin             │──┼────────────┼─▶│ Plugin             │  │
│  │                    │  │ GovStack   │  │                    │  │
│  │ Reads:             │  │ JSON       │  │ Reads:             │  │
│  │ - sender YAML      │  │            │  │ - receiver YAML    │  │
│  │ - form data        │  │            │  │ - form mappings    │  │
│  │                    │  │            │  │                    │  │
│  │ Transforms to:     │  │            │  │ Transforms to:     │  │
│  │ - GovStack JSON    │  │            │  │ - Multiple forms   │  │
│  └────────────────────┘  │            │  │ - Grid records     │  │
│                          │            │  └────────────────────┘  │
│  YAML Config:            │            │                          │
│  service_name.yml        │            │  YAML Config:            │
│  (Joget→JSON mappings)   │            │  service_name.yml        │
│                          │            │  (JSON→Joget mappings)   │
└──────────────────────────┘            └──────────────────────────┘

Key Point: SAME PLUGIN CODE handles farmers, students, licenses, etc.
           Only YAML files change for each service type!
```

[SCREENSHOT: Full system architecture diagram showing sender, HTTP transmission, receiver, and database]

---

## 2. Prerequisites

### 2.1 Required Software

Before starting, ensure you have:

#### ✅ Joget DX 8.1 Enterprise (Two Instances)
- **Sender Instance** (e.g., localhost:9999): For data entry
- **Receiver Instance** (e.g., localhost:8080): For data storage
- Both must be running and accessible
- Enterprise edition required (Community edition not supported)

[SCREENSHOT: Joget login screen showing version 8.1]

#### ✅ MySQL Database (5.7 or higher)
- Accessible from both Joget instances
- Separate databases for sender and receiver recommended
- Credentials ready (username, password, database name)

#### ✅ Java Development Kit (JDK 11)
- For building plugin JAR files
- Verify: `java -version` should show 1.11.x

#### ✅ Apache Maven (3.6+)
- For compiling and packaging plugins
- Verify: `mvn -version` should show 3.6 or higher

[SCREENSHOT: Terminal showing java -version and mvn -version output]

#### ✅ Text Editor with YAML Support
- Recommended: VS Code with YAML extension
- Alternative: IntelliJ IDEA, Sublime Text, or Atom
- Syntax highlighting and validation are essential

### 2.2 Required Knowledge

**🟢 Basic Level (Required for all users):**
- Basic understanding of forms and data fields
- Familiarity with file systems and directories
- Ability to edit text files
- Basic understanding of key-value pairs

**🟡 Intermediate Level (Helpful but not required):**
- YAML syntax basics (indentation, lists, objects)
- JSON structure understanding
- Basic database concepts (tables, fields, relationships)
- HTTP request/response concepts

**🔴 Advanced Level (Only for advanced topics):**
- JSONPath expressions
- Regular expressions for transformations
- Joget form builder experience
- SQL queries for verification

### 2.3 System Access Checklist

Before proceeding, verify you have:

```
□ Access to sender Joget admin console (http://localhost:9999)
□ Access to receiver Joget admin console (http://localhost:8080)
□ Database connection credentials for both instances
□ Write permissions to Joget plugin directories:
  - /path/to/joget-sender/wflow/app_plugins/
  - /path/to/joget-receiver/wflow/app_plugins/
□ Write permissions to plugin source code directories:
  - /path/to/doc-submitter/src/main/resources/docs-metadata/
  - /path/to/processing-server/src/main/resources/docs-metadata/
□ Ability to restart Joget instances if needed
□ Terminal/command line access for Maven builds
```

### 2.4 Plugin Installation Verification

Let's verify the plugins are installed correctly:

#### Check Sender Plugin (DocSubmitter)

1. Navigate to sender Joget: `http://localhost:9999`
2. Login as admin
3. Go to: **Settings → Manage Plugins**
4. Search for: "DocSubmitter" or "GovStack"
5. Verify: Plugin shows as "Active"

[SCREENSHOT: Joget Manage Plugins page showing DocSubmitter plugin active]

#### Check Receiver Plugin (ProcessingServer)

1. Navigate to receiver Joget: `http://localhost:8080`
2. Login as admin
3. Go to: **Settings → Manage Plugins**
4. Search for: "ProcessingServer" or "GovStack"
5. Verify: Plugin shows as "Active"

[SCREENSHOT: Joget Manage Plugins page showing ProcessingServer plugin active]

#### Check Plugin Directories

Verify JAR files exist:

```bash
# Check sender plugin
ls -lh /path/to/joget-sender/wflow/app_plugins/doc-submitter-8.1-SNAPSHOT.jar

# Check receiver plugin
ls -lh /path/to/joget-receiver/wflow/app_plugins/processing-server-8.1-SNAPSHOT.jar
```

Expected output:
```
-rw-r--r--  1 user  staff   6.3M Oct 29 10:00 doc-submitter-8.1-SNAPSHOT.jar
-rw-r--r--  1 user  staff   2.7M Oct 29 10:00 processing-server-8.1-SNAPSHOT.jar
```

[SCREENSHOT: Terminal showing ls -lh output for both plugin JARs]

### 2.5 Test Database Connection

Verify database connectivity from both Joget instances:

```bash
# From sender instance
mysql -h localhost -P 9990 -u root -p sender_db -e "SELECT 1"

# From receiver instance
mysql -h localhost -P 3307 -u root -p receiver_db -e "SELECT 1"
```

Expected output:
```
+---+
| 1 |
+---+
| 1 |
+---+
```

### 2.6 Directory Structure Setup

Create a working directory for your services:

```bash
# Create workspace
mkdir -p ~/govstack-services
cd ~/govstack-services

# Create subdirectories
mkdir -p receiver-configs    # Receiver YAML files
mkdir -p sender-configs      # Sender YAML files
mkdir -p examples            # Example services
mkdir -p tools               # Helper scripts
mkdir -p docs                # Documentation
```

Your directory structure should look like:

```
~/govstack-services/
├── receiver-configs/
│   ├── farmers_registry.yml
│   └── (future services here)
├── sender-configs/
│   ├── farmers_registry.yml
│   └── (future services here)
├── examples/
│   ├── minimal-contact-form/
│   └── (example services here)
├── tools/
│   ├── yaml-validator.sh
│   └── (helper scripts here)
└── docs/
    └── (your documentation here)
```

[SCREENSHOT: File explorer showing the created directory structure]

---

## 3. Your First Service in 5 Minutes

Let's create the simplest possible service to understand the basic flow. We'll create a **Contact Form** that captures just a name and email.

### 3.1 Overview - What We'll Build

**Service Name**: `contact_form`
**Purpose**: Capture contact information (name, email)
**Forms**: 1 form (no grids or complex structures)
**Time**: 5 minutes

**Data Flow**:
```
User fills form → DocSubmitter reads data → Sends to receiver → ProcessingServer saves data
```

### 3.2 Step 1: Create Receiver Form (2 minutes)

#### Access Receiver Joget

1. Open browser: `http://localhost:8080`
2. Login as admin
3. Navigate to: **Design → Form Builder**
4. Click: **[+ New Form]**

[SCREENSHOT: Form Builder main page with New Form button highlighted]

#### Configure Form Properties

1. **Form ID**: `contactForm`
2. **Form Name**: `Contact Form`
3. **Table Name**: `contact_submissions`
4. Click: **[Save]**

[SCREENSHOT: Form Properties dialog with fields filled in]

#### Add Fields to Form

Drag and drop fields from left panel:

1. **Text Field**:
   - Field ID: `full_name`
   - Label: `Full Name`
   - Required: ✓

2. **Email Field**:
   - Field ID: `email`
   - Label: `Email Address`
   - Required: ✓

3. **Hidden Field** (Important!):
   - Field ID: `parent_id`
   - (This links to parent form - required by system)

[SCREENSHOT: Form Builder showing the three fields added]

#### Save and Publish

1. Click: **[Save]** (top right)
2. Click: **[Publish]**
3. Verify: Form shows in form list

[SCREENSHOT: Form published successfully message]

### 3.3 Step 2: Create Parent Form (1 minute)

#### Create New Form

1. In Form Builder, click: **[+ New Form]**
2. **Form ID**: `contactRegistrationForm`
3. **Form Name**: `Contact Registration`
4. **Table Name**: `contacts_registry`

[SCREENSHOT: Parent form properties dialog]

#### Add Parent Reference Field

1. Drag **Text Field** to form
2. Field ID: `contact_data`
3. Label: `Contact Data Reference`
4. (This will auto-populate with UUID)

[SCREENSHOT: Parent form with contact_data field]

5. Click: **[Save]** and **[Publish]**

### 3.4 Step 3: Create Receiver YAML (1 minute)

Create file: `~/govstack-services/receiver-configs/contact_form.yml`

```yaml
service:
  id: contact_form
  name: "Contact Form Service"
  version: "1.0"
  govstackVersion: "1.0"
  metadataVersion: "1.0.0"
  lastUpdated: "2025-10-29"

  serviceConfig:
    # Parent form that holds references
    parentFormId: "contactRegistrationForm"

    # Fields in parent form that reference child forms
    parentReferenceFields:
      - "contact_data"

    # Map sections to forms
    sectionToFormMap:
      contactInfo: "contactForm"

    # No grids in this simple example
    gridMappings: {}

# Field mappings - how GovStack JSON maps to Joget forms
formMappings:
  contactInfo:
    type: form
    formId: contactForm
    fields:
      - joget: full_name
        govstack: name.text
        jsonPath: $.name.text

      - joget: email
        govstack: extension.email
        jsonPath: $.extension.email
```

**Key Points**:
- `serviceConfig.parentFormId`: Must match parent form ID exactly
- `serviceConfig.parentReferenceFields`: Must match parent form field ID
- `sectionToFormMap`: Maps section names to form IDs
- `formMappings`: Maps GovStack JSON fields to Joget form fields

[SCREENSHOT: YAML file open in VS Code with syntax highlighting]

### 3.5 Step 4: Deploy Receiver YAML (30 seconds)

Copy YAML to receiver plugin:

```bash
# Copy to processing-server resources
cp ~/govstack-services/receiver-configs/contact_form.yml \
   /path/to/processing-server/src/main/resources/docs-metadata/
```

**Rebuild receiver plugin**:

```bash
cd /path/to/processing-server
mvn clean package -Dmaven.test.skip=true
```

Wait for build to complete (~20 seconds):
```
[INFO] BUILD SUCCESS
[INFO] Total time:  18.234 s
```

[SCREENSHOT: Terminal showing Maven build success]

**Deploy to receiver Joget**:

```bash
cp target/processing-server-8.1-SNAPSHOT.jar \
   /path/to/joget-receiver/wflow/app_plugins/
```

**Restart receiver Joget** (or reload plugins from Settings)

### 3.6 Step 5: Test the Service (30 seconds)

#### Test with curl

Send a test submission:

```bash
curl -X POST http://localhost:8080/jw/api/govstack/registration/services/contact_form/applications \
  -H "Content-Type: application/json" \
  -d '{
    "name": {
      "text": "John Doe"
    },
    "extension": {
      "email": "john.doe@example.com"
    }
  }'
```

**Expected Response**:
```json
{
  "status": "success",
  "message": "Application submitted successfully",
  "applicationId": "a3b5c7d9-e1f2-4a5b-8c9d-0e1f2a3b4c5d"
}
```

[SCREENSHOT: Terminal showing curl command and successful response]

#### Verify in Database

```bash
mysql -h localhost -P 3307 -u root -p receiver_db

mysql> SELECT * FROM contacts_registry;
+--------------------------------------+--------------------------------------+
| id                                   | contact_data                         |
+--------------------------------------+--------------------------------------+
| a3b5c7d9-e1f2-4a5b-8c9d-0e1f2a3b4c5d | a3b5c7d9-e1f2-4a5b-8c9d-0e1f2a3b4c5d |
+--------------------------------------+--------------------------------------+

mysql> SELECT * FROM contact_submissions;
+--------------------------------------+-----------+----------------------+--------------------------------------+
| id                                   | full_name | email                | parent_id                            |
+--------------------------------------+-----------+----------------------+--------------------------------------+
| generated-uuid                       | John Doe  | john.doe@example.com | a3b5c7d9-e1f2-4a5b-8c9d-0e1f2a3b4c5d |
+--------------------------------------+-----------+----------------------+--------------------------------------+
```

[SCREENSHOT: MySQL terminal showing the two tables with data]

### 3.7 Congratulations!

🎉 You've just created your first GovStack service!

**What you accomplished:**
- Created forms in receiver Joget
- Wrote YAML configuration
- Deployed the plugin
- Successfully submitted and stored data

**Next Steps:**
- Continue to Part 2 to learn planning principles
- Or jump to Part 3 for complete Student Enrollment tutorial
- Or explore Part 7 for more examples

---

## 4. Architecture Overview

### 4.1 The Big Picture

The GovStack Multi-Service Plugin System consists of three main components:

```
┌─────────────────────────────────────────────────────────────────┐
│                         COMPONENT OVERVIEW                       │
└─────────────────────────────────────────────────────────────────┘

1. SENDER SIDE (DocSubmitter Plugin)
   ┌─────────────────────────────────────┐
   │ • Joget instance where users enter │
   │   data through forms                │
   │ • DocSubmitter plugin reads form    │
   │   data and converts to GovStack     │
   │   JSON format                       │
   │ • Sends via HTTP POST to receiver   │
   └─────────────────────────────────────┘

2. TRANSMISSION (GovStack JSON)
   ┌─────────────────────────────────────┐
   │ • Standardized JSON format          │
   │ • Can include multiple sections     │
   │ • Supports arrays (grids)           │
   │ • Carries UUID for record tracking  │
   └─────────────────────────────────────┘

3. RECEIVER SIDE (ProcessingServer Plugin)
   ┌─────────────────────────────────────┐
   │ • Joget instance with data storage  │
   │   forms                             │
   │ • ProcessingServer plugin receives  │
   │   JSON and maps to multiple forms   │
   │ • Creates parent record + child     │
   │   records + grid records            │
   └─────────────────────────────────────┘
```

[SCREENSHOT: Architecture diagram with three components highlighted]

### 4.2 Why This Architecture?

**Problem**: Traditional approach requires separate code for each service type
- Farmer registration → FarmerPlugin.java
- Student enrollment → StudentPlugin.java
- Business license → LicensePlugin.java

**Solution**: Generic architecture with YAML configuration
- ANY service type → Same plugin + service-specific YAML

**Benefits**:
1. **Rapid Deployment**: Add new services in hours
2. **Maintainability**: One codebase for all services
3. **Consistency**: Same data flow patterns
4. **Testability**: Test once, works for all services
5. **Scalability**: No code changes for new services

### 4.3 Data Flow Deep Dive

Let's trace a sample submission through the entire system:

#### Phase 1: User Submission (Sender Side)

```
User fills form on sender Joget:
┌─────────────────────────────┐
│ Student Enrollment Form     │
├─────────────────────────────┤
│ Name: Jane Smith            │
│ Email: jane@example.com     │
│ Grade: 10                   │
│ [Submit]                    │
└─────────────────────────────┘
         │
         ▼
Process executes with DocSubmitter tool configured
```

[SCREENSHOT: Sample form filled with data]

#### Phase 2: Data Extraction (DocSubmitter Plugin)

```
DocSubmitter Plugin:
1. Reads form data from Joget FormData API
2. Loads sender YAML: student_enrollment.yml
3. Maps Joget fields → GovStack JSON using YAML mappings
4. Constructs HTTP POST request
```

**Sender YAML Snippet**:
```yaml
formMappings:
  studentBasicInfo:
    fields:
      - joget: full_name          # From Joget form
        govstack: name.text       # To GovStack JSON
      - joget: email
        govstack: extension.contact.email
```

**Resulting JSON**:
```json
{
  "name": {
    "text": "Jane Smith"
  },
  "extension": {
    "contact": {
      "email": "jane@example.com"
    },
    "academics": {
      "gradeLevel": "10"
    }
  }
}
```

[SCREENSHOT: JSON payload in browser developer tools or Postman]

#### Phase 3: Transmission

```
HTTP POST to receiver:
URL: http://localhost:8080/jw/api/govstack/registration/services/student_enrollment/applications
Headers:
  Content-Type: application/json
Body:
  {GovStack JSON payload}
```

[SCREENSHOT: Network request in browser developer tools showing the POST]

#### Phase 4: Data Reception (ProcessingServer Plugin)

```
ProcessingServer Plugin:
1. Receives HTTP POST request
2. Extracts serviceId from URL: "student_enrollment"
3. Loads receiver YAML: student_enrollment.yml
4. Parses incoming JSON
5. Maps JSON → Multiple Joget forms using YAML
6. Creates database records:
   ├── Parent record in students_registry
   ├── Child record in app_fd_student_basic_info
   ├── Child record in app_fd_student_academics
   └── Grid records in app_fd_student_courses
```

**Receiver YAML Snippet**:
```yaml
serviceConfig:
  parentFormId: "studentRegistrationForm"
  sectionToFormMap:
    studentBasicInfo: "studentBasicInfo"
    studentAcademics: "studentAcademics"

formMappings:
  studentBasicInfo:
    fields:
      - joget: full_name          # To Joget form
        govstack: name.text       # From GovStack JSON
        jsonPath: $.name.text
```

[SCREENSHOT: Joget form list showing the multiple forms created]

#### Phase 5: Data Storage (Database)

```
MySQL Tables Created:

students_registry (parent):
┌──────────────────────┬─────────────┬───────────────┬───────────────┐
│ id                   │ basic_data  │ academics_data│ dateCreated   │
├──────────────────────┼─────────────┼───────────────┼───────────────┤
│ uuid-1234            │ uuid-1234   │ uuid-1234     │ 2025-10-29    │
└──────────────────────┴─────────────┴───────────────┴───────────────┘

app_fd_student_basic_info (child):
┌──────────────────────┬────────────┬────────────────────┬───────────────┐
│ id                   │ full_name  │ email              │ parent_id     │
├──────────────────────┼────────────┼────────────────────┼───────────────┤
│ generated-uuid       │ Jane Smith │ jane@example.com   │ uuid-1234     │
└──────────────────────┴────────────┴────────────────────┴───────────────┘

app_fd_student_academics (child):
┌──────────────────────┬────────────┬───────────────┐
│ id                   │ grade_level│ parent_id     │
├──────────────────────┼────────────┼───────────────┤
│ generated-uuid       │ 10         │ uuid-1234     │
└──────────────────────┴────────────┴───────────────┘
```

[SCREENSHOT: MySQL Workbench or terminal showing these three tables with data]

### 4.4 The Role of YAML Configuration

YAML files are the heart of the multi-service architecture. They define:

**1. Service Identification**
```yaml
service:
  id: student_enrollment          # Unique service identifier
  name: "Student Enrollment"      # Human-readable name
  version: "1.0"                  # Service version
```

**2. Form Structure**
```yaml
serviceConfig:
  parentFormId: "studentRegistrationForm"   # Main form
  parentReferenceFields:                     # Links to child forms
    - "basic_data"
    - "academics_data"
```

**3. Data Mappings**
```yaml
formMappings:
  sectionName:
    formId: targetForm
    fields:
      - joget: form_field_id
        govstack: json.path.here
        jsonPath: $.json.path.here
```

**4. Grid Configurations**
```yaml
gridMappings:
  coursesGrid:
    formId: "studentCoursesForm"
    parentField: "student_id"
```

[SCREENSHOT: Split screen showing YAML file and resulting database structure]

### 4.5 Key Design Principles

The architecture follows these principles:

#### 1. Configuration Over Code
```
❌ OLD WAY: Write Java code for each service
✅ NEW WAY: Write YAML config for each service
```

#### 2. Fail-Fast Validation
```
If required YAML field missing:
  → Throw ConfigurationException immediately
  → Log clear error message
  → Don't proceed with submission
```

#### 3. Explicit Configuration
```
❌ NO silent fallbacks or defaults
✅ Every service must specify all required fields
```

#### 4. Convention-Based Naming
```
Process name: {serviceId}_submission
URL pattern: /services/{serviceId}/applications
YAML file: {serviceId}.yml
```

#### 5. Separation of Concerns
```
Sender Side:  Joget Form Data → GovStack JSON
Receiver Side: GovStack JSON → Joget Form Data
```

---

## 5. Key Concepts

Before diving into creating your own services, let's understand the key concepts.

### 5.1 Service ID

The **Service ID** is the unique identifier for your service throughout the system.

**Naming Rules**:
- Lowercase only
- Underscores for spaces (e.g., `student_enrollment`)
- No special characters
- Descriptive and concise

**Where it appears**:
1. YAML file name: `student_enrollment.yml`
2. URL path: `/services/student_enrollment/applications`
3. Process configuration
4. Log messages

[SCREENSHOT: Service ID highlighted in multiple locations]

**Example Service IDs**:
```
✅ GOOD:
- farmers_registry
- student_enrollment
- business_license
- subsidy_application

❌ BAD:
- StudentEnrollment     (uppercase)
- student-enrollment    (hyphens)
- student enrollment    (spaces)
- student_app_v2.0      (version in ID)
```

### 5.2 Parent-Child Form Relationships

The system uses a **parent-child** form structure:

```
Parent Form (Registry)
├── Child Form 1 (Section 1 data)
├── Child Form 2 (Section 2 data)
├── Child Form 3 (Section 3 data)
└── Grid Form (Array data)
```

[SCREENSHOT: Diagram showing parent form with UUID connecting to child forms]

**Parent Form**:
- Contains ONE record per submission
- Holds UUID that links all related records
- Contains reference fields pointing to child forms
- Table name typically ends with `_registry`

**Child Forms**:
- Contain section-specific data
- Include `parent_id` field with UUID from parent
- One child record per parent record
- Can have multiple child forms per parent

**Why This Structure?**
- **Flexibility**: Each section can have different fields
- **Organization**: Data logically grouped by section
- **Scalability**: Easy to add new sections
- **Joget Compatibility**: Works with Joget's subform system

### 5.3 Section-to-Form Mapping

**Sections** are logical groupings of data in your service.
**Forms** are physical Joget forms that store the data.

**Mapping Configuration**:
```yaml
sectionToFormMap:
  studentBasicInfo: "studentBasicInfo"        # Section → Form
  studentAcademics: "studentAcademics"
  studentGuardian: "studentGuardian"
```

[SCREENSHOT: Side-by-side showing section names in JSON and form names in Joget]

**Important**: Section names in YAML must match section names in formMappings:

```yaml
sectionToFormMap:
  studentBasicInfo: "studentBasicInfo"    # Section name

formMappings:
  studentBasicInfo:                       # Must match!
    type: form
    formId: studentBasicInfo
    fields: [...]
```

### 5.4 Field Mappings

Field mappings define how data flows between systems:

**Sender Side (Joget → JSON)**:
```yaml
fields:
  - joget: full_name              # Field ID in Joget form
    govstack: name.text           # Path in GovStack JSON
```

**Receiver Side (JSON → Joget)**:
```yaml
fields:
  - joget: full_name              # Field ID in Joget form
    govstack: name.text           # Path in GovStack JSON
    jsonPath: $.name.text         # JSONPath expression
```

[SCREENSHOT: Mapping diagram showing Joget field → JSON → Joget field]

**JSONPath Syntax**:
- `$` = Root of JSON
- `.` = Object property accessor
- `[0]` = Array index accessor
- Example: `$.extension.contact.email` = Access nested email field

### 5.5 Grid Mappings (Arrays)

**Grids** represent one-to-many relationships (e.g., one student, many courses).

**Configuration**:
```yaml
gridMappings:
  studentCourses:                    # Grid name
    formId: "studentCoursesForm"     # Form to store records
    parentField: "student_id"        # Field linking to parent
```

**Data Flow**:
```
JSON Array:
{
  "extension": {
    "courses": [
      {"courseCode": "MATH101", "courseName": "Algebra"},
      {"courseCode": "ENG101", "courseName": "English"}
    ]
  }
}

↓ ProcessingServer processes ↓

Database Records:
app_fd_student_courses:
┌───────────────┬─────────────┬───────────┬─────────────┐
│ id            │ course_code │ course_name│ student_id  │
├───────────────┼─────────────┼───────────┼─────────────┤
│ uuid-1        │ MATH101     │ Algebra   │ parent-uuid │
│ uuid-2        │ ENG101      │ English   │ parent-uuid │
└───────────────┴─────────────┴───────────┴─────────────┘
```

[SCREENSHOT: JSON array shown alongside resulting database table]

### 5.6 Data Transformations

Sometimes data needs to be transformed during mapping:

**Available Transformations**:
```yaml
# Date format conversion
transform: date_ISO8601_to_date
# Input: "2025-10-29T10:30:00Z"
# Output: "2025-10-29"

# String to number
transform: numeric
# Input: "123"
# Output: 123

# Boolean conversion
transform: boolean
# Input: "true", "1", "yes"
# Output: true

# Uppercase
transform: uppercase
# Input: "john doe"
# Output: "JOHN DOE"
```

**Usage**:
```yaml
fields:
  - joget: birth_date
    govstack: birthDate
    jsonPath: $.birthDate
    transform: date_ISO8601_to_date    # Apply transformation
```

[SCREENSHOT: Before/after showing date transformation]

### 5.7 Parent Reference Fields

**Parent Reference Fields** link the parent form to child forms.

**Configuration**:
```yaml
parentReferenceFields:
  - "basic_data"
  - "academics_data"
  - "guardian_data"
```

**Database Result**:
```
students_registry table:
┌──────────────────┬─────────────┬────────────────┬──────────────┐
│ id (UUID)        │ basic_data  │ academics_data │ guardian_data│
├──────────────────┼─────────────┼────────────────┼──────────────┤
│ abc-123-def      │ abc-123-def │ abc-123-def    │ abc-123-def  │
└──────────────────┴─────────────┴────────────────┴──────────────┘
```

**Purpose**:
- Joget's subform system uses these references
- Enables viewing related data in forms
- Required for Joget's form navigation

[SCREENSHOT: Joget form showing parent-child navigation using these references]

### 5.8 Configuration Exception Handling

The system uses **fail-fast** approach for configuration errors:

**If YAML is missing required fields**:
```
❌ System WILL NOT use defaults
❌ System WILL NOT silently skip fields
✅ System WILL throw ConfigurationException
✅ System WILL log clear error message
```

**Example Error**:
```
ConfigurationException: Grid form mapping not found in YAML configuration for grid: studentCourses
Please add gridMappings.studentCourses.formId to serviceConfig in your service YAML file.
```

[SCREENSHOT: Log file showing ConfigurationException with clear message]

**Why Fail-Fast?**
- Catches configuration errors immediately
- Prevents partial/incorrect data storage
- Makes debugging easier
- Ensures all services are properly configured

### 5.9 URL Construction

Understanding how URLs are constructed helps with configuration:

**Full URL Provided**:
```yaml
apiUrl: "http://localhost:8080/jw/api/govstack/registration/services/student_enrollment/applications"
```
→ Used as-is, no modification

**Base URL Provided**:
```yaml
apiUrl: "http://localhost:8080/jw/api/govstack/registration"
apiId: "registration"
serviceId: "student_enrollment"
```
→ Constructs: `{baseUrl}/{apiId}/services/{serviceId}/applications`

[SCREENSHOT: Process configuration showing URL field]

### 5.10 Build and Deployment Cycle

Understanding the deployment cycle:

```
1. YAML Created/Modified
   ↓
2. Copy to src/main/resources/docs-metadata/
   ↓
3. Maven Build
   mvn clean package -Dmaven.test.skip=true
   ↓
4. Copy JAR to Joget plugins directory
   cp target/*.jar /path/to/joget/wflow/app_plugins/
   ↓
5. Restart Joget OR Reload Plugins
   ↓
6. Test Submission
```

[SCREENSHOT: Terminal showing the build and deploy commands]

**Important**: YAML changes require rebuild and redeploy of JAR file.

---

**End of Part 1: Quick Start**

You now understand:
- ✅ Basic architecture and data flow
- ✅ Key concepts (service ID, parent-child, mappings)
- ✅ How to create a minimal service
- ✅ Prerequisites and setup requirements

**Next**: Continue to Part 2 (Planning Your Service) or jump to Part 3 (Complete Tutorial) for hands-on implementation.

[Continue to Part 2 →](#part-2-planning-your-service)

---

# PART 2: PLANNING YOUR SERVICE

---

## 6. Service Design Principles

Before creating any forms or YAML files, proper planning ensures a smooth implementation. This section guides you through designing your service.

### 6.1 Start with the User Journey

**Think from the user's perspective first:**

```
1. What is the user trying to accomplish?
2. What information do we need to collect?
3. How is the data logically grouped?
4. What relationships exist in the data?
```

**Example: Student Enrollment**

```
User Goal: Enroll a student in school

Information Needed:
├── Personal Information (name, DOB, ID)
├── Academic Background (previous school, grade)
├── Guardian Information (parent/guardian contact)
└── Course Selections (multiple courses)

Logical Groups:
├── Section 1: Student Basic Info (single form)
├── Section 2: Academic Background (single form)
├── Section 3: Guardian Info (single form)
└── Section 4: Courses (grid - multiple records)
```

[SCREENSHOT: User journey diagram showing enrollment flow]

### 6.2 Design Principles

Follow these principles for successful services:

#### Principle 1: Keep It Simple

```
✅ GOOD: Start with minimal viable service
- Core fields only
- Simple structure
- No complex transformations

❌ BAD: Overengineered first version
- Optional fields for edge cases
- Complex nested structures
- Many conditional validations
```

**Example**:
```yaml
# ✅ Simple First Version
Student Enrollment:
  - Name, DOB, Email (required)
  - Phone (optional)

# Later iterations can add:
  - Multiple addresses
  - Multiple guardians
  - Document uploads
```

#### Principle 2: Group Related Data

```
✅ GOOD: Logical sections
studentBasicInfo:
  - name, dob, gender, ID

studentAcademics:
  - grade, program, previousSchool

❌ BAD: Mixed concerns
studentForm:
  - name, grade, guardianName, courseCode...
```

[SCREENSHOT: Diagram showing good vs bad data grouping]

#### Principle 3: Plan for Relationships

```
One-to-One:  Student → Basic Info (use child form)
One-to-Many: Student → Courses (use grid)
```

**Decision Tree**:
```
Is there exactly ONE instance of this data per submission?
├── YES → Child Form (e.g., Basic Info)
└── NO → Grid (e.g., Multiple Courses)
```

[SCREENSHOT: Decision tree flowchart]

#### Principle 4: Use Consistent Naming

```
✅ GOOD Naming Conventions:
- Service ID: student_enrollment (lowercase, underscores)
- Form IDs: studentBasicInfo (camelCase)
- Field IDs: full_name (lowercase, underscores)
- Section names: Match form names

❌ BAD Naming:
- Mixed cases: StudentEnrollment, student_basic_info
- Inconsistent: basicInfo vs basic_information
- Unclear: form1, data2
```

#### Principle 5: Explicit Over Implicit

```
✅ Always specify in YAML:
- All form IDs
- All field mappings
- All parent references

❌ Never rely on:
- Default values
- Implicit behaviors
- Hardcoded fallbacks
```

### 6.3 Service Complexity Assessment

Use this guide to assess your service complexity:

#### Simple Service (1-2 hours to implement)
```
Characteristics:
- 1-3 forms
- No grids
- Simple field types (text, email, date)
- No transformations
- Flat JSON structure

Examples:
- Contact form
- Job application (basic)
- Feedback form
```

#### Medium Service (3-5 hours to implement)
```
Characteristics:
- 4-7 forms
- 1-2 grids
- Some transformations (dates, numbers)
- Nested JSON (2-3 levels)
- Some conditional fields

Examples:
- Student enrollment
- Farmer registration
- Event registration
```

#### Complex Service (1-2 days to implement)
```
Characteristics:
- 8+ forms
- 3+ grids
- Complex transformations
- Deeply nested JSON (4+ levels)
- Many conditional validations
- Document uploads

Examples:
- Business license application
- Tender submission
- Grant application
```

[SCREENSHOT: Complexity assessment matrix]

**Recommendation**: Start with a simple or medium service for your first implementation.

### 6.4 Planning Checklist

Use this checklist before starting implementation:

```
□ Service Overview
  □ Service ID chosen (lowercase, underscores)
  □ Service name defined
  □ Purpose clearly stated

□ Data Requirements
  □ All required fields identified
  □ Optional fields documented
  □ Field types determined (text, number, date, etc.)
  □ Field validation rules noted

□ Structure Planning
  □ Logical sections identified
  □ One-to-one vs one-to-many relationships determined
  □ Number of forms calculated
  □ Number of grids calculated

□ Naming Conventions
  □ Parent form name decided
  □ Child form names decided
  □ Grid form names decided
  □ Section names defined

□ Technical Considerations
  □ Data transformations identified
  □ JSONPath expressions planned
  □ Database table names chosen
  □ Foreign key relationships mapped

□ Implementation Plan
  □ Order of form creation decided
  □ YAML structure sketched
  □ Test data prepared
  □ Verification queries written
```

[SCREENSHOT: Completed planning checklist example]

---

## 7. Data Modeling

Proper data modeling is crucial for a successful service implementation.

### 7.1 Entity-Relationship Design

Start by identifying entities (things) and their relationships:

**Example: Student Enrollment Service**

```
Entities:
┌────────────────┐
│ STUDENT        │ (main entity)
│ - studentId    │
│ - enrollDate   │
└────────────────┘
        │
        │ has one
        ▼
┌────────────────┐
│ BASIC INFO     │ (1:1 relationship)
│ - name         │
│ - dob          │
│ - email        │
└────────────────┘
        │
        │ has one
        ▼
┌────────────────┐
│ ACADEMICS      │ (1:1 relationship)
│ - grade        │
│ - program      │
└────────────────┘
        │
        │ has one
        ▼
┌────────────────┐
│ GUARDIAN       │ (1:1 relationship)
│ - guardianName │
│ - contact      │
└────────────────┘
        │
        │ has many
        ▼
┌────────────────┐
│ COURSES        │ (1:N relationship)
│ - courseCode   │
│ - courseName   │
└────────────────┘
```

[SCREENSHOT: ER diagram with entities and relationships]

### 7.2 Mapping Entities to Forms

Each entity becomes a form in Joget:

```
Entity: STUDENT
↓
Form: studentRegistrationForm (parent)
Table: students_registry
Fields:
  - id (UUID, primary key)
  - basic_data (reference to basic info)
  - academics_data (reference to academics)
  - guardian_data (reference to guardian)
  - courses (reference to courses)
  - dateCreated, dateModified

Entity: BASIC INFO
↓
Form: studentBasicInfo (child)
Table: app_fd_student_basic_info
Fields:
  - id (auto-generated)
  - full_name
  - dob
  - email
  - parent_id (foreign key to parent)

Entity: COURSES
↓
Form: studentCoursesForm (grid)
Table: app_fd_student_courses
Fields:
  - id (auto-generated)
  - course_code
  - course_name
  - credits
  - student_id (foreign key to parent)
```

[SCREENSHOT: Entity-to-form mapping diagram]

### 7.3 Field Type Selection

Choose appropriate Joget field types:

| Data Type | Joget Field Type | Example |
|-----------|-----------------|---------|
| Short text | Text Field | Name, Email, ID |
| Long text | Text Area | Comments, Description |
| Number | Number Field | Age, Credits, Amount |
| Decimal | Decimal Field | GPA, Price |
| Date | Date Picker | Birth Date, Enrollment Date |
| Yes/No | Checkbox | Agree to Terms |
| Selection | Select Box | Country, Grade Level |
| Multiple selections | Checkboxes | Course Interests |
| File | File Upload | Certificate, Photo |
| Reference | Hidden Field | parent_id, UUID |

[SCREENSHOT: Joget Form Builder showing different field types]

**Common Field Patterns**:

```yaml
# Text Field
- joget: full_name
  fieldType: text
  required: true
  maxLength: 100

# Email Field
- joget: email
  fieldType: email
  required: true
  validation: email

# Date Field
- joget: birth_date
  fieldType: date
  required: true
  format: yyyy-MM-dd

# Number Field
- joget: age
  fieldType: number
  required: false
  min: 0
  max: 150

# Select Box
- joget: grade_level
  fieldType: selectbox
  options:
    - value: "9"
      label: "Grade 9"
    - value: "10"
      label: "Grade 10"
```

### 7.4 Relationship Mapping

Understanding how to model different relationship types:

#### One-to-One Relationships

```
Parent: Student (UUID: abc-123)
  ↓
Child: Basic Info (parent_id: abc-123)

Configuration:
sectionToFormMap:
  studentBasicInfo: "studentBasicInfo"

parentReferenceFields:
  - "basic_data"
```

#### One-to-Many Relationships

```
Parent: Student (UUID: abc-123)
  ↓
Children: Courses
  - Course 1 (student_id: abc-123)
  - Course 2 (student_id: abc-123)
  - Course 3 (student_id: abc-123)

Configuration:
gridMappings:
  studentCourses:
    formId: "studentCoursesForm"
    parentField: "student_id"
```

[SCREENSHOT: Database tables showing one-to-many relationship]

#### Many-to-Many Relationships

For many-to-many (e.g., students enrolled in courses, courses having students), use junction table:

```
Student Table:
┌────────────┐
│ student_id │
│ name       │
└────────────┘

Enrollment Table (junction):
┌────────────┬───────────┐
│ student_id │ course_id │
└────────────┴───────────┘

Course Table:
┌───────────┐
│ course_id │
│ name      │
└───────────┘
```

**Note**: Many-to-many is advanced - consider if you really need it or if one-to-many suffices.

### 7.5 Data Model Templates

Use these templates as starting points:

#### Template 1: Simple Registration
```
Service: Simple Registration (e.g., newsletter signup)

Forms:
├── registrationForm (parent)
└── basicInfo (child)
    ├── name
    ├── email
    └── phone
```

#### Template 2: Person with Details
```
Service: Person Registration (e.g., member registration)

Forms:
├── personRegistrationForm (parent)
├── personalInfo (child)
│   ├── name, dob, gender
├── contactInfo (child)
│   ├── email, phone, address
└── preferencesInfo (child)
    ├── language, notifications
```

#### Template 3: Person with Related Items
```
Service: Person with Items (e.g., farmer with crops)

Forms:
├── farmerRegistrationForm (parent)
├── farmerBasicInfo (child)
│   ├── name, id, dob
├── farmerLocation (child)
│   ├── address, region, district
└── cropsGrid (grid)
    ├── crop_name, area, quantity
```

[SCREENSHOT: Three template structures side-by-side]

### 7.6 Planning Worksheet: Data Model

Use this worksheet to plan your data model:

```
SERVICE: _______________________________

PARENT ENTITY: _______________________________
Parent Form ID: _______________________________
Parent Table: _______________________________

CHILD ENTITIES (1:1 relationships):

1. Entity: _______________________________
   Form ID: _______________________________
   Table: _______________________________
   Fields:
   - _______________________________
   - _______________________________
   - _______________________________

2. Entity: _______________________________
   Form ID: _______________________________
   Table: _______________________________
   Fields:
   - _______________________________
   - _______________________________
   - _______________________________

GRID ENTITIES (1:N relationships):

1. Grid: _______________________________
   Form ID: _______________________________
   Table: _______________________________
   Parent Field: _______________________________
   Fields:
   - _______________________________
   - _______________________________
   - _______________________________
```

[SCREENSHOT: Completed worksheet example for student enrollment]

---

## 8. Form Structure Planning

With your data model defined, plan the physical form structure in Joget.

### 8.1 Parent Form Design

The parent form is the registry that holds all references.

**Template Structure**:
```
Form ID: {service}RegistrationForm
Table: {service}_registry

Required Fields:
├── id (Primary Key, Hidden, Auto UUID)
├── {section1}_data (Hidden Text Field)
├── {section2}_data (Hidden Text Field)
├── ...
├── dateCreated (Hidden, Auto)
├── dateModified (Hidden, Auto)
├── createdBy (Hidden, Auto)
└── modifiedBy (Hidden, Auto)
```

**Example: Student Enrollment Parent Form**
```
Form ID: studentRegistrationForm
Table: students_registry

Fields:
├── id (Hidden, UUID)
├── basic_data (Hidden Text)
├── academics_data (Hidden Text)
├── guardian_data (Hidden Text)
├── courses (Hidden Text)
├── dateCreated (Hidden)
└── dateModified (Hidden)
```

[SCREENSHOT: Joget form builder showing parent form with hidden fields]

**Important**: Parent form typically has NO visible fields - all references are hidden and auto-populated.

### 8.2 Child Form Design

Child forms contain the actual data for each section.

**Template Structure**:
```
Form ID: {sectionName}
Table: app_fd_{section_name}

Required Fields:
├── id (Primary Key, Auto-generated)
├── {data fields for this section}
├── parent_id (Hidden, stores UUID from parent)
├── dateCreated (Hidden, Auto)
└── dateModified (Hidden, Auto)
```

**Example: Student Basic Info Form**
```
Form ID: studentBasicInfo
Table: app_fd_student_basic_info

Fields:
├── id (Hidden, Auto)
├── full_name (Text Field, Required)
├── dob (Date Picker, Required)
├── gender (Select Box, Required)
├── student_id (Text Field, Required)
├── email (Email Field, Required)
├── phone (Text Field, Optional)
├── parent_id (Hidden)
├── dateCreated (Hidden)
└── dateModified (Hidden)
```

[SCREENSHOT: Child form in Joget with visible fields and hidden parent_id]

**Key Points**:
- `parent_id` field is MANDATORY in all child forms
- `parent_id` stores the UUID from parent form
- ProcessingServer automatically populates `parent_id`

### 8.3 Grid Form Design

Grid forms store array data (one-to-many relationships).

**Template Structure**:
```
Form ID: {gridName}Form
Table: app_fd_{grid_name}

Required Fields:
├── id (Primary Key, Auto-generated)
├── {data fields for grid items}
├── {parent_field} (Hidden, stores UUID from parent)
├── dateCreated (Hidden, Auto)
└── dateModified (Hidden, Auto)
```

**Example: Student Courses Grid Form**
```
Form ID: studentCoursesForm
Table: app_fd_student_courses

Fields:
├── id (Hidden, Auto)
├── course_code (Text Field, Required)
├── course_name (Text Field, Required)
├── credits (Number Field, Required)
├── student_id (Hidden, stores parent UUID)
├── dateCreated (Hidden)
└── dateModified (Hidden)
```

[SCREENSHOT: Grid form showing course fields]

**Important Differences from Child Forms**:
- Parent field name typically matches entity (e.g., `student_id` not `parent_id`)
- Multiple records created per submission (one per array item)
- Grid configuration in YAML specifies `parentField` name

### 8.4 Form Naming Best Practices

Follow these conventions for consistency:

```
✅ GOOD Form Names:

Parent Forms:
- studentRegistrationForm
- farmerRegistrationForm
- businessLicenseForm

Child Forms (sections):
- studentBasicInfo
- studentAcademics
- studentGuardian

Grid Forms:
- studentCoursesForm
- farmerCropsForm
- businessOwnersForm

✅ Pattern: {entity}{Section/Purpose}[Form]

❌ BAD Form Names:
- student_form (use camelCase)
- StudentBasicInfo (don't capitalize first letter)
- form1 (not descriptive)
- basic_information (inconsistent with other forms)
```

### 8.5 Table Naming Best Practices

```
✅ GOOD Table Names:

Parent Tables:
- students_registry
- farmers_registry
- business_licenses_registry

Child Tables:
- app_fd_student_basic_info
- app_fd_student_academics
- app_fd_student_guardian

Grid Tables:
- app_fd_student_courses
- app_fd_farmer_crops

✅ Pattern:
  Parent: {service}_registry
  Child/Grid: app_fd_{entity}_{section}

❌ BAD Table Names:
- students (missing _registry)
- student_basic_info (missing app_fd_ prefix for child)
- STUDENT_INFO (don't use uppercase)
```

**Note**: Joget automatically prefixes custom tables with `app_fd_`, so `student_basic_info` becomes `app_fd_student_basic_info`.

[SCREENSHOT: Database showing table names following naming convention]

### 8.6 Field Naming Best Practices

```
✅ GOOD Field Names:

- full_name (descriptive, lowercase, underscores)
- date_of_birth or dob (consistent abbreviation)
- email_address or email
- parent_id (standard foreign key name)
- student_id (entity-specific foreign key)

❌ BAD Field Names:
- FullName (no camelCase for fields)
- DOB vs dateOfBirth (inconsistent abbreviation)
- email1 (use descriptive names)
- parentID (use lowercase)
```

### 8.7 Form Creation Order

Create forms in this recommended order:

```
Step 1: Create all child forms first
  └── Easier to test independently
  └── Can verify field types work correctly

Step 2: Create grid forms
  └── Similar to child forms but with grid-specific parent field

Step 3: Create parent form last
  └── Add reference fields matching child form IDs
  └── Must know all child form IDs to create references
```

**Example Order for Student Enrollment**:
```
1. studentBasicInfo form
2. studentAcademics form
3. studentGuardian form
4. studentCoursesForm (grid)
5. studentRegistrationForm (parent)
```

### 8.8 Form Planning Worksheet

Use this worksheet to plan your forms:

```
SERVICE: _______________________________

PARENT FORM:
  Form ID: _______________________________
  Table Name: _______________________________
  Reference Fields:
    - _______________________________
    - _______________________________
    - _______________________________

CHILD FORMS:

Form 1:
  Form ID: _______________________________
  Table Name: _______________________________
  Section Name: _______________________________
  Fields:
    - Field: _____________ Type: _______ Required: □
    - Field: _____________ Type: _______ Required: □
    - Field: _____________ Type: _______ Required: □
    - parent_id (Hidden)

Form 2:
  Form ID: _______________________________
  Table Name: _______________________________
  Section Name: _______________________________
  Fields:
    - Field: _____________ Type: _______ Required: □
    - Field: _____________ Type: _______ Required: □
    - parent_id (Hidden)

GRID FORMS:

Grid 1:
  Form ID: _______________________________
  Table Name: _______________________________
  Grid Name: _______________________________
  Parent Field: _______________________________
  Fields:
    - Field: _____________ Type: _______ Required: □
    - Field: _____________ Type: _______ Required: □
    - {parent_field} (Hidden)
```

[SCREENSHOT: Completed form planning worksheet]

---

## 9. Field Mapping Strategies

The final planning step: how data flows from sender to receiver.

### 9.1 Understanding the Mapping Flow

```
Sender Joget Form
      ↓
sender YAML maps fields
      ↓
GovStack JSON
      ↓
HTTP Transmission
      ↓
receiver YAML maps fields
      ↓
Receiver Joget Forms
```

**Key Point**: Both sender and receiver need YAML configurations with field mappings.

[SCREENSHOT: Data flow diagram showing mapping at each stage]

### 9.2 JSONPath Planning

JSONPath expressions navigate JSON structure. Plan your JSON structure first:

**Example JSON Structure for Student**:
```json
{
  "name": {
    "text": "Jane Smith"
  },
  "birthDate": "2008-05-15",
  "identifiers": [
    {
      "type": "STUDENT_ID",
      "value": "STU2025001"
    }
  ],
  "extension": {
    "contact": {
      "email": "jane@example.com",
      "phone": "+1234567890"
    },
    "academics": {
      "gradeLevel": "10",
      "program": "Science",
      "previousSchool": "ABC Middle School"
    },
    "guardian": {
      "name": "John Smith",
      "relationship": "father",
      "phone": "+1234567891"
    },
    "courses": [
      {
        "courseCode": "MATH101",
        "courseName": "Algebra",
        "credits": 4
      },
      {
        "courseCode": "ENG101",
        "courseName": "English Literature",
        "credits": 3
      }
    ]
  }
}
```

[SCREENSHOT: JSON structure with JSONPath expressions annotated]

**JSONPath Examples**:
```
$.name.text                              → "Jane Smith"
$.birthDate                              → "2008-05-15"
$.identifiers[0].value                   → "STU2025001"
$.extension.contact.email                → "jane@example.com"
$.extension.academics.gradeLevel         → "10"
$.extension.guardian.name                → "John Smith"
$.extension.courses                      → [array of courses]
$.extension.courses[0].courseCode        → "MATH101"
```

**JSONPath Syntax Quick Reference**:
```
$                Root of JSON
.property        Access object property
[index]          Access array by index
[*]              All array elements
..property       Recursive descent (all levels)
```

### 9.3 Simple Field Mappings

For straightforward field mappings:

**Receiver YAML**:
```yaml
formMappings:
  studentBasicInfo:
    type: form
    formId: studentBasicInfo
    fields:
      # Simple text field
      - joget: full_name
        govstack: name.text
        jsonPath: $.name.text

      # Simple email field
      - joget: email
        govstack: extension.contact.email
        jsonPath: $.extension.contact.email

      # Simple number field
      - joget: grade_level
        govstack: extension.academics.gradeLevel
        jsonPath: $.extension.academics.gradeLevel
```

**Sender YAML** (reverse mapping):
```yaml
formMappings:
  studentBasicInfo:
    type: form
    formId: studentEnrollmentForm
    fields:
      - joget: full_name
        govstack: name.text

      - joget: email
        govstack: extension.contact.email

      - joget: grade_level
        govstack: extension.academics.gradeLevel
```

[SCREENSHOT: Split view showing sender and receiver YAML side-by-side]

### 9.4 Array Field Mappings (Grids)

For one-to-many relationships:

**JSON Structure**:
```json
{
  "extension": {
    "courses": [
      {"courseCode": "MATH101", "courseName": "Algebra", "credits": 4},
      {"courseCode": "ENG101", "courseName": "English", "credits": 3}
    ]
  }
}
```

**Receiver YAML**:
```yaml
formMappings:
  studentCourses:
    type: array                              # Important: type is "array"
    govstack: extension.courses              # Path to array in JSON
    formId: studentCoursesForm               # Grid form ID
    parentField: student_id                  # Field linking to parent
    fields:
      - joget: course_code
        govstack: courseCode                 # Relative to array item
        jsonPath: $.courseCode

      - joget: course_name
        govstack: courseName
        jsonPath: $.courseName

      - joget: credits
        govstack: credits
        jsonPath: $.credits
        transform: numeric                   # Convert to number
```

**Result**: Creates multiple database records, one per array item.

[SCREENSHOT: JSON array mapping to multiple database rows]

### 9.5 Transformation Planning

Identify fields that need transformation:

| Transform | Use Case | Example |
|-----------|----------|---------|
| `date_ISO8601_to_date` | ISO date → simple date | "2025-10-29T10:00:00Z" → "2025-10-29" |
| `numeric` | String → number | "123" → 123 |
| `boolean` | String → boolean | "true"/"1"/"yes" → true |
| `uppercase` | Force uppercase | "john" → "JOHN" |
| `lowercase` | Force lowercase | "JOHN" → "john" |
| `trim` | Remove whitespace | " text " → "text" |

**Example with Transformations**:
```yaml
fields:
  # Date transformation
  - joget: birth_date
    govstack: birthDate
    jsonPath: $.birthDate
    transform: date_ISO8601_to_date

  # Numeric transformation
  - joget: credits
    govstack: credits
    jsonPath: $.credits
    transform: numeric

  # Boolean transformation
  - joget: is_active
    govstack: active
    jsonPath: $.active
    transform: boolean
```

[SCREENSHOT: Before/after showing transformation results]

### 9.6 Nested Object Mappings

For deeply nested JSON:

**JSON Structure**:
```json
{
  "relatedPerson": [
    {
      "name": {
        "text": "John Smith"
      },
      "relationship": [
        {
          "coding": [
            {
              "code": "father"
            }
          ]
        }
      ],
      "telecom": [
        {
          "value": "+1234567890"
        }
      ]
    }
  ]
}
```

**Mapping**:
```yaml
fields:
  - joget: guardian_name
    govstack: relatedPerson[0].name.text
    jsonPath: $.relatedPerson[0].name.text

  - joget: relationship
    govstack: relatedPerson[0].relationship[0].coding[0].code
    jsonPath: $.relatedPerson[0].relationship[0].coding[0].code

  - joget: guardian_phone
    govstack: relatedPerson[0].telecom[0].value
    jsonPath: $.relatedPerson[0].telecom[0].value
```

**Tip**: Use JSON visualization tools to plan complex paths.

[SCREENSHOT: JSON visualization tool showing nested structure]

### 9.7 Mapping Planning Worksheet

Use this worksheet to plan your field mappings:

```
SERVICE: _______________________________

JSON STRUCTURE SKETCH:
{
  "name": {},
  "extension": {
    "section1": {},
    "section2": {},
    "arrays": []
  }
}

SECTION MAPPINGS:

Section: _______________________________
Form ID: _______________________________

Fields:
1. Joget Field: _________________
   JSON Path: _________________
   Transform: _________________

2. Joget Field: _________________
   JSON Path: _________________
   Transform: _________________

3. Joget Field: _________________
   JSON Path: _________________
   Transform: _________________

ARRAY MAPPINGS:

Array: _______________________________
Form ID: _______________________________
Parent Field: _______________________________

Fields:
1. Joget Field: _________________
   JSON Path: _________________ (relative to array item)
   Transform: _________________

2. Joget Field: _________________
   JSON Path: _________________
   Transform: _________________
```

[SCREENSHOT: Completed mapping worksheet]

### 9.8 Validation Planning

Plan validation at multiple levels:

**1. Joget Form Validation**
- Field-level: Required, format, regex
- Form-level: Custom validators
- Happens before submission

**2. DocSubmitter Validation**
- Checks YAML completeness
- Verifies mappings exist
- Happens during JSON construction

**3. ProcessingServer Validation**
- JSON structure validation
- Required field checking
- YAML configuration validation
- Happens before database insert

**Example Validation Configuration**:
```yaml
# In future iterations, validation rules can be added:
validation:
  studentBasicInfo:
    full_name:
      required: true
      minLength: 2
      maxLength: 100
      pattern: "^[A-Za-z ]+$"

    email:
      required: true
      format: email

    dob:
      required: true
      ageRange: [5, 25]  # For school enrollment
```

**Note**: Validation configuration is an advanced topic, not required for basic implementation.

---

**End of Part 2: Planning Your Service**

You now understand:
- ✅ Service design principles
- ✅ Data modeling approach
- ✅ Form structure planning
- ✅ Field mapping strategies

**Next**: Continue to Part 3 (Complete Tutorial) for hands-on step-by-step implementation of Student Enrollment service.

[Continue to Part 3 →](#part-3-complete-tutorial---student-enrollment)

---

# PART 3: COMPLETE TUTORIAL - STUDENT ENROLLMENT

---

## 10. Tutorial Overview

### 10.1 What We'll Build

In this comprehensive tutorial, we'll implement a complete **Student Enrollment Service** from scratch. By the end, you'll have a fully functional service that:

- Accepts student enrollment submissions from sender Joget
- Transforms data through standardized GovStack JSON format
- Stores data in multiple forms on receiver Joget
- Handles one-to-many relationships (student → courses)
- Demonstrates real-world complexity

**Service Specifications**:
```
Service ID: student_enrollment
Service Name: Student Enrollment Service

Data Structure:
├── Student Basic Information
│   ├── Full Name
│   ├── Date of Birth
│   ├── Gender
│   ├── Student ID
│   ├── Email
│   └── Phone
├── Academic Information
│   ├── Grade Level
│   ├── Program/Major
│   ├── Previous School
│   └── Academic Year
├── Guardian Information
│   ├── Guardian Name
│   ├── Relationship
│   ├── Guardian Contact
│   └── Guardian Address
└── Course Selections (Grid)
    ├── Course Code
    ├── Course Name
    └── Credits

Total Forms: 5 (1 parent + 3 child + 1 grid)
Complexity: Medium
Estimated Time: 3-4 hours
```

[SCREENSHOT: Final system diagram showing complete data flow]

### 10.2 Prerequisites Check

Before starting, ensure you have completed:

```
✅ Read Parts 1 & 2 of this guide
✅ Joget sender instance running (e.g., localhost:9999)
✅ Joget receiver instance running (e.g., localhost:8080)
✅ Both plugins installed (DocSubmitter and ProcessingServer)
✅ Database access to both instances
✅ Maven and JDK 11 available
✅ Text editor with YAML support ready
```

### 10.3 Tutorial Structure

This tutorial follows a structured workflow:

```
Phase 1: Planning & Design (30 minutes)
├── Step 1: Design the Data Model

Phase 2: Receiver Setup (90 minutes)
├── Step 2: Create Receiver Forms
└── Step 3: Configure Receiver YAML

Phase 3: Sender Setup (45 minutes)
├── Step 4: Configure Sender YAML
└── Step 5: Build and Deploy

Phase 4: Integration & Testing (45 minutes)
├── Step 6: Create Sender Process
└── Step 7: Test and Verify
```

### 10.4 Files You'll Create

By the end of this tutorial, you'll have:

**Receiver Side**:
- `student_enrollment.yml` (receiver YAML configuration)
- 5 Joget forms (studentRegistrationForm + 4 data forms)
- Database tables (1 parent + 3 child + 1 grid)

**Sender Side**:
- `student_enrollment.yml` (sender YAML configuration)
- 1 Joget form (student enrollment input form)
- 1 Joget process with DocSubmitter tool

**Verification**:
- Test data
- Verification SQL queries
- Test results documentation

### 10.5 Getting Help

If you encounter issues:
- Check Part 6 (Troubleshooting) for common errors
- Verify each step's output before proceeding
- Review screenshots for visual confirmation
- Use the YAML validator tool (Appendix A)

---

## 11. Step 1: Design the Data Model

Let's start by planning our student enrollment data structure.

### 11.1 Identify Entities

Using the planning principles from Part 2, identify entities:

```
Primary Entity: STUDENT
Supporting Entities:
├── Basic Information (1:1 relationship)
├── Academic Information (1:1 relationship)
├── Guardian Information (1:1 relationship)
└── Course Selections (1:N relationship)
```

[SCREENSHOT: Hand-drawn entity diagram]

### 11.2 Define Form Structure

Map entities to Joget forms:

**Parent Form**: `studentRegistrationForm`
- Purpose: Hold references to all child forms
- Table: `students_registry`
- Fields: UUID references only (hidden fields)

**Child Form 1**: `studentBasicInfo`
- Purpose: Store basic student information
- Table: `app_fd_student_basic_info`
- Fields: name, dob, gender, student_id, email, phone

**Child Form 2**: `studentAcademics`
- Purpose: Store academic background
- Table: `app_fd_student_academics`
- Fields: grade_level, program, previous_school, academic_year

**Child Form 3**: `studentGuardian`
- Purpose: Store guardian/parent information
- Table: `app_fd_student_guardian`
- Fields: guardian_name, relationship, contact, address

**Grid Form**: `studentCoursesForm`
- Purpose: Store course selections (multiple per student)
- Table: `app_fd_student_courses`
- Fields: course_code, course_name, credits

[SCREENSHOT: Form structure diagram with relationships]

### 11.3 Complete Data Model Worksheet

Fill out the planning worksheet:

```
SERVICE: Student Enrollment

PARENT ENTITY: Student Registration
Parent Form ID: studentRegistrationForm
Parent Table: students_registry

CHILD ENTITIES (1:1 relationships):

1. Entity: Student Basic Information
   Form ID: studentBasicInfo
   Table: app_fd_student_basic_info
   Fields:
   - full_name (text, required)
   - dob (date, required)
   - gender (select, required)
   - student_id (text, required, unique)
   - email (email, required)
   - phone (text, optional)
   - parent_id (hidden, UUID)

2. Entity: Student Academics
   Form ID: studentAcademics
   Table: app_fd_student_academics
   Fields:
   - grade_level (select, required)
   - program (text, required)
   - previous_school (text, required)
   - academic_year (text, required)
   - parent_id (hidden, UUID)

3. Entity: Student Guardian
   Form ID: studentGuardian
   Table: app_fd_student_guardian
   Fields:
   - guardian_name (text, required)
   - relationship (select, required)
   - contact (text, required)
   - address (textarea, required)
   - parent_id (hidden, UUID)

GRID ENTITIES (1:N relationships):

1. Grid: Student Courses
   Form ID: studentCoursesForm
   Table: app_fd_student_courses
   Parent Field: student_id
   Fields:
   - course_code (text, required)
   - course_name (text, required)
   - credits (number, required)
   - student_id (hidden, UUID)
```

[SCREENSHOT: Completed worksheet]

### 11.4 Plan Section Mappings

Define section names for YAML configuration:

```yaml
sectionToFormMap:
  studentBasicInfo: "studentBasicInfo"
  studentAcademics: "studentAcademics"
  studentGuardian: "studentGuardian"
```

**Important**: Section names (left) will be used in formMappings. Form IDs (right) must match Joget form IDs exactly.

### 11.5 Plan Parent Reference Fields

List all child form references that will go in parent form:

```yaml
parentReferenceFields:
  - "basic_data"
  - "academics_data"
  - "guardian_data"
  - "courses"
```

These will be field IDs in the parent form (studentRegistrationForm).

### 11.6 Plan Grid Mappings

Configure the courses grid:

```yaml
gridMappings:
  studentCourses:
    formId: "studentCoursesForm"
    parentField: "student_id"
```

[SCREENSHOT: Complete data model diagram with all relationships]

### 11.7 Design Checkpoint

Before proceeding, verify:

```
✅ All entities identified
✅ All form names decided
✅ All table names decided
✅ All field types determined
✅ Section names mapped to forms
✅ Parent reference fields listed
✅ Grid configuration planned
```

**Time Check**: You should be ~30 minutes into the tutorial.

---

## 12. Step 2: Create Receiver Forms

Now we'll create all forms in the receiver Joget instance.

### 12.1 Access Receiver Joget

1. Open browser: `http://localhost:8080`
2. Login with admin credentials
3. Navigate to: **Settings → Design → Form Builder**
4. You should see the Form Builder dashboard

[SCREENSHOT: Joget login screen]
[SCREENSHOT: Form Builder dashboard]

### 12.2 Create Child Form 1: Student Basic Info

#### Create New Form

1. Click: **[+ New Form]** button
2. Enter Form Details:
   - **Form ID**: `studentBasicInfo`
   - **Form Name**: `Student Basic Information`
   - **Table Name**: `student_basic_info`
   - **Description**: "Stores basic student information including name, DOB, and contact details"
3. Click: **[Save]**

[SCREENSHOT: New Form dialog with fields filled]

#### Add Form Fields

Drag and drop fields from the left palette onto the canvas:

**Field 1: Full Name**
1. Drag: **Text Field** to canvas
2. Click on field to open properties
3. Configure:
   - **Field ID**: `full_name`
   - **Label**: `Full Name`
   - **Required**: ✓ (checked)
   - **Max Length**: 100
   - **Validator**: None
4. Click: **[OK]**

[SCREENSHOT: Text Field properties dialog]

**Field 2: Date of Birth**
1. Drag: **Date Picker** to canvas
2. Configure:
   - **Field ID**: `dob`
   - **Label**: `Date of Birth`
   - **Required**: ✓
   - **Format**: `yyyy-MM-dd`
   - **Start Date**: -25 years (students typically 5-25 years old)
   - **End Date**: -5 years
3. Click: **[OK]**

[SCREENSHOT: Date Picker properties]

**Field 3: Gender**
1. Drag: **Select Box** to canvas
2. Configure:
   - **Field ID**: `gender`
   - **Label**: `Gender`
   - **Required**: ✓
   - **Options**:
     ```
     Value | Label
     M     | Male
     F     | Female
     O     | Other
     ```
3. Click: **[OK]**

[SCREENSHOT: Select Box with options configured]

**Field 4: Student ID**
1. Drag: **Text Field** to canvas
2. Configure:
   - **Field ID**: `student_id`
   - **Label**: `Student ID`
   - **Required**: ✓
   - **Max Length**: 20
   - **Placeholder**: e.g., STU2025001
3. Click: **[OK]**

**Field 5: Email**
1. Drag: **Email Field** to canvas
2. Configure:
   - **Field ID**: `email`
   - **Label**: `Email Address`
   - **Required**: ✓
   - **Validator**: Email (built-in)
3. Click: **[OK]**

**Field 6: Phone**
1. Drag: **Text Field** to canvas
2. Configure:
   - **Field ID**: `phone`
   - **Label**: `Phone Number`
   - **Required**: ☐ (unchecked - optional)
   - **Placeholder**: e.g., +1234567890
3. Click: **[OK]**

**Field 7: Parent ID (Hidden)**
1. Drag: **Hidden Field** to canvas
2. Configure:
   - **Field ID**: `parent_id`
   - **Default Value**: (leave empty - will be set by ProcessingServer)
3. Click: **[OK]**

[SCREENSHOT: Complete form with all fields visible]

#### Save and Publish

1. Click: **[Save]** button (top right)
2. Wait for confirmation: "Form saved successfully"
3. Click: **[Publish]** button
4. Confirm: Form appears in form list

[SCREENSHOT: Form published successfully message]

### 12.3 Create Child Form 2: Student Academics

#### Create New Form

1. Click: **[+ New Form]**
2. Enter Form Details:
   - **Form ID**: `studentAcademics`
   - **Form Name**: `Student Academic Information`
   - **Table Name**: `student_academics`
3. Click: **[Save]**

[SCREENSHOT: New form dialog]

#### Add Form Fields

**Field 1: Grade Level**
1. Drag: **Select Box** to canvas
2. Configure:
   - **Field ID**: `grade_level`
   - **Label**: `Grade Level`
   - **Required**: ✓
   - **Options**:
     ```
     Value | Label
     9     | Grade 9
     10    | Grade 10
     11    | Grade 11
     12    | Grade 12
     ```
3. Click: **[OK]**

[SCREENSHOT: Grade level select box]

**Field 2: Program/Major**
1. Drag: **Text Field** to canvas
2. Configure:
   - **Field ID**: `program`
   - **Label**: `Program/Major`
   - **Required**: ✓
   - **Max Length**: 100
   - **Placeholder**: e.g., Science, Arts, Commerce
3. Click: **[OK]**

**Field 3: Previous School**
1. Drag: **Text Field** to canvas
2. Configure:
   - **Field ID**: `previous_school`
   - **Label**: `Previous School`
   - **Required**: ✓
   - **Max Length**: 200
3. Click: **[OK]**

**Field 4: Academic Year**
1. Drag: **Text Field** to canvas
2. Configure:
   - **Field ID**: `academic_year`
   - **Label**: `Academic Year`
   - **Required**: ✓
   - **Placeholder**: e.g., 2025-2026
   - **Max Length**: 20
3. Click: **[OK]**

**Field 5: Parent ID (Hidden)**
1. Drag: **Hidden Field** to canvas
2. Configure:
   - **Field ID**: `parent_id`
3. Click: **[OK]**

[SCREENSHOT: Complete studentAcademics form]

#### Save and Publish

1. Click: **[Save]**
2. Click: **[Publish]**
3. Verify in form list

[SCREENSHOT: Published form confirmation]

### 12.4 Create Child Form 3: Student Guardian

#### Create New Form

1. Click: **[+ New Form]**
2. Enter Form Details:
   - **Form ID**: `studentGuardian`
   - **Form Name**: `Student Guardian Information`
   - **Table Name**: `student_guardian`
3. Click: **[Save]**

#### Add Form Fields

**Field 1: Guardian Name**
1. Drag: **Text Field** to canvas
2. Configure:
   - **Field ID**: `guardian_name`
   - **Label**: `Guardian/Parent Name`
   - **Required**: ✓
   - **Max Length**: 100
3. Click: **[OK]**

**Field 2: Relationship**
1. Drag: **Select Box** to canvas
2. Configure:
   - **Field ID**: `relationship`
   - **Label**: `Relationship to Student`
   - **Required**: ✓
   - **Options**:
     ```
     Value   | Label
     father  | Father
     mother  | Mother
     guardian| Legal Guardian
     other   | Other
     ```
3. Click: **[OK]**

[SCREENSHOT: Relationship select box]

**Field 3: Contact**
1. Drag: **Text Field** to canvas
2. Configure:
   - **Field ID**: `contact`
   - **Label**: `Contact Number`
   - **Required**: ✓
   - **Placeholder**: e.g., +1234567890
3. Click: **[OK]**

**Field 4: Address**
1. Drag: **Text Area** to canvas
2. Configure:
   - **Field ID**: `address`
   - **Label**: `Residential Address`
   - **Required**: ✓
   - **Rows**: 4
   - **Columns**: 50
3. Click: **[OK]**

[SCREENSHOT: Text area field]

**Field 5: Parent ID (Hidden)**
1. Drag: **Hidden Field** to canvas
2. Configure:
   - **Field ID**: `parent_id`
3. Click: **[OK]**

[SCREENSHOT: Complete studentGuardian form]

#### Save and Publish

1. Click: **[Save]**
2. Click: **[Publish]**
3. Verify in form list

### 12.5 Create Grid Form: Student Courses

#### Create New Form

1. Click: **[+ New Form]**
2. Enter Form Details:
   - **Form ID**: `studentCoursesForm`
   - **Form Name**: `Student Courses`
   - **Table Name**: `student_courses`
3. Click: **[Save]**

[SCREENSHOT: New form for grid]

#### Add Form Fields

**Field 1: Course Code**
1. Drag: **Text Field** to canvas
2. Configure:
   - **Field ID**: `course_code`
   - **Label**: `Course Code`
   - **Required**: ✓
   - **Max Length**: 20
   - **Placeholder**: e.g., MATH101
3. Click: **[OK]**

**Field 2: Course Name**
1. Drag: **Text Field** to canvas
2. Configure:
   - **Field ID**: `course_name`
   - **Label**: `Course Name`
   - **Required**: ✓
   - **Max Length**: 100
   - **Placeholder**: e.g., Algebra
3. Click: **[OK]**

**Field 3: Credits**
1. Drag: **Number Field** to canvas
2. Configure:
   - **Field ID**: `credits`
   - **Label**: `Credits`
   - **Required**: ✓
   - **Min Value**: 1
   - **Max Value**: 10
3. Click: **[OK]**

[SCREENSHOT: Number field properties]

**Field 4: Student ID (Hidden)**
1. Drag: **Hidden Field** to canvas
2. Configure:
   - **Field ID**: `student_id`
   - **Comment**: This links to parent student record
3. Click: **[OK]**

[SCREENSHOT: Complete studentCoursesForm]

#### Save and Publish

1. Click: **[Save]**
2. Click: **[Publish]**
3. Verify in form list

### 12.6 Create Parent Form: Student Registration

This is the final form that references all child forms.

#### Create New Form

1. Click: **[+ New Form]**
2. Enter Form Details:
   - **Form ID**: `studentRegistrationForm`
   - **Form Name**: `Student Registration`
   - **Table Name**: `students_registry`
   - **Description**: "Parent form holding references to all student data"
3. Click: **[Save]**

[SCREENSHOT: Parent form creation]

#### Add Reference Fields

**IMPORTANT**: All fields in parent form are HIDDEN fields that will be auto-populated.

**Field 1: Basic Data Reference**
1. Drag: **Hidden Field** to canvas
2. Configure:
   - **Field ID**: `basic_data`
   - **Comment**: References studentBasicInfo
3. Click: **[OK]**

**Field 2: Academics Data Reference**
1. Drag: **Hidden Field** to canvas
2. Configure:
   - **Field ID**: `academics_data`
   - **Comment**: References studentAcademics
3. Click: **[OK]**

**Field 3: Guardian Data Reference**
1. Drag: **Hidden Field** to canvas
2. Configure:
   - **Field ID**: `guardian_data`
   - **Comment**: References studentGuardian
3. Click: **[OK]**

**Field 4: Courses Reference**
1. Drag: **Hidden Field** to canvas
2. Configure:
   - **Field ID**: `courses`
   - **Comment**: References studentCoursesForm grid
3. Click: **[OK]**

[SCREENSHOT: Parent form with all hidden fields]

**Note**: The form will appear empty since all fields are hidden. This is correct!

#### Save and Publish

1. Click: **[Save]**
2. Click: **[Publish]**
3. Verify in form list

[SCREENSHOT: All 5 forms listed in Form Builder]

### 12.7 Forms Creation Checkpoint

Verify you have created all forms:

```
✅ studentBasicInfo (6 visible + 1 hidden field)
✅ studentAcademics (4 visible + 1 hidden field)
✅ studentGuardian (4 visible + 1 hidden field)
✅ studentCoursesForm (3 visible + 1 hidden field)
✅ studentRegistrationForm (4 hidden fields)
```

[SCREENSHOT: Form list showing all 5 forms]

**Database Verification**:

Check that tables were created:

```bash
mysql -h localhost -P 3307 -u root -p receiver_db

mysql> SHOW TABLES LIKE '%student%';
```

Expected output:
```
+-------------------------------------+
| Tables_in_receiver_db (%student%)   |
+-------------------------------------+
| app_fd_student_academics            |
| app_fd_student_basic_info           |
| app_fd_student_courses              |
| app_fd_student_guardian             |
| students_registry                   |
+-------------------------------------+
5 rows in set
```

[SCREENSHOT: MySQL output showing tables created]

**Time Check**: You should be ~2 hours into the tutorial (30 min planning + 90 min forms).

---

## 13. Step 3: Configure Receiver YAML

Now we'll create the YAML configuration for the receiver side.

### 13.1 Create YAML File

Create a new file:

```bash
mkdir -p ~/govstack-services/receiver-configs
cd ~/govstack-services/receiver-configs
touch student_enrollment.yml
```

Open in your editor:

```bash
code student_enrollment.yml   # VS Code
# or
vim student_enrollment.yml    # Vim
# or
nano student_enrollment.yml   # Nano
```

[SCREENSHOT: Empty YAML file open in VS Code]

### 13.2 Add Service Metadata

Start with service identification:

```yaml
# GovStack Student Enrollment Service - Receiver Configuration
# Purpose: Maps GovStack JSON to Joget forms for student enrollment
# Version: 1.0.0
# Last Updated: 2025-10-29

service:
  id: student_enrollment
  name: "Student Enrollment Service"
  version: "1.0"
  govstackVersion: "1.0"
  metadataVersion: "1.0.0"
  lastUpdated: "2025-10-29"
  description: "Handles student enrollment applications with basic info, academics, guardian, and course selections"
```

[SCREENSHOT: Service metadata section in YAML]

### 13.3 Add Service Configuration

Add the serviceConfig section:

```yaml
  serviceConfig:
    # Parent form that holds all references
    parentFormId: "studentRegistrationForm"

    # Fields in parent form that reference child forms
    # These MUST match the field IDs in studentRegistrationForm exactly
    parentReferenceFields:
      - "basic_data"
      - "academics_data"
      - "guardian_data"
      - "courses"

    # Map section names to form IDs
    # Section names (keys) are used in formMappings below
    # Form IDs (values) must match Joget form IDs exactly
    sectionToFormMap:
      studentBasicInfo: "studentBasicInfo"
      studentAcademics: "studentAcademics"
      studentGuardian: "studentGuardian"

    # Grid configurations for one-to-many relationships
    gridMappings:
      studentCourses:
        formId: "studentCoursesForm"
        parentField: "student_id"

    # Default configurations
    defaults:
      gridParentField: "student_id"
      gridParentColumn: "c_student_id"
```

[SCREENSHOT: Service configuration section]

**Key Points**:
- `parentFormId` must match exactly: `studentRegistrationForm`
- `parentReferenceFields` must match parent form field IDs
- `sectionToFormMap` keys will be used in formMappings
- `gridMappings.parentField` must match hidden field in grid form

### 13.4 Add Form Mappings - Basic Info

Now add the field mappings. Start with basic info:

```yaml
# Field Mappings
# Define how GovStack JSON maps to Joget forms

formMappings:
  # Section 1: Student Basic Information
  studentBasicInfo:
    type: form
    formId: studentBasicInfo
    description: "Maps basic student information from GovStack JSON to Joget form"
    fields:
      - joget: full_name
        govstack: name.text
        jsonPath: $.name.text
        description: "Student's full legal name"

      - joget: dob
        govstack: birthDate
        jsonPath: $.birthDate
        transform: date_ISO8601_to_date
        description: "Date of birth, converted from ISO 8601 format"

      - joget: gender
        govstack: gender
        jsonPath: $.gender
        description: "Gender code (M/F/O)"

      - joget: student_id
        govstack: identifiers[0].value
        jsonPath: $.identifiers[0].value
        description: "Unique student identifier"

      - joget: email
        govstack: extension.contact.email
        jsonPath: $.extension.contact.email
        description: "Student's email address"

      - joget: phone
        govstack: extension.contact.phone
        jsonPath: $.extension.contact.phone
        description: "Student's phone number (optional)"
```

[SCREENSHOT: Basic info mappings in YAML]

**Important Notes**:
- `studentBasicInfo` (section name) MUST match key in `sectionToFormMap`
- `formId` MUST match Joget form ID exactly
- `joget` field names MUST match field IDs in Joget form
- `jsonPath` expressions navigate the incoming JSON structure
- `transform` applies data conversion (dates, numbers, etc.)

### 13.5 Add Form Mappings - Academics

Add academic information mappings:

```yaml
  # Section 2: Student Academic Information
  studentAcademics:
    type: form
    formId: studentAcademics
    description: "Maps academic background from GovStack JSON to Joget form"
    fields:
      - joget: grade_level
        govstack: extension.academics.gradeLevel
        jsonPath: $.extension.academics.gradeLevel
        description: "Current grade level (9-12)"

      - joget: program
        govstack: extension.academics.program
        jsonPath: $.extension.academics.program
        description: "Program of study (Science, Arts, Commerce)"

      - joget: previous_school
        govstack: extension.academics.previousSchool
        jsonPath: $.extension.academics.previousSchool
        description: "Name of previous school attended"

      - joget: academic_year
        govstack: extension.academics.academicYear
        jsonPath: $.extension.academics.academicYear
        description: "Academic year for enrollment (e.g., 2025-2026)"
```

[SCREENSHOT: Academics mappings]

### 13.6 Add Form Mappings - Guardian

Add guardian information mappings:

```yaml
  # Section 3: Student Guardian Information
  studentGuardian:
    type: form
    formId: studentGuardian
    description: "Maps guardian/parent information from GovStack JSON to Joget form"
    fields:
      - joget: guardian_name
        govstack: relatedPerson[0].name.text
        jsonPath: $.relatedPerson[0].name.text
        description: "Guardian or parent full name"

      - joget: relationship
        govstack: relatedPerson[0].relationship[0].coding[0].code
        jsonPath: $.relatedPerson[0].relationship[0].coding[0].code
        description: "Relationship code (father/mother/guardian/other)"

      - joget: contact
        govstack: relatedPerson[0].telecom[0].value
        jsonPath: $.relatedPerson[0].telecom[0].value
        description: "Guardian contact phone number"

      - joget: address
        govstack: relatedPerson[0].address[0].text
        jsonPath: $.relatedPerson[0].address[0].text
        description: "Guardian residential address"
```

[SCREENSHOT: Guardian mappings]

**Note**: The deeply nested JSON structure (`relatedPerson[0].relationship[0].coding[0].code`) is based on HL7 FHIR standards used by GovStack.

### 13.7 Add Form Mappings - Courses Grid

Add the grid mapping for courses:

```yaml
  # Section 4: Student Courses (Grid - One to Many)
  studentCourses:
    type: array
    govstack: extension.courses
    formId: studentCoursesForm
    parentField: student_id
    description: "Maps course selections from JSON array to grid records"
    fields:
      - joget: course_code
        govstack: courseCode
        jsonPath: $.courseCode
        description: "Course code (e.g., MATH101)"

      - joget: course_name
        govstack: courseName
        jsonPath: $.courseName
        description: "Full course name (e.g., Algebra)"

      - joget: credits
        govstack: credits
        jsonPath: $.credits
        transform: numeric
        description: "Course credit hours (numeric)"
```

[SCREENSHOT: Grid mappings]

**Important Grid Differences**:
- `type: array` (not "form")
- `govstack` points to array in JSON (not individual field)
- `parentField` specifies the field linking to parent
- Field `jsonPath` expressions are relative to array item (use `$.fieldName` not `$.extension.courses[0].fieldName`)

### 13.8 Complete YAML File

Your complete `student_enrollment.yml` should look like this:

```yaml
# GovStack Student Enrollment Service - Receiver Configuration
# Purpose: Maps GovStack JSON to Joget forms for student enrollment
# Version: 1.0.0
# Last Updated: 2025-10-29

service:
  id: student_enrollment
  name: "Student Enrollment Service"
  version: "1.0"
  govstackVersion: "1.0"
  metadataVersion: "1.0.0"
  lastUpdated: "2025-10-29"
  description: "Handles student enrollment applications with basic info, academics, guardian, and course selections"

  serviceConfig:
    parentFormId: "studentRegistrationForm"

    parentReferenceFields:
      - "basic_data"
      - "academics_data"
      - "guardian_data"
      - "courses"

    sectionToFormMap:
      studentBasicInfo: "studentBasicInfo"
      studentAcademics: "studentAcademics"
      studentGuardian: "studentGuardian"

    gridMappings:
      studentCourses:
        formId: "studentCoursesForm"
        parentField: "student_id"

    defaults:
      gridParentField: "student_id"
      gridParentColumn: "c_student_id"

formMappings:
  studentBasicInfo:
    type: form
    formId: studentBasicInfo
    fields:
      - joget: full_name
        govstack: name.text
        jsonPath: $.name.text

      - joget: dob
        govstack: birthDate
        jsonPath: $.birthDate
        transform: date_ISO8601_to_date

      - joget: gender
        govstack: gender
        jsonPath: $.gender

      - joget: student_id
        govstack: identifiers[0].value
        jsonPath: $.identifiers[0].value

      - joget: email
        govstack: extension.contact.email
        jsonPath: $.extension.contact.email

      - joget: phone
        govstack: extension.contact.phone
        jsonPath: $.extension.contact.phone

  studentAcademics:
    type: form
    formId: studentAcademics
    fields:
      - joget: grade_level
        govstack: extension.academics.gradeLevel
        jsonPath: $.extension.academics.gradeLevel

      - joget: program
        govstack: extension.academics.program
        jsonPath: $.extension.academics.program

      - joget: previous_school
        govstack: extension.academics.previousSchool
        jsonPath: $.extension.academics.previousSchool

      - joget: academic_year
        govstack: extension.academics.academicYear
        jsonPath: $.extension.academics.academicYear

  studentGuardian:
    type: form
    formId: studentGuardian
    fields:
      - joget: guardian_name
        govstack: relatedPerson[0].name.text
        jsonPath: $.relatedPerson[0].name.text

      - joget: relationship
        govstack: relatedPerson[0].relationship[0].coding[0].code
        jsonPath: $.relatedPerson[0].relationship[0].coding[0].code

      - joget: contact
        govstack: relatedPerson[0].telecom[0].value
        jsonPath: $.relatedPerson[0].telecom[0].value

      - joget: address
        govstack: relatedPerson[0].address[0].text
        jsonPath: $.relatedPerson[0].address[0].text

  studentCourses:
    type: array
    govstack: extension.courses
    formId: studentCoursesForm
    parentField: student_id
    fields:
      - joget: course_code
        govstack: courseCode
        jsonPath: $.courseCode

      - joget: course_name
        govstack: courseName
        jsonPath: $.courseName

      - joget: credits
        govstack: credits
        jsonPath: $.credits
        transform: numeric
```

[SCREENSHOT: Complete YAML file in editor]

### 13.9 Validate YAML Syntax

Before proceeding, validate your YAML:

**Option 1: Online Validator**
1. Copy your YAML content
2. Visit: https://www.yamllint.com/
3. Paste and check for errors

[SCREENSHOT: YAML validator showing no errors]

**Option 2: Command Line** (if you have yamllint installed)
```bash
yamllint student_enrollment.yml
```

Expected output:
```
No errors found
```

**Option 3: Use the provided validator tool** (see Appendix A)
```bash
./tools/yaml-validator.sh student_enrollment.yml
```

### 13.10 YAML Configuration Checkpoint

Verify your YAML file:

```
✅ Service metadata complete
✅ parentFormId matches Joget parent form
✅ parentReferenceFields match parent form field IDs
✅ sectionToFormMap has all 3 child forms
✅ gridMappings configured for courses
✅ All formMappings sections present
✅ Field names match Joget form field IDs
✅ JSONPath expressions are correct
✅ YAML syntax validates without errors
```

**Save the file** before proceeding.

---

## 14. Step 4: Configure Sender YAML

Now create the sender YAML configuration (reverse mapping).

### 14.1 Understanding Sender vs Receiver YAML

**Key Differences**:

```
Receiver YAML (we just created):
  GovStack JSON → Joget Forms
  Uses jsonPath to extract from JSON

Sender YAML (we'll create now):
  Joget Form → GovStack JSON
  No jsonPath needed (direct mapping)
```

### 14.2 Create Sender YAML File

Create a new file for sender configuration:

```bash
mkdir -p ~/govstack-services/sender-configs
cd ~/govstack-services/sender-configs
touch student_enrollment.yml
```

Open in editor:

```bash
code student_enrollment.yml
```

[SCREENSHOT: Empty sender YAML file]

### 14.3 Add Service Metadata (Sender)

Same metadata as receiver:

```yaml
# GovStack Student Enrollment Service - Sender Configuration
# Purpose: Maps Joget form data to GovStack JSON for transmission
# Version: 1.0.0
# Last Updated: 2025-10-29

service:
  id: student_enrollment
  name: "Student Enrollment Service"
  version: "1.0"
  govstackVersion: "1.0"
  metadataVersion: "1.0.0"
  lastUpdated: "2025-10-29"
  description: "Sends student enrollment data from Joget to receiver"
```

### 14.4 Add Form Mappings - Sender Style

For sender, we map FROM Joget TO JSON. The structure is simpler:

```yaml
formMappings:
  # All fields come from single enrollment form on sender side
  studentBasicInfo:
    type: form
    formId: studentEnrollmentForm
    description: "Maps basic fields from enrollment form to GovStack JSON"
    fields:
      - joget: full_name
        govstack: name.text

      - joget: dob
        govstack: birthDate

      - joget: gender
        govstack: gender

      - joget: student_id
        govstack: identifiers[0].value

      - joget: email
        govstack: extension.contact.email

      - joget: phone
        govstack: extension.contact.phone

  studentAcademics:
    type: form
    formId: studentEnrollmentForm
    fields:
      - joget: grade_level
        govstack: extension.academics.gradeLevel

      - joget: program
        govstack: extension.academics.program

      - joget: previous_school
        govstack: extension.academics.previousSchool

      - joget: academic_year
        govstack: extension.academics.academicYear

  studentGuardian:
    type: form
    formId: studentEnrollmentForm
    fields:
      - joget: guardian_name
        govstack: relatedPerson[0].name.text

      - joget: relationship
        govstack: relatedPerson[0].relationship[0].coding[0].code

      - joget: guardian_contact
        govstack: relatedPerson[0].telecom[0].value

      - joget: guardian_address
        govstack: relatedPerson[0].address[0].text

  studentCourses:
    type: array
    formId: studentEnrollmentForm
    govstack: extension.courses
    jogetGridField: courses_grid
    fields:
      - joget: course_code
        govstack: courseCode

      - joget: course_name
        govstack: courseName

      - joget: credits
        govstack: credits
```

[SCREENSHOT: Complete sender YAML]

**Key Differences from Receiver**:
- All sections can reference same `formId` (single enrollment form on sender)
- No `jsonPath` needed
- Grid uses `jogetGridField` to identify grid element in sender form
- Simpler structure overall

### 14.5 Validate and Save

1. Validate YAML syntax (yamllint or online validator)
2. Save the file
3. Keep it ready for deployment

[SCREENSHOT: Validated sender YAML]

---

## 15. Step 5: Build and Deploy

Now we'll deploy the YAML configurations and rebuild the plugins.

### 15.1 Deploy Receiver YAML

Copy receiver YAML to processing-server:

```bash
# Copy YAML file to processing-server resources
cp ~/govstack-services/receiver-configs/student_enrollment.yml \
   /Users/aarelaponin/IdeaProjects/plugins/processing-server/src/main/resources/docs-metadata/

# Verify file exists
ls -l /Users/aarelaponin/IdeaProjects/plugins/processing-server/src/main/resources/docs-metadata/student_enrollment.yml
```

Expected output:
```
-rw-r--r--  1 user  staff  3245 Oct 29 14:30 student_enrollment.yml
```

[SCREENSHOT: YAML file in processing-server directory]

### 15.2 Rebuild Processing-Server Plugin

Build the receiver plugin:

```bash
cd /Users/aarelaponin/IdeaProjects/plugins/processing-server
mvn clean package -Dmaven.test.skip=true
```

Watch for successful build:
```
[INFO] Scanning for projects...
[INFO]
[INFO] -------------------< global.govstack:processing-server >-------------------
[INFO] Building processing-server 8.1-SNAPSHOT
[INFO] --------------------------------[ bundle ]---------------------------------
...
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  18.456 s
[INFO] Finished at: 2025-10-29T14:32:15+03:00
[INFO] ------------------------------------------------------------------------
```

[SCREENSHOT: Maven build success]

Check JAR file was created:

```bash
ls -lh target/processing-server-8.1-SNAPSHOT.jar
```

Expected output:
```
-rw-r--r--  1 user  staff   2.7M Oct 29 14:32 processing-server-8.1-SNAPSHOT.jar
```

### 15.3 Deploy to Receiver Joget

Copy JAR to receiver Joget:

```bash
cp target/processing-server-8.1-SNAPSHOT.jar \
   /Users/aarelaponin/joget-enterprise-linux-8.1.6-2/wflow/app_plugins/
```

Verify deployment:

```bash
ls -lh /Users/aarelaponin/joget-enterprise-linux-8.1.6-2/wflow/app_plugins/processing-server-8.1-SNAPSHOT.jar
```

[SCREENSHOT: JAR file in Joget plugins directory]

### 15.4 Restart or Reload Receiver

**Option 1: Reload Plugin (Faster)**
1. Open receiver Joget: `http://localhost:8080`
2. Navigate: **Settings → Manage Plugins**
3. Find: processing-server plugin
4. Click: **[Refresh]** or **[Reload]**
5. Verify: Plugin shows as "Active"

[SCREENSHOT: Plugin reload in Joget]

**Option 2: Restart Joget (More thorough)**
```bash
cd /Users/aarelaponin/joget-enterprise-linux-8.1.6-2
./joget.sh stop
./joget.sh start
```

Wait for Joget to start (~30 seconds).

[SCREENSHOT: Joget restart logs]

### 15.5 Deploy Sender YAML

Copy sender YAML to doc-submitter:

```bash
cp ~/govstack-services/sender-configs/student_enrollment.yml \
   /Users/aarelaponin/IdeaProjects/plugins/doc-submitter/src/main/resources/docs-metadata/

# Verify
ls -l /Users/aarelaponin/IdeaProjects/plugins/doc-submitter/src/main/resources/docs-metadata/student_enrollment.yml
```

### 15.6 Rebuild Doc-Submitter Plugin

Build the sender plugin:

```bash
cd /Users/aarelaponin/IdeaProjects/plugins/doc-submitter
mvn clean package -Dmaven.test.skip=true
```

Watch for success:
```
[INFO] BUILD SUCCESS
[INFO] Total time:  22.123 s
```

Check JAR:
```bash
ls -lh target/doc-submitter-8.1-SNAPSHOT.jar
```

Expected:
```
-rw-r--r--  1 user  staff   6.3M Oct 29 14:35 doc-submitter-8.1-SNAPSHOT.jar
```

[SCREENSHOT: Sender build success]

### 15.7 Deploy to Sender Joget

Copy JAR to sender Joget:

```bash
cp target/doc-submitter-8.1-SNAPSHOT.jar \
   /Users/aarelaponin/joget-enterprise-linux-8.1.6/wflow/app_plugins/
```

Verify:
```bash
ls -lh /Users/aarelaponin/joget-enterprise-linux-8.1.6/wflow/app_plugins/doc-submitter-8.1-SNAPSHOT.jar
```

### 15.8 Restart or Reload Sender

Same as receiver - either reload plugin or restart Joget.

[SCREENSHOT: Sender plugin reloaded]

### 15.9 Deployment Checkpoint

Verify deployment:

```
✅ Receiver YAML deployed to processing-server
✅ Processing-server rebuilt successfully
✅ Processing-server JAR deployed to receiver Joget
✅ Receiver Joget restarted/reloaded
✅ Sender YAML deployed to doc-submitter
✅ Doc-submitter rebuilt successfully
✅ Doc-submitter JAR deployed to sender Joget
✅ Sender Joget restarted/reloaded
```

**Time Check**: You should be ~2.75 hours into the tutorial.

---

## 16. Step 6: Create Sender Process

Now we'll create the enrollment process on sender Joget.

### 16.1 Create Enrollment Form (Sender Side)

We need a single data entry form on sender side.

#### Access Sender Joget

1. Open: `http://localhost:9999`
2. Login as admin
3. Navigate: **Design → Form Builder**

[SCREENSHOT: Sender Joget Form Builder]

#### Create New Form

1. Click: **[+ New Form]**
2. Configure:
   - **Form ID**: `studentEnrollmentForm`
   - **Form Name**: `Student Enrollment Application`
   - **Table Name**: `student_enrollment_data`
3. Click: **[Save]**

[SCREENSHOT: Sender form creation]

#### Add All Fields

Add fields for ALL data (basic, academics, guardian):

**Section 1: Basic Information** (use Section element for grouping)

1. Drag **Section** to canvas
2. Label: "Student Basic Information"
3. Inside section, add:
   - `full_name` (Text Field, Required)
   - `dob` (Date Picker, Required, format: yyyy-MM-dd)
   - `gender` (Select Box: M/F/O, Required)
   - `student_id` (Text Field, Required)
   - `email` (Email Field, Required)
   - `phone` (Text Field, Optional)

[SCREENSHOT: Basic info section in sender form]

**Section 2: Academic Information**

1. Drag **Section** to canvas
2. Label: "Academic Information"
3. Inside section, add:
   - `grade_level` (Select Box: 9/10/11/12, Required)
   - `program` (Text Field, Required)
   - `previous_school` (Text Field, Required)
   - `academic_year` (Text Field, Required)

[SCREENSHOT: Academic section]

**Section 3: Guardian Information**

1. Drag **Section** to canvas
2. Label: "Guardian/Parent Information"
3. Inside section, add:
   - `guardian_name` (Text Field, Required)
   - `relationship` (Select Box: father/mother/guardian/other, Required)
   - `guardian_contact` (Text Field, Required)
   - `guardian_address` (Text Area, Required)

[SCREENSHOT: Guardian section]

**Section 4: Course Selections (Grid)**

1. Drag **Grid** element to canvas
2. Configure Grid:
   - **Grid ID**: `courses_grid`
   - **Label**: "Course Selections"
   - **Min Rows**: 1
   - **Max Rows**: 10
3. Add columns to grid:
   - `course_code` (Text Field)
   - `course_name` (Text Field)
   - `credits` (Number Field, min: 1, max: 10)

[SCREENSHOT: Grid element with course columns]

#### Save and Publish

1. Click: **[Save]**
2. Click: **[Publish]**
3. Verify form appears in list

[SCREENSHOT: Published sender form]

### 16.2 Create Process Definition

Now create the workflow process.

#### Access Process Builder

1. In sender Joget, navigate: **Design → Process Builder**
2. Click: **[+ New Process]**

[SCREENSHOT: Process Builder dashboard]

#### Configure Process

1. Enter Process Details:
   - **Process ID**: `student_enrollment_submission`
   - **Process Name**: `Student Enrollment Submission`
   - **Version**: 1
2. Click: **[Save]**

[SCREENSHOT: New process dialog]

#### Design Process Flow

1. Drag **Start** node to canvas (should be there by default)
2. Drag **Activity** node to canvas
3. Connect Start → Activity with arrow
4. Drag **End** node to canvas
5. Connect Activity → End

[SCREENSHOT: Simple process flow diagram]

#### Configure Activity

1. Double-click Activity node
2. Configure:
   - **Activity ID**: `enrollment_form`
   - **Activity Name**: `Student Enrollment Form`
   - **Type**: Form
3. In **Form** tab:
   - Select Form: `studentEnrollmentForm`
4. Click: **[OK]**

[SCREENSHOT: Activity configuration]

#### Save Process

1. Click: **[Save]** in Process Builder
2. Verify process appears in list

[SCREENSHOT: Process saved]

### 16.3 Add DocSubmitter Tool to Process

Now add the plugin tool that sends data.

#### Access Process Tools

1. In Process Builder, with your process open
2. Select the Activity node (`enrollment_form`)
3. Click: **Tools** tab (right panel)
4. Click: **[+ Add Tool]**

[SCREENSHOT: Tools panel]

#### Configure DocSubmitter Tool

1. Select Tool: **DocSubmitter** (or "GovStack Document Submitter")
2. Configure:
   - **Tool ID**: `doc_submitter`
   - **Tool Name**: `Submit to Receiver`
   - **Execute on**: Complete
3. Click: **[Configure]** or **[Edit]**

[SCREENSHOT: Tool selection]

#### Configure Tool Properties

Fill in the plugin configuration:

**Basic Settings**:
```
Service ID: student_enrollment
API URL: http://localhost:8080/jw/api/govstack/registration/services/student_enrollment/applications
```

**Note**: Use full URL including the service path. No trailing slash!

[SCREENSHOT: Plugin configuration showing Service ID and API URL fields]

**Advanced Settings** (if available):
```
Metadata File: student_enrollment.yml
API ID: registration
```

**Note**: Exact configuration fields depend on plugin version. The critical fields are Service ID and API URL.

[SCREENSHOT: Complete plugin configuration]

#### Save Tool Configuration

1. Click: **[OK]** or **[Save]** in tool configuration
2. Click: **[Save]** in Process Builder
3. Click: **[Close]** Process Builder

[SCREENSHOT: Process with tool configured]

### 16.4 Create Process App

Package the process in an application.

#### Create New App

1. Navigate: **Apps → Build App**
2. Click: **[+ Create New App]**
3. Configure:
   - **App ID**: `student_enrollment_app`
   - **App Name**: `Student Enrollment`
4. Click: **[Save]**

[SCREENSHOT: New app creation]

#### Add Process to App

1. In App Composer, click: **Processes** tab
2. Click: **[+ Add Process]**
3. Select: `student_enrollment_submission`
4. Click: **[Add]**

[SCREENSHOT: Process added to app]

#### Publish App

1. Click: **[Publish]** button (top right)
2. Confirm publication
3. App is now live!

[SCREENSHOT: App published]

### 16.5 Test Process Manually (Optional)

Before full testing, verify the form works:

1. Navigate: **Apps → Student Enrollment**
2. Click: **Start Process** or find the enrollment form
3. Fill in sample data (don't submit yet)
4. Verify all fields appear correctly

[SCREENSHOT: Form with sample data filled]

### 16.6 Process Creation Checkpoint

Verify process setup:

```
✅ Sender enrollment form created with all fields
✅ Grid element added for courses
✅ Process definition created
✅ Activity configured with form
✅ DocSubmitter tool added and configured
✅ Tool executes on activity complete
✅ App created and published
✅ Process is accessible from Apps menu
```

**Time Check**: You should be ~3.25 hours into the tutorial.

---

## 17. Step 7: Test and Verify

Time to test the complete end-to-end flow!

### 17.1 Prepare Test Data

Let's prepare sample student data for testing:

```json
Student: Jane Smith
DOB: 2008-05-15
Gender: F
Student ID: STU2025001
Email: jane.smith@example.com
Phone: +1234567890

Academics:
Grade: 10
Program: Science
Previous School: ABC Middle School
Academic Year: 2025-2026

Guardian:
Name: John Smith
Relationship: father
Contact: +1234567891
Address: 123 Main Street, Anytown, ST 12345

Courses:
1. MATH101 - Algebra - 4 credits
2. ENG101 - English Literature - 3 credits
3. SCI101 - Biology - 4 credits
```

[SCREENSHOT: Test data sheet]

### 17.2 Submit Test Enrollment

#### Access Sender App

1. Open sender Joget: `http://localhost:9999`
2. Navigate: **Apps**
3. Click: **Student Enrollment** app
4. Click: **[Start Process]** or access enrollment form

[SCREENSHOT: App landing page]

#### Fill Enrollment Form

**Basic Information Section**:
- Full Name: `Jane Smith`
- Date of Birth: `2008-05-15` (use date picker)
- Gender: `F` (select from dropdown)
- Student ID: `STU2025001`
- Email: `jane.smith@example.com`
- Phone: `+1234567890`

[SCREENSHOT: Basic info filled]

**Academic Information Section**:
- Grade Level: `10` (select from dropdown)
- Program: `Science`
- Previous School: `ABC Middle School`
- Academic Year: `2025-2026`

[SCREENSHOT: Academic info filled]

**Guardian Information Section**:
- Guardian Name: `John Smith`
- Relationship: `father` (select from dropdown)
- Contact: `+1234567891`
- Address: `123 Main Street, Anytown, ST 12345`

[SCREENSHOT: Guardian info filled]

**Course Selections Grid**:

Row 1:
- Course Code: `MATH101`
- Course Name: `Algebra`
- Credits: `4`

Row 2:
- Course Code: `ENG101`
- Course Name: `English Literature`
- Credits: `3`

Row 3:
- Course Code: `SCI101`
- Course Name: `Biology`
- Credits: `4`

[SCREENSHOT: Grid with three courses filled]

#### Submit Form

1. Click: **[Submit]** button
2. Wait for confirmation
3. Expected: "Form submitted successfully" or similar message

[SCREENSHOT: Submission confirmation]

### 17.3 Check Sender Logs

Verify sender processed the submission:

```bash
tail -f /Users/aarelaponin/joget-enterprise-linux-8.1.6/joget-tomcat9/logs/catalina.out
```

Look for:
```
DocSubmitter: Starting execution
DocSubmitter: Service ID: student_enrollment
DocSubmitter: Loaded YAML configuration
DocSubmitter: Mapped 4 sections
DocSubmitter: Constructed GovStack JSON
DocSubmitter: Sending POST to: http://localhost:8080/jw/api/govstack/registration/services/student_enrollment/applications
DocSubmitter: Response Code: 200
DocSubmitter: Successfully sent data to GovStack API
```

[SCREENSHOT: Sender logs showing success]

**If you see errors**, check Part 6 (Troubleshooting) for solutions.

### 17.4 Check Receiver Logs

Verify receiver processed the data:

```bash
tail -f /Users/aarelaponin/joget-enterprise-linux-8.1.6-2/joget-tomcat9/logs/catalina.out
```

Look for:
```
ProcessingServer: Received POST request for service: student_enrollment
ProcessingServer: Loaded YAML configuration: student_enrollment.yml
ProcessingServer: Loaded section to form map from configuration: 3 mappings
ProcessingServer: Using primary key: abc-123-def-456
ProcessingServer: Created parent record in form: studentRegistrationForm
ProcessingServer: Saved to form: studentBasicInfo
ProcessingServer: Saved to form: studentAcademics
ProcessingServer: Saved to form: studentGuardian
ProcessingServer: Saved array data for 1 grids
ProcessingServer: Grid studentCourses: Saved 3 records
ProcessingServer: Successfully processed student enrollment application
```

[SCREENSHOT: Receiver logs showing success]

### 17.5 Verify Database - Parent Table

Check parent record was created:

```bash
mysql -h localhost -P 3307 -u root -p receiver_db
```

Query parent table:
```sql
SELECT * FROM students_registry ORDER BY dateCreated DESC LIMIT 1;
```

Expected output (adjust UUIDs):
```
+--------------------------------------+--------------------------------------+--------------------------------------+--------------------------------------+--------------------------------------+---------------------+
| id                                   | basic_data                           | academics_data                       | guardian_data                        | courses                              | dateCreated         |
+--------------------------------------+--------------------------------------+--------------------------------------+--------------------------------------+--------------------------------------+---------------------+
| abc-123-def-456-...                  | abc-123-def-456-...                  | abc-123-def-456-...                  | abc-123-def-456-...                  | abc-123-def-456-...                  | 2025-10-29 14:45:12 |
+--------------------------------------+--------------------------------------+--------------------------------------+--------------------------------------+--------------------------------------+---------------------+
1 row in set
```

[SCREENSHOT: MySQL output showing parent record]

**Key Check**: All reference fields (`basic_data`, `academics_data`, etc.) should contain the same UUID as `id`.

### 17.6 Verify Database - Child Tables

Check child form data:

**Basic Info**:
```sql
SELECT * FROM app_fd_student_basic_info WHERE parent_id = 'abc-123-def-456';
```

Expected:
```
+----------------+-----------+------------+--------+-------------+----------------------+------------+--------------+
| id             | full_name | dob        | gender | student_id  | email                | phone      | parent_id    |
+----------------+-----------+------------+--------+-------------+----------------------+------------+--------------+
| generated-uuid | Jane Smith| 2008-05-15 | F      | STU2025001  | jane.smith@example...| +123456... | abc-123-def..|
+----------------+-----------+------------+--------+-------------+----------------------+------------+--------------+
```

[SCREENSHOT: Basic info table data]

**Academics**:
```sql
SELECT * FROM app_fd_student_academics WHERE parent_id = 'abc-123-def-456';
```

Expected:
```
+----------------+-------------+---------+--------------------+--------------+--------------+
| id             | grade_level | program | previous_school    | academic_year| parent_id    |
+----------------+-------------+---------+--------------------+--------------+--------------+
| generated-uuid | 10          | Science | ABC Middle School  | 2025-2026    | abc-123-def..|
+----------------+-------------+---------+--------------------+--------------+--------------+
```

[SCREENSHOT: Academics table data]

**Guardian**:
```sql
SELECT * FROM app_fd_student_guardian WHERE parent_id = 'abc-123-def-456';
```

Expected:
```
+----------------+---------------+--------------+-------------+--------------------------------+--------------+
| id             | guardian_name | relationship | contact     | address                        | parent_id    |
+----------------+---------------+--------------+-------------+--------------------------------+--------------+
| generated-uuid | John Smith    | father       | +123456...  | 123 Main Street, Anytown...   | abc-123-def..|
+----------------+---------------+--------------+-------------+--------------------------------+--------------+
```

[SCREENSHOT: Guardian table data]

### 17.7 Verify Database - Grid Table

Check course grid records:

```sql
SELECT * FROM app_fd_student_courses WHERE student_id = 'abc-123-def-456' ORDER BY id;
```

Expected (3 rows):
```
+----------------+-------------+-----------------------+---------+--------------+
| id             | course_code | course_name           | credits | student_id   |
+----------------+-------------+-----------------------+---------+--------------+
| generated-uuid | MATH101     | Algebra               | 4       | abc-123-def..|
| generated-uuid | ENG101      | English Literature    | 3       | abc-123-def..|
| generated-uuid | SCI101      | Biology               | 4       | abc-123-def..|
+----------------+-------------+-----------------------+---------+--------------+
3 rows in set
```

[SCREENSHOT: Courses grid table with 3 rows]

**Key Check**: All 3 courses should be present with correct data!

### 17.8 Verify Data Completeness

Run a comprehensive verification query:

```sql
SELECT
    sr.id as 'Student UUID',
    sbi.full_name as 'Name',
    sbi.student_id as 'Student ID',
    sbi.email as 'Email',
    sa.grade_level as 'Grade',
    sa.program as 'Program',
    sg.guardian_name as 'Guardian',
    COUNT(sc.id) as 'Courses'
FROM students_registry sr
LEFT JOIN app_fd_student_basic_info sbi ON sr.id = sbi.parent_id
LEFT JOIN app_fd_student_academics sa ON sr.id = sa.parent_id
LEFT JOIN app_fd_student_guardian sg ON sr.id = sg.parent_id
LEFT JOIN app_fd_student_courses sc ON sr.id = sc.student_id
WHERE sr.id = 'abc-123-def-456'
GROUP BY sr.id;
```

Expected output:
```
+--------------------------------------+------------+-------------+----------------------+-------+---------+------------+---------+
| Student UUID                         | Name       | Student ID  | Email                | Grade | Program | Guardian   | Courses |
+--------------------------------------+------------+-------------+----------------------+-------+---------+------------+---------+
| abc-123-def-456...                   | Jane Smith | STU2025001  | jane.smith@example...| 10    | Science | John Smith | 3       |
+--------------------------------------+------------+-------------+----------------------+-------+---------+------------+---------+
```

[SCREENSHOT: Comprehensive verification query result]

### 17.9 Test Second Submission

Submit another student to verify the system handles multiple submissions:

**Student 2 Data**:
```
Name: Bob Johnson
DOB: 2009-03-20
Gender: M
Student ID: STU2025002
Email: bob.johnson@example.com
Phone: +1234567892

Grade: 9
Program: Arts
Previous School: XYZ Elementary
Academic Year: 2025-2026

Guardian: Mary Johnson (mother), +1234567893
Address: 456 Oak Avenue, Somewhere, ST 67890

Courses:
- ART101, Drawing, 3 credits
- MUS101, Music Theory, 2 credits
```

Submit and verify same as above. Both students should exist in database.

[SCREENSHOT: Database showing 2 students]

### 17.10 Success Criteria Verification

Confirm all success criteria met:

```
✅ Sender form accepts data input
✅ DocSubmitter plugin executes successfully
✅ HTTP 200 response received from receiver
✅ Receiver logs show successful processing
✅ Parent record created in students_registry
✅ All reference fields populated with UUID
✅ Basic info saved in app_fd_student_basic_info
✅ Academics saved in app_fd_student_academics
✅ Guardian saved in app_fd_student_guardian
✅ All course records saved in app_fd_student_courses
✅ No hardcoded fallback messages in logs
✅ Multiple submissions work correctly
✅ Data integrity maintained (UUIDs link correctly)
✅ NO JAVA CODE CHANGES were needed!
```

[SCREENSHOT: Checklist with all items checked]

### 17.11 Testing Complete!

🎉 **Congratulations!** You've successfully implemented a complete multi-service enrollment system!

**What You Achieved**:
- Created 5 Joget forms (1 parent + 3 child + 1 grid)
- Wrote comprehensive receiver YAML (80+ lines)
- Configured sender YAML
- Built and deployed both plugins
- Created working sender process
- Successfully submitted and verified data
- Handled one-to-many relationships (courses grid)
- All through YAML configuration - NO code changes!

**Time Check**: You should be ~4 hours into the tutorial (as estimated).

---

**End of Part 3: Complete Tutorial**

You now have:
- ✅ Complete working student enrollment service
- ✅ Hands-on experience with entire workflow
- ✅ Understanding of receiver and sender configurations
- ✅ Knowledge of form creation and mappings
- ✅ Testing and verification skills

**Next**: Explore Part 4 (Advanced Topics) for complex features, or Part 5 (YAML Reference) for detailed syntax guide.

[Continue to Part 4 →](#part-4-advanced-topics)

---

# PART 4: ADVANCED TOPICS

---

## 18. Complex Data Structures

Once you're comfortable with basic services, you can handle more complex data scenarios.

### 18.1 Nested Objects (Multiple Levels)

Sometimes data is nested several levels deep in the JSON structure.

**Example: Address with Geographic Coordinates**

```json
{
  "address": {
    "street": "123 Main St",
    "city": "Springfield",
    "state": "ST",
    "postalCode": "12345",
    "country": "USA",
    "geolocation": {
      "latitude": 42.1234,
      "longitude": -71.5678,
      "accuracy": "high"
    }
  }
}
```

**Mapping Strategy**:

```yaml
formMappings:
  addressInfo:
    type: form
    formId: addressForm
    fields:
      # Simple nested fields
      - joget: street
        govstack: address.street
        jsonPath: $.address.street

      - joget: city
        govstack: address.city
        jsonPath: $.address.city

      # Deeply nested fields
      - joget: latitude
        govstack: address.geolocation.latitude
        jsonPath: $.address.geolocation.latitude
        transform: numeric

      - joget: longitude
        govstack: address.geolocation.longitude
        jsonPath: $.address.geolocation.longitude
        transform: numeric
```

[SCREENSHOT: Deeply nested JSON structure visualization]

**Best Practice**: Flatten deep nesting in Joget forms. Store `latitude` and `longitude` as top-level fields in your form rather than trying to replicate JSON structure.

### 18.2 Arrays of Objects

When you have arrays that aren't simple grids but contain complex nested data.

**Example: Multiple Addresses with Types**

```json
{
  "addresses": [
    {
      "type": "home",
      "address": {
        "street": "123 Main St",
        "city": "Springfield"
      },
      "isPrimary": true
    },
    {
      "type": "work",
      "address": {
        "street": "456 Office Blvd",
        "city": "Worktown"
      },
      "isPrimary": false
    }
  ]
}
```

**Mapping Strategy**:

```yaml
formMappings:
  addresses:
    type: array
    govstack: addresses
    formId: addressesGrid
    parentField: person_id
    fields:
      - joget: address_type
        govstack: type
        jsonPath: $.type

      - joget: street
        govstack: address.street
        jsonPath: $.address.street

      - joget: city
        govstack: address.city
        jsonPath: $.address.city

      - joget: is_primary
        govstack: isPrimary
        jsonPath: $.isPrimary
        transform: boolean
```

[SCREENSHOT: Complex array mapping diagram]

**Key Point**: JSONPath for array items is relative to each array element. `$.address.street` means "the street field within the address object within each array item".

### 18.3 Conditional Fields

Fields that only exist under certain conditions.

**Example: Student with Optional Guardian**

```json
{
  "student": {
    "name": "Jane Doe",
    "age": 10,
    "hasGuardian": true,
    "guardian": {
      "name": "John Doe",
      "relationship": "father"
    }
  }
}
```

**Handling Strategy**:

1. **In YAML**: Map the field normally
2. **In Forms**: Make field optional (not required)
3. **System Behavior**: ProcessingServer handles missing fields gracefully

```yaml
fields:
  - joget: guardian_name
    govstack: guardian.name
    jsonPath: $.guardian.name
    # No special configuration needed - system handles nulls
```

**Important**: If a JSON field doesn't exist, the system will:
- Skip mapping that field
- Leave Joget field empty
- Continue processing other fields
- Log a warning (not an error)

### 18.4 Multiple Arrays in Same Service

When one service has multiple one-to-many relationships.

**Example: Student with Courses AND Awards**

```json
{
  "extension": {
    "courses": [
      {"code": "MATH101", "name": "Algebra"}
    ],
    "awards": [
      {"title": "Honor Roll", "year": "2024"}
    ]
  }
}
```

**Configuration**:

```yaml
serviceConfig:
  gridMappings:
    studentCourses:
      formId: "studentCoursesForm"
      parentField: "student_id"
    studentAwards:
      formId: "studentAwardsForm"
      parentField: "student_id"

formMappings:
  studentCourses:
    type: array
    govstack: extension.courses
    formId: studentCoursesForm
    parentField: student_id
    fields:
      - joget: course_code
        govstack: code
        jsonPath: $.code

  studentAwards:
    type: array
    govstack: extension.awards
    formId: studentAwardsForm
    parentField: student_id
    fields:
      - joget: award_title
        govstack: title
        jsonPath: $.title
```

[SCREENSHOT: Database showing multiple grid tables linked to same parent]

### 18.5 Polymorphic Data (Different Types)

When an array can contain different types of objects.

**Example: Contact Methods (Email, Phone, Social Media)**

```json
{
  "contactMethods": [
    {"type": "email", "value": "john@example.com", "preferred": true},
    {"type": "phone", "value": "+1234567890", "smsEnabled": true},
    {"type": "linkedin", "value": "john-doe", "public": false}
  ]
}
```

**Approach 1: Single Grid with Type Discriminator**

```yaml
formMappings:
  contactMethods:
    type: array
    govstack: contactMethods
    formId: contactMethodsGrid
    fields:
      - joget: contact_type
        govstack: type
        jsonPath: $.type

      - joget: contact_value
        govstack: value
        jsonPath: $.value

      # Optional fields (not all items have them)
      - joget: is_preferred
        govstack: preferred
        jsonPath: $.preferred
        transform: boolean

      - joget: sms_enabled
        govstack: smsEnabled
        jsonPath: $.smsEnabled
        transform: boolean
```

**Approach 2: Separate Grids per Type** (more complex, not recommended for beginners)

### 18.6 Handling Empty Arrays

When JSON might have empty arrays:

```json
{
  "courses": []
}
```

**System Behavior**:
- ProcessingServer checks array length
- If empty (length = 0), skips grid processing
- No error thrown
- Parent record still created

**No special configuration needed** - system handles automatically.

---

## 19. Data Transformations

Transform data between sender and receiver to handle format differences.

### 19.1 Available Transform Functions

#### Date Transformations

**date_ISO8601_to_date**: Converts ISO 8601 datetime to simple date
```yaml
- joget: birth_date
  govstack: birthDate
  jsonPath: $.birthDate
  transform: date_ISO8601_to_date

# Input:  "2008-05-15T00:00:00Z"
# Output: "2008-05-15"
```

**date_to_ISO8601**: Reverse conversion (sender side)
```yaml
- joget: enrollment_date
  govstack: enrollmentDate
  transform: date_to_ISO8601

# Input:  "2025-10-29"
# Output: "2025-10-29T00:00:00Z"
```

[SCREENSHOT: Date transformation visualization]

#### Numeric Transformations

**numeric**: Converts string to number
```yaml
- joget: credits
  govstack: credits
  jsonPath: $.credits
  transform: numeric

# Input:  "4"
# Output: 4
```

**decimal**: Converts to decimal with precision
```yaml
- joget: gpa
  govstack: gpa
  jsonPath: $.gpa
  transform: decimal

# Input:  "3.75"
# Output: 3.75
```

#### Boolean Transformations

**boolean**: Converts various formats to true/false
```yaml
- joget: is_active
  govstack: active
  jsonPath: $.active
  transform: boolean

# Accepts: "true", "false", "1", "0", "yes", "no"
# Output: true or false
```

#### String Transformations

**uppercase**: Convert to uppercase
```yaml
- joget: country_code
  govstack: countryCode
  jsonPath: $.countryCode
  transform: uppercase

# Input:  "usa"
# Output: "USA"
```

**lowercase**: Convert to lowercase
```yaml
- joget: email
  govstack: email
  jsonPath: $.email
  transform: lowercase

# Input:  "John@EXAMPLE.COM"
# Output: "john@example.com"
```

**trim**: Remove leading/trailing whitespace
```yaml
- joget: description
  govstack: description
  jsonPath: $.description
  transform: trim

# Input:  "  Some text  "
# Output: "Some text"
```

### 19.2 Chaining Transformations

Some systems support chaining multiple transforms:

```yaml
- joget: email
  govstack: email
  jsonPath: $.email
  transform:
    - trim
    - lowercase
```

**Note**: Check your plugin version for chaining support. If not supported, apply one transform at a time.

### 19.3 Custom Transformations

For transformations not provided by default, you have options:

**Option 1: Pre-process in Sender Form**

Use Joget's form validators or custom JavaScript to transform data before DocSubmitter processes it.

**Option 2: Post-process in Receiver Form**

Use Joget's form load/store binders to transform data after ProcessingServer saves it.

**Option 3: Request Feature**

If you need a common transformation (e.g., phone number formatting), request it as a feature for the plugin.

### 19.4 Conditional Transformations

Apply transformations only when certain conditions are met.

**Example: Convert age to numeric only if present**

```yaml
- joget: age
  govstack: age
  jsonPath: $.age
  transform: numeric
  # System skips transform if field is null/empty
```

**System Behavior**: Transformations are skipped for null or empty values automatically.

### 19.5 Transform Error Handling

When a transformation fails:

**Strict Mode** (default):
- Transformation error → Field not saved
- Warning logged
- Processing continues for other fields

**Fail-Fast Mode** (optional configuration):
- Transformation error → Entire submission fails
- Error returned to sender
- No partial data saved

Configure in YAML:

```yaml
serviceConfig:
  transformErrorHandling: "strict"  # or "fail-fast"
```

---

## 20. Error Handling

Understanding and handling errors effectively.

### 20.1 Error Categories

**1. Configuration Errors** (Prevent startup)
```
ConfigurationException: parentFormId not found in YAML
ConfigurationException: sectionToFormMap is empty
```

**Fix**: Update YAML before deployment

**2. Validation Errors** (Reject submission)
```
HTTP 400: Required field missing: name.text
HTTP 400: Invalid JSON structure
```

**Fix**: Ensure sender sends complete data

**3. Mapping Errors** (Partial failure)
```
WARNING: Field 'phone' not found in JSON, skipping
WARNING: Transform failed for field 'credits': not a number
```

**Fix**: Review field mappings and data quality

**4. System Errors** (Technical failure)
```
HTTP 500: Database connection failed
HTTP 500: Form not found: studentBasicInfo
```

**Fix**: Check system configuration, form existence

### 20.2 Error Response Formats

**Success Response**:
```json
{
  "status": "success",
  "message": "Application submitted successfully",
  "applicationId": "abc-123-def-456"
}
```

**Error Response**:
```json
{
  "status": "error",
  "message": "Failed to process application",
  "error": {
    "code": "CONFIGURATION_ERROR",
    "details": "Form 'studentBasicInfo' not found in system"
  }
}
```

[SCREENSHOT: Error response in browser developer tools]

### 20.3 Graceful Degradation

The system handles missing optional fields gracefully:

**Scenario**: Phone number is optional, not provided

**Behavior**:
```
1. JSON has no "phone" field
2. Mapping attempts: $.extension.contact.phone
3. Result: null/empty
4. Action: Skip field, log warning, continue
5. Result: Form saved without phone
```

**Logs**:
```
WARN: Field 'phone' not found in JSON at path $.extension.contact.phone
INFO: Saved to form: studentBasicInfo (5 of 6 fields populated)
```

### 20.4 Validation at Each Stage

**Stage 1: Sender Side (DocSubmitter)**

Validates:
- YAML file exists and is valid
- Service ID matches YAML
- All mapped fields exist in form
- Required Joget validations pass

**Stage 2: Transmission**

Validates:
- JSON is well-formed
- API endpoint is reachable
- Network connectivity

**Stage 3: Receiver Side (ProcessingServer)**

Validates:
- Service YAML exists for service ID
- Required configuration fields present
- Forms exist in Joget
- Parent form can be created

**Stage 4: Database**

Validates:
- Tables exist
- Field types match
- Constraints satisfied (unique, not null)

[SCREENSHOT: Validation stages diagram]

### 20.5 Retry Logic

**Built-in Retry** (depending on plugin version):

```yaml
serviceConfig:
  retry:
    enabled: true
    maxAttempts: 3
    backoffMs: 1000
```

**Retry Scenarios**:
- ✅ Network timeout → Retry
- ✅ HTTP 503 (Service Unavailable) → Retry
- ❌ HTTP 400 (Bad Request) → Don't retry
- ❌ HTTP 500 (Server Error) → Don't retry (likely config issue)

### 20.6 Transaction Handling

**Default Behavior**: All-or-nothing per section

```
Submission includes:
- Basic Info (3 fields)
- Academics (4 fields)
- Guardian (4 fields)

If Guardian section fails:
- Basic Info: ✅ Saved
- Academics: ✅ Saved
- Guardian: ❌ Not saved

Result: Partial success (2 of 3 sections)
```

**Atomicity Level**: Per-form, not per-submission

### 20.7 Error Logging Best Practices

**In Production**:

1. **Enable detailed logging**:
```properties
# In Joget's log4j.properties
log4j.logger.global.govstack=DEBUG
```

2. **Monitor log files**:
```bash
# Tail logs in real-time
tail -f /path/to/joget/logs/catalina.out | grep -i "govstack\|error"
```

3. **Set up alerts** for critical errors:
```bash
# Example: Alert on ConfigurationException
tail -f catalina.out | grep --line-buffered "ConfigurationException" | \
  while read line; do
    echo "ALERT: Configuration error detected"
    # Send notification
  done
```

---

## 21. Performance Optimization

Optimize your service for high-volume submissions.

### 21.1 YAML Configuration Caching

**How it works**:
- YAML files loaded once at startup
- Cached in memory
- Reused for all submissions

**Implication**: YAML changes require plugin reload or restart.

**Best Practice**: Design YAML carefully to minimize reloads.

### 21.2 Database Connection Pooling

Joget manages database connections. For high volume:

**Tune connection pool** in Joget's `app_datasource-default.properties`:

```properties
# Increase pool size for high concurrency
datasource.default.maxActive=100
datasource.default.maxIdle=20
datasource.default.minIdle=10

# Adjust timeouts
datasource.default.maxWait=30000
datasource.default.timeBetweenEvictionRunsMillis=60000
```

[SCREENSHOT: Connection pool configuration]

### 21.3 Form Design for Performance

**Optimization 1: Index Key Fields**

Add database indexes on frequently queried fields:

```sql
-- Add index on parent_id for faster joins
ALTER TABLE app_fd_student_basic_info
  ADD INDEX idx_parent_id (parent_id);

-- Add index on student_id for faster lookups
ALTER TABLE app_fd_student_basic_info
  ADD INDEX idx_student_id (student_id);
```

**Optimization 2: Minimize Grid Records**

Large grids impact performance. Consider:
- Limiting max rows (e.g., 10 courses per student)
- Pagination for viewing
- Archiving old records

**Optimization 3: Reduce Field Count**

Forms with 50+ fields are slower. Consider:
- Splitting into multiple sections
- Removing rarely-used fields
- Using text area for flexible data

### 21.4 JSON Payload Size

**Monitor payload size**:

```bash
# Check JSON size in logs
grep "JSON payload size" catalina.out
```

**Optimization strategies**:

1. **Limit array sizes**: Cap courses, documents, etc.
2. **Compress large text**: Truncate descriptions
3. **Avoid base64 binary**: Use file uploads separately
4. **Remove unnecessary fields**: Only send what's mapped

**Benchmark**:
- Small payload (<10KB): ~50ms processing
- Medium payload (10-100KB): ~200ms processing
- Large payload (>100KB): ~500ms+ processing

### 21.5 Concurrent Submissions

**Test concurrent load**:

```bash
# Simulate 10 concurrent submissions
for i in {1..10}; do
  curl -X POST http://localhost:8080/.../applications \
    -H "Content-Type: application/json" \
    -d @test-data.json &
done
wait
```

**Monitor**:
- Response times
- Database locks
- Memory usage
- Error rates

**Tuning**: Adjust based on your environment:
- Small deployment (< 100 submissions/day): Default settings fine
- Medium deployment (100-1000/day): Increase connection pool
- Large deployment (>1000/day): Consider clustering, load balancing

### 21.6 Caching Strategies

**What to cache**:
- ✅ YAML configurations
- ✅ Form definitions
- ✅ User sessions
- ❌ Submission data (always fresh)
- ❌ Database records (always current)

**Joget built-in caching**: Enabled by default for forms and processes.

### 21.7 Monitoring and Metrics

**Key metrics to track**:

1. **Submission rate**: Submissions per minute/hour
2. **Success rate**: Successful / Total submissions
3. **Average processing time**: Time from POST to database save
4. **Error rate**: Errors per 100 submissions
5. **Database query time**: Time spent in database operations

**Simple monitoring script**:

```bash
#!/bin/bash
# Count submissions in last hour
echo "Submissions in last hour:"
mysql -h localhost -P 3307 -u root -p receiver_db -e \
  "SELECT COUNT(*) FROM students_registry
   WHERE dateCreated > DATE_SUB(NOW(), INTERVAL 1 HOUR);"

# Check error rate
echo "Errors in last hour:"
grep "ERROR" catalina.out | grep -c "$(date +%Y-%m-%d)" || echo "0"
```

[SCREENSHOT: Simple monitoring dashboard]

### 21.8 Performance Checklist

Before going to production:

```
□ Database indexes added on key fields
□ Connection pool sized appropriately
□ YAML configurations validated
□ Large payload handling tested
□ Concurrent load tested (10+ simultaneous)
□ Error handling verified
□ Monitoring scripts set up
□ Backup strategy in place
□ Log rotation configured
□ Performance baseline established
```

---

**End of Part 4: Advanced Topics**

You now understand:
- ✅ Complex data structure handling
- ✅ Data transformation techniques
- ✅ Comprehensive error handling
- ✅ Performance optimization strategies

**Next**: Continue to Part 5 (YAML Reference) for complete syntax documentation.

[Continue to Part 5 →](#part-5-yaml-reference)

---

# PART 5: YAML REFERENCE

---

## 22. Complete YAML Schema

This section provides the complete reference for service YAML configuration files.

### 22.1 Full Schema Overview

```yaml
# Service Configuration YAML - Complete Schema
# Use this as a reference for all available fields

service:
  id: string                    # REQUIRED - Unique service identifier
  name: string                  # REQUIRED - Human-readable service name
  version: string               # REQUIRED - Service version (e.g., "1.0")
  govstackVersion: string       # REQUIRED - GovStack spec version
  metadataVersion: string       # REQUIRED - YAML schema version
  lastUpdated: string           # REQUIRED - ISO date (YYYY-MM-DD)
  description: string           # OPTIONAL - Service description

  serviceConfig:
    parentFormId: string        # REQUIRED - Parent form ID
    parentReferenceFields:      # REQUIRED - List of reference fields
      - string                  # Field names in parent form
    sectionToFormMap:           # REQUIRED - Section to form mapping
      sectionName: formId       # Key: section, Value: form ID
    gridMappings:               # OPTIONAL - Grid form configurations
      sectionName:              # Grid section name
        formId: string          # Grid form ID
        parentField: string     # Parent reference field name
        parentColumn: string    # OPTIONAL - Database column name
    defaults:                   # OPTIONAL - Default values
      gridParentField: string   # Default: "parent_id"
      gridParentColumn: string  # Default: "c_parent_id"

formMappings:
  sectionName:                  # Must match sectionToFormMap key
    type: form|grid             # REQUIRED - Type of mapping
    formId: string              # REQUIRED - Joget form ID
    description: string         # OPTIONAL - Mapping description
    fields:                     # REQUIRED - Field mappings array
      - joget: string           # REQUIRED - Joget field ID
        govstack: string        # REQUIRED - GovStack path (dot notation)
        jsonPath: string        # REQUIRED (receiver) - JSONPath expression
        transform: string       # OPTIONAL - Transform function name
        description: string     # OPTIONAL - Field description
        required: boolean       # OPTIONAL - Is field required
        default: any            # OPTIONAL - Default value if missing
```

[SCREENSHOT: Complete YAML structure diagram]

### 22.2 Hierarchical Visualization

```
service
├── id (required, string, lowercase_with_underscores)
├── name (required, string)
├── version (required, string, e.g., "1.0")
├── govstackVersion (required, string, e.g., "1.0")
├── metadataVersion (required, string, e.g., "1.0.0")
├── lastUpdated (required, string, ISO date YYYY-MM-DD)
├── description (optional, string)
└── serviceConfig (required, object)
    ├── parentFormId (required, string)
    ├── parentReferenceFields (required, array of strings)
    ├── sectionToFormMap (required, object)
    │   └── {sectionName}: {formId} (string key-value pairs)
    ├── gridMappings (optional, object)
    │   └── {sectionName} (grid section name)
    │       ├── formId (required, string)
    │       ├── parentField (required, string)
    │       └── parentColumn (optional, string, default: "c_parent_id")
    └── defaults (optional, object)
        ├── gridParentField (optional, string, default: "parent_id")
        └── gridParentColumn (optional, string, default: "c_parent_id")

formMappings (required, object)
└── {sectionName} (must match sectionToFormMap key)
    ├── type (required, enum: "form" | "grid")
    ├── formId (required, string, must match Joget form ID exactly)
    ├── description (optional, string)
    └── fields (required, array)
        └── field (object)
            ├── joget (required, string, Joget field ID)
            ├── govstack (required, string, dot notation path)
            ├── jsonPath (required for receiver, string, JSONPath expression)
            ├── transform (optional, string, transform function name)
            ├── description (optional, string)
            ├── required (optional, boolean, default: false)
            └── default (optional, any type)
```

### 22.3 Schema Version Compatibility

| Schema Version | Joget Version | GovStack Version | Added Features |
|----------------|---------------|------------------|----------------|
| 1.0.0          | 8.1+          | 1.0              | Initial release, basic mappings |
| 1.0.1          | 8.1+          | 1.0              | Added transform functions |
| 1.1.0          | 8.1+          | 1.0              | Grid mappings support |
| 1.2.0          | 8.1+          | 1.0              | Default values, optional fields |

**Current Version**: 1.2.0 (all examples in this guide)

---

## 23. Required vs Optional Fields

### 23.1 Required Fields Table

These fields **MUST** be present in every service YAML file:

| Field Path | Type | Description | Example |
|------------|------|-------------|---------|
| `service.id` | string | Unique service identifier | `student_enrollment` |
| `service.name` | string | Human-readable name | `"Student Enrollment Service"` |
| `service.version` | string | Service version | `"1.0"` |
| `service.govstackVersion` | string | GovStack spec version | `"1.0"` |
| `service.metadataVersion` | string | YAML schema version | `"1.0.0"` |
| `service.lastUpdated` | string | ISO date | `"2025-10-29"` |
| `service.serviceConfig.parentFormId` | string | Parent form ID | `"studentRegistrationForm"` |
| `service.serviceConfig.parentReferenceFields` | array | Reference field list | `["basic_data", "courses"]` |
| `service.serviceConfig.sectionToFormMap` | object | Section to form map | `{basicInfo: "studentBasicInfo"}` |
| `formMappings` | object | Form mappings container | See schema |
| `formMappings.{section}.type` | string | Mapping type | `"form"` or `"grid"` |
| `formMappings.{section}.formId` | string | Joget form ID | `"studentBasicInfo"` |
| `formMappings.{section}.fields` | array | Field mappings | See schema |
| `formMappings.{section}.fields[].joget` | string | Joget field ID | `"full_name"` |
| `formMappings.{section}.fields[].govstack` | string | GovStack path | `"name.text"` |

**Receiver Only**:
| Field Path | Type | Description | Example |
|------------|------|-------------|---------|
| `formMappings.{section}.fields[].jsonPath` | string | JSONPath expression | `"$.name.text"` |

### 23.2 Optional Fields Table

These fields can be omitted (defaults will be used):

| Field Path | Type | Default | Description |
|------------|------|---------|-------------|
| `service.description` | string | `""` | Service description |
| `service.serviceConfig.gridMappings` | object | `{}` | Grid configurations (only if grids exist) |
| `service.serviceConfig.defaults` | object | See below | Default configurations |
| `service.serviceConfig.defaults.gridParentField` | string | `"parent_id"` | Grid parent field name |
| `service.serviceConfig.defaults.gridParentColumn` | string | `"c_parent_id"` | Grid parent column name |
| `formMappings.{section}.description` | string | `""` | Mapping description |
| `formMappings.{section}.fields[].transform` | string | none | Transform function |
| `formMappings.{section}.fields[].description` | string | `""` | Field description |
| `formMappings.{section}.fields[].required` | boolean | `false` | Is field required |
| `formMappings.{section}.fields[].default` | any | none | Default value |

### 23.3 Conditional Requirements

Some fields are required only in specific contexts:

**Grid Mappings** (required if service has grid forms):
```yaml
serviceConfig:
  gridMappings:
    coursesSection:           # REQUIRED if section is a grid
      formId: "coursesForm"   # REQUIRED
      parentField: "parent_id" # REQUIRED (or use default)
```

**JSONPath** (required for receiver, not needed for sender):
```yaml
# Receiver configuration
fields:
  - joget: full_name
    govstack: name.text
    jsonPath: $.name.text    # REQUIRED for receiver

# Sender configuration
fields:
  - joget: full_name
    govstack: name.text      # jsonPath NOT needed for sender
```

**Parent Column** (optional, only for custom column names):
```yaml
gridMappings:
  courses:
    formId: "coursesForm"
    parentField: "parent_id"
    parentColumn: "c_custom_parent_id"  # Only if not using default
```

### 23.4 Validation Rules

The YAML validator (`tools/yaml-validator.sh`) checks:

1. **Service ID Format**:
   - ✅ Lowercase letters, numbers, underscores only
   - ✅ No spaces, hyphens, or special characters
   - ❌ `StudentEnrollment` → Use `student_enrollment`
   - ❌ `student-enrollment` → Use `student_enrollment`

2. **Date Format** (`lastUpdated`):
   - ✅ ISO 8601 format: `YYYY-MM-DD`
   - ❌ `10/29/2025` → Use `2025-10-29`
   - ❌ `2025-10-29T10:30:00Z` → Use `2025-10-29`

3. **Form ID Matching**:
   - Form IDs in YAML must match Joget form IDs exactly (case-sensitive)
   - Section names in `sectionToFormMap` must match `formMappings` keys

4. **Array Consistency**:
   - Every item in `parentReferenceFields` should correspond to a section in `sectionToFormMap`
   - Every section in `formMappings` should appear in `sectionToFormMap`

---

## 24. Field Mapping Syntax

### 24.1 GovStack Path Patterns

GovStack paths use **dot notation** to navigate JSON structure:

| Pattern | Description | Example | Maps To |
|---------|-------------|---------|---------|
| `field` | Top-level field | `birthDate` | `$.birthDate` |
| `object.field` | Nested field | `name.text` | `$.name.text` |
| `object.nested.field` | Multi-level | `address.city.name` | `$.address.city.name` |
| `extension.custom` | Extension fields | `extension.email` | `$.extension.email` |

**Common GovStack Paths**:

```yaml
# Standard GovStack fields
name.text                    # Full name
birthDate                    # Birth date (ISO 8601)
extension.email              # Email address
extension.phone              # Phone number
extension.address.street     # Street address
extension.address.city       # City
extension.address.postalCode # Postal/ZIP code
```

[SCREENSHOT: GovStack JSON structure with paths highlighted]

### 24.2 JSONPath Reference

JSONPath expressions are used in **receiver configurations** to extract data from GovStack JSON.

**Basic Syntax**:

| Expression | Description | Example |
|------------|-------------|---------|
| `$` | Root object | `$` |
| `$.field` | Direct field access | `$.name` |
| `$.object.field` | Nested access | `$.name.text` |
| `$.array[0]` | Array element | `$.courses[0]` |
| `$.array[*]` | All array elements | `$.courses[*]` |
| `$..field` | Recursive descent | `$..email` |

**Complete JSONPath Reference Table**:

| Operator | Description | Example | Result |
|----------|-------------|---------|--------|
| `$` | Root node | `$` | Entire JSON |
| `.field` | Child field | `$.name` | Value of "name" |
| `..field` | Recursive search | `$..email` | All "email" fields |
| `[n]` | Array index | `$.courses[0]` | First course |
| `[*]` | All array items | `$.courses[*]` | All courses |
| `[start:end]` | Array slice | `$.courses[0:2]` | First 2 courses |
| `[?(@.field)]` | Filter exists | `$.courses[?(@.code)]` | Courses with code |
| `[?(@.field=='value')]` | Filter equals | `$.courses[?(@.type=='required')]` | Required courses |
| `[?(@.field>value)]` | Filter compare | `$.courses[?(@.credits>3)]` | High-credit courses |

**Practical Examples**:

```yaml
# Simple field access
- joget: full_name
  govstack: name.text
  jsonPath: $.name.text

# Nested object access
- joget: email
  govstack: extension.contact.email
  jsonPath: $.extension.contact.email

# Array access (for grids)
- joget: course_code
  govstack: courses[*].code
  jsonPath: $.courses[*].code

# Deep nested access
- joget: street
  govstack: extension.address.street.name
  jsonPath: $.extension.address.street.name

# Conditional access (with default handling)
- joget: middle_name
  govstack: name.middle
  jsonPath: $.name.middle
  default: ""  # Empty if not present
```

### 24.3 Joget Field ID Conventions

**Naming Rules**:
- Lowercase letters and underscores only
- No spaces, hyphens, or special characters
- Descriptive and concise
- Match database column names (prefixed with `c_`)

**Common Patterns**:

| Field Type | Joget ID Pattern | Example |
|------------|------------------|---------|
| Text field | `field_name` | `full_name`, `email` |
| Date field | `field_date` | `birth_date`, `start_date` |
| Select box | `field_type` | `course_type`, `gender` |
| Number field | `field_num` | `age_num`, `credits_num` |
| Hidden field | `field_id` | `parent_id`, `student_id` |
| Grid reference | `grid_name` | `courses`, `addresses` |

**Database Column Names**:

Joget automatically prefixes column names with `c_`:

| Joget Field ID | Database Column | SQL Reference |
|----------------|-----------------|---------------|
| `full_name` | `c_full_name` | `SELECT c_full_name FROM ...` |
| `birth_date` | `c_birth_date` | `WHERE c_birth_date > ...` |
| `parent_id` | `c_parent_id` | `JOIN ON c_parent_id = ...` |

**Reserved Field Names** (automatic in Joget):
- `id` - Auto-generated UUID (primary key)
- `dateCreated` - Timestamp of creation
- `dateModified` - Timestamp of last modification
- `createdBy` - Username who created record
| `createdByName` - Full name who created record
| `modifiedBy` - Username who last modified
| `modifiedByName` - Full name who last modified

### 24.4 Complete Mapping Examples

**Example 1: Simple Field Mapping**

```yaml
formMappings:
  contactInfo:
    type: form
    formId: contactForm
    description: "Basic contact information"
    fields:
      # Text field
      - joget: full_name
        govstack: name.text
        jsonPath: $.name.text
        description: "Contact's full name"
        required: true

      # Email field
      - joget: email
        govstack: extension.email
        jsonPath: $.extension.email
        description: "Email address"
        required: true

      # Optional phone field with default
      - joget: phone
        govstack: extension.phone
        jsonPath: $.extension.phone
        description: "Phone number"
        required: false
        default: ""
```

**Example 2: Grid Mapping with Transforms**

```yaml
formMappings:
  courses:
    type: grid
    formId: studentCoursesForm
    description: "Student course selections"
    fields:
      # Course code (uppercase transform)
      - joget: course_code
        govstack: courses[*].code
        jsonPath: $.courses[*].code
        transform: uppercase
        description: "Course code (uppercase)"

      # Course name (no transform)
      - joget: course_name
        govstack: courses[*].name
        jsonPath: $.courses[*].name
        description: "Course name"

      # Credits (numeric transform)
      - joget: credits
        govstack: courses[*].credits
        jsonPath: $.courses[*].credits
        transform: numeric
        description: "Credit hours (numeric)"

      # Start date (date transform)
      - joget: start_date
        govstack: courses[*].startDate
        jsonPath: $.courses[*].startDate
        transform: date_ISO8601_to_date
        description: "Course start date"
```

**Example 3: Complex Nested Structure**

```yaml
formMappings:
  address:
    type: form
    formId: addressForm
    description: "Detailed address information"
    fields:
      # Street address
      - joget: street
        govstack: extension.address.street
        jsonPath: $.extension.address.street
        description: "Street address"

      # City
      - joget: city
        govstack: extension.address.city
        jsonPath: $.extension.address.city
        description: "City name"

      # State/Province
      - joget: state
        govstack: extension.address.state
        jsonPath: $.extension.address.state
        transform: uppercase
        description: "State code (uppercase)"

      # Postal code
      - joget: postal_code
        govstack: extension.address.postalCode
        jsonPath: $.extension.address.postalCode
        description: "Postal/ZIP code"

      # Country (with default)
      - joget: country
        govstack: extension.address.country
        jsonPath: $.extension.address.country
        default: "USA"
        description: "Country code"

      # Geolocation coordinates (deep nesting)
      - joget: latitude
        govstack: extension.address.geolocation.latitude
        jsonPath: $.extension.address.geolocation.latitude
        description: "GPS latitude"

      - joget: longitude
        govstack: extension.address.geolocation.longitude
        jsonPath: $.extension.address.geolocation.longitude
        description: "GPS longitude"
```

---

## 25. Transform Functions

Transform functions convert data from one format to another during mapping.

### 25.1 Complete Transform Catalog

**Date Transforms**:

| Transform Name | Input Format | Output Format | Example Input | Example Output |
|----------------|--------------|---------------|---------------|----------------|
| `date_ISO8601_to_date` | ISO 8601 string | `yyyy-MM-dd` | `"2024-10-29T10:30:00Z"` | `"2024-10-29"` |
| `date_to_ISO8601` | `yyyy-MM-dd` | ISO 8601 string | `"2024-10-29"` | `"2024-10-29T00:00:00Z"` |
| `date_to_timestamp` | `yyyy-MM-dd` | Unix timestamp | `"2024-10-29"` | `1730160000` |
| `timestamp_to_date` | Unix timestamp | `yyyy-MM-dd` | `1730160000` | `"2024-10-29"` |

**String Transforms**:

| Transform Name | Description | Example Input | Example Output |
|----------------|-------------|---------------|----------------|
| `uppercase` | Convert to uppercase | `"hello"` | `"HELLO"` |
| `lowercase` | Convert to lowercase | `"HELLO"` | `"hello"` |
| `trim` | Remove whitespace | `"  hello  "` | `"hello"` |
| `capitalize` | Capitalize first letter | `"hello world"` | `"Hello world"` |
| `title_case` | Capitalize each word | `"hello world"` | `"Hello World"` |

**Numeric Transforms**:

| Transform Name | Description | Example Input | Example Output |
|----------------|-------------|---------------|----------------|
| `numeric` | Convert to number | `"123"` | `123` |
| `string_to_int` | Convert to integer | `"123.45"` | `123` |
| `string_to_float` | Convert to float | `"123"` | `123.0` |
| `round` | Round to nearest int | `123.67` | `124` |
| `floor` | Round down | `123.67` | `123` |
| `ceiling` | Round up | `123.01` | `124` |

**Boolean Transforms**:

| Transform Name | Description | Example Input | Example Output |
|----------------|-------------|---------------|----------------|
| `boolean` | Convert to boolean | `"true"`, `"1"`, `"yes"` | `true` |
| `string_to_bool` | String to boolean | `"false"`, `"0"`, `"no"` | `false` |
| `bool_to_string` | Boolean to string | `true` | `"true"` |
| `bool_to_int` | Boolean to integer | `true` | `1` |

**Array Transforms**:

| Transform Name | Description | Example Input | Example Output |
|----------------|-------------|---------------|----------------|
| `array_to_string` | Join array | `["a", "b", "c"]` | `"a, b, c"` |
| `string_to_array` | Split string | `"a,b,c"` | `["a", "b", "c"]` |
| `array_first` | First element | `["a", "b", "c"]` | `"a"` |
| `array_last` | Last element | `["a", "b", "c"]` | `"c"` |
| `array_length` | Array length | `["a", "b", "c"]` | `3` |

### 25.2 Transform Usage Examples

**Date Transformation**:

```yaml
fields:
  - joget: birth_date
    govstack: birthDate
    jsonPath: $.birthDate
    transform: date_ISO8601_to_date
    description: "Convert ISO date to Joget date format"

# Input:  {"birthDate": "2005-03-15T00:00:00Z"}
# Output: c_birth_date = "2005-03-15" (in database)
```

**String Transformation**:

```yaml
fields:
  - joget: state_code
    govstack: extension.address.state
    jsonPath: $.extension.address.state
    transform: uppercase
    description: "Ensure state code is uppercase"

# Input:  {"extension": {"address": {"state": "ca"}}}
# Output: c_state_code = "CA"
```

**Numeric Transformation**:

```yaml
fields:
  - joget: age
    govstack: extension.age
    jsonPath: $.extension.age
    transform: numeric
    description: "Convert age string to number"

# Input:  {"extension": {"age": "25"}}
# Output: c_age = 25 (numeric type)
```

**Boolean Transformation**:

```yaml
fields:
  - joget: is_active
    govstack: extension.active
    jsonPath: $.extension.active
    transform: boolean
    description: "Convert active flag to boolean"

# Input:  {"extension": {"active": "yes"}}
# Output: c_is_active = true
```

### 25.3 Transform Chaining

**Currently not supported** - Each field can have only ONE transform.

If you need multiple transformations, handle in sequence:

```yaml
# NOT SUPPORTED:
- joget: state_code
  transform: ["trim", "uppercase"]  # ❌ Won't work

# INSTEAD, use single transform most critical:
- joget: state_code
  transform: uppercase  # ✅ Works
  # Pre-process data to trim before sending

# OR create intermediate mapping:
# Step 1: Trim in sender
# Step 2: Uppercase in receiver
```

### 25.4 Custom Transform Development

To add custom transforms, modify the plugin Java code:

**File**: `processing-server/src/main/java/global/govstack/processing/service/TransformService.java`

```java
public class TransformService {

    public Object applyTransform(String transformName, Object value) {
        switch (transformName) {
            case "date_ISO8601_to_date":
                return convertISO8601ToDate(value);

            case "uppercase":
                return value.toString().toUpperCase();

            // Add your custom transform here:
            case "my_custom_transform":
                return myCustomTransformLogic(value);

            default:
                return value;
        }
    }

    private Object myCustomTransformLogic(Object value) {
        // Your transformation logic
        return transformedValue;
    }
}
```

**Steps to Add Custom Transform**:

1. Edit `TransformService.java`
2. Add new case in `applyTransform()` switch statement
3. Implement transformation logic
4. Rebuild plugin: `mvn clean package`
5. Deploy to Joget
6. Use in YAML: `transform: my_custom_transform`

[SCREENSHOT: Custom transform code example]

### 25.5 Transform Error Handling

If a transform fails, the system logs a warning but continues:

```
WARN: Transform failed for field 'age' using transform 'numeric'
      Input value: "invalid"
      Storing original value: "invalid"
```

**Best Practices**:
1. **Validate data before transforms**: Ensure input data matches expected format
2. **Use defaults for optional fields**: Provide fallback values
3. **Test transforms thoroughly**: Verify with edge cases
4. **Log transform failures**: Monitor logs for transform errors

```yaml
# Example with default value for transform failure
fields:
  - joget: age
    govstack: extension.age
    jsonPath: $.extension.age
    transform: numeric
    default: 0  # Use 0 if transform fails
    description: "Age (numeric, defaults to 0 if invalid)"
```

---

**End of Part 5: YAML Reference**

You now have:
- ✅ Complete YAML schema documentation
- ✅ Required vs optional field reference
- ✅ Field mapping syntax guide
- ✅ Complete transform function catalog

**Next**: Continue to Part 6 (Troubleshooting) for error resolution guide.

[Continue to Part 6 →](#part-6-troubleshooting)

---

# PART 6: TROUBLESHOOTING

---

## 26. Common Errors and Solutions

This section covers the most frequent errors you'll encounter and how to fix them.

### 26.1 HTTP 400 Errors

HTTP 400 errors indicate bad requests - there's something wrong with the data or configuration.

#### Error: "Section mapping not found"

**Full Error Message**:
```
HTTP 400 Bad Request
{
  "error": "Section mapping not found for section: studentBasicInfo"
}
```

**Cause**: The section name in the JSON doesn't match any section in `sectionToFormMap`.

**Fix**:

Check your receiver YAML configuration:

```yaml
serviceConfig:
  sectionToFormMap:
    studentBasicInfo: "studentBasicInfo"  # Section name
    # ^^^^^^^^^^^^^^
    # This must match section names in JSON and formMappings

formMappings:
  studentBasicInfo:  # Must match above
  # ^^^^^^^^^^^^^^
    type: form
    formId: studentBasicInfo
```

**Verification**:
1. Check section names are consistent across:
   - `sectionToFormMap` (keys)
   - `formMappings` (keys)
   - JSON data being sent
2. Verify spelling and case (case-sensitive!)

[SCREENSHOT: Section mapping mismatch highlighted in code]

---

#### Error: "Required field missing"

**Full Error Message**:
```
HTTP 400 Bad Request
{
  "error": "Required field 'name.text' is missing from JSON"
}
```

**Cause**: A required field in your YAML mapping doesn't exist in the incoming JSON.

**Fix**:

**Option 1**: Add the field to the sender's form/JSON:
```yaml
# Sender YAML - ensure field is mapped
fields:
  - joget: full_name
    govstack: name.text  # This field is required
```

**Option 2**: Make the field optional in receiver YAML:
```yaml
# Receiver YAML - mark field as optional
fields:
  - joget: full_name
    govstack: name.text
    jsonPath: $.name.text
    required: false      # Add this
    default: ""          # Provide default value
```

**Option 3**: Remove the field mapping if not needed:
```yaml
# Receiver YAML - remove or comment out
# - joget: full_name
#   govstack: name.text
#   jsonPath: $.name.text
```

---

#### Error: "Invalid JSON structure"

**Full Error Message**:
```
HTTP 400 Bad Request
{
  "error": "Invalid JSON structure: Expected object at $.extension"
}
```

**Cause**: The JSON structure doesn't match what your mappings expect.

**Fix**:

1. **Check the actual JSON being sent**:

```bash
# View DocSubmitter logs for sent JSON
tail -f /path/to/sender/joget-tomcat9/logs/catalina.out | grep "Sending JSON"
```

2. **Compare with your JSONPath expressions**:

```yaml
# If JSONPath is $.extension.email
# JSON must have:
{
  "extension": {
    "email": "value"
  }
}

# NOT:
{
  "email": "value"  # Missing "extension" wrapper
}
```

3. **Fix sender configuration** to match expected structure:

```yaml
# Sender YAML
fields:
  - joget: email
    govstack: extension.email  # Creates {"extension": {"email": "..."}}
```

[SCREENSHOT: JSON structure validation error in logs]

---

#### Error: "parentFormId not configured"

**Full Error Message**:
```
HTTP 500 Internal Server Error
{
  "error": "parentFormId not configured in service configuration"
}
```

**Cause**: Missing `parentFormId` in receiver YAML's `serviceConfig`.

**Fix**:

Add `parentFormId` to your receiver YAML:

```yaml
service:
  id: student_enrollment
  serviceConfig:
    parentFormId: "studentRegistrationForm"  # ADD THIS LINE
    parentReferenceFields:
      - "basic_data"
    sectionToFormMap:
      studentBasicInfo: "studentBasicInfo"
```

**Verification**:
```bash
# Validate YAML syntax
./tools/yaml-validator.sh receiver/student_enrollment.yml
```

---

### 26.2 HTTP 500 Errors

HTTP 500 errors indicate server-side issues - something went wrong during processing.

#### Error: "Form not found"

**Full Error Message**:
```
HTTP 500 Internal Server Error
ERROR: Form not found: studentBasicInfo
```

**Cause**: Form ID in YAML doesn't match any form in Joget.

**Fix**:

1. **Check form exists in Joget**:
   - Navigate to: **Design → Form Builder**
   - Verify form appears in list
   - Note exact form ID (case-sensitive)

2. **Compare form ID with YAML**:

```yaml
formMappings:
  studentBasicInfo:
    formId: studentBasicInfo  # Must match EXACTLY
    #       ^^^^^^^^^^^^^^^^
```

3. **Common mistakes**:
   - ❌ YAML: `studentBasicInfo` vs Joget: `StudentBasicInfo` (case mismatch)
   - ❌ YAML: `student_basic_info` vs Joget: `studentBasicInfo` (underscore vs camelCase)
   - ❌ YAML: `basicInfo` vs Joget: `studentBasicInfo` (partial name)

4. **Fix**: Update YAML to match exact Joget form ID:

```yaml
formMappings:
  studentBasicInfo:
    formId: StudentBasicInfo  # Match exact Joget form ID
```

[SCREENSHOT: Form list in Joget with form IDs visible]

---

#### Error: "Database connection failed"

**Full Error Message**:
```
HTTP 500 Internal Server Error
ERROR: Could not connect to database
```

**Cause**: Joget can't connect to the database.

**Fix**:

1. **Check Joget database configuration**:

```bash
# Check datasource configuration
cat /path/to/joget/wflow/app_datasources-default.properties
```

2. **Verify database is running**:

```bash
# MySQL
mysql -h localhost -P 3307 -u root -p

# PostgreSQL
psql -h localhost -p 5432 -U postgres
```

3. **Check connection pool settings**:

Navigate to: **Settings → Database → Connection Pool**

4. **Restart Joget** if configuration changed:

```bash
cd /path/to/joget
./joget.sh stop
./joget.sh start
```

5. **Check logs for specific database errors**:

```bash
tail -f /path/to/joget/joget-tomcat9/logs/catalina.out | grep "database"
```

---

#### Error: "Grid form mapping not found"

**Full Error Message**:
```
HTTP 500 Internal Server Error
ERROR: Grid mapping not found for section: studentCourses
```

**Cause**: Section is a grid but `gridMappings` is missing or incorrect.

**Fix**:

Add grid mapping to receiver YAML:

```yaml
serviceConfig:
  sectionToFormMap:
    studentCourses: "studentCoursesForm"  # Grid section

  gridMappings:  # ADD THIS BLOCK
    studentCourses:  # Must match section name above
      formId: "studentCoursesForm"
      parentField: "parent_id"
      parentColumn: "c_parent_id"  # Optional, can omit to use default

formMappings:
  studentCourses:  # Must match section name
    type: grid  # Must be "grid" not "form"
    formId: studentCoursesForm
    fields:
      - joget: course_code
        govstack: courses[*].code  # Note: array syntax [*]
        jsonPath: $.courses[*].code
```

**Verification checklist**:
```
□ Section appears in sectionToFormMap
□ Section appears in gridMappings
□ Section appears in formMappings
□ formMappings type is "grid"
□ Grid form has hidden parent_id field
```

---

### 26.3 Configuration Errors

Configuration errors occur during plugin startup or YAML loading.

#### Error: "ConfigurationException: Missing required field"

**Full Error Message**:
```
SEVERE: ConfigurationException: Missing required field 'service.serviceConfig.parentFormId'
Failed to load service configuration: student_enrollment.yml
```

**Cause**: Required YAML field is missing.

**Fix**:

1. **Identify missing field** from error message
2. **Add to YAML configuration**:

```yaml
service:
  id: student_enrollment
  name: "Student Enrollment"
  version: "1.0"
  govstackVersion: "1.0"
  metadataVersion: "1.0.0"
  lastUpdated: "2025-10-29"

  serviceConfig:
    parentFormId: "studentRegistrationForm"  # REQUIRED
    parentReferenceFields:  # REQUIRED
      - "basic_data"
    sectionToFormMap:  # REQUIRED
      studentBasicInfo: "studentBasicInfo"
```

3. **Validate YAML**:

```bash
./tools/yaml-validator.sh student_enrollment.yml
```

**Required fields reference**: See [Part 5, Section 23.1](#231-required-fields-table)

---

#### Error: "YAML syntax error"

**Full Error Message**:
```
ERROR: YAML parsing failed at line 42
ScannerException: mapping values are not allowed here
```

**Cause**: Invalid YAML syntax (indentation, colons, quotes, etc.)

**Fix**:

1. **Check line number** mentioned in error (line 42 in example)

2. **Common YAML syntax issues**:

```yaml
# ❌ WRONG: Inconsistent indentation
fields:
  - joget: full_name
  govstack: name.text  # Should be indented 4 spaces

# ✅ CORRECT: Consistent 2-space indentation
fields:
  - joget: full_name
    govstack: name.text

# ❌ WRONG: Missing colon
fields
  - joget: full_name

# ✅ CORRECT: Colon after key
fields:
  - joget: full_name

# ❌ WRONG: Unquoted special characters
description: Student's name (required)  # Apostrophe breaks parsing

# ✅ CORRECT: Quote strings with special chars
description: "Student's name (required)"

# ❌ WRONG: Tab characters (YAML requires spaces)
fields:
→ - joget: full_name  # Tab character

# ✅ CORRECT: Use spaces only
fields:
  - joget: full_name  # 2 spaces
```

3. **Use YAML validator**:

```bash
# Validate syntax
./tools/yaml-validator.sh student_enrollment.yml

# Or use yq
yq eval student_enrollment.yml

# Or use Python
python3 -c "import yaml; yaml.safe_load(open('student_enrollment.yml'))"
```

4. **Use proper YAML editor** with syntax highlighting:
   - VS Code with YAML extension
   - IntelliJ IDEA
   - Sublime Text with YAML package

[SCREENSHOT: YAML syntax error highlighted in editor]

---

### 26.4 Mapping Errors

Mapping errors occur when fields can't be found or mapped correctly.

#### Warning: "Field not found in JSON"

**Log Message**:
```
WARN: Field 'extension.phone' not found in JSON, using default value
WARN: JSONPath '$.extension.phone' returned null
```

**Cause**: Optional field doesn't exist in incoming JSON.

**Impact**: Low - field gets default value (or null)

**Fix** (if warning is expected):

Mark field as optional with default:

```yaml
fields:
  - joget: phone
    govstack: extension.phone
    jsonPath: $.extension.phone
    required: false
    default: ""  # Use empty string if missing
    description: "Phone number (optional)"
```

**Fix** (if field should be present):

Check sender configuration includes field:

```yaml
# Sender YAML
fields:
  - joget: phone
    govstack: extension.phone  # Ensure this mapping exists
```

---

#### Error: "Transform failed"

**Log Message**:
```
WARN: Transform 'date_ISO8601_to_date' failed for field 'birth_date'
      Input value: "invalid-date"
      Storing original value
```

**Cause**: Transform function couldn't convert the value.

**Common scenarios**:

1. **Invalid date format**:
```yaml
# Input: "10/29/2024" (MM/DD/YYYY)
# Transform: date_ISO8601_to_date (expects ISO 8601)
# Result: FAIL

# Fix: Ensure sender formats dates correctly
fields:
  - joget: birth_date
    govstack: birthDate
    # Add validation in sender form to enforce YYYY-MM-DD
```

2. **Non-numeric value for numeric transform**:
```yaml
# Input: "N/A"
# Transform: numeric
# Result: FAIL

# Fix: Add default value
fields:
  - joget: age
    govstack: extension.age
    jsonPath: $.extension.age
    transform: numeric
    default: 0  # Use if transform fails
```

3. **Null value**:
```yaml
# Input: null
# Transform: any
# Result: FAIL

# Fix: Add null check with default
fields:
  - joget: field_name
    govstack: path.to.field
    jsonPath: $.path.to.field
    transform: uppercase
    default: ""  # Handle null values
```

**Best practice**: Always provide default values for fields with transforms.

---

#### Error: "Parent ID not set for grid record"

**Log Message**:
```
ERROR: Cannot save grid record: parent_id is null
       Grid form: studentCoursesForm
       Parent record should exist but ID not found
```

**Cause**: Grid record can't link to parent because parent_id is missing.

**Fix**:

1. **Verify parent form was created first**:

Check logs for parent creation:
```bash
tail -f catalina.out | grep "Created parent record"
# Should see: Created parent record in form: studentRegistrationForm, UUID: xxx
```

2. **Check grid has parent_id hidden field**:

Navigate to grid form → verify hidden field exists:
   - **Field ID**: `parent_id` (or your custom name)
   - **Type**: Hidden Field

3. **Check gridMappings configuration**:

```yaml
gridMappings:
  studentCourses:
    formId: "studentCoursesForm"
    parentField: "parent_id"  # Must match hidden field ID
    parentColumn: "c_parent_id"  # Optional
```

4. **Verify parent reference field** in parent form:

Parent form should have hidden field matching section name:
```yaml
parentReferenceFields:
  - "courses"  # Matches hidden field ID in parent form
```

[SCREENSHOT: Parent-child relationship diagram with field IDs]

---

### 26.5 Deployment Issues

Issues that occur during plugin deployment or reload.

#### Error: "Plugin not appearing in Joget"

**Symptoms**: After deploying JAR, plugin doesn't appear in Joget.

**Possible causes and fixes**:

**1. JAR not in correct location**:

```bash
# Check JAR is in plugins directory
ls -la /path/to/joget/wflow/app_plugins/processing-server*.jar

# Should show: processing-server-8.1-SNAPSHOT.jar

# If not, copy it:
cp target/processing-server-8.1-SNAPSHOT.jar /path/to/joget/wflow/app_plugins/
```

**2. Plugin not registered in Activator**:

Check `Activator.java`:

```java
public class Activator implements BundleActivator {
    public void start(BundleContext context) {
        Registration.registerPlugin(ProcessingServerPlugin.class);  // Must be present
    }
}
```

**3. OSGi bundle errors**:

Check logs for OSGi errors:

```bash
tail -f /path/to/joget/joget-tomcat9/logs/catalina.out | grep -i "osgi\|bundle"
```

Look for:
```
ERROR: Bundle exception while starting
ERROR: ClassNotFoundException
ERROR: NoClassDefFoundError
```

**4. Joget needs restart**:

```bash
cd /path/to/joget
./joget.sh stop
./joget.sh start
```

**5. Plugin cache**:

Clear Joget's plugin cache:

```bash
rm -rf /path/to/joget/wflow/app_plugins/.cache
```

Then restart Joget.

---

#### Error: "ClassNotFoundException" or "NoClassDefFoundError"

**Full Error**:
```
ERROR: java.lang.ClassNotFoundException: com.jayway.jsonpath.JsonPath
ERROR: java.lang.NoClassDefFoundError: org/json/JSONObject
```

**Cause**: Required dependency not bundled in plugin JAR.

**Fix**:

1. **Check `pom.xml`** dependencies:

```xml
<dependency>
    <groupId>com.jayway.jsonpath</groupId>
    <artifactId>json-path</artifactId>
    <version>2.7.0</version>
    <!-- Should NOT have <scope>provided</scope> -->
</dependency>
```

2. **Ensure Maven Bundle Plugin** includes dependencies:

```xml
<plugin>
    <groupId>org.apache.felix</groupId>
    <artifactId>maven-bundle-plugin</artifactId>
    <configuration>
        <instructions>
            <Embed-Dependency>*;scope=compile|runtime</Embed-Dependency>
            <Embed-Transitive>true</Embed-Transitive>
        </instructions>
    </configuration>
</plugin>
```

3. **Rebuild plugin**:

```bash
mvn clean package
```

4. **Verify JAR contains dependencies**:

```bash
jar tf target/processing-server-8.1-SNAPSHOT.jar | grep "jsonpath"
# Should show: OSGI-INF/lib/json-path-2.7.0.jar
```

---

## 27. Debugging Techniques

### 27.1 Reading Log Files Effectively

**Log file locations**:

```bash
# Sender Joget logs
/path/to/sender/joget-tomcat9/logs/catalina.out

# Receiver Joget logs
/path/to/receiver/joget-tomcat9/logs/catalina.out
```

**Useful log filtering**:

```bash
# Follow logs in real-time
tail -f catalina.out

# Show only DocSubmitter logs
tail -f catalina.out | grep "DocSubmitter"

# Show only ProcessingServer logs
tail -f catalina.out | grep "ProcessingServer"

# Show only errors
tail -f catalina.out | grep -i "error\|exception"

# Show errors with context (10 lines before/after)
tail -f catalina.out | grep -B10 -A10 -i "error"

# Show specific service logs
tail -f catalina.out | grep "student_enrollment"

# Show logs from last 100 lines
tail -100 catalina.out
```

**Log message patterns**:

```
# Successful submission (sender)
DocSubmitter: Service ID: student_enrollment
DocSubmitter: Sending JSON to: http://localhost:8080/jw/api/...
DocSubmitter: Response Code: 200
DocSubmitter: Successfully sent data

# Successful processing (receiver)
ProcessingServer: Received POST request for service: student_enrollment
ProcessingServer: Created parent record, UUID: a3a6df93-ac7e-4036-a4c9-70ffdc3cdc78
ProcessingServer: Saved to form: studentBasicInfo
ProcessingServer: Saved to form: studentAcademics
ProcessingServer: Processing complete

# Error patterns
ERROR: Form not found: studentBasicInfo
WARN: Field 'extension.phone' not found in JSON
ConfigurationException: Missing required field
```

[SCREENSHOT: Log file with color-coded severity levels]

### 27.2 Using Browser Developer Tools

**Chrome/Firefox Developer Tools** are invaluable for debugging API calls.

**How to use**:

1. **Open Developer Tools**:
   - Press `F12` or `Cmd+Option+I` (Mac) / `Ctrl+Shift+I` (Windows)

2. **Navigate to Network tab**

3. **Submit form** in sender Joget

4. **Find API call**:
   - Look for: `/jw/api/govstack/registration/services/...`
   - Click on request

5. **Inspect request**:

**Headers tab**:
```
Request URL: http://localhost:8080/jw/api/govstack/registration/services/student_enrollment/applications
Request Method: POST
Status Code: 200 OK (or error code)
```

**Payload tab** (Request body):
```json
{
  "name": {
    "text": "John Doe"
  },
  "birthDate": "2005-03-15",
  "extension": {
    "email": "john.doe@example.com"
  }
}
```

**Response tab**:
```json
{
  "success": true,
  "message": "Application submitted successfully",
  "parentId": "a3a6df93-ac7e-4036-a4c9-70ffdc3cdc78"
}
```

**Response tab** (if error):
```json
{
  "error": "Section mapping not found for section: studentBasicInfo",
  "statusCode": 400
}
```

6. **Copy request as cURL** for replay:
   - Right-click request → Copy → Copy as cURL

[SCREENSHOT: Browser Developer Tools Network tab with API call highlighted]

### 27.3 Testing with cURL Commands

**cURL** allows testing API calls independently of Joget.

**Basic test**:

```bash
curl -X POST \
  http://localhost:8080/jw/api/govstack/registration/services/student_enrollment/applications \
  -H "Content-Type: application/json" \
  -d '{
    "name": {
      "text": "John Doe"
    },
    "birthDate": "2005-03-15T00:00:00Z",
    "extension": {
      "email": "john.doe@example.com",
      "phone": "+1234567890"
    }
  }'
```

**Pretty-print response**:

```bash
curl -X POST \
  http://localhost:8080/jw/api/govstack/registration/services/student_enrollment/applications \
  -H "Content-Type: application/json" \
  -d @test-data.json | jq .
```

**Save response to file**:

```bash
curl -X POST \
  http://localhost:8080/jw/api/govstack/registration/services/student_enrollment/applications \
  -H "Content-Type: application/json" \
  -d @test-data.json \
  -o response.json
```

**Show response headers**:

```bash
curl -i -X POST \
  http://localhost:8080/jw/api/govstack/registration/services/student_enrollment/applications \
  -H "Content-Type: application/json" \
  -d @test-data.json
```

**Test with authentication** (if required):

```bash
curl -X POST \
  http://localhost:8080/jw/api/govstack/registration/services/student_enrollment/applications \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -d @test-data.json
```

**Create test data file** (`test-data.json`):

```json
{
  "name": {
    "text": "Jane Smith"
  },
  "birthDate": "2006-05-20T00:00:00Z",
  "extension": {
    "email": "jane.smith@example.com",
    "phone": "+9876543210",
    "address": {
      "street": "456 Oak Ave",
      "city": "Springfield",
      "state": "ST",
      "postalCode": "67890"
    }
  },
  "courses": [
    {
      "code": "MATH101",
      "name": "Mathematics I",
      "credits": 4
    },
    {
      "code": "ENG101",
      "name": "English I",
      "credits": 3
    }
  ]
}
```

**Run test**:

```bash
./test-curl.sh
```

### 27.4 JSON Validation Tools

**Online validators**:
- https://jsonlint.com/
- https://jsonformatter.curiousconcept.com/

**Command-line validation**:

```bash
# Using jq
cat test-data.json | jq .

# Using Python
python3 -m json.tool test-data.json

# Using Node.js
node -e "console.log(JSON.stringify(require('./test-data.json'), null, 2))"
```

**Validate against schema**:

```bash
# Install ajv-cli
npm install -g ajv-cli

# Validate JSON against JSON Schema
ajv validate -s govstack-schema.json -d test-data.json
```

### 27.5 YAML Validation Tools

**Use the provided validator**:

```bash
./tools/yaml-validator.sh student_enrollment.yml
```

**Command-line tools**:

```bash
# Using yq
yq eval student_enrollment.yml

# Using Python
python3 -c "import yaml; yaml.safe_load(open('student_enrollment.yml'))"

# Using Ruby
ruby -ryaml -e "YAML.load_file('student_enrollment.yml')"
```

**Online validators**:
- http://www.yamllint.com/
- https://yaml-online-parser.appspot.com/

---

## 28. Log Analysis

### 28.1 Log Message Patterns

**Success patterns** (what to look for):

**Sender (DocSubmitter)**:
```
DocSubmitter: Service ID: student_enrollment
DocSubmitter: Processing form: studentSubmissionForm
DocSubmitter: Mapped 15 fields to GovStack JSON
DocSubmitter: Sending JSON to: http://localhost:8080/jw/api/...
DocSubmitter: Response Code: 200
DocSubmitter: Response: {"success":true,"parentId":"uuid..."}
DocSubmitter: Successfully sent data to GovStack API
```

**Receiver (ProcessingServer)**:
```
ProcessingServer: Received POST request for service: student_enrollment
ProcessingServer: Loaded configuration: student_enrollment.yml
ProcessingServer: Created parent record in form: studentRegistrationForm
ProcessingServer: Parent UUID: a3a6df93-ac7e-4036-a4c9-70ffdc3cdc78
ProcessingServer: Processing section: studentBasicInfo
ProcessingServer: Saved to form: studentBasicInfo (5 fields)
ProcessingServer: Processing section: studentCourses (grid)
ProcessingServer: Saved grid record 1/2
ProcessingServer: Saved grid record 2/2
ProcessingServer: Processing complete, total time: 234ms
```

**Error patterns** (what indicates problems):

```
# Configuration errors (critical)
SEVERE: ConfigurationException: Missing required field 'service.serviceConfig.parentFormId'
ERROR: Failed to load service configuration: student_enrollment.yml

# Form errors (critical)
ERROR: Form not found: studentBasicInfo
ERROR: Cannot save to form: studentAcademics

# Data errors (may be acceptable)
WARN: Field 'extension.phone' not found in JSON, using default value
WARN: Transform failed for field 'age' using transform 'numeric'

# Database errors (critical)
ERROR: Database connection failed
ERROR: Cannot save grid record: parent_id is null
ERROR: Duplicate entry for key 'PRIMARY'
```

### 28.2 Error vs Warning Identification

**ERROR** - Submission failed, must be fixed:
- Form not found
- Database connection failed
- Required field missing (if field is truly required)
- ConfigurationException
- Invalid JSON structure

**WARN** - Submission succeeded, but review recommended:
- Optional field not found (using default)
- Transform failed (using original value)
- Grid section empty (no grid records to save)

**INFO** - Normal operation:
- Service ID loaded
- Parent record created
- Form data saved
- Processing complete

### 28.3 Tracing Submission Flow

Follow a single submission through logs using UUID:

```bash
# Get parent UUID from response
# Example: a3a6df93-ac7e-4036-a4c9-70ffdc3cdc78

# Search all logs for this UUID
grep "a3a6df93-ac7e-4036-a4c9-70ffdc3cdc78" catalina.out

# Expected flow:
# 1. Sender: Submitting data...
# 2. Sender: Response Code: 200
# 3. Receiver: Received POST request
# 4. Receiver: Created parent record, UUID: a3a6df93-...
# 5. Receiver: Saved to form: studentBasicInfo
# 6. Receiver: Processing complete
```

**Timeline reconstruction**:

```bash
# Show timestamps with grep
grep "a3a6df93" catalina.out | grep -E "^[0-9]{4}-[0-9]{2}-[0-9]{2}"

# Output example:
2025-10-30 10:23:15 DocSubmitter: Sending JSON...
2025-10-30 10:23:16 ProcessingServer: Received POST request
2025-10-30 10:23:16 ProcessingServer: Created parent record
2025-10-30 10:23:16 ProcessingServer: Saved to form: studentBasicInfo
2025-10-30 10:23:16 ProcessingServer: Processing complete

# Total time: 1 second
```

### 28.4 Performance Indicators

**Look for these metrics in logs**:

```
ProcessingServer: Processing complete, total time: 234ms
ProcessingServer: Database query time: 45ms
ProcessingServer: Form save time: 89ms
```

**Performance benchmarks**:
- ✅ **< 500ms**: Excellent
- ⚠️ **500-2000ms**: Acceptable
- ❌ **> 2000ms**: Needs optimization

**Identifying bottlenecks**:

```bash
# Find slow submissions
grep "total time:" catalina.out | awk -F': ' '{print $3}' | sort -n

# Find database slowness
grep "Database query time:" catalina.out | awk -F': ' '{print $3}' | sort -n
```

---

## 29. Database Verification

### 29.1 Essential Verification Queries

**Check parent record was created**:

```sql
-- Find most recent parent record
SELECT id, dateCreated, createdBy
FROM students_registry
ORDER BY dateCreated DESC
LIMIT 1;

-- Expected: One row with recent timestamp
```

**Check child record was created and linked**:

```sql
-- Find child records linked to parent
SELECT
    sr.id AS parent_id,
    sr.dateCreated AS parent_created,
    sbi.id AS child_id,
    sbi.c_full_name,
    sbi.c_email
FROM students_registry sr
LEFT JOIN student_basic_info sbi ON sbi.c_parent_id = sr.id
ORDER BY sr.dateCreated DESC
LIMIT 1;

-- Expected: Matching parent_id and c_parent_id
```

**Check all sections for a submission**:

```sql
-- Replace UUID with your parent UUID
SET @parent_uuid = 'a3a6df93-ac7e-4036-a4c9-70ffdc3cdc78';

-- Parent
SELECT 'Parent' AS record_type, id, dateCreated
FROM students_registry
WHERE id = @parent_uuid

UNION ALL

-- Basic Info
SELECT 'Basic Info', id, dateCreated
FROM student_basic_info
WHERE c_parent_id = @parent_uuid

UNION ALL

-- Academics
SELECT 'Academics', id, dateCreated
FROM student_academics
WHERE c_parent_id = @parent_uuid

UNION ALL

-- Guardian
SELECT 'Guardian', id, dateCreated
FROM student_guardian
WHERE c_parent_id = @parent_uuid

UNION ALL

-- Courses (grid)
SELECT 'Course', id, dateCreated
FROM student_courses
WHERE c_parent_id = @parent_uuid;

-- Expected: 1 parent + N child records
```

**Check grid records**:

```sql
-- Grid records for a parent
SELECT
    c_parent_id,
    c_course_code,
    c_course_name,
    c_credits
FROM student_courses
WHERE c_parent_id = 'a3a6df93-ac7e-4036-a4c9-70ffdc3cdc78'
ORDER BY dateCreated;

-- Expected: Multiple rows if grid had multiple items
```

### 29.2 Data Integrity Checks

**Verify foreign key relationships**:

```sql
-- Find orphaned child records (no parent)
SELECT
    sbi.id,
    sbi.c_full_name,
    sbi.c_parent_id
FROM student_basic_info sbi
LEFT JOIN students_registry sr ON sr.id = sbi.c_parent_id
WHERE sr.id IS NULL;

-- Expected: Empty result (no orphans)
```

**Verify all parent references are used**:

```sql
-- Check if all reference fields in parent have corresponding children
SELECT
    sr.id AS parent_id,
    sr.c_basic_data,
    sbi.id AS basic_info_exists,
    sr.c_academics_data,
    sa.id AS academics_exists,
    sr.c_courses,
    COUNT(sc.id) AS course_count
FROM students_registry sr
LEFT JOIN student_basic_info sbi ON sbi.c_parent_id = sr.id
LEFT JOIN student_academics sa ON sa.c_parent_id = sr.id
LEFT JOIN student_courses sc ON sc.c_parent_id = sr.id
GROUP BY sr.id
ORDER BY sr.dateCreated DESC
LIMIT 10;

-- Expected: All reference fields should have corresponding records
```

**Check for duplicate submissions**:

```sql
-- Find potential duplicates by email
SELECT
    c_email,
    COUNT(*) AS submission_count,
    GROUP_CONCAT(id) AS submission_ids
FROM student_basic_info
GROUP BY c_email
HAVING submission_count > 1;

-- Review results to determine if duplicates are valid or errors
```

### 29.3 Common Database Issues

**Issue: UUIDs don't match**

**Symptom**:
```sql
SELECT sr.id, sbi.c_parent_id
FROM students_registry sr
LEFT JOIN student_basic_info sbi ON sbi.c_parent_id = sr.id
WHERE sr.id = 'xxx';

-- Result: c_parent_id is NULL or different UUID
```

**Cause**: parent_id not being set correctly

**Fix**: Check gridMappings configuration and parent_id hidden field in forms

**Issue: Null values in required fields**

**Symptom**:
```sql
SELECT * FROM student_basic_info
WHERE c_full_name IS NULL OR c_email IS NULL;

-- Result: Rows with null required fields
```

**Cause**: Required field missing from JSON or mapping incorrect

**Fix**: Add required fields to sender configuration or mark as optional in receiver

**Issue: Transform not applied**

**Symptom**:
```sql
SELECT c_birth_date FROM student_basic_info;

-- Expected: 2005-03-15
-- Actual: 2005-03-15T00:00:00Z
```

**Cause**: Transform not configured or failed

**Fix**: Add transform to receiver YAML:
```yaml
- joget: birth_date
  jsonPath: $.birthDate
  transform: date_ISO8601_to_date
```

---

**End of Part 6: Troubleshooting**

You now have:
- ✅ Complete error reference with solutions
- ✅ Debugging techniques and tools
- ✅ Log analysis skills
- ✅ Database verification queries

**Next**: Continue to Part 7 (Examples and Templates) for additional learning resources.

[Continue to Part 7 →](#part-7-examples-and-templates)

---

# PART 7: EXAMPLES AND TEMPLATES

---

## 30. Minimal Contact Form

This example was created in `examples/minimal-contact-form/` and is the simplest possible service.

### 30.1 Overview

**Complexity**: Simple
**Time to implement**: 15-20 minutes
**Forms required**: 2 (1 parent + 1 child)
**Grids**: None

**Perfect for**: Learning fundamentals, testing setup, quick demos

### 30.2 What You'll Build

A contact form that captures:
- Full Name
- Email Address
- Phone Number (optional)
- Message/Inquiry

**Data Flow**:
```
User fills contact form → DocSubmitter → GovStack JSON → ProcessingServer → Joget forms
```

### 30.3 Forms Structure

**Child Form** (`contactForm`):
```
Table: contact_submissions
Fields:
  - full_name (Text Field, required)
  - email (Email Field, required)
  - phone (Text Field, optional)
  - message (Text Area, required)
  - parent_id (Hidden Field)
```

**Parent Form** (`contactRegistrationForm`):
```
Table: contacts_registry
Fields:
  - contact_data (Hidden Field) → references contactForm
```

### 30.4 Receiver Configuration

**File**: `receiver/contact_form.yml`

```yaml
service:
  id: contact_form
  name: "Contact Form Service"
  version: "1.0"
  govstackVersion: "1.0"
  metadataVersion: "1.0.0"
  lastUpdated: "2025-10-29"
  description: "Minimal example service for contact information collection"

  serviceConfig:
    parentFormId: "contactRegistrationForm"
    parentReferenceFields:
      - "contact_data"
    sectionToFormMap:
      contactInfo: "contactForm"
    gridMappings: {}

formMappings:
  contactInfo:
    type: form
    formId: contactForm
    description: "Maps contact information from GovStack JSON to Joget form"
    fields:
      - joget: full_name
        govstack: name.text
        jsonPath: $.name.text
        description: "Contact's full name"

      - joget: email
        govstack: extension.email
        jsonPath: $.extension.email
        description: "Contact's email address"

      - joget: phone
        govstack: extension.phone
        jsonPath: $.extension.phone
        description: "Contact's phone number (optional)"

      - joget: message
        govstack: extension.message
        jsonPath: $.extension.message
        description: "Contact message or inquiry"
```

### 30.5 Sender Configuration

**File**: `sender/contact_form.yml`

```yaml
service:
  id: contact_form
  name: "Contact Form Service"
  version: "1.0"
  govstackVersion: "1.0"
  metadataVersion: "1.0.0"
  lastUpdated: "2025-10-29"
  description: "Sends contact form data from Joget to receiver"

formMappings:
  contactInfo:
    type: form
    formId: contactSubmissionForm
    description: "Maps contact form fields to GovStack JSON structure"
    fields:
      - joget: full_name
        govstack: name.text

      - joget: email
        govstack: extension.email

      - joget: phone
        govstack: extension.phone

      - joget: message
        govstack: extension.message
```

### 30.6 Key Learning Points

This minimal example teaches:

1. **Parent-Child Relationship**: How parent form holds references
2. **Section Mapping**: How sections map to forms
3. **Field Mapping**: How Joget fields map to JSON paths
4. **Basic YAML Structure**: Minimal required configuration
5. **End-to-End Flow**: Complete data submission workflow

### 30.7 Testing

**Test JSON payload**:

```json
{
  "name": {
    "text": "John Doe"
  },
  "extension": {
    "email": "john.doe@example.com",
    "phone": "+1234567890",
    "message": "This is a test message"
  }
}
```

**cURL test command**:

```bash
curl -X POST \
  http://localhost:8080/jw/api/govstack/registration/services/contact_form/applications \
  -H "Content-Type: application/json" \
  -d '{
    "name": {"text": "John Doe"},
    "extension": {
      "email": "john.doe@example.com",
      "phone": "+1234567890",
      "message": "This is a test message"
    }
  }'
```

**Expected response**:

```json
{
  "success": true,
  "message": "Application submitted successfully",
  "parentId": "uuid-here"
}
```

**Full implementation guide**: See `examples/minimal-contact-form/README.md`

---

## 31. Service Templates

### 31.1 Simple Service Template (No Grids)

Use this template for services with a single entity and no one-to-many relationships.

**File**: `templates/simple-service-template.yml`

```yaml
# Simple Service Template
# Use for: Basic forms, contact forms, simple registrations
# Complexity: Low
# Forms needed: 1 parent + 1-3 child forms

service:
  id: YOUR_SERVICE_ID_HERE          # Change this
  name: "Your Service Name"         # Change this
  version: "1.0"
  govstackVersion: "1.0"
  metadataVersion: "1.0.0"
  lastUpdated: "YYYY-MM-DD"         # Today's date
  description: "Service description"

  serviceConfig:
    parentFormId: "yourParentForm"  # Change this

    parentReferenceFields:          # Change this
      - "section1_data"
      - "section2_data"

    sectionToFormMap:               # Change this
      section1: "yourSection1Form"
      section2: "yourSection2Form"

    gridMappings: {}                # No grids

formMappings:
  section1:                         # Change this
    type: form
    formId: yourSection1Form        # Change this
    description: "Section 1 description"
    fields:
      - joget: field1               # Change these
        govstack: path.to.field1
        jsonPath: $.path.to.field1
        description: "Field 1 description"

      - joget: field2
        govstack: path.to.field2
        jsonPath: $.path.to.field2
        description: "Field 2 description"

  section2:                         # Repeat for each section
    type: form
    formId: yourSection2Form
    description: "Section 2 description"
    fields:
      - joget: field1
        govstack: path.to.field1
        jsonPath: $.path.to.field1
```

**Usage**:
1. Copy template to `receiver/your_service.yml`
2. Replace all `YOUR_SERVICE_ID_HERE` placeholders
3. Update form IDs to match your Joget forms
4. Add field mappings for your specific fields
5. Validate: `./tools/yaml-validator.sh your_service.yml`

---

### 31.2 Medium Service Template (With Grids)

Use this template for services with one-to-many relationships (grids).

**File**: `templates/medium-service-template.yml`

```yaml
# Medium Service Template
# Use for: Services with grids (courses, addresses, family members)
# Complexity: Medium
# Forms needed: 1 parent + 2-4 child forms + 1-2 grid forms

service:
  id: YOUR_SERVICE_ID_HERE
  name: "Your Service Name"
  version: "1.0"
  govstackVersion: "1.0"
  metadataVersion: "1.0.0"
  lastUpdated: "YYYY-MM-DD"
  description: "Service with grid example"

  serviceConfig:
    parentFormId: "yourParentForm"

    parentReferenceFields:
      - "basic_data"
      - "additional_data"
      - "grid_data"                 # Grid reference

    sectionToFormMap:
      basicInfo: "yourBasicForm"
      additionalInfo: "yourAdditionalForm"
      gridSection: "yourGridForm"    # Grid section

    gridMappings:                    # Grid configuration
      gridSection:                   # Must match section name
        formId: "yourGridForm"
        parentField: "parent_id"
        parentColumn: "c_parent_id"

    defaults:
      gridParentField: "parent_id"
      gridParentColumn: "c_parent_id"

formMappings:
  basicInfo:
    type: form
    formId: yourBasicForm
    fields:
      - joget: field1
        govstack: path.to.field1
        jsonPath: $.path.to.field1

  additionalInfo:
    type: form
    formId: yourAdditionalForm
    fields:
      - joget: field1
        govstack: path.to.field1
        jsonPath: $.path.to.field1

  gridSection:
    type: grid                       # Type is "grid"
    formId: yourGridForm
    description: "Grid items"
    fields:
      - joget: item_code
        govstack: items[*].code      # Array notation
        jsonPath: $.items[*].code
        description: "Item code"

      - joget: item_name
        govstack: items[*].name
        jsonPath: $.items[*].name
        description: "Item name"

      - joget: item_quantity
        govstack: items[*].quantity
        jsonPath: $.items[*].quantity
        transform: numeric           # Optional transform
        description: "Quantity (numeric)"
```

**Grid form requirements**:
- Must have hidden field: `parent_id` (matches `parentField`)
- Database column: `c_parent_id` (matches `parentColumn`)
- All grid items will have same parent UUID

---

### 31.3 Complex Service Template (Multiple Grids)

Use this template for complex services with multiple grids and transformations.

**File**: `templates/complex-service-template.yml`

```yaml
# Complex Service Template
# Use for: Complex registrations with multiple entities and grids
# Complexity: High
# Forms needed: 1 parent + 4+ child forms + 2+ grid forms

service:
  id: YOUR_SERVICE_ID_HERE
  name: "Your Complex Service"
  version: "1.0"
  govstackVersion: "1.0"
  metadataVersion: "1.0.0"
  lastUpdated: "YYYY-MM-DD"
  description: "Complex service with multiple grids and transforms"

  serviceConfig:
    parentFormId: "yourParentForm"

    parentReferenceFields:
      - "basic_data"
      - "personal_data"
      - "contact_data"
      - "grid1_data"
      - "grid2_data"

    sectionToFormMap:
      basicInfo: "yourBasicForm"
      personalInfo: "yourPersonalForm"
      contactInfo: "yourContactForm"
      grid1Section: "yourGrid1Form"
      grid2Section: "yourGrid2Form"

    gridMappings:
      grid1Section:
        formId: "yourGrid1Form"
        parentField: "parent_id"
      grid2Section:
        formId: "yourGrid2Form"
        parentField: "parent_id"

formMappings:
  basicInfo:
    type: form
    formId: yourBasicForm
    fields:
      - joget: full_name
        govstack: name.text
        jsonPath: $.name.text
        required: true

      - joget: birth_date
        govstack: birthDate
        jsonPath: $.birthDate
        transform: date_ISO8601_to_date
        required: true

  personalInfo:
    type: form
    formId: yourPersonalForm
    fields:
      - joget: gender
        govstack: extension.gender
        jsonPath: $.extension.gender
        transform: uppercase
        default: "U"

      - joget: nationality
        govstack: extension.nationality
        jsonPath: $.extension.nationality
        transform: uppercase

  contactInfo:
    type: form
    formId: yourContactForm
    fields:
      - joget: email
        govstack: extension.contact.email
        jsonPath: $.extension.contact.email
        required: true

      - joget: phone
        govstack: extension.contact.phone
        jsonPath: $.extension.contact.phone
        default: ""

      - joget: street
        govstack: extension.address.street
        jsonPath: $.extension.address.street

      - joget: city
        govstack: extension.address.city
        jsonPath: $.extension.address.city

      - joget: postal_code
        govstack: extension.address.postalCode
        jsonPath: $.extension.address.postalCode

  grid1Section:
    type: grid
    formId: yourGrid1Form
    description: "First grid items"
    fields:
      - joget: item_code
        govstack: grid1Items[*].code
        jsonPath: $.grid1Items[*].code
        transform: uppercase

      - joget: item_name
        govstack: grid1Items[*].name
        jsonPath: $.grid1Items[*].name

      - joget: item_date
        govstack: grid1Items[*].date
        jsonPath: $.grid1Items[*].date
        transform: date_ISO8601_to_date

  grid2Section:
    type: grid
    formId: yourGrid2Form
    description: "Second grid items"
    fields:
      - joget: item_type
        govstack: grid2Items[*].type
        jsonPath: $.grid2Items[*].type

      - joget: item_value
        govstack: grid2Items[*].value
        jsonPath: $.grid2Items[*].value
        transform: numeric

      - joget: item_active
        govstack: grid2Items[*].active
        jsonPath: $.grid2Items[*].active
        transform: boolean
```

---

## 32. Common Service Patterns

### 32.1 Person Registration Pattern

**Used for**: Student enrollment, employee registration, citizen registration

**Common sections**:
- Basic Info: Name, birth date, ID number
- Contact Info: Email, phone, address
- Personal Details: Gender, nationality, marital status
- Emergency Contact: Name, phone, relationship

**Template snippet**:

```yaml
formMappings:
  basicInfo:
    type: form
    formId: personBasicInfo
    fields:
      - joget: full_name
        govstack: name.text
        jsonPath: $.name.text

      - joget: birth_date
        govstack: birthDate
        jsonPath: $.birthDate
        transform: date_ISO8601_to_date

      - joget: id_number
        govstack: extension.idNumber
        jsonPath: $.extension.idNumber

  contactInfo:
    type: form
    formId: personContactInfo
    fields:
      - joget: email
        govstack: extension.contact.email
        jsonPath: $.extension.contact.email

      - joget: phone
        govstack: extension.contact.phone
        jsonPath: $.extension.contact.phone

      - joget: street
        govstack: extension.address.street
        jsonPath: $.extension.address.street

      - joget: city
        govstack: extension.address.city
        jsonPath: $.extension.address.city

      - joget: state
        govstack: extension.address.state
        jsonPath: $.extension.address.state
        transform: uppercase

      - joget: postal_code
        govstack: extension.address.postalCode
        jsonPath: $.extension.address.postalCode
```

### 32.2 Business Registration Pattern

**Used for**: Company registration, business license, vendor registration

**Common sections**:
- Business Info: Name, type, registration number
- Address: Business location
- Owner/Representative: Contact person details
- Documents: Grid of uploaded documents

**Template snippet**:

```yaml
formMappings:
  businessInfo:
    type: form
    formId: businessBasicInfo
    fields:
      - joget: business_name
        govstack: name.text
        jsonPath: $.name.text

      - joget: business_type
        govstack: extension.businessType
        jsonPath: $.extension.businessType
        transform: uppercase

      - joget: registration_number
        govstack: extension.registrationNumber
        jsonPath: $.extension.registrationNumber

      - joget: tax_id
        govstack: extension.taxId
        jsonPath: $.extension.taxId

  ownerInfo:
    type: form
    formId: businessOwnerInfo
    fields:
      - joget: owner_name
        govstack: extension.owner.name
        jsonPath: $.extension.owner.name

      - joget: owner_email
        govstack: extension.owner.email
        jsonPath: $.extension.owner.email

      - joget: owner_phone
        govstack: extension.owner.phone
        jsonPath: $.extension.owner.phone

  documents:
    type: grid
    formId: businessDocuments
    fields:
      - joget: document_type
        govstack: documents[*].type
        jsonPath: $.documents[*].type

      - joget: document_number
        govstack: documents[*].number
        jsonPath: $.documents[*].number

      - joget: document_file
        govstack: documents[*].fileUrl
        jsonPath: $.documents[*].fileUrl
```

### 32.3 Application/Request Pattern

**Used for**: Permit applications, service requests, claims

**Common sections**:
- Applicant Info: Who is applying
- Request Details: What is being requested
- Supporting Documents: Evidence/attachments
- Declaration: Terms acceptance

**Template snippet**:

```yaml
formMappings:
  applicantInfo:
    type: form
    formId: applicantInfo
    fields:
      - joget: applicant_name
        govstack: name.text
        jsonPath: $.name.text

      - joget: applicant_email
        govstack: extension.email
        jsonPath: $.extension.email

      - joget: applicant_phone
        govstack: extension.phone
        jsonPath: $.extension.phone

  requestDetails:
    type: form
    formId: requestDetails
    fields:
      - joget: request_type
        govstack: extension.requestType
        jsonPath: $.extension.requestType

      - joget: request_purpose
        govstack: extension.purpose
        jsonPath: $.extension.purpose

      - joget: requested_date
        govstack: extension.requestedDate
        jsonPath: $.extension.requestedDate
        transform: date_ISO8601_to_date

  declaration:
    type: form
    formId: declaration
    fields:
      - joget: terms_accepted
        govstack: extension.termsAccepted
        jsonPath: $.extension.termsAccepted
        transform: boolean

      - joget: declaration_date
        govstack: extension.declarationDate
        jsonPath: $.extension.declarationDate
        transform: date_ISO8601_to_date
```

---

## 33. Quick Start Workflows

### 33.1 Creating Your First Service (30 minutes)

**Step-by-step checklist**:

```
Phase 1: Planning (5 minutes)
□ Identify service name and ID
□ List all data sections needed
□ Identify any grids (one-to-many relationships)
□ Sketch form structure on paper

Phase 2: Receiver Forms (10 minutes)
□ Create parent form with hidden reference fields
□ Create child form(s) for each section
□ Create grid form(s) if needed
□ Add parent_id hidden field to all child/grid forms
□ Save and publish all forms

Phase 3: Receiver YAML (5 minutes)
□ Copy simple or medium template
□ Update service.id and service.name
□ Update parentFormId
□ Update parentReferenceFields list
□ Update sectionToFormMap
□ Add gridMappings if needed
□ Add field mappings for each section
□ Validate YAML: ./tools/yaml-validator.sh

Phase 4: Deploy Receiver (3 minutes)
□ Copy YAML to docs-metadata/
□ Build: mvn clean package
□ Deploy JAR to receiver Joget
□ Restart or reload plugins

Phase 5: Sender Form & YAML (5 minutes)
□ Create sender form in sender Joget
□ Copy sender template
□ Update formId to match sender form
□ Add field mappings (same fields as receiver)
□ Validate YAML

Phase 6: Sender Process (2 minutes)
□ Create process with form activity
□ Add DocSubmitter tool
□ Configure service ID and API URL
□ Create and publish app

Phase 7: Test (5 minutes minimum)
□ Submit test data through sender
□ Check HTTP response (should be 200)
□ Check receiver logs
□ Query database to verify records
□ Celebrate success! 🎉
```

### 33.2 Adding a New Field (5 minutes)

**To add a field to existing service**:

```
Receiver Side:
1. Add field to Joget form
2. Add mapping to receiver YAML:
   - joget: new_field
     govstack: extension.newField
     jsonPath: $.extension.newField
3. Rebuild and redeploy receiver plugin

Sender Side:
1. Add field to sender form
2. Add mapping to sender YAML:
   - joget: new_field
     govstack: extension.newField
3. Rebuild and redeploy sender plugin

Test:
4. Submit form with new field
5. Verify new field appears in database
```

### 33.3 Adding a New Grid (15 minutes)

**To add a grid to existing service**:

```
Receiver Side:
1. Create grid form with fields + parent_id hidden field
2. Add hidden reference field to parent form
3. Update receiver YAML:
   a. Add to parentReferenceFields: "grid_data"
   b. Add to sectionToFormMap: gridSection: "gridForm"
   c. Add to gridMappings:
      gridSection:
        formId: "gridForm"
        parentField: "parent_id"
   d. Add to formMappings:
      gridSection:
        type: grid
        formId: gridForm
        fields: [...]
4. Rebuild and redeploy

Sender Side:
1. Modify sender form to collect grid data
2. Update sender YAML with grid mappings
3. Rebuild and redeploy

Test:
4. Submit with multiple grid items
5. Verify all grid records created with correct parent_id
```

---

## 34. Template Files Location

All templates are available in the `templates/` directory:

```
templates/
├── simple-service-template.yml      # No grids
├── medium-service-template.yml      # With 1-2 grids
├── complex-service-template.yml     # Multiple grids + transforms
├── person-registration-template.yml # Person registration pattern
├── business-registration-template.yml # Business pattern
└── application-request-template.yml # Application/request pattern
```

**Usage**:
1. Copy appropriate template
2. Rename to your service ID
3. Replace all placeholder values
4. Validate with yaml-validator.sh
5. Deploy and test

---

**End of Part 7: Examples and Templates**

You now have:
- ✅ Complete minimal example walkthrough
- ✅ Service templates for different complexity levels
- ✅ Common service patterns (person, business, application)
- ✅ Quick start workflows for common tasks

**Next**: Continue to Appendices for tool documentation and quick reference cards.

[Continue to Appendices →](#appendices)

---

# APPENDICES

---

## Appendix A: YAML Validator Tool

### A.1 Overview

The YAML validator tool (`tools/yaml-validator.sh`) validates service YAML configurations for syntax errors and missing required fields.

**Location**: `/processing-server/tools/yaml-validator.sh`

**Purpose**: Catch configuration errors before deployment

### A.2 Usage

**Basic usage**:

```bash
./tools/yaml-validator.sh student_enrollment.yml
```

**With full path**:

```bash
./tools/yaml-validator.sh /path/to/receiver/student_enrollment.yml
```

**Validate multiple files**:

```bash
for file in receiver/*.yml; do
  ./tools/yaml-validator.sh "$file"
done
```

### A.3 Validation Checks

The validator performs these checks:

**1. YAML Syntax Validation**
- Checks for valid YAML structure
- Detects indentation errors
- Identifies missing colons, brackets, quotes
- Reports line numbers for syntax errors

**2. Required Fields Verification**
```yaml
# These fields MUST be present:
service.id
service.name
service.version
service.govstackVersion
service.metadataVersion
service.lastUpdated
service.serviceConfig.parentFormId
service.serviceConfig.parentReferenceFields
service.serviceConfig.sectionToFormMap
formMappings
```

**3. Naming Convention Checks**
- Service ID must be lowercase with underscores
- No spaces, hyphens, or special characters
- Date format must be YYYY-MM-DD

**4. Field Mapping Validation**
- Checks that all fields have `joget` and `govstack` properties
- For receiver configs, checks for `jsonPath` property
- Warns about missing optional fields

**5. Section Consistency**
- Verifies sections in `sectionToFormMap` match `formMappings` keys
- Checks grid sections have corresponding `gridMappings`

### A.4 Output Interpretation

**Color-coded output**:

- **GREEN** (✅): Validation passed
- **YELLOW** (⚠️): Warnings (review recommended)
- **RED** (❌): Errors (must fix before deployment)
- **BLUE** (ℹ️): Informational messages

**Example successful output**:

```
Validating: student_enrollment.yml

✅ YAML syntax valid
✅ Required field 'service.id' present
✅ Required field 'service.serviceConfig.parentFormId' present
✅ Service ID format correct: student_enrollment
✅ Date format correct: 2025-10-29
✅ Section mapping consistent
✅ Field mappings complete

VALIDATION PASSED ✅
Ready to deploy
```

**Example with warnings**:

```
Validating: student_enrollment.yml

✅ YAML syntax valid
✅ Required fields present
⚠️ Optional field 'description' not provided
⚠️ Field 'phone' missing default value
✅ Section mapping consistent

VALIDATION PASSED WITH WARNINGS ⚠️
Review warnings above
```

**Example with errors**:

```
Validating: student_enrollment.yml

❌ YAML syntax error at line 42
    Error: mapping values are not allowed here
❌ Required field 'service.serviceConfig.parentFormId' missing
❌ Service ID format invalid: 'Student-Enrollment' (use lowercase with underscores)
⚠️ Section 'courses' in sectionToFormMap but missing from formMappings

VALIDATION FAILED ❌
Fix errors above before deployment
```

### A.5 Exit Codes

The validator returns standard exit codes:

- **0**: Validation passed (no errors)
- **1**: Validation failed (errors found)
- **2**: Validation passed with warnings

**Use in scripts**:

```bash
#!/bin/bash

if ./tools/yaml-validator.sh student_enrollment.yml; then
  echo "Validation passed, proceeding with deployment..."
  mvn clean package
else
  echo "Validation failed, aborting deployment"
  exit 1
fi
```

### A.6 CI/CD Integration

**GitHub Actions example**:

```yaml
name: Validate YAML Configurations

on: [push, pull_request]

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Validate receiver YAMLs
        run: |
          for file in receiver/*.yml; do
            ./tools/yaml-validator.sh "$file" || exit 1
          done
      - name: Validate sender YAMLs
        run: |
          for file in sender/*.yml; do
            ./tools/yaml-validator.sh "$file" || exit 1
          done
```

**GitLab CI example**:

```yaml
validate-yaml:
  stage: test
  script:
    - chmod +x tools/yaml-validator.sh
    - for file in receiver/*.yml; do ./tools/yaml-validator.sh "$file" || exit 1; done
    - for file in sender/*.yml; do ./tools/yaml-validator.sh "$file" || exit 1; done
  only:
    - merge_requests
    - main
```

### A.7 Dependencies

**Required tools** (validator will check for these):

- `yq` (recommended) or Python with PyYAML
- `bash` 4.0+
- Standard Unix tools: `grep`, `awk`, `sed`

**Install yq**:

```bash
# macOS
brew install yq

# Linux (snap)
sudo snap install yq

# Linux (binary)
wget https://github.com/mikefarah/yq/releases/latest/download/yq_linux_amd64
chmod +x yq_linux_amd64
sudo mv yq_linux_amd64 /usr/local/bin/yq
```

**Install Python PyYAML** (fallback):

```bash
pip install pyyaml
```

---

## Appendix B: Quick Reference Card

### B.1 Common Commands

**Build & Deploy**:

```bash
# Build receiver plugin
cd processing-server
mvn clean package

# Build sender plugin
cd doc-submitter
mvn clean package

# Deploy to Joget
cp target/*.jar /path/to/joget/wflow/app_plugins/

# Restart Joget
cd /path/to/joget
./joget.sh stop && ./joget.sh start
```

**Validation**:

```bash
# Validate YAML
./tools/yaml-validator.sh service.yml

# Validate JSON
cat test-data.json | jq .

# Test API endpoint
curl -X POST http://localhost:8080/jw/api/govstack/registration/services/SERVICE_ID/applications \
  -H "Content-Type: application/json" \
  -d @test-data.json
```

**Logs**:

```bash
# Follow logs
tail -f /path/to/joget/joget-tomcat9/logs/catalina.out

# Filter by plugin
tail -f catalina.out | grep "ProcessingServer"
tail -f catalina.out | grep "DocSubmitter"

# Filter by service
tail -f catalina.out | grep "student_enrollment"

# Show only errors
tail -f catalina.out | grep -i "error\|exception"
```

**Database**:

```bash
# Connect to MySQL
mysql -h localhost -P 3307 -u root -p database_name

# Connect to PostgreSQL
psql -h localhost -p 5432 -U postgres -d database_name

# Check recent submissions
SELECT * FROM students_registry ORDER BY dateCreated DESC LIMIT 10;

# Check child records for parent
SELECT * FROM student_basic_info WHERE c_parent_id = 'UUID_HERE';
```

### B.2 YAML Structure Cheat Sheet

**Minimal receiver YAML**:

```yaml
service:
  id: SERVICE_ID
  name: "Service Name"
  version: "1.0"
  govstackVersion: "1.0"
  metadataVersion: "1.0.0"
  lastUpdated: "YYYY-MM-DD"

  serviceConfig:
    parentFormId: "parentForm"
    parentReferenceFields: ["section1_data"]
    sectionToFormMap:
      section1: "section1Form"
    gridMappings: {}

formMappings:
  section1:
    type: form
    formId: section1Form
    fields:
      - joget: field1
        govstack: path.to.field1
        jsonPath: $.path.to.field1
```

**Grid mapping**:

```yaml
serviceConfig:
  gridMappings:
    gridSection:
      formId: "gridForm"
      parentField: "parent_id"

formMappings:
  gridSection:
    type: grid
    formId: gridForm
    fields:
      - joget: item_code
        govstack: items[*].code
        jsonPath: $.items[*].code
```

### B.3 JSONPath Quick Reference

| Pattern | Example | Description |
|---------|---------|-------------|
| `$` | `$` | Root object |
| `$.field` | `$.name` | Top-level field |
| `$.nested.field` | `$.name.text` | Nested field |
| `$.array[0]` | `$.courses[0]` | First array element |
| `$.array[*]` | `$.courses[*]` | All array elements |
| `$..field` | `$..email` | Recursive search |

### B.4 Transform Functions Quick Reference

| Transform | Input | Output | Use For |
|-----------|-------|--------|---------|
| `date_ISO8601_to_date` | `"2024-10-29T10:30:00Z"` | `"2024-10-29"` | ISO dates to Joget |
| `uppercase` | `"hello"` | `"HELLO"` | Force uppercase |
| `lowercase` | `"HELLO"` | `"hello"` | Force lowercase |
| `numeric` | `"123"` | `123` | String to number |
| `boolean` | `"true"` | `true` | String to boolean |
| `trim` | `"  text  "` | `"text"` | Remove whitespace |

### B.5 Common Error Solutions

| Error | Quick Fix |
|-------|-----------|
| "Section mapping not found" | Match section names in `sectionToFormMap` and `formMappings` |
| "Form not found" | Verify form ID matches Joget form exactly (case-sensitive) |
| "parentFormId not configured" | Add `parentFormId: "formName"` to `serviceConfig` |
| "Grid mapping not found" | Add section to `gridMappings` with `formId` and `parentField` |
| "Required field missing" | Add field to sender or mark as optional in receiver |
| "Transform failed" | Add `default` value for field or fix input data format |
| "Parent ID not set" | Check grid form has `parent_id` hidden field |

### B.6 Form Checklist

**Child Form Requirements**:
- ✅ Form ID matches YAML exactly
- ✅ Has `parent_id` hidden field
- ✅ All mapped fields present
- ✅ Form published

**Grid Form Requirements**:
- ✅ Form ID matches YAML exactly
- ✅ Has `parent_id` hidden field (default name)
- ✅ All mapped fields present
- ✅ Form published
- ✅ Section in `gridMappings` with `formId` and `parentField`

**Parent Form Requirements**:
- ✅ Form ID matches `parentFormId` in YAML
- ✅ Has hidden reference fields matching `parentReferenceFields`
- ✅ Reference field IDs match section names in sender
- ✅ Form published

### B.7 Testing Checklist

**Before Testing**:
- □ All forms created and published
- □ YAML validated successfully
- □ Plugins built without errors
- □ Plugins deployed to correct directories
- □ Joget restarted or plugins reloaded

**During Testing**:
- □ Submit test data through sender
- □ HTTP response is 200 OK
- □ Response contains parent UUID
- □ Sender logs show successful submission
- □ Receiver logs show successful processing

**After Testing**:
- □ Parent record created in registry table
- □ All child records created
- □ Grid records created (if applicable)
- □ All parent_id values match parent UUID
- □ All data fields populated correctly
- □ Transforms applied correctly

### B.8 Performance Tuning

**Database Indexes** (add to key fields):

```sql
-- Parent UUID index (for joins)
CREATE INDEX idx_parent_id ON child_table(c_parent_id);

-- Common query fields
CREATE INDEX idx_email ON contact_info(c_email);
CREATE INDEX idx_date ON submissions(dateCreated);
```

**Connection Pool Settings** (Joget Settings → Database):

- **Min Connections**: 5
- **Max Connections**: 20 (for low traffic) or 50 (for high traffic)
- **Max Idle**: 10
- **Test on Borrow**: Yes

**JVM Settings** (for high-volume processing):

```bash
# In Joget's setenv.sh or joget.sh
export JAVA_OPTS="-Xms2048m -Xmx4096m -XX:MaxPermSize=512m"
```

---

## Appendix C: Glossary

**Child Form**: A Joget form that contains actual data fields and links to a parent form via `parent_id`.

**Doc-Submitter**: Sender plugin that converts Joget form data to GovStack JSON and submits to receiver API.

**Form Mapping**: Configuration that maps Joget field IDs to GovStack JSON paths.

**GovStack JSON**: Standardized JSON format following GovStack specifications.

**Grid Form**: A Joget form used for one-to-many relationships (arrays in JSON).

**Grid Mapping**: Configuration that defines how array items in JSON map to grid form records.

**JSONPath**: Expression language for navigating JSON structures (e.g., `$.name.text`).

**Parent Form**: A Joget form that holds UUID references to child forms (all fields hidden).

**Parent UUID**: Unique identifier generated for each submission, linking all related records.

**Processing-Server**: Receiver plugin that receives GovStack JSON and saves to multiple Joget forms.

**Section**: Logical grouping of fields in YAML configuration, maps to a single form.

**Section Mapping** (`sectionToFormMap`): Configuration mapping section names to Joget form IDs.

**Service ID**: Unique identifier for a service (lowercase with underscores, e.g., `student_enrollment`).

**Transform**: Function that converts data from one format to another (e.g., date formatting).

**YAML Configuration**: Service definition file specifying all field mappings and form relationships.

---

## Appendix D: Additional Resources

### D.1 Documentation

- **GovStack Specifications**: https://govstack.gitbook.io/specification/
- **Joget Documentation**: https://dev.joget.org/
- **JSONPath Specification**: https://goessner.net/articles/JsonPath/
- **YAML Specification**: https://yaml.org/spec/

### D.2 Tools

- **Joget DX**: https://www.joget.org/
- **yq (YAML processor)**: https://github.com/mikefarah/yq
- **jq (JSON processor)**: https://stedolan.github.io/jq/
- **Postman** (API testing): https://www.postman.com/
- **VS Code** (code editor): https://code.visualstudio.com/

### D.3 Useful VS Code Extensions

- **YAML** (Red Hat): YAML language support with validation
- **JSON Tools**: Format, minify, and validate JSON
- **REST Client**: Test HTTP requests within VS Code
- **GitLens**: Enhanced Git integration
- **Path Intellisense**: Autocomplete filenames

### D.4 Community & Support

- **GovStack Community**: https://www.govstack.global/community/
- **Joget Community**: https://community.joget.org/
- **Issue Tracker**: [Your repository URL]

---

**End of Appendices**

---

# CONCLUSION

Congratulations! You have completed the GovStack Multi-Service Plugin System Creation Guide.

## What You've Learned

This guide has covered:

✅ **Parts 1-3: Fundamentals & Tutorial** (115 pages)
- Quick start with minimal example
- Service planning and design principles
- Complete step-by-step student enrollment implementation

✅ **Part 4: Advanced Topics** (30 pages)
- Complex data structure handling
- Data transformation techniques
- Error handling strategies
- Performance optimization

✅ **Part 5: YAML Reference** (20 pages)
- Complete schema documentation
- Required vs optional fields
- Field mapping syntax
- Transform function catalog

✅ **Part 6: Troubleshooting** (25 pages)
- Common error solutions
- Debugging techniques
- Log analysis
- Database verification

✅ **Part 7: Examples & Templates** (15 pages)
- Minimal contact form example
- Service templates (simple, medium, complex)
- Common patterns (person, business, application)
- Quick start workflows

✅ **Appendices** (10 pages)
- YAML validator documentation
- Quick reference cards
- Glossary and resources

**Total**: ~215 pages of comprehensive documentation

## You Can Now

- ✅ Design and plan new services from scratch
- ✅ Create simple services in 30 minutes
- ✅ Implement complex services with grids and transformations
- ✅ Troubleshoot and debug issues independently
- ✅ Optimize service performance
- ✅ Scale to multiple services without code changes

## Next Steps

1. **Start Simple**: Implement the minimal contact form example
2. **Practice**: Create a simple service for your use case
3. **Experiment**: Try adding grids and transforms
4. **Scale**: Deploy multiple services
5. **Contribute**: Share your experiences and improvements

## Important Reminders

- **Always validate** YAML before deployment: `./tools/yaml-validator.sh`
- **Test thoroughly** with real data before production
- **Monitor logs** during initial deployments
- **Backup databases** before major changes
- **Document custom services** for team knowledge sharing

## Success Metrics

The system is working correctly when:
- ✅ HTTP 200 responses from API
- ✅ Parent and child records created with matching UUIDs
- ✅ All data fields populated correctly
- ✅ Transforms applied as expected
- ✅ No errors in logs

## Support

If you encounter issues:

1. **Check Part 6** (Troubleshooting) for common errors
2. **Use yaml-validator.sh** to catch configuration errors
3. **Review logs** carefully for specific error messages
4. **Verify database** records to understand data flow
5. **Consult examples** in `examples/` directory
6. **Ask the community** or file issues

---

## Document Information

**Version**: 1.0 (Parts 1-7 Complete)
**Last Updated**: 2025-10-30
**Total Pages**: ~215
**Status**: ✅ **Complete and Functional**

**Location**: `/processing-server/SERVICE_CREATION_GUIDE.md`
**Tools**: `/processing-server/tools/`
**Examples**: `/processing-server/examples/`
**Templates**: `/processing-server/templates/`

**Maintained By**: GovStack Registration Building Block Team
**Contributors Welcome**: Yes

---

**Happy Building! 🎉**

You now have everything you need to create scalable, configuration-driven services for the GovStack ecosystem.

