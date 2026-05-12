# Adding a Second Service Type - Step-by-Step Guide

This guide demonstrates how to add a new service type (e.g., **Student Enrollment**) to the now-generic GovStack Registration Building Block plugins **without modifying any Java code**.

**Completion Status**: Ready to implement
**Estimated Time**: 2-4 hours for a simple service
**Required Skills**: YAML configuration, Joget form design

---

## 🎯 Goal

Add a **Student Enrollment** service that runs alongside the existing Farmers Registry service, demonstrating that the plugins are truly generic and service-agnostic.

---

## 📋 Prerequisites

Before starting, ensure:
- ✅ All generalization fixes are complete (see `GENERALIZATION_SUMMARY.md`)
- ✅ Existing farmer registry service is working
- ✅ You have access to both sender and receiver Joget instances
- ✅ You understand basic YAML syntax
- ✅ You can create Joget forms

---

## 🏗️ Architecture Overview

```
Student Enrollment Service Structure:

Sender Side (localhost:9999):
├── DocSubmitter Plugin
├── students_registry.yml (NEW - sender config)
└── Student enrollment form

Receiver Side (localhost:8080):
├── Processing-Server Plugin
├── students_registry.yml (NEW - receiver config)
└── Student data forms:
    ├── studentRegistrationForm (parent form)
    ├── studentBasicInfo
    ├── studentAcademics
    ├── studentGuardian
    └── studentCourses (grid)
```

---

## 📝 Step-by-Step Implementation

### Step 1: Design Student Data Model

Plan your student enrollment data structure:

```yaml
Student Registration:
  - Basic Information
    * Full name
    * Date of birth
    * Gender
    * Student ID
    * Email
    * Phone

  - Academic Information
    * Previous school
    * Grade level
    * Program/major
    * Academic year

  - Guardian Information
    * Guardian name
    * Relationship
    * Guardian contact
    * Guardian address

  - Course Selection (Grid)
    * Course code
    * Course name
    * Credits
```

---

### Step 2: Create Joget Forms (Receiver Side)

#### 2.1 Create Parent Form
**Form ID**: `studentRegistrationForm`

**Table Name**: `students_registry`

**Fields**:
```
- id (primary key, auto-generated)
- basic_data (subform reference)
- academics_data (subform reference)
- guardian_data (subform reference)
- courses (subform reference)
- dateCreated
- dateModified
- createdBy
- modifiedBy
```

#### 2.2 Create Section Forms

**Form: studentBasicInfo**
- Form ID: `studentBasicInfo`
- Table: `app_fd_student_basic_info`
- Fields: full_name, dob, gender, student_id, email, phone, parent_id

**Form: studentAcademics**
- Form ID: `studentAcademics`
- Table: `app_fd_student_academics`
- Fields: previous_school, grade_level, program, academic_year, parent_id

**Form: studentGuardian**
- Form ID: `studentGuardian`
- Table: `app_fd_student_guardian`
- Fields: guardian_name, relationship, contact, address, parent_id

**Form: studentCourses** (Grid)
- Form ID: `studentCoursesForm`
- Table: `app_fd_student_courses`
- Fields: course_code, course_name, credits, student_id

---

### Step 3: Create Receiver YAML Configuration

**File**: `/Users/aarelaponin/IdeaProjects/plugins/processing-server/src/main/resources/docs-metadata/students_registry.yml`

```yaml
service:
  id: students_registry
  name: "Student Enrollment Service"
  version: "1.0"
  govstackVersion: "1.0"
  metadataVersion: "1.0.0"
  lastUpdated: "2025-10-29"

  serviceConfig:
    # Parent form configuration
    parentFormId: "studentRegistrationForm"

    # Parent reference fields linking parent to child forms
    parentReferenceFields:
      - "basic_data"
      - "academics_data"
      - "guardian_data"
      - "courses"

    # Section to form mappings
    sectionToFormMap:
      studentBasicInfo: "studentBasicInfo"
      studentAcademics: "studentAcademics"
      studentGuardian: "studentGuardian"

    # Grid configurations
    gridMappings:
      studentCourses:
        formId: "studentCoursesForm"
        parentField: "student_id"

    # Default parent field for grids
    defaults:
      gridParentField: "student_id"
      gridParentColumn: "c_student_id"

# Form mappings - define how GovStack JSON maps to Joget forms
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
      - joget: previous_school
        govstack: extension.academics.previousSchool
        jsonPath: $.extension.academics.previousSchool

      - joget: grade_level
        govstack: extension.academics.gradeLevel
        jsonPath: $.extension.academics.gradeLevel

      - joget: program
        govstack: extension.academics.program
        jsonPath: $.extension.academics.program

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

---

### Step 4: Create Sender YAML Configuration

**File**: Create similar `students_registry.yml` for sender side with reverse mappings (Joget → GovStack JSON)

The sender configuration maps FROM Joget forms TO GovStack JSON format for transmission.

---

### Step 5: Build and Deploy

```bash
# Build processing-server with new YAML
cd /Users/aarelaponin/IdeaProjects/plugins/processing-server
mvn clean package -Dmaven.test.skip=true

# Deploy to receiver
cp target/processing-server-8.1-SNAPSHOT.jar \
   /Users/aarelaponin/joget-enterprise-linux-8.1.6-2/wflow/app_plugins/

# Build doc-submitter with new YAML
cd /Users/aarelaponin/IdeaProjects/gs-farmer/doc-submitter
mvn clean package -Dmaven.test.skip=true

# Deploy to sender
cp target/doc-submitter-8.1-SNAPSHOT.jar \
   /Users/aarelaponin/joget-enterprise-linux-8.1.6/wflow/app_plugins/
```

---

### Step 6: Configure Sender Process

In sender Joget instance (localhost:9999):

1. Create a new process definition: `students_registry_submission`
2. Add student enrollment form
3. Add DocSubmitter tool with configuration:
   ```json
   {
     "serviceId": "students_registry",
     "apiUrl": "http://localhost:8080/jw/api/govstack/registration/services/students_registry/applications",
     "metadataFile": "students_registry.yml"
   }
   ```

---

### Step 7: Test Student Enrollment

1. **Submit a test student enrollment** through sender form
2. **Check sender logs** (port 9999):
   ```
   Successfully sent data to GovStack API
   API Response Code: 200
   ```

3. **Check receiver logs** (port 8080):
   ```
   Loaded section to form map from configuration: 3 mappings
   Using primary key: [UUID]
   Created parent record in form: studentRegistrationForm
   Saved to form: studentBasicInfo
   Saved to form: studentAcademics
   Saved to form: studentGuardian
   Saved array data for 1 grids
   ```

4. **Verify database**:
   - Check `students_registry` table has parent record
   - Check `app_fd_student_basic_info` has student data
   - Check `app_fd_student_academics` has academic data
   - Check `app_fd_student_guardian` has guardian data
   - Check `app_fd_student_courses` has course selections

---

## ✅ Success Criteria

Student enrollment service is working when:

1. ✅ Student data successfully submitted from sender
2. ✅ HTTP 200 response received
3. ✅ All student forms populated in receiver database
4. ✅ Grid data (courses) correctly stored
5. ✅ **NO Java code changes were needed**
6. ✅ Both farmers and students services work simultaneously

---

## 🎯 Validation

To prove the system is truly generic:

### Test 1: Parallel Submissions
Submit both farmer and student registrations alternately. Both should work without interference.

### Test 2: Code Inspection
```bash
# Verify NO hardcoded "student" references in Java
cd /Users/aarelaponin/IdeaProjects/plugins/processing-server
grep -r "student" src/main/java/

# Should return: NO matches (only in YAML and docs)
```

### Test 3: Third Service
Can you add a third service (e.g., business licenses) in under 1 hour?
If yes, the architecture is truly generic!

---

## 🐛 Troubleshooting

### Issue: HTTP 400 "Section mapping not found"
**Cause**: Missing sectionToFormMap entry
**Fix**: Add all sections to `sectionToFormMap` in YAML

### Issue: HTTP 500 "parentFormId not configured"
**Cause**: Missing parentFormId in serviceConfig
**Fix**: Add `parentFormId: "studentRegistrationForm"` to YAML

### Issue: Grid data not saving
**Cause**: Missing gridMappings configuration
**Fix**: Add grid configuration to `gridMappings` section

### Issue: Data going to wrong forms
**Cause**: Incorrect sectionToFormMap mappings
**Fix**: Verify section names match YAML keys exactly

---

## 📚 Reference Examples

**Farmers Registry**: Working example to reference
- Sender YAML: `/Users/aarelaponin/IdeaProjects/gs-farmer/doc-submitter/src/main/resources/docs-metadata/farmers_registry.yml`
- Receiver YAML: `/Users/aarelaponin/IdeaProjects/plugins/processing-server/src/main/resources/docs-metadata/farmers_registry.yml`

---

## 🚀 Next Steps After Students

Once student enrollment is working:

1. **Business Licenses**: Test with a document-heavy service
2. **Subsidies Application**: Test with complex eligibility rules
3. **Multi-tenant**: Multiple organizations using same service
4. **Performance**: Test with 1000+ concurrent submissions

---

## 📝 Notes

- Keep YAML files in sync between sender and receiver
- Use semantic versioning for YAML (metadataVersion)
- Test each service independently before parallel testing
- Document any service-specific business rules separately

---

**Ready to implement? Start with Step 1 and work through systematically. Good luck! 🎓**
