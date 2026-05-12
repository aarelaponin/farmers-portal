# Joget Status Framework

Generic status-lifecycle framework for Joget DX 8.x plugins. Provides a single, reusable foundation for "can this status transition happen?" plus auditable execution of the transition.

Adopted from the proven pattern in `gam-plugins/gam-framework`, generalised so any consuming plugin can register its own entity types and status enums without duplicating the validate-load-store-audit machinery.

## What's in the box

| Component | Purpose |
|---|---|
| `api.EntityType` (interface) | Marker for entity types; consumer declares `getTableName()` |
| `api.Status` (interface) | Marker for status values; consumer declares `getCode()` + `getLabel()` |
| `api.InvalidTransitionException` | Thrown when a transition is not allowed |
| `api.TransitionAuditEntry` | Immutable DTO; renders to a `FormRow` for `audit_log` table |
| `core.StatusFramework` | Static API: `register`, `canTransition`, `getValidTransitions`, `transition`, `fromCode` |
| `forms/audit_log.json` | Joget form definition that owns the `app_fd_audit_log` schema |

## Usage

### 1. Define your entity types and statuses as enums

```java
public enum BankEntityType implements EntityType {
    STATEMENT("bank_statement"),
    BANK_TRX("bank_total_trx");

    private final String tableName;
    BankEntityType(String t) { this.tableName = t; }
    @Override public String getTableName() { return tableName; }
}

public enum BankStatus implements Status {
    NEW("new", "New"),
    IMPORTING("importing", "Importing"),
    IMPORTED("imported", "Imported"),
    ERROR("error", "Error");

    private final String code, label;
    BankStatus(String c, String l) { this.code = c; this.label = l; }
    @Override public String getCode()  { return code; }
    @Override public String getLabel() { return label; }
}
```

### 2. Register the transitions in your plugin's Activator

```java
public class Activator implements BundleActivator {
    @Override public void start(BundleContext ctx) {
        Map<Status, Set<Status>> tx = new LinkedHashMap<>();
        tx.put(BankStatus.NEW,       Set.of(BankStatus.IMPORTING));
        tx.put(BankStatus.IMPORTING, Set.of(BankStatus.IMPORTED, BankStatus.ERROR));
        tx.put(BankStatus.IMPORTED,  Set.of());
        tx.put(BankStatus.ERROR,     Set.of(BankStatus.NEW));
        StatusFramework.register(BankEntityType.STATEMENT, tx, Set.of(BankStatus.NEW));
    }
}
```

### 3. Use it from anywhere in the plugin

```java
// Pure validation
if (StatusFramework.canTransition(STATEMENT, currentStatus, IMPORTING)) {
    // ... safe to proceed
}

// Execute with audit
StatusFramework.transition(dao, STATEMENT, recordId,
        BankStatus.IMPORTING, "statement-importer", "CSV upload started");
```

Two side effects per `transition()` call:

1. The entity row's `c_status` column is updated to the new code.
2. One row is appended to `app_fd_audit_log` capturing entity, from-status, to-status, who, why, and ISO-8601 timestamp.

If the transition is not allowed by the registered map, `InvalidTransitionException` is thrown and **neither** write happens.

### 4. Import the audit_log form into your Joget app

The first plugin to be installed must also import `src/main/resources/forms/audit_log.json` into Joget's App Composer so the underlying `app_fd_audit_log` table is created. Subsequent plugins that depend on the framework reuse the same table.

## Maven dependency

```xml
<dependency>
    <groupId>global.govstack</groupId>
    <artifactId>joget-status-framework</artifactId>
    <version>8.1-SNAPSHOT</version>
</dependency>
```

For OSGi plugin consumers, embed it via your plugin's `maven-bundle-plugin` `Embed-Dependency` directive, same pattern as `wf-activator` already does for gson.

## Testing

```bash
mvn test
```

The included `StatusFrameworkTest` (25 tests) covers registration, validation, audit row shape, edge cases (null current status, terminal states, custom table override, re-registration). Use it as the regression net when changing framework internals.

## Design notes

- **Codes are globally unique.** When you call `StatusFramework.fromCode("new")`, the framework looks up across all registered entities. Two entities sharing a code must refer to the same enum constant; if not, the second registration's value wins.
- **Re-registration is allowed.** Calling `register()` twice with the same `EntityType` replaces the previous transition map.
- **No DDL.** The framework never executes `ALTER TABLE` — Joget owns the schema. Add new columns to `audit_log.json` and re-import; never modify the table directly.
- **Thread-safe registry.** Backed by `ConcurrentHashMap`; safe to call from multiple plugin Activators concurrently.

## Files

```
joget-status-framework/
├── pom.xml
├── README.md                                    (this file)
└── src/
    ├── main/
    │   ├── java/global/govstack/statusframework/
    │   │   ├── api/
    │   │   │   ├── EntityType.java              (interface)
    │   │   │   ├── Status.java                  (interface)
    │   │   │   ├── InvalidTransitionException.java
    │   │   │   └── TransitionAuditEntry.java
    │   │   └── core/
    │   │       └── StatusFramework.java         (the engine)
    │   └── resources/forms/audit_log.json
    └── test/java/global/govstack/statusframework/core/
        └── StatusFrameworkTest.java             (25 tests)
```
