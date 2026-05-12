# Smart Farmer Search Plugin - Testing Strategy

**Date:** 2026-01-11
**Purpose:** Define a feasible, actionable testing strategy for fixing the 23 identified issues while ensuring regression safety.

---

## Table of Contents

1. [Testing Philosophy](#testing-philosophy)
2. [Test Architecture Overview](#test-architecture-overview)
3. [Test Categories](#test-categories)
4. [Implementation Plan](#implementation-plan)
5. [Test Infrastructure Setup](#test-infrastructure-setup)
6. [Specific Test Cases by Issue](#specific-test-cases-by-issue)
7. [Regression Test Suite](#regression-test-suite)
8. [Continuous Integration](#continuous-integration)
9. [Manual Test Checklist](#manual-test-checklist)

---

## Testing Philosophy

### Guiding Principles

1. **Test at the Right Level**: Unit tests for logic, integration tests for flows, E2E for critical paths only
2. **Automate the Repeatable**: Focus automation on pure functions and deterministic behavior
3. **Isolate Dependencies**: Mock Joget platform, database, and browser where possible
4. **Regression Safety First**: Establish baseline tests BEFORE making any fixes
5. **Pragmatic Coverage**: 100% coverage is not the goal; testing critical paths is

### The Testing Pyramid for This Plugin

```
         /\
        /  \        E2E Tests (Manual + Selenium)
       /    \       - 5-10 critical user flows
      /------\
     /        \     Integration Tests
    /          \    - Config flow tests
   /            \   - API endpoint tests
  /--------------\
 /                \ Unit Tests
/                  \ - Java services (JUnit)
/                    \ - JavaScript modules (Jest)
----------------------
```

### What We CAN Automate

| Component | Testable in Isolation | Tool |
|-----------|----------------------|------|
| FuzzyMatchService.java | Yes (pure logic) | JUnit 5 |
| FarmerSearchService scoring | Yes (with mocks) | JUnit 5 + Mockito |
| SmartSearchElement config parsing | Partial (needs mocks) | JUnit 5 + Mockito |
| confidence-engine.js | Yes (pure logic) | Jest |
| recent-farmers.js | Yes (mock localStorage) | Jest |
| smart-search.js utilities | Partial (extract pure functions) | Jest |
| Config flow JSON→Java→FTL→JS | Yes (custom harness) | Node.js script |
| REST API endpoints | Yes (HTTP client) | REST Assured / curl |

### What Requires Manual/E2E Testing

| Component | Why Manual | Approach |
|-----------|-----------|----------|
| Popup/Modal UI | DOM interactions | Selenium or manual |
| Joget form integration | Platform-specific | Manual in Joget |
| CSS styling | Visual | Manual inspection |
| Keyboard navigation | Browser events | Selenium or manual |
| Offline behavior | Network conditions | Manual with DevTools |

---

## Test Architecture Overview

```
joget-smart-search/
├── src/
│   ├── main/
│   │   ├── java/          # Production Java code
│   │   └── resources/     # FTL, JS, JSON, CSS
│   └── test/
│       ├── java/          # JUnit tests
│       │   └── global/govstack/smartsearch/
│       │       ├── service/
│       │       │   ├── FuzzyMatchServiceTest.java
│       │       │   ├── FarmerSearchServiceTest.java
│       │       │   └── StatisticsServiceTest.java
│       │       ├── element/
│       │       │   └── SmartSearchElementTest.java
│       │       └── integration/
│       │           └── ConfigFlowTest.java
│       └── resources/
│           └── test-data/
│               ├── farmers.json        # Test farmer data
│               └── statistics.json     # Test statistics
├── src/test/js/                        # Jest tests
│   ├── confidence-engine.test.js
│   ├── recent-farmers.test.js
│   ├── smart-search-utils.test.js
│   └── config-flow.test.js
├── e2e/                                # E2E tests
│   ├── playwright/                     # Or Selenium
│   │   └── farmer-search.spec.js
│   └── manual/
│       └── TEST_CHECKLIST.md
└── docs/
    └── TESTING_STRATEGY.md             # This file
```

---

## Test Categories

### Category 1: Java Unit Tests (Fully Automatable)

**Scope:** Pure business logic in Java services

**FuzzyMatchService Tests:**
```java
@Test void soundex_shouldGenerateCorrectCode()
@Test void levenshteinDistance_shouldCalculateCorrectly()
@Test void normalizePhone_shouldStripNonDigits()
@Test void normalizeName_shouldLowercaseAndTrim()
@Test void calculateNameRelevanceScore_exactMatch_shouldReturn100()
@Test void calculateNameRelevanceScore_prefixMatch_shouldReturnHighScore()
@Test void calculateNameRelevanceScore_soundexMatch_shouldReturnMediumScore()
@Test void calculateCombinedRelevanceScore_withDistrictMatch_shouldAddBonus()
```

**FarmerSearchService Tests (with mocked DataSource):**
```java
@Test void search_withNationalId_shouldReturnExactMatch()
@Test void search_withPhone_shouldNormalizeAndMatch()
@Test void search_withNameOnly_shouldRequireDistrict()
@Test void scoreAndRank_shouldSortByRelevanceDescending()
@Test void searchCriteria_setLimit_shouldNotExceedMax()
```

**SmartSearchElement Tests (with mocked Joget APIs):**
```java
@Test void renderTemplate_shouldReadAllProperties()
@Test void renderTemplate_withMissingProperty_shouldUseDefault()
@Test void renderTemplate_shouldPassPatternsToDataModel()  // For Issue #3, #4
@Test void renderTemplate_shouldPassRecentFarmersConfig()  // For Issue #1, #2
```

### Category 2: JavaScript Unit Tests (Fully Automatable)

**confidence-engine.js Tests:**
```javascript
describe('ConfidenceEngine', () => {
  describe('calculate()', () => {
    it('should return 100 for exact nationalId match')
    it('should return 100 for exact phone match')
    it('should apply district factor correctly')
    it('should apply village factor correctly')
    it('should use configured nationalIdMinLength')  // For Issue #6
    it('should use configured phoneMinLength')       // For Issue #6
  })

  describe('validateCriteria()', () => {
    it('should reject name-only criteria')
    it('should accept name + village')
    it('should warn for name + district only')
  })

  describe('caching', () => {
    it('should cache statistics to localStorage')
    it('should return cached data when offline')
    it('should respect TTL')
  })
})
```

**recent-farmers.js Tests:**
```javascript
describe('RecentFarmers', () => {
  beforeEach(() => localStorage.clear())

  it('should add farmer to list')
  it('should move existing farmer to front')
  it('should respect maxItems limit')              // For Issue #2
  it('should handle missing localStorage gracefully')
  it('should generate ID when missing')
  it('should sort by timestamp descending')
})
```

**smart-search.js Utility Tests:**
```javascript
describe('detectInputType()', () => {
  it('should detect national_id pattern')
  it('should detect phone pattern')
  it('should detect name (non-numeric)')
  it('should use configured nationalIdPattern')   // For Issue #3
  it('should use configured phonePattern')        // For Issue #4
  it('should respect nationalIdMinLength')
  it('should respect phoneMinLength')
})

describe('buildCriteria()', () => {
  it('should build criteria from search state')
  it('should include additional criteria')
  it('should handle partial ID/phone')
})

describe('getScoreClass()', () => {
  it('should return high for score >= 90')        // For Issue #7
  it('should return medium for score >= 70')
  it('should return low for score >= 50')
  it('should return verylow for score < 50')
})
```

### Category 3: Configuration Flow Tests (Automatable with Custom Harness)

**Purpose:** Verify that properties defined in JSON are correctly propagated through the entire chain.

**Approach:** Create a Node.js test script that:
1. Parses SmartSearchElement.json to get all properties
2. Scans SmartSearchElement.java for `getPropertyString()` calls
3. Scans SmartSearchElement.ftl for `${propertyName}` references
4. Scans smart-search.js config object for property usage
5. Reports any breaks in the chain

```javascript
// config-flow.test.js
describe('Configuration Flow', () => {
  const jsonProps = parseJsonProperties('SmartSearchElement.json')
  const javaProps = extractJavaPropertyReads('SmartSearchElement.java')
  const ftlProps = extractFtlVariables('SmartSearchElement.ftl')
  const jsConfig = extractJsConfigProps('smart-search.js')

  jsonProps.forEach(prop => {
    if (prop.shouldPropagate) {
      it(`${prop.name} should be read in Java`, () => {
        expect(javaProps).toContain(prop.name)
      })

      it(`${prop.name} should be passed in FTL`, () => {
        expect(ftlProps).toContain(prop.name)
      })

      it(`${prop.name} should be used in JS config`, () => {
        expect(jsConfig).toContain(prop.name)
      })
    }
  })
})
```

### Category 4: API Integration Tests (Automatable)

**Approach:** Use REST Assured (Java) or supertest (Node.js) to test API endpoints.

```java
@Test
void searchEndpoint_withValidCriteria_shouldReturnResults() {
    given()
        .header("api_id", testApiId)
        .header("api_key", testApiKey)
        .contentType(ContentType.JSON)
        .body(new SearchRequest("MAS", "John"))
    .when()
        .post("/jw/api/fss/search")
    .then()
        .statusCode(200)
        .body("success", equalTo(true))
        .body("farmers.size()", greaterThan(0));
}

@Test
void searchByNationalId_shouldReturnExactMatch() {
    given()
        .header("api_id", testApiId)
        .header("api_key", testApiKey)
    .when()
        .get("/jw/api/fss/search/byNationalId/1234567890123")
    .then()
        .statusCode(200)
        .body("farmers[0].nationalId", equalTo("1234567890123"))
        .body("farmers[0].relevanceScore", equalTo(100));
}
```

### Category 5: E2E Tests (Partially Automatable with Playwright/Selenium)

**Critical User Flows to Automate:**

1. **Happy Path - Search and Select**
   - Open form with Smart Search element
   - Click search button
   - Enter name + select district
   - Click Search
   - Select a farmer from results
   - Verify hidden field is populated

2. **Inline Quick Entry - ID Lookup**
   - Enter national ID in inline input
   - Wait for auto-lookup
   - Verify farmer auto-selected

3. **Recent Farmers - Select from List**
   - Open search dialog
   - Click on a recent farmer
   - Verify selection

4. **Error Handling - Network Failure**
   - Simulate offline
   - Attempt search
   - Verify error message shown

5. **Clear Selection**
   - With farmer selected
   - Click clear button
   - Verify field cleared

```javascript
// playwright/farmer-search.spec.js
test('search and select farmer', async ({ page }) => {
  await page.goto('/joget/form/test_form')

  // Open search
  await page.click('.fss-search-btn')
  await expect(page.locator('.fss-dialog')).toBeVisible()

  // Enter criteria
  await page.fill('.fss-search-input', 'John')
  await page.selectOption('.fss-district-select', 'MAS')

  // Search
  await page.click('.fss-do-search-btn')
  await expect(page.locator('.fss-farmer-card')).toBeVisible()

  // Select first result
  await page.click('.fss-farmer-card:first-child')

  // Verify selection
  await expect(page.locator('.fss-selected-display')).toContainText('John')
  await expect(page.locator('input[name="farmerId"]')).not.toHaveValue('')
})
```

---

## Implementation Plan

### Phase 1: Setup (Day 1)

1. **Create test directory structure**
2. **Add test dependencies to pom.xml:**
   ```xml
   <dependency>
     <groupId>org.junit.jupiter</groupId>
     <artifactId>junit-jupiter</artifactId>
     <version>5.9.0</version>
     <scope>test</scope>
   </dependency>
   <dependency>
     <groupId>org.mockito</groupId>
     <artifactId>mockito-core</artifactId>
     <version>5.0.0</version>
     <scope>test</scope>
   </dependency>
   ```
3. **Setup Jest for JavaScript tests:**
   ```json
   {
     "devDependencies": {
       "jest": "^29.0.0",
       "jest-environment-jsdom": "^29.0.0"
     }
   }
   ```
4. **Create test data fixtures**

### Phase 2: Baseline Tests (Days 2-3)

**Goal:** Establish passing tests for CURRENT behavior before making fixes.

1. Write FuzzyMatchService tests (existing behavior)
2. Write confidence-engine.js tests (existing behavior)
3. Write recent-farmers.js tests (existing behavior)
4. Write detectInputType tests (existing behavior)
5. Document any bugs found during test writing

### Phase 3: Fix P1 Issues with TDD (Days 4-6)

For each P1 issue:

1. **Write failing test** that exposes the bug
2. **Make the fix** in the code
3. **Verify test passes**
4. **Run full regression suite**

**Example for Issue #1 (showRecentFarmers not propagated):**

```java
// Step 1: Failing test
@Test
void renderTemplate_shouldPassShowRecentFarmersToDataModel() {
    // Setup mock to return "true" for showRecentFarmers
    when(mockElement.getPropertyString("showRecentFarmers")).thenReturn("true");

    Map<String, Object> dataModel = new HashMap<>();
    element.renderTemplate(formData, dataModel);

    // This will FAIL initially because property isn't read
    assertTrue((Boolean) dataModel.get("showRecentFarmers"));
}

// Step 2: Fix in SmartSearchElement.java
String showRecentFarmersStr = getPropertyString("showRecentFarmers");
boolean showRecentFarmers = "true".equalsIgnoreCase(showRecentFarmersStr);
dataModel.put("showRecentFarmers", showRecentFarmers);

// Step 3: Test should now pass
```

### Phase 4: Fix P2-P4 Issues (Days 7-10)

Same TDD approach but lower urgency.

### Phase 5: E2E Test Suite (Day 11-12)

1. Setup Playwright
2. Write 5 critical path tests
3. Integrate into CI

---

## Test Infrastructure Setup

### Maven Test Configuration (pom.xml additions)

```xml
<build>
  <plugins>
    <!-- Surefire for unit tests -->
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-surefire-plugin</artifactId>
      <version>3.0.0</version>
      <configuration>
        <includes>
          <include>**/*Test.java</include>
        </includes>
      </configuration>
    </plugin>

    <!-- Failsafe for integration tests -->
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-failsafe-plugin</artifactId>
      <version>3.0.0</version>
      <executions>
        <execution>
          <goals>
            <goal>integration-test</goal>
            <goal>verify</goal>
          </goals>
        </execution>
      </executions>
      <configuration>
        <includes>
          <include>**/*IT.java</include>
        </includes>
      </configuration>
    </plugin>

    <!-- Frontend Maven Plugin for Jest -->
    <plugin>
      <groupId>com.github.eirslett</groupId>
      <artifactId>frontend-maven-plugin</artifactId>
      <version>1.12.1</version>
      <executions>
        <execution>
          <id>install node and npm</id>
          <goals><goal>install-node-and-npm</goal></goals>
          <configuration>
            <nodeVersion>v18.17.0</nodeVersion>
          </configuration>
        </execution>
        <execution>
          <id>npm install</id>
          <goals><goal>npm</goal></goals>
          <configuration>
            <arguments>install</arguments>
          </configuration>
        </execution>
        <execution>
          <id>jest tests</id>
          <goals><goal>npm</goal></goals>
          <phase>test</phase>
          <configuration>
            <arguments>test</arguments>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

### Jest Configuration (package.json)

```json
{
  "name": "joget-smart-search-tests",
  "scripts": {
    "test": "jest",
    "test:watch": "jest --watch",
    "test:coverage": "jest --coverage"
  },
  "jest": {
    "testEnvironment": "jsdom",
    "roots": ["<rootDir>/src/test/js"],
    "moduleFileExtensions": ["js"],
    "testMatch": ["**/*.test.js"],
    "setupFiles": ["<rootDir>/src/test/js/setup.js"],
    "collectCoverageFrom": [
      "src/main/resources/static/*.js"
    ]
  },
  "devDependencies": {
    "jest": "^29.7.0",
    "jest-environment-jsdom": "^29.7.0"
  }
}
```

### Jest Setup File (src/test/js/setup.js)

```javascript
// Mock localStorage
const localStorageMock = {
  getItem: jest.fn(),
  setItem: jest.fn(),
  removeItem: jest.fn(),
  clear: jest.fn(),
};
global.localStorage = localStorageMock;

// Mock console for cleaner test output
global.console = {
  ...console,
  log: jest.fn(),
  warn: jest.fn(),
  error: jest.fn(),
};

// Mock navigator.onLine
Object.defineProperty(navigator, 'onLine', {
  writable: true,
  value: true
});
```

---

## Specific Test Cases by Issue

### P1 Issues - Must Have Tests

| Issue | Test Type | Test File | Test Cases |
|-------|-----------|-----------|------------|
| #1 showRecentFarmers | Unit | SmartSearchElementTest.java | `renderTemplate_shouldReadShowRecentFarmers`, `renderTemplate_showRecentFarmers_defaultFalse` |
| #1 showRecentFarmers | Unit | config-flow.test.js | `showRecentFarmers should be in JS config` |
| #1 showRecentFarmers | E2E | farmer-search.spec.js | `recent farmers panel visibility based on config` |
| #2 maxRecentFarmers | Unit | SmartSearchElementTest.java | `renderTemplate_shouldReadMaxRecentFarmers` |
| #2 maxRecentFarmers | Unit | recent-farmers.test.js | `should respect configured maxItems` |
| #3 nationalIdPattern | Unit | SmartSearchElementTest.java | `renderTemplate_shouldPassNationalIdPattern` |
| #3 nationalIdPattern | Unit | smart-search-utils.test.js | `detectInputType should use configured pattern` |
| #4 phonePattern | Unit | SmartSearchElementTest.java | `renderTemplate_shouldPassPhonePattern` |
| #4 phonePattern | Unit | smart-search-utils.test.js | `detectInputType should use configured phone pattern` |

### P2 Issues - Should Have Tests

| Issue | Test Type | Test File | Test Cases |
|-------|-----------|-----------|------------|
| #5 CRITERIA_TYPES minLength | Unit | smart-search-utils.test.js | `partial_id criteria should use configured minLength` |
| #6 confidence partials | Unit | confidence-engine.test.js | `partialId validation should use config` |
| #7 score thresholds | Unit | smart-search-utils.test.js | `getScoreClass should use configured thresholds` |
| #8 too many results | Unit | smart-search-utils.test.js | `should use configured maxResultsWarning` |
| #9 search limit | Integration | API tests | `search limit should be configurable` |

### P3-P4 Issues - Nice to Have Tests

Lower priority, add if time permits.

---

## Regression Test Suite

### Purpose

Ensure that fixing one issue doesn't break existing functionality.

### Baseline Behavior Tests (Run Before ANY Fix)

```
REGRESSION_BASELINE.md

## Search Functionality
- [ ] National ID exact search returns correct farmer
- [ ] Phone exact search returns correct farmer
- [ ] Name + District search returns relevant results
- [ ] Name + Village search returns relevant results
- [ ] Partial ID search works (4+ digits)
- [ ] Fuzzy name matching works (misspellings)
- [ ] Soundex matching works (phonetic)

## UI Functionality
- [ ] Popup opens when clicking Search button
- [ ] Popup closes on X button
- [ ] Popup closes on overlay click
- [ ] Popup closes on Escape key
- [ ] Inline input detects ID type
- [ ] Inline input detects phone type
- [ ] Inline input detects name type
- [ ] Type badge displays correctly
- [ ] District dropdown populates
- [ ] Confidence bar updates on input
- [ ] Search button enables/disables correctly

## Selection Functionality
- [ ] Clicking farmer card selects farmer
- [ ] Hidden field receives correct value
- [ ] Display field shows farmer name
- [ ] Clear button clears selection
- [ ] Auto-select works for high-confidence single result

## Recent Farmers
- [ ] Recent farmers panel shows on dialog open
- [ ] Selecting recent farmer works
- [ ] Clear recent button works
- [ ] Recent farmers persist across sessions

## Criteria Builder
- [ ] Can add village criteria
- [ ] Can add community council criteria
- [ ] Can add cooperative criteria
- [ ] Can add partial ID criteria
- [ ] Can remove criteria
- [ ] Autocomplete works for villages

## Error Handling
- [ ] No results shows appropriate message
- [ ] Network error shows retry option
- [ ] Too many results shows warning

## Offline Support
- [ ] Offline indicator shows when disconnected
- [ ] Search disabled when offline
- [ ] Recent farmers accessible offline
- [ ] Cached statistics used when offline
```

### Automated Regression Suite

```javascript
// regression.test.js - Run after EVERY fix
describe('Regression Suite', () => {
  describe('Search Logic', () => {
    it('exact ID search still works')
    it('exact phone search still works')
    it('fuzzy name search still works')
    it('scoring algorithm unchanged for same inputs')
  })

  describe('Input Detection', () => {
    it('9-13 digit numbers detected as ID')
    it('8+ digit with + detected as phone')
    it('alphabetic input detected as name')
  })

  describe('Confidence Calculation', () => {
    it('100% for exact ID')
    it('100% for exact phone')
    it('increases with more criteria')
  })

  describe('Recent Farmers', () => {
    it('stores farmers to localStorage')
    it('retrieves farmers from localStorage')
    it('limits to max items')
  })
})
```

---

## Continuous Integration

### GitHub Actions Workflow (.github/workflows/test.yml)

```yaml
name: Test Suite

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 11
        uses: actions/setup-java@v3
        with:
          java-version: '11'
          distribution: 'temurin'

      - name: Run Java Unit Tests
        run: mvn test -Dtest="*Test"

      - name: Set up Node.js
        uses: actions/setup-node@v3
        with:
          node-version: '18'

      - name: Install JS dependencies
        run: npm install

      - name: Run Jest Tests
        run: npm test

      - name: Upload coverage
        uses: codecov/codecov-action@v3

  integration-tests:
    runs-on: ubuntu-latest
    needs: unit-tests
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 11
        uses: actions/setup-java@v3
        with:
          java-version: '11'
          distribution: 'temurin'

      - name: Run Integration Tests
        run: mvn verify -Dtest="*IT"

  e2e-tests:
    runs-on: ubuntu-latest
    needs: integration-tests
    steps:
      - uses: actions/checkout@v3

      - name: Install Playwright
        run: npx playwright install --with-deps

      - name: Run E2E Tests
        run: npx playwright test
```

### Pre-commit Hook

```bash
#!/bin/sh
# .git/hooks/pre-commit

echo "Running tests before commit..."

# Run Java tests
mvn test -q
if [ $? -ne 0 ]; then
  echo "Java tests failed. Commit aborted."
  exit 1
fi

# Run JS tests
npm test -- --passWithNoTests
if [ $? -ne 0 ]; then
  echo "JavaScript tests failed. Commit aborted."
  exit 1
fi

echo "All tests passed!"
```

---

## Manual Test Checklist

For issues that cannot be fully automated, use this checklist:

### Pre-Fix Verification
- [ ] Document current behavior
- [ ] Take screenshots if UI-related
- [ ] Note any existing bugs

### Post-Fix Verification
- [ ] Feature works as expected
- [ ] Run automated regression suite
- [ ] Manual check of related features
- [ ] Test in different browsers (Chrome, Firefox, Safari)
- [ ] Test in Joget form builder preview
- [ ] Test in actual Joget form

### Browser Compatibility Matrix

| Browser | Version | Test Status |
|---------|---------|-------------|
| Chrome | Latest | |
| Firefox | Latest | |
| Safari | Latest | |
| Edge | Latest | |

---

## Recommended Test Execution Order

### For Each Fix

1. **Write failing test** for the specific issue
2. **Run baseline regression tests** - all should pass
3. **Make the code fix**
4. **Run new test** - should now pass
5. **Run full regression suite** - all should still pass
6. **Manual smoke test** in Joget
7. **Commit with test**

### Weekly Full Test Run

```bash
# Full test suite
mvn clean verify
npm test
npx playwright test

# Generate coverage report
mvn jacoco:report
npm test -- --coverage
```

---

## Appendix: Test Data Fixtures

### test-data/farmers.json
```json
{
  "farmers": [
    {
      "id": "test-001",
      "nationalId": "1234567890123",
      "firstName": "John",
      "lastName": "Doe",
      "districtCode": "MAS",
      "districtName": "Maseru",
      "village": "Ha Matala",
      "phone": "+26622123456"
    },
    {
      "id": "test-002",
      "nationalId": "9876543210987",
      "firstName": "Jane",
      "lastName": "Smith",
      "districtCode": "LEI",
      "districtName": "Leribe",
      "village": "Hlotse",
      "phone": "+26622654321"
    }
  ]
}
```

### test-data/statistics.json
```json
{
  "total_farmers": 50000,
  "district_counts": {
    "MAS": 15000,
    "LEI": 8000,
    "BER": 6000
  },
  "surname_frequency": {
    "mokoena": 0.05,
    "nkosi": 0.04,
    "_default": 0.001
  },
  "effectiveness_factors": {
    "village": 0.85,
    "partial_id_4": 0.92,
    "partial_phone_4": 0.90
  }
}
```

---

## Summary

This testing strategy provides:

1. **Automated unit tests** for Java services and JavaScript modules
2. **Configuration flow verification** to catch propagation issues
3. **API integration tests** for endpoint validation
4. **E2E tests** for critical user flows
5. **Regression test suite** to prevent breaking changes
6. **CI pipeline** for continuous verification
7. **Manual checklist** for non-automatable scenarios

**Estimated effort:**
- Setup: 1 day
- Baseline tests: 2 days
- Issue-specific tests: 0.5 day per issue
- E2E tests: 2 days
- Total: ~2 weeks for comprehensive coverage

**Minimum viable testing:**
- Java unit tests for FuzzyMatchService
- Jest tests for confidence-engine.js and recent-farmers.js
- Config flow verification script
- Manual regression checklist
- Total: ~3-4 days
