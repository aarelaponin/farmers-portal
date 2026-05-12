# Configuration Synchronization Guide

## Overview

DocSubmitter is a client of the Registration Building Block and must synchronize its configuration from the **ProcessingServer** (receiver) to ensure compatibility. The receiver is the source of truth for service metadata.

## Version Compatibility

| Metadata Version | DocSubmitter | ProcessingServer | Status | Notes |
|------------------|--------------|------------------|--------|-------|
| 1.0.0            | 8.1.0       | 8.1.0           | ✅ Current | Initial release |

## How Version Checking Works

When DocSubmitter sends a request, it includes the `metadataVersion` in the JSON payload:

```json
{
  "serviceId": "farmers_registry",
  "metadataVersion": "1.0.0",
  "timestamp": "2025-10-28T00:00:00Z",
  ...
}
```

The ProcessingServer validates this version:

- **Compatible**: Major.minor match (e.g., 1.0.x ↔ 1.0.y) ✅
- **Warning**: Version mismatch logged, processing continues ⚠️
- **Incompatible**: Major version mismatch (e.g., 1.x ↔ 2.x) ❌

### Check Your Configuration Version

The version is defined in `src/main/resources/docs-metadata/services.yml`:

```yaml
service:
  id: farmers_registry
  name: "Farmers Registry Service"
  version: "1.0"
  govstackVersion: "1.0"
  metadataVersion: "1.0.0"  # ← This is sent with every request
  metadataCompatibility: "1.0.x"
  lastUpdated: "2025-10-28"
```

## When to Sync Configuration

Sync your configuration when you see:

1. **Version mismatch warnings** in ProcessingServer logs
2. **400 Bad Request** responses from the API
3. **Data not appearing** in receiver database
4. **After receiver upgrade** to new version
5. **Field validation errors** in logs

## Manual Sync Process (Current Method)

### Step 1: Get Latest services.yml from ProcessingServer

```bash
# Copy from ProcessingServer source
cp /path/to/processing-server/src/main/resources/docs-metadata/services.yml \
   /path/to/doc-submitter/src/main/resources/docs-metadata/services.yml
```

Or request it from the development team managing the ProcessingServer.

### Step 2: Verify Changes

Review what changed in the services.yml:

```bash
# Compare versions
diff services.yml.old services.yml

# Key things to check:
# - metadataVersion changed?
# - New fields added?
# - Field types/transforms changed?
# - Grid configurations changed?
```

### Step 3: Rebuild Plugin

```bash
cd doc-submitter
mvn clean package -Dmaven.test.skip=true
```

### Step 4: Deploy to Sender Joget

```bash
# Copy to sender Joget plugins directory
cp target/doc-submitter-8.1-SNAPSHOT.jar \
   /path/to/sender-joget/wflow/app_plugins/

# Restart Joget or reload plugins
```

### Step 5: Test Submission

1. Submit a test form from sender Joget
2. Check sender logs for metadataVersion in payload
3. Check receiver logs for version compatibility check
4. Verify data appears in receiver database

Expected log output:

```
# Sender logs
INFO - Added metadataVersion to payload: 1.0.0

# Receiver logs
INFO - Metadata version check passed. Client: 1.0.0, Server: 1.0.0
```

## Automatic Sync (Future - See TODO.md)

In a future release, DocSubmitter will fetch configuration from the receiver's metadata API:

```
GET /jw/api/govstack/registration/services/{serviceId}/metadata
```

This will enable:
- Automatic configuration download
- Version compatibility warnings in UI
- Self-service configuration updates
- Real-time sync status

## Configuration Files to Sync

### Primary: services.yml
**Location**: `src/main/resources/docs-metadata/services.yml`

Contains:
- Service metadata (id, version, metadataVersion)
- Form mappings (Joget → GovStack field mappings)
- Grid configurations
- Transform types
- Master data field lists

**Sync Frequency**: After any ProcessingServer configuration change

### Optional: form_structure.yaml
**Location**: `src/main/resources/docs-metadata/form_structure.yaml`

Contains:
- Database schema information
- Form-to-table mappings
- Foreign key relationships

**Sync Frequency**: When database schema changes

## Breaking Changes Policy

### Understanding Version Numbers

**Format**: `major.minor.patch` (e.g., 1.0.0)

**Compatibility**:
- Same major.minor = Compatible (1.0.0 ↔ 1.0.5) ✅
- Different major = Breaking (1.x ↔ 2.x) ❌
- Different minor = May have new fields (1.0.x vs 1.1.x) ⚠️

### What Triggers Version Changes

**Major Version (Breaking Changes)**:
- Required fields added or removed
- Field names changed
- Field data types changed
- Grid/array structures changed
- API endpoint changes

**Action Required**: Must update configuration before using

**Minor Version (New Features)**:
- Optional fields added
- New transform types added
- Validation rules relaxed
- Documentation updates

**Action Required**: Update when convenient, backward compatible

**Patch Version (Bug Fixes)**:
- Field mapping corrections
- Transform logic fixes
- Documentation fixes

**Action Required**: Optional, but recommended

## Troubleshooting

### Warning: Metadata Version Mismatch

**Symptom**:
```
WARN - Metadata version mismatch detected. Client: 1.0.0, Server: 1.0.1
```

**Impact**: Processing continues but may have issues with new fields

**Fix**:
1. Get latest `services.yml` from ProcessingServer
2. Update `metadataVersion` in your services.yml
3. Rebuild and redeploy DocSubmitter

### Error: Bad Request (400)

**Symptom**: `ERROR - API call failed: Bad Request`

**Possible Causes**:
- Missing required fields (configuration out of sync)
- Invalid field format
- Version incompatibility

**Fix**:
1. Check receiver logs for specific validation errors
2. Sync configuration from ProcessingServer
3. Verify field mappings match receiver expectations

### Data Not Saving

**Symptom**: HTTP 200 but data missing in receiver database

**Possible Causes**:
- Field mappings point to wrong database columns
- Grid foreign key relationships incorrect
- Transform types not matching data format

**Fix**:
1. Sync latest `services.yml` from ProcessingServer
2. Check receiver logs for validation warnings
3. Verify grid parent field references

### Validation Errors for Grid Fields

**Symptom**: `ERROR - Column not found: c_cropManagement`

**Cause**: Grid fields don't have physical columns (they're references to child tables)

**Fix**: This should be handled correctly in ProcessingServer v8.1.0+. If you see this, the receiver needs to be updated.

## Best Practices

1. **Version Control**: Keep `services.yml` in version control
2. **Change Log**: Document what changed when updating configuration
3. **Test Environment**: Test configuration changes in dev/staging first
4. **Coordination**: Coordinate updates with receiver team
5. **Monitoring**: Monitor logs after configuration updates

## Development Workflow

```
1. ProcessingServer team updates services.yml
   ↓
2. Increment metadataVersion
   ↓
3. Test ProcessingServer with new config
   ↓
4. Share updated services.yml with DocSubmitter team
   ↓
5. DocSubmitter team updates their services.yml
   ↓
6. Rebuild DocSubmitter plugin
   ↓
7. Test end-to-end submission
   ↓
8. Deploy to production (receiver first, then sender)
```

## Support

**Configuration Issues**:
- Review logs: `/path/to/sender-joget/logs/joget.log`
- Check services.yml syntax (YAML validation)
- Verify metadataVersion matches receiver

**Communication**:
- Contact ProcessingServer team for latest configuration
- Report compatibility issues
- Request new features or field mappings

## Related Documentation

- [README.md](README.md) - Plugin overview and setup
- [CLAUDE.md](CLAUDE.md) - Development guidelines
- [TODO.md](TODO.md) - Future improvements
- [GovStack Registration BB](https://registration.govstack.global/) - Specification
