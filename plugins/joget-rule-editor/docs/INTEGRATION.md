# Integration Guide

This guide covers integrating the Joget Rule Editor with different systems and platforms beyond Joget.

## Table of Contents

1. [Integration Overview](#integration-overview)
2. [REST API Integration](#rest-api-integration)
3. [Java Library Integration](#java-library-integration)
4. [Standalone Web Integration](#standalone-web-integration)
5. [Custom Backend Integration](#custom-backend-integration)
6. [Database Integration](#database-integration)
7. [Workflow Integration](#workflow-integration)
8. [Use Case Examples](#use-case-examples)

---

## Integration Overview

### Integration Levels

| Level | Description | Use Case |
|-------|-------------|----------|
| **REST API** | HTTP endpoints for validation/compilation | Any HTTP client, microservices |
| **Java Library** | Direct Java class usage | Java/JVM applications |
| **Web Component** | Standalone HTML/JS editor | Any web page |
| **Database** | SQL query execution | Eligibility engines |

### Architecture Patterns

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Your Application                                 │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐            │
│  │  UI Editor   │     │  Backend     │     │  Database    │            │
│  │  (optional)  │────▶│  Service     │────▶│  Engine      │            │
│  └──────────────┘     └──────────────┘     └──────────────┘            │
│         │                    │                    │                     │
│         ▼                    ▼                    ▼                     │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                     Joget Rule Editor                            │  │
│  │  ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐          │  │
│  │  │ Editor  │   │  REST   │   │  ANTLR  │   │   SQL   │          │  │
│  │  │   UI    │   │  API    │   │  Parser │   │  Output │          │  │
│  │  └─────────┘   └─────────┘   └─────────┘   └─────────┘          │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

**Note**: The parser uses ANTLR 4 for robust grammar parsing. Java 17+ is required.

---

## REST API Integration

### Authentication

All API endpoints require Joget API Builder authentication:

```http
POST /jw/api/erel/rules/validate
Content-Type: application/json
api_id: YOUR_API_ID
api_key: YOUR_API_KEY
```

### Endpoint Reference

#### Validate Script

```http
POST /jw/api/erel/rules/validate
```

**Request:**
```json
{
  "script": "RULE \"Test\" TYPE: INCLUSION WHEN age >= 18",
  "scopeCode": "FARMER_ELIGIBILITY"
}
```

**Response (Success):**
```json
{
  "valid": true,
  "ruleCount": 1,
  "rules": [
    {
      "ruleName": "Test",
      "ruleCode": "TEST",
      "ruleType": "INCLUSION",
      "conditionCount": 1
    }
  ],
  "errors": [],
  "warnings": []
}
```

**Response (Error):**
```json
{
  "valid": false,
  "ruleCount": 0,
  "rules": [],
  "errors": [
    {
      "line": 1,
      "column": 15,
      "message": "Expected rule type (INCLUSION, EXCLUSION, PRIORITY, or BONUS)",
      "severity": "ERROR"
    }
  ],
  "warnings": []
}
```

#### Compile to SQL

```http
POST /jw/api/erel/rules/compile
```

**Request:**
```json
{
  "script": "RULE \"Adult\" TYPE: INCLUSION WHEN age >= 18\nRULE \"Low Income\" TYPE: INCLUSION WHEN income < 50000",
  "scopeCode": "FARMER_ELIGIBILITY",
  "rulesetCode": "RS-ELIG-001"
}
```

**Response:**
```json
{
  "success": true,
  "rulesetCode": "RS-ELIG-001",
  "scopeCode": "FARMER_ELIGIBILITY",
  "statistics": {
    "totalRules": 2,
    "inclusionRules": 2,
    "exclusionRules": 0,
    "bonusRules": 0
  },
  "sql": {
    "eligibilityWhereClause": "(f.c_age >= 18)\n    AND (f.c_income < 50000)",
    "exclusionWhereClause": null,
    "eligibilityCheckQuery": "SELECT f.id, ... WHERE (f.c_age >= 18) AND (f.c_income < 50000)",
    "scoringQuery": "SELECT f.id, CASE WHEN ... END AS score_adult, ...",
    "fullEligibilityQuery": "SELECT f.id, ..."
  },
  "compiledRules": [
    {
      "ruleName": "Adult",
      "ruleCode": "ADULT",
      "ruleType": "INCLUSION",
      "mandatory": false,
      "whereClause": "f.c_age >= 18",
      "selectExpression": "CASE WHEN f.c_age >= 18 THEN 1 ELSE 0 END AS rule_adult",
      "usedFields": ["age"]
    }
  ]
}
```

#### Get Fields

```http
GET /jw/api/erel/rules/fields?scopeCode=FARMER_ELIGIBILITY
```

**Response:**
```json
{
  "scopeCode": "FARMER_ELIGIBILITY",
  "count": 25,
  "categories": [
    {
      "category": "DEMOGRAPHIC",
      "fields": [
        {
          "fieldId": "age",
          "fieldLabel": "Age",
          "fieldType": "NUMBER",
          "isGrid": false,
          "operators": ["=", "!=", ">", ">=", "<", "<=", "BETWEEN"]
        },
        {
          "fieldId": "gender",
          "fieldLabel": "Gender",
          "fieldType": "TEXT",
          "isGrid": false,
          "lookupValues": ["male", "female"]
        }
      ]
    },
    {
      "category": "HOUSEHOLD",
      "fields": [
        {
          "fieldId": "householdMembers",
          "fieldLabel": "Household Members",
          "fieldType": "GRID",
          "isGrid": true,
          "aggregations": ["COUNT", "SUM", "AVG"]
        }
      ]
    }
  ]
}
```

### Client Examples

#### Python

```python
import requests

class RulesClient:
    def __init__(self, base_url, api_id, api_key):
        self.base_url = base_url
        self.headers = {
            'Content-Type': 'application/json',
            'api_id': api_id,
            'api_key': api_key
        }

    def validate(self, script, scope_code='FARMER_ELIGIBILITY'):
        response = requests.post(
            f'{self.base_url}/jw/api/erel/rules/validate',
            json={'script': script, 'scopeCode': scope_code},
            headers=self.headers
        )
        return response.json()

    def compile(self, script, ruleset_code=None):
        data = {'script': script}
        if ruleset_code:
            data['rulesetCode'] = ruleset_code

        response = requests.post(
            f'{self.base_url}/jw/api/erel/rules/compile',
            json=data,
            headers=self.headers
        )
        return response.json()

# Usage
client = RulesClient('http://localhost:8080', 'API-xxx', 'secret')
result = client.validate('RULE "Test" TYPE: INCLUSION WHEN age >= 18')
print(f"Valid: {result['valid']}")
```

#### JavaScript/Node.js

```javascript
class RulesClient {
    constructor(baseUrl, apiId, apiKey) {
        this.baseUrl = baseUrl;
        this.headers = {
            'Content-Type': 'application/json',
            'api_id': apiId,
            'api_key': apiKey
        };
    }

    async validate(script, scopeCode = 'FARMER_ELIGIBILITY') {
        const response = await fetch(`${this.baseUrl}/jw/api/erel/rules/validate`, {
            method: 'POST',
            headers: this.headers,
            body: JSON.stringify({ script, scopeCode })
        });
        return response.json();
    }

    async compile(script, rulesetCode = null) {
        const response = await fetch(`${this.baseUrl}/jw/api/erel/rules/compile`, {
            method: 'POST',
            headers: this.headers,
            body: JSON.stringify({ script, rulesetCode })
        });
        return response.json();
    }
}

// Usage
const client = new RulesClient('http://localhost:8080', 'API-xxx', 'secret');
const result = await client.validate('RULE "Test" TYPE: INCLUSION WHEN age >= 18');
console.log('Valid:', result.valid);
```

#### cURL

```bash
# Validate
curl -X POST "http://localhost:8080/jw/api/erel/rules/validate" \
  -H "Content-Type: application/json" \
  -H "api_id: API-xxx" \
  -H "api_key: secret" \
  -d '{"script": "RULE \"Test\" TYPE: INCLUSION WHEN age >= 18"}'

# Compile
curl -X POST "http://localhost:8080/jw/api/erel/rules/compile" \
  -H "Content-Type: application/json" \
  -H "api_id: API-xxx" \
  -H "api_key: secret" \
  -d '{"script": "RULE \"Test\" TYPE: INCLUSION WHEN age >= 18"}'
```

---

## Java Library Integration

### Direct Parser Usage

You can use the parser classes directly without the REST API:

```java
import global.govstack.ruleeditor.parser.RuleScriptParser;
import global.govstack.ruleeditor.model.ValidationResult;
import global.govstack.ruleeditor.model.Rule;

public class EligibilityService {

    public ValidationResult validateRules(String script) {
        RuleScriptParser parser = new RuleScriptParser();
        return parser.parse(script);
    }

    public boolean isValidScript(String script) {
        ValidationResult result = validateRules(script);
        return result.isValid();
    }

    public List<Rule> parseRules(String script) {
        ValidationResult result = validateRules(script);
        if (!result.isValid()) {
            throw new IllegalArgumentException("Invalid script: " + result.getErrors());
        }
        return result.getRules();
    }
}
```

### Compiler Integration

```java
import global.govstack.ruleeditor.compiler.RuleScriptCompiler;
import global.govstack.ruleeditor.compiler.FieldMapping;
import global.govstack.ruleeditor.compiler.CompiledRuleset;

public class SQLGenerator {

    private final FieldMapping fieldMapping;

    public SQLGenerator() {
        // Create custom field mapping for your database schema
        this.fieldMapping = createFieldMapping();
    }

    private FieldMapping createFieldMapping() {
        FieldMapping mapping = new FieldMapping();

        // Set main table
        mapping.setMainTable("beneficiaries", "b");

        // Map Rules Script fields to database columns
        mapping.addField("age", "b.age");
        mapping.addField("income", "b.annual_income");
        mapping.addField("district", "b.district_code");
        mapping.addField("farmSize", "b.farm_hectares");

        // Map grid fields (for subforms/child tables)
        mapping.addGrid("householdMembers", new GridInfo(
            "household_members",  // table name
            "hm",                 // alias
            "hm.beneficiary_id = b.id"  // correlation
        ));

        return mapping;
    }

    public CompiledRuleset compile(List<Rule> rules, String rulesetCode) {
        RuleScriptCompiler compiler = new RuleScriptCompiler(fieldMapping);
        return compiler.compile(rules, rulesetCode, "ELIGIBILITY");
    }

    public String generateEligibilitySQL(String rulesScript) {
        // Parse
        RuleScriptParser parser = new RuleScriptParser();
        ValidationResult result = parser.parse(rulesScript);

        if (!result.isValid()) {
            throw new IllegalArgumentException("Invalid script");
        }

        // Compile
        CompiledRuleset compiled = compile(result.getRules(), "RS-001");

        return compiled.getEligibilityCheckQuery();
    }
}
```

### Maven Dependency (Local)

If you want to use the library in another project:

```xml
<dependency>
    <groupId>global.govstack</groupId>
    <artifactId>joget-rule-editor</artifactId>
    <version>8.1-SNAPSHOT</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/lib/joget-rule-editor-8.1-SNAPSHOT.jar</systemPath>
</dependency>
```

---

## Standalone Web Integration

### Minimal HTML Setup

```html
<!DOCTYPE html>
<html>
<head>
    <title>Rule Editor</title>
    <!-- CodeMirror -->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.15/codemirror.min.css">
    <script src="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.15/codemirror.min.js"></script>

    <!-- EREL (copy from plugin) -->
    <link rel="stylesheet" href="jre-editor.css">
    <script src="jre-mode.js"></script>
    <script src="jre-editor.js"></script>
</head>
<body>
    <div id="editor"></div>
    <input type="hidden" id="scriptField" name="script">

    <script>
        var editor = JREEditor.init('editor', {
            apiBase: 'http://your-joget-server/jw/api/erel/erel',
            apiId: 'YOUR_API_ID',
            apiKey: 'YOUR_API_KEY',
            hiddenFieldId: 'scriptField',
            height: '400px',
            showSaveButton: false,  // Handle save yourself
            onValidate: function(result) {
                console.log('Validation result:', result);
            }
        });
    </script>
</body>
</html>
```

### Custom Validation Backend

If you want to use a different backend (not Joget):

```javascript
// Override API call behavior
var JREEditorCustom = {
    init: function(containerId, options) {
        // Custom validation function
        options.customValidate = function(script, callback) {
            fetch('/your-api/validate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ script: script })
            })
            .then(r => r.json())
            .then(result => callback(null, result))
            .catch(err => callback(err));
        };

        return JREEditor.init(containerId, options);
    }
};
```

### React Component Wrapper

```jsx
import React, { useEffect, useRef } from 'react';

function JREEditorReact({ value, onChange, onValidate, config }) {
    const containerRef = useRef(null);
    const editorRef = useRef(null);

    useEffect(() => {
        if (containerRef.current && !editorRef.current) {
            editorRef.current = JREEditor.init(containerRef.current.id, {
                ...config,
                onValidate: (result) => {
                    if (onValidate) onValidate(result);
                }
            });

            if (value) {
                editorRef.current.setValue(value);
            }
        }

        return () => {
            // Cleanup if needed
        };
    }, []);

    useEffect(() => {
        if (editorRef.current && value !== editorRef.current.getValue()) {
            editorRef.current.setValue(value || '');
        }
    }, [value]);

    return (
        <div
            id={`jre-editor-${Math.random().toString(36).substr(2, 9)}`}
            ref={containerRef}
        />
    );
}

// Usage
<JREEditorReact
    value={script}
    onChange={setScript}
    onValidate={handleValidate}
    config={{
        apiBase: '/api/erel',
        height: '400px'
    }}
/>
```

---

## Custom Backend Integration

### Creating a Non-Joget Backend

If you want to run the Rules Script parser outside Joget:

```java
// Spring Boot REST Controller
@RestController
@RequestMapping("/api/erel")
public class RulesController {

    @PostMapping("/validate")
    public ResponseEntity<ValidationResult> validate(@RequestBody ValidateRequest request) {
        RuleScriptParser parser = new RuleScriptParser();
        ValidationResult result = parser.parse(request.getScript());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/compile")
    public ResponseEntity<CompiledRuleset> compile(@RequestBody CompileRequest request) {
        // Parse
        RuleScriptParser parser = new RuleScriptParser();
        ValidationResult parseResult = parser.parse(request.getScript());

        if (!parseResult.isValid()) {
            return ResponseEntity.badRequest().body(null);
        }

        // Compile
        FieldMapping mapping = createFieldMapping();
        RuleScriptCompiler compiler = new RuleScriptCompiler(mapping);
        CompiledRuleset compiled = compiler.compile(
            parseResult.getRules(),
            request.getRulesetCode(),
            request.getScopeCode()
        );

        return ResponseEntity.ok(compiled);
    }

    @GetMapping("/fields")
    public ResponseEntity<FieldDefinitions> getFields(@RequestParam String scopeCode) {
        // Return field definitions from your data source
        return ResponseEntity.ok(fieldService.getFields(scopeCode));
    }
}
```

### Express.js Backend (Using Java)

```javascript
const express = require('express');
const { spawn } = require('child_process');

const app = express();
app.use(express.json());

app.post('/api/rules/validate', async (req, res) => {
    const { script } = req.body;

    // Call Java parser via command line or JNI
    const result = await callJavaParser(script);
    res.json(result);
});

function callJavaParser(script) {
    return new Promise((resolve, reject) => {
        const java = spawn('java', [
            '-cp', 'joget-rule-editor.jar',
            'global.govstack.ruleeditor.cli.ValidateCommand',
            script
        ]);

        let output = '';
        java.stdout.on('data', (data) => output += data);
        java.on('close', () => resolve(JSON.parse(output)));
    });
}
```

---

## Database Integration

### Executing Compiled SQL

```java
public class EligibilityEngine {

    private final DataSource dataSource;

    public List<EligibleBeneficiary> checkEligibility(String rulesetCode) {
        // Load compiled ruleset
        CompiledRuleset ruleset = rulesetRepository.load(rulesetCode);

        // Execute eligibility query
        String sql = ruleset.getFullEligibilityQuery();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            List<EligibleBeneficiary> results = new ArrayList<>();
            while (rs.next()) {
                EligibleBeneficiary b = new EligibleBeneficiary();
                b.setId(rs.getString("id"));
                b.setEligible(rs.getBoolean("is_eligible"));
                b.setScore(rs.getInt("total_score"));
                results.add(b);
            }
            return results;
        }
    }

    public void runEligibilityBatch(String programCode) {
        // Get program rules
        CompiledRuleset ruleset = rulesetService.compileForProgram(programCode);

        // Execute in batch
        String updateSql = String.format("""
            UPDATE beneficiaries b
            SET
                eligibility_status = CASE WHEN (%s) THEN 'ELIGIBLE' ELSE 'INELIGIBLE' END,
                eligibility_score = (%s),
                evaluated_at = NOW()
            WHERE b.program_code = ?
            """,
            ruleset.getEligibilityWhereClause(),
            ruleset.getScoringExpression()
        );

        jdbcTemplate.update(updateSql, programCode);
    }
}
```

### MySQL/MariaDB Considerations

```java
// The compiler generates MySQL-compatible SQL
// For other databases, you may need to adjust:

public class DatabaseAdapter {

    public String adaptSQL(String sql, DatabaseType dbType) {
        switch (dbType) {
            case POSTGRESQL:
                // Convert boolean syntax
                sql = sql.replace("'true'", "true");
                sql = sql.replace("'false'", "false");
                break;

            case ORACLE:
                // Convert LIMIT to ROWNUM
                // Convert boolean to NUMBER
                break;

            case SQLSERVER:
                // Convert LIMIT to TOP
                // Convert boolean syntax
                break;
        }
        return sql;
    }
}
```

---

## Workflow Integration

### BPMN Process Integration

```java
// Camunda/Flowable integration
@Component
public class EligibilityTask implements JavaDelegate {

    @Autowired
    private EligibilityService eligibilityService;

    @Override
    public void execute(DelegateExecution execution) {
        String applicantId = (String) execution.getVariable("applicantId");
        String programCode = (String) execution.getVariable("programCode");

        // Check eligibility
        EligibilityResult result = eligibilityService.checkEligibility(
            applicantId, programCode
        );

        // Set process variables
        execution.setVariable("isEligible", result.isEligible());
        execution.setVariable("eligibilityScore", result.getScore());
        execution.setVariable("failedRules", result.getFailedRules());
    }
}
```

### Event-Driven Integration

```java
// Kafka/RabbitMQ integration
@Service
public class EligibilityEventHandler {

    @KafkaListener(topics = "applications.submitted")
    public void handleApplicationSubmitted(ApplicationEvent event) {
        // Get applicable rules
        CompiledRuleset rules = rulesetService.getRulesForProgram(event.getProgramCode());

        // Check eligibility
        EligibilityResult result = eligibilityEngine.evaluate(
            event.getApplicantId(), rules
        );

        // Publish result
        kafkaTemplate.send("eligibility.evaluated", new EligibilityEvent(
            event.getApplicantId(),
            result.isEligible(),
            result.getScore()
        ));
    }
}
```

---

## Use Case Examples

### Subsidy Program Eligibility

```java
public class SubsidyProgramService {

    public EligibilityResult checkSubsidyEligibility(String farmerId, String programCode) {
        // Load program rules
        String rulesScript = programRepository.getRuleScript(programCode);

        // Parse and compile
        RuleScriptParser parser = new RuleScriptParser();
        ValidationResult parsed = parser.parse(rulesScript);

        if (!parsed.isValid()) {
            throw new InvalidRulesException(parsed.getErrors());
        }

        // Create field mapping for farmer database
        FieldMapping mapping = FieldMapping.createFarmerEligibilityMapping();

        // Compile to SQL
        RuleScriptCompiler compiler = new RuleScriptCompiler(mapping);
        CompiledRuleset compiled = compiler.compile(
            parsed.getRules(),
            programCode,
            "FARMER_ELIGIBILITY"
        );

        // Execute eligibility check
        return executeEligibilityQuery(farmerId, compiled);
    }
}
```

### Social Protection Targeting

```java
public class SocialProtectionService {

    public TargetingResult evaluateBeneficiary(String beneficiaryId) {
        // Load active rulesets
        List<CompiledRuleset> rulesets = rulesetService.getActiveRulesets("SOCIAL_PROTECTION");

        TargetingResult result = new TargetingResult(beneficiaryId);

        for (CompiledRuleset ruleset : rulesets) {
            // Check each program's eligibility
            boolean eligible = executeEligibilityCheck(beneficiaryId, ruleset);
            int score = calculateScore(beneficiaryId, ruleset);

            result.addProgramResult(new ProgramResult(
                ruleset.getRulesetCode(),
                eligible,
                score
            ));
        }

        return result;
    }
}
```

---

## Next Steps

- [DEVELOPER.md](./DEVELOPER.md) - General developer guide
- [EXTENDING-GRAMMAR.md](./EXTENDING-GRAMMAR.md) - Adding new language features
- [UI-CUSTOMIZATION.md](./UI-CUSTOMIZATION.md) - Styling the editor
