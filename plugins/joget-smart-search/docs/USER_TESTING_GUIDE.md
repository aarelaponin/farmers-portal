# Smart Farmer Search Plugin - User Testing Guide

**Version:** 8.1-SNAPSHOT
**Last Updated:** 2026-01-27
**Document Type:** QA Testing Guide

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Test Environment Setup](#2-test-environment-setup)
3. [Functional Test Cases](#3-functional-test-cases)
4. [Cross-Browser Testing](#4-cross-browser-testing)
5. [Performance Checklist](#5-performance-checklist)
6. [Regression Checklist](#6-regression-checklist)
7. [Bug Reporting Template](#7-bug-reporting-template)
8. [Appendix](#8-appendix)

---

## 1. Introduction

### 1.1 Purpose

This guide provides comprehensive test cases for validating the Smart Farmer Search plugin functionality. It is designed for QA testers, system administrators, and end users performing User Acceptance Testing (UAT).

### 1.2 Prerequisites

Before testing, ensure you have:

- [ ] Access to a Joget DX8/DX9 environment with the plugin installed
- [ ] Valid user credentials with form access permissions
- [ ] Test farmer data loaded in the database (minimum 50 records recommended)
- [ ] API credentials configured (API ID and API Key)
- [ ] Modern web browser (Chrome, Firefox, Safari, or Edge)

### 1.3 Plugin Overview

The Smart Farmer Search plugin enables users to find farmers in a registry using:

| Search Method | Description | Confidence |
|---------------|-------------|------------|
| National ID | Exact 9-13 digit match | 100% |
| Phone Number | Exact match (8+ digits) | 100% |
| Name + Location | Fuzzy matching with filters | Variable |

### 1.4 Test Data Requirements

Ensure your test environment includes farmers with:

- Various districts (at least 3 different districts)
- Common and unique names (for fuzzy matching tests)
- Complete phone numbers
- Valid national IDs
- Village and cooperative associations

---

## 2. Test Environment Setup

### 2.1 Plugin Configuration Checklist

Before testing, verify these settings in the Form Builder:

| Setting | Recommended Test Value | Verified |
|---------|----------------------|----------|
| Field ID | `farmerId` | [ ] |
| Value to Store | `nationalId` | [ ] |
| Display Mode | `popup` | [ ] |
| API Endpoint | `/jw/api/fss` | [ ] |
| API ID | (your API ID) | [ ] |
| API Key | (your API key) | [ ] |
| Show Recent Farmers | `enabled` | [ ] |
| Max Recent Farmers | `5` | [ ] |
| Auto-Select Single Result | `enabled` | [ ] |

### 2.2 API Verification

Test the API connection before UI testing:

```
GET /jw/api/fss/fss/statistics
Headers: api_id: [YOUR_API_ID], api_key: [YOUR_API_KEY]

Expected: HTTP 200 with JSON response containing total_farmers count
```

### 2.3 Database Verification

Confirm test data exists:

```sql
SELECT COUNT(*) FROM v_farmer_search;
-- Should return > 0

SELECT DISTINCT c_district_name FROM v_farmer_search;
-- Should return multiple districts
```

---

## 3. Functional Test Cases

### 3.1 Exact Match Search

#### FSS-001: Search by National ID

| Field | Value |
|-------|-------|
| **Preconditions** | Know a valid national ID in the test data |
| **Priority** | High |

**Steps:**

1. [ ] Open the form containing the Smart Farmer Search element
2. [ ] Click the "Search" button to open the search dialog
3. [ ] In the search input, enter a complete national ID (e.g., `1234567890123`)
4. [ ] Observe the input type badge changes to "ID"
5. [ ] Click the "Search" button

**Expected Results:**

- [ ] Single farmer result displayed
- [ ] Relevance score shows 100%
- [ ] Score badge is green (high confidence)
- [ ] Farmer details match the entered ID

---

#### FSS-002: Search by Phone Number

| Field | Value |
|-------|-------|
| **Preconditions** | Know a valid phone number in the test data |
| **Priority** | High |

**Steps:**

1. [ ] Open the search dialog
2. [ ] Enter a complete phone number (e.g., `+26622123456` or `22123456`)
3. [ ] Observe the input type badge changes to "Phone"
4. [ ] Click "Search"

**Expected Results:**

- [ ] Single farmer result displayed
- [ ] Relevance score shows 100%
- [ ] Phone number in result matches search input

---

#### FSS-003: Auto-Select on Exact Match

| Field | Value |
|-------|-------|
| **Preconditions** | Auto-select enabled in config, valid national ID known |
| **Priority** | High |

**Steps:**

1. [ ] Open the search dialog
2. [ ] Enter a valid national ID
3. [ ] Click "Search"

**Expected Results:**

- [ ] Farmer is automatically selected (dialog closes)
- [ ] Selection display shows the farmer's name
- [ ] Hidden field contains the national ID
- [ ] Brief notification appears: "Farmer auto-selected"

---

### 3.2 Fuzzy Name Search

#### FSS-010: Search by Name and District

| Field | Value |
|-------|-------|
| **Preconditions** | Know a farmer name and district in test data |
| **Priority** | High |

**Steps:**

1. [ ] Open the search dialog
2. [ ] Enter a farmer's first or last name (e.g., "John")
3. [ ] Select a district from the dropdown
4. [ ] Observe the confidence bar updates
5. [ ] Click "Search"

**Expected Results:**

- [ ] Multiple results displayed (if name is common)
- [ ] Results sorted by relevance score (highest first)
- [ ] Matching names highlighted or prioritized
- [ ] Confidence bar showed estimate before search

---

#### FSS-011: Fuzzy Matching - Misspelling

| Field | Value |
|-------|-------|
| **Preconditions** | Know exact spelling of a farmer name |
| **Priority** | Medium |

**Steps:**

1. [ ] Open the search dialog
2. [ ] Enter a slightly misspelled name (e.g., "Jonn" instead of "John")
3. [ ] Select the correct district
4. [ ] Click "Search"

**Expected Results:**

- [ ] Correct farmer appears in results despite misspelling
- [ ] Score reflects partial match (typically 60-85%)

---

#### FSS-012: Fuzzy Name Matching (pg_trgm)

| Field | Value |
|-------|-------|
| **Preconditions** | Know a farmer name, `pg_trgm` extension enabled |
| **Priority** | Medium |

**Steps:**

1. [ ] Open the search dialog
2. [ ] Enter a slightly different name (e.g., "Tabo" instead of "Thabo")
3. [ ] Select appropriate district
4. [ ] Click "Search"

**Expected Results:**

- [ ] Farmer with similar name appears in results (e.g., "Thabo" found when searching "Tabo")
- [ ] Score reflects partial match (typically 50-85%)

> **Note:** Requires `pg_trgm` PostgreSQL extension. If not available, fuzzy matching falls back to LIKE and Levenshtein distance.
> Soundex matching is an additional optional layer requiring the `fuzzystrmatch` extension.

---

### 3.3 Progressive Criteria Building

#### FSS-020: Add Village Filter

| Field | Value |
|-------|-------|
| **Preconditions** | District selected |
| **Priority** | High |

**Steps:**

1. [ ] Open search dialog and enter a name
2. [ ] Select a district
3. [ ] Click "Add Criteria" button
4. [ ] Select "Village" from the dropdown
5. [ ] Start typing a village name
6. [ ] Select from autocomplete suggestions
7. [ ] Observe confidence bar updates
8. [ ] Click "Search"

**Expected Results:**

- [ ] Autocomplete shows villages in selected district
- [ ] Confidence increases after adding village
- [ ] Results filtered to selected village

---

#### FSS-021: Add Community Council Filter

| Field | Value |
|-------|-------|
| **Preconditions** | None |
| **Priority** | Medium |

**Steps:**

1. [ ] Open search dialog
2. [ ] Click "Add Criteria"
3. [ ] Select "Community Council"
4. [ ] Type to search and select a council
5. [ ] Click "Search"

**Expected Results:**

- [ ] Autocomplete shows available community councils
- [ ] Results filtered by community council

---

#### FSS-022: Add Cooperative Filter

| Field | Value |
|-------|-------|
| **Preconditions** | None |
| **Priority** | Medium |

**Steps:**

1. [ ] Open search dialog
2. [ ] Click "Add Criteria"
3. [ ] Select "Cooperative"
4. [ ] Type to search and select a cooperative
5. [ ] Click "Search"

**Expected Results:**

- [ ] Autocomplete shows matching cooperatives
- [ ] Results filtered by cooperative membership

---

#### FSS-023: Add Partial ID Filter

| Field | Value |
|-------|-------|
| **Preconditions** | Know partial national ID (last 4+ digits) |
| **Priority** | Medium |

**Steps:**

1. [ ] Open search dialog
2. [ ] Click "Add Criteria"
3. [ ] Select "Partial ID"
4. [ ] Enter 4 or more digits from a known ID
5. [ ] Observe hint text shows minimum requirement
6. [ ] Click "Search"

**Expected Results:**

- [ ] Input validates minimum digit requirement
- [ ] Placeholder shows "Enter at least X digits..."
- [ ] Results include farmers with matching ID substring

---

#### FSS-024: Remove Criteria

| Field | Value |
|-------|-------|
| **Preconditions** | Multiple criteria added |
| **Priority** | Medium |

**Steps:**

1. [ ] Add multiple criteria (village, cooperative, etc.)
2. [ ] Click the "X" button on one criteria row
3. [ ] Observe the criteria is removed
4. [ ] Check confidence bar updates

**Expected Results:**

- [ ] Criteria row removed with animation
- [ ] Confidence bar recalculates
- [ ] Remaining criteria still functional

---

### 3.4 Search Results

#### FSS-030: Result Score Color Coding

| Field | Value |
|-------|-------|
| **Preconditions** | Search returns multiple results |
| **Priority** | Medium |

**Steps:**

1. [ ] Perform a name + district search
2. [ ] Examine the score badges on results

**Expected Results:**

| Score Range | Expected Color |
|-------------|----------------|
| 90-100% | Green |
| 70-89% | Yellow |
| 50-69% | Orange |
| Below 50% | Red |

---

#### FSS-031: No Results Message

| Field | Value |
|-------|-------|
| **Preconditions** | None |
| **Priority** | High |

**Steps:**

1. [ ] Open search dialog
2. [ ] Enter a name that doesn't exist (e.g., "XYZNONEXISTENT")
3. [ ] Select any district
4. [ ] Click "Search"

**Expected Results:**

- [ ] "No farmers found" message displayed
- [ ] Message suggests refining criteria
- [ ] No JavaScript errors in console

---

#### FSS-032: Too Many Results Warning

| Field | Value |
|-------|-------|
| **Preconditions** | Large test dataset |
| **Priority** | Medium |

**Steps:**

1. [ ] Open search dialog
2. [ ] Enter a very common name or single letter
3. [ ] Select a large district
4. [ ] Click "Search"

**Expected Results:**

- [ ] Warning message if results exceed threshold
- [ ] Suggestion to add more criteria
- [ ] Results still displayed (limited)

---

### 3.5 Farmer Selection

#### FSS-040: Select Farmer from Results

| Field | Value |
|-------|-------|
| **Preconditions** | Search completed with results |
| **Priority** | High |

**Steps:**

1. [ ] Perform a search that returns results
2. [ ] Click on a farmer card in the results

**Expected Results:**

- [ ] Dialog closes
- [ ] Selection display shows farmer name
- [ ] Hidden field populated with correct value
- [ ] Form field shows selected state

---

#### FSS-041: Clear Selection

| Field | Value |
|-------|-------|
| **Preconditions** | Farmer already selected |
| **Priority** | High |

**Steps:**

1. [ ] With a farmer selected, locate the clear button (X)
2. [ ] Click the clear button

**Expected Results:**

- [ ] Selection cleared
- [ ] Display returns to empty state
- [ ] Hidden field cleared
- [ ] Search button re-enabled

---

### 3.6 Recent Farmers

#### FSS-050: Recent Farmers Panel Display

| Field | Value |
|-------|-------|
| **Preconditions** | showRecentFarmers enabled, previous selections made |
| **Priority** | Medium |

**Steps:**

1. [ ] Select a farmer and close the dialog
2. [ ] Reopen the search dialog

**Expected Results:**

- [ ] Recent farmers panel visible
- [ ] Previously selected farmer appears in list
- [ ] Most recent selection at top

---

#### FSS-051: Select from Recent Farmers

| Field | Value |
|-------|-------|
| **Preconditions** | Recent farmers panel has entries |
| **Priority** | Medium |

**Steps:**

1. [ ] Open search dialog
2. [ ] Click on a farmer in the Recent Farmers panel

**Expected Results:**

- [ ] Farmer selected immediately
- [ ] Dialog closes
- [ ] Selection display updated

---

#### FSS-052: Recent Farmers Persistence

| Field | Value |
|-------|-------|
| **Preconditions** | Recent farmers exist |
| **Priority** | Medium |

**Steps:**

1. [ ] Select several farmers
2. [ ] Refresh the page (F5)
3. [ ] Reopen the search dialog

**Expected Results:**

- [ ] Recent farmers persist after page refresh
- [ ] Order maintained (most recent first)
- [ ] Maximum limit respected (default: 5)

---

#### FSS-053: Recent Farmers Disabled

| Field | Value |
|-------|-------|
| **Preconditions** | showRecentFarmers disabled in config |
| **Priority** | Low |

**Steps:**

1. [ ] Configure plugin with showRecentFarmers = false
2. [ ] Open search dialog

**Expected Results:**

- [ ] Recent farmers panel NOT visible
- [ ] No errors in console

---

### 3.7 UI/UX Testing

#### FSS-060: Popup Dialog Open/Close

| Field | Value |
|-------|-------|
| **Preconditions** | displayMode = popup |
| **Priority** | High |

**Steps:**

1. [ ] Click "Search" button - dialog should open
2. [ ] Click X button - dialog should close
3. [ ] Reopen and click overlay background - dialog should close
4. [ ] Reopen and press Escape key - dialog should close

**Expected Results:**

- [ ] All close methods work correctly
- [ ] No orphaned overlays or modals
- [ ] Focus returns to trigger element

---

#### FSS-061: Inline Mode Display

| Field | Value |
|-------|-------|
| **Preconditions** | displayMode = inline |
| **Priority** | Medium |

**Steps:**

1. [ ] Configure plugin with displayMode = inline
2. [ ] Load the form

**Expected Results:**

- [ ] Search interface embedded in form (no popup)
- [ ] All functionality works inline
- [ ] Proper sizing within form layout

---

#### FSS-062: Keyboard Navigation

| Field | Value |
|-------|-------|
| **Preconditions** | Search dialog open |
| **Priority** | Medium |

**Steps:**

1. [ ] Use Tab to navigate between elements
2. [ ] Use Enter to activate buttons
3. [ ] Use Arrow keys in autocomplete dropdowns
4. [ ] Use Escape to close dialog

**Expected Results:**

- [ ] All interactive elements reachable via Tab
- [ ] Focus indicators visible
- [ ] Enter activates focused buttons
- [ ] Escape closes dialog/dropdowns

---

#### FSS-063: Input Type Detection Badge

| Field | Value |
|-------|-------|
| **Preconditions** | Search dialog open |
| **Priority** | Medium |

**Steps:**

1. [ ] Type numbers (4+ digits) - should show "ID" badge
2. [ ] Type 8+ digits or +number - should show "Phone" badge
3. [ ] Type letters - should show "Name" badge
4. [ ] Clear input - badge should disappear

**Expected Results:**

- [ ] Badge updates in real-time
- [ ] Correct type detected based on input pattern
- [ ] Badge styling clearly visible

---

### 3.8 Error Handling

#### FSS-070: Network Timeout

| Field | Value |
|-------|-------|
| **Preconditions** | Ability to simulate slow network |
| **Priority** | Medium |

**Steps:**

1. [ ] Open browser DevTools > Network > Throttle to "Slow 3G"
2. [ ] Perform a search
3. [ ] Wait for timeout (30 seconds)

**Expected Results:**

- [ ] Timeout error message displayed
- [ ] Retry option available
- [ ] UI remains responsive

---

#### FSS-071: API Authentication Failure

| Field | Value |
|-------|-------|
| **Preconditions** | Invalid API credentials |
| **Priority** | High |

**Steps:**

1. [ ] Configure plugin with incorrect API key
2. [ ] Attempt a search

**Expected Results:**

- [ ] Authentication error message
- [ ] No sensitive information exposed
- [ ] Clear guidance to contact administrator

---

#### FSS-072: Server Error

| Field | Value |
|-------|-------|
| **Preconditions** | Backend returns 500 error |
| **Priority** | Medium |

**Steps:**

1. [ ] Trigger a server error condition (if possible)
2. [ ] Observe error handling

**Expected Results:**

- [ ] User-friendly error message
- [ ] Retry option available
- [ ] Error logged for debugging

---

### 3.9 Configuration Testing

#### FSS-080: Store Value Options

| Field | Value |
|-------|-------|
| **Preconditions** | Access to form builder |
| **Priority** | High |

**Steps:**

For each storeValue option (nationalId, id, phone):

1. [ ] Configure the plugin with the option
2. [ ] Select a farmer
3. [ ] Check the hidden field value

**Expected Results:**

| storeValue | Hidden Field Contains |
|------------|----------------------|
| nationalId | Farmer's national ID |
| id | Internal record ID |
| phone | Farmer's phone number |

---

#### FSS-081: Custom ID Pattern

| Field | Value |
|-------|-------|
| **Preconditions** | Access to form builder |
| **Priority** | Medium |

**Steps:**

1. [ ] Configure nationalIdPattern to `^\d{4}$` (4 digits only)
2. [ ] Configure nationalIdMinLength to `4`
3. [ ] Enter "1234" in search
4. [ ] Verify it's detected as ID type

**Expected Results:**

- [ ] 4-digit input detected as ID
- [ ] Longer numbers not detected as ID until pattern matches
- [ ] Pattern validation works correctly

---

## 4. Cross-Browser Testing

Complete the following matrix:

| Test Case | Chrome | Firefox | Safari | Edge |
|-----------|--------|---------|--------|------|
| FSS-001: ID Search | [ ] | [ ] | [ ] | [ ] |
| FSS-010: Name Search | [ ] | [ ] | [ ] | [ ] |
| FSS-040: Selection | [ ] | [ ] | [ ] | [ ] |
| FSS-060: Popup | [ ] | [ ] | [ ] | [ ] |
| FSS-062: Keyboard | [ ] | [ ] | [ ] | [ ] |
| FSS-050: Recent Farmers | [ ] | [ ] | [ ] | [ ] |

**Browser Versions Tested:**

| Browser | Version | Date Tested | Tester |
|---------|---------|-------------|--------|
| Chrome | | | |
| Firefox | | | |
| Safari | | | |
| Edge | | | |

---

## 5. Performance Checklist

| Metric | Target | Actual | Pass |
|--------|--------|--------|------|
| Search response time | < 3 seconds | | [ ] |
| Autocomplete response | < 1 second | | [ ] |
| Dialog open time | < 500ms | | [ ] |
| Initial page load impact | < 500ms | | [ ] |

**Test Conditions:**
- Network: Standard broadband
- Database: Production-size dataset
- Concurrent users: (specify)

---

## 6. Regression Checklist

Quick smoke test after any updates:

### Core Functionality
- [ ] National ID search returns exact match
- [ ] Phone search returns exact match
- [ ] Name + District search works
- [ ] Fuzzy matching finds misspellings

### UI
- [ ] Popup opens and closes
- [ ] Results display correctly
- [ ] Score colors correct
- [ ] Selection updates form field

### Features
- [ ] Recent farmers panel works
- [ ] Autocomplete triggers
- [ ] Confidence bar updates
- [ ] Clear selection works

### No Errors
- [ ] No JavaScript console errors
- [ ] No network errors (check DevTools)

---

## 7. Bug Reporting Template

When reporting issues, include:

```
## Bug Report

**Test Case ID:** FSS-XXX
**Severity:** Critical / High / Medium / Low
**Environment:**
- Browser:
- Joget Version:
- Plugin Version: 8.1-SNAPSHOT-phase10

**Steps to Reproduce:**
1.
2.
3.

**Expected Result:**


**Actual Result:**


**Screenshots/Videos:**
[Attach files]

**Console Errors:**
[Copy any JavaScript errors]

**Additional Notes:**

```

---

## 8. Appendix

### 8.1 Sample Test Data

Recommended test farmers:

| National ID | Name | District | Village | Phone |
|-------------|------|----------|---------|-------|
| 1234567890123 | John Doe | Maseru | Ha Matala | +26622123456 |
| 9876543210987 | Jane Smith | Leribe | Hlotse | +26622654321 |
| 5555555555555 | Thabo Mokoena | Berea | Teyateyaneng | +26622555555 |

### 8.2 API Endpoints Reference

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/jw/api/fss/fss/search` | POST | Main criteria search |
| `/jw/api/fss/fss/lookup/{id}` | GET | Single farmer lookup |
| `/jw/api/fss/fss/search/byNationalId/{id}` | GET | Exact ID match |
| `/jw/api/fss/fss/search/byPhone/{phone}` | GET | Exact phone match |
| `/jw/api/fss/fss/villages` | GET | Village autocomplete |
| `/jw/api/fss/fss/community-councils` | GET | Council autocomplete |
| `/jw/api/fss/fss/cooperatives` | GET | Cooperative autocomplete |
| `/jw/api/fss/fss/statistics` | GET | Statistics for confidence |

### 8.3 Troubleshooting

| Issue | Possible Cause | Solution |
|-------|----------------|----------|
| Search returns no results | API credentials invalid | Verify API ID/Key in config |
| Autocomplete not working | Network issue | Check browser DevTools network tab |
| Recent farmers not showing | Feature disabled | Enable showRecentFarmers in config |
| Dialog won't close | JavaScript error | Check console for errors |
| Wrong value stored | storeValue misconfigured | Verify storeValue setting |

### 8.4 Test Sign-Off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| QA Tester | | | |
| Developer | | | |
| Product Owner | | | |

---

**Document Control:**

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-01-11 | Claude Code | Initial version |
| 1.1 | 2026-01-27 | Claude Code | Marked Soundex test as optional (Azure PostgreSQL compatibility) |
