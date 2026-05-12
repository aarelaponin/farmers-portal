# Minimal Contact Form Example

**Complexity**: Simple
**Time to implement**: 15-20 minutes
**Forms required**: 2 (1 parent + 1 child)
**Grids**: None

---

## Overview

This is the simplest possible GovStack service example. It demonstrates:
- Basic parent-child form relationship
- Simple field mappings
- No grids or complex structures
- Minimal YAML configuration

Perfect for learning the fundamentals before tackling more complex services.

---

## What You'll Build

A contact form that captures:
- Full Name
- Email Address
- Phone Number (optional)
- Message/Inquiry

**Data Flow**:
```
User fills contact form → DocSubmitter → GovStack JSON → ProcessingServer → Joget forms
```

---

## Prerequisites

- Joget sender instance running (e.g., localhost:9999)
- Joget receiver instance running (e.g., localhost:8080)
- Both plugins installed (DocSubmitter and ProcessingServer)
- Basic understanding of Joget Form Builder

---

## Step-by-Step Implementation

### Step 1: Create Receiver Forms

#### Form 1: Child Form (contactForm)

1. Open receiver Joget: `http://localhost:8080`
2. Navigate: **Design → Form Builder**
3. Click: **[+ New Form]**
4. Configure:
   - **Form ID**: `contactForm`
   - **Form Name**: `Contact Form`
   - **Table Name**: `contact_submissions`
5. Add fields:

| Field ID   | Type        | Label           | Required | Notes        |
|------------|-------------|-----------------|----------|--------------|
| full_name  | Text Field  | Full Name       | Yes      | Max: 100     |
| email      | Email Field | Email Address   | Yes      | With validator |
| phone      | Text Field  | Phone Number    | No       | Optional     |
| message    | Text Area   | Message         | Yes      | Rows: 5      |
| parent_id  | Hidden Field| (hidden)        | -        | Auto-populated |

6. Click: **[Save]** and **[Publish]**

#### Form 2: Parent Form (contactRegistrationForm)

1. Click: **[+ New Form]**
2. Configure:
   - **Form ID**: `contactRegistrationForm`
   - **Form Name**: `Contact Registration`
   - **Table Name**: `contacts_registry`
3. Add fields:

| Field ID     | Type         | Notes                    |
|--------------|--------------|--------------------------|
| contact_data | Hidden Field | References contactForm   |

4. Click: **[Save]** and **[Publish]**

**Note**: Parent form appears empty (all fields hidden). This is correct!

---

### Step 2: Deploy Receiver Configuration

1. Copy receiver YAML:
```bash
cp receiver/contact_form.yml \
   /path/to/processing-server/src/main/resources/docs-metadata/
```

2. Rebuild processing-server:
```bash
cd /path/to/processing-server
mvn clean package -Dmaven.test.skip=true
```

3. Deploy JAR:
```bash
cp target/processing-server-8.1-SNAPSHOT.jar \
   /path/to/joget-receiver/wflow/app_plugins/
```

4. Restart receiver Joget or reload plugins

---

### Step 3: Create Sender Form

1. Open sender Joget: `http://localhost:9999`
2. Navigate: **Design → Form Builder**
3. Click: **[+ New Form]**
4. Configure:
   - **Form ID**: `contactSubmissionForm`
   - **Form Name**: `Contact Us`
   - **Table Name**: `contact_form_data`
5. Add fields:

| Field ID   | Type        | Label           | Required |
|------------|-------------|-----------------|----------|
| full_name  | Text Field  | Full Name       | Yes      |
| email      | Email Field | Email Address   | Yes      |
| phone      | Text Field  | Phone Number    | No       |
| message    | Text Area   | Your Message    | Yes      |

6. Click: **[Save]** and **[Publish]**

---

### Step 4: Deploy Sender Configuration

1. Copy sender YAML:
```bash
cp sender/contact_form.yml \
   /path/to/doc-submitter/src/main/resources/docs-metadata/
```

2. Rebuild doc-submitter:
```bash
cd /path/to/doc-submitter
mvn clean package -Dmaven.test.skip=true
```

3. Deploy JAR:
```bash
cp target/doc-submitter-8.1-SNAPSHOT.jar \
   /path/to/joget-sender/wflow/app_plugins/
```

4. Restart sender Joget or reload plugins

---

### Step 5: Create Sender Process

1. In sender Joget: **Design → Process Builder**
2. Click: **[+ New Process]**
3. Configure:
   - **Process ID**: `contact_form_submission`
   - **Process Name**: `Contact Form Submission`
4. Create simple flow: **Start → Activity → End**
5. Configure Activity:
   - **Activity ID**: `contact_form`
   - **Form**: `contactSubmissionForm`
6. Add **DocSubmitter** tool:
   - **Tool ID**: `submit_contact`
   - **Execute on**: Complete
   - **Service ID**: `contact_form`
   - **API URL**: `http://localhost:8080/jw/api/govstack/registration/services/contact_form/applications`
7. Save process

8. Create App:
   - Navigate: **Apps → Build App**
   - **App ID**: `contact_form_app`
   - **App Name**: `Contact Us`
   - Add process: `contact_form_submission`
   - Publish

---

### Step 6: Test

1. Open sender app: **Apps → Contact Us**
2. Click: **[Start Process]**
3. Fill in test data:
   - **Name**: John Doe
   - **Email**: john.doe@example.com
   - **Phone**: +1234567890
   - **Message**: This is a test message
4. Click: **[Submit]**
5. Expected: "Form submitted successfully"

---

### Step 7: Verify

#### Check Sender Logs
```bash
tail -f /path/to/joget-sender/joget-tomcat9/logs/catalina.out
```

Look for:
```
DocSubmitter: Service ID: contact_form
DocSubmitter: Response Code: 200
DocSubmitter: Successfully sent data to GovStack API
```

#### Check Receiver Logs
```bash
tail -f /path/to/joget-receiver/joget-tomcat9/logs/catalina.out
```

Look for:
```
ProcessingServer: Received POST request for service: contact_form
ProcessingServer: Created parent record in form: contactRegistrationForm
ProcessingServer: Saved to form: contactForm
```

#### Verify Database
```sql
-- Check parent table
SELECT * FROM contacts_registry ORDER BY dateCreated DESC LIMIT 1;

-- Check contact data
SELECT * FROM contact_submissions ORDER BY dateCreated DESC LIMIT 1;
```

Expected: Both tables have 1 record with matching UUIDs

---

## Success Criteria

✅ Sender form accepts input
✅ HTTP 200 response from receiver
✅ Parent record created in contacts_registry
✅ Contact data saved in contact_submissions
✅ parent_id matches parent UUID
✅ No errors in logs

---

## Common Issues

### Issue: HTTP 400 "Section mapping not found"

**Cause**: sectionToFormMap in YAML doesn't match formMappings keys

**Fix**: Ensure `contactInfo` appears in both:
```yaml
sectionToFormMap:
  contactInfo: "contactForm"  # Must match below

formMappings:
  contactInfo:  # Must match above
    ...
```

### Issue: HTTP 500 "parentFormId not configured"

**Cause**: Missing parentFormId in serviceConfig

**Fix**: Add to receiver YAML:
```yaml
serviceConfig:
  parentFormId: "contactRegistrationForm"
```

### Issue: Data not saving to contactForm

**Cause**: Form ID mismatch

**Fix**: Verify form IDs match exactly:
- Joget form ID: `contactForm`
- YAML formId: `contactForm`
- sectionToFormMap value: `contactForm`

---

## What's Different from Complex Services?

| Feature | Contact Form (Simple) | Student Enrollment (Medium) |
|---------|----------------------|----------------------------|
| Parent form | 1 | 1 |
| Child forms | 1 | 3 |
| Grid forms | 0 | 1 |
| Sections | 1 | 3 |
| YAML lines | ~60 | ~150 |
| Time to implement | 15-20 min | 3-4 hours |

---

## Next Steps

Once you've successfully implemented this minimal example:

1. **Add a second field**: Try adding a "Subject" field
2. **Add validation**: Make phone number follow a pattern
3. **Add a grid**: Try adding multiple contact methods
4. **Create your own service**: Use this as a template

For more complex examples, see:
- **examples/simple-job-application/** - Adds date fields and select boxes
- **examples/student-enrollment/** - Includes grids and transformations
- **examples/business-license/** - Complex with multiple grids

---

## File Structure

```
minimal-contact-form/
├── README.md (this file)
├── receiver/
│   └── contact_form.yml (receiver configuration)
├── sender/
│   └── contact_form.yml (sender configuration)
└── test-data.json (optional sample JSON payload)
```

---

## Learning Points

This example teaches:

1. **Parent-Child Relationship**: How parent form holds references
2. **Section Mapping**: How sections map to forms
3. **Field Mapping**: How Joget fields map to JSON paths
4. **Basic YAML Structure**: Minimal required configuration
5. **End-to-End Flow**: Complete data submission workflow

Perfect foundation before tackling more complex services!

---

## Support

For issues or questions:
- Check main guide: `SERVICE_CREATION_GUIDE.md` Part 6 (Troubleshooting)
- Use YAML validator: `tools/yaml-validator.sh contact_form.yml`
- Review logs carefully for specific error messages

---

**Estimated Implementation Time**: 15-20 minutes
**Difficulty**: Beginner
**Prerequisites**: Basic Joget knowledge
**Good for**: Learning fundamentals, testing setup, quick demos
