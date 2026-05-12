# Configuration Synchronization Guide

## Overview

The Registration Building Block (ProcessingServer) is the **source of truth** for service metadata configuration. DocSubmitter clients should synchronize their configuration from the receiver to ensure compatibility.

**Design Philosophy**: These plugins are **transport-layer only**. They validate structure/metadata compliance, NOT business data. All functional validation is done in Joget forms using native validation tools.

## Version Compatibility

| Metadata Version | DocSubmitter Min | ProcessingServer Min | Breaking Changes | Notes |
|------------------|------------------|----------------------|------------------|-------|
| 1.0.0            | 8.1.0           | 8.1.0               | Initial release  | Current version |

## Version Checking

The ProcessingServer automatically checks metadata version compatibility when receiving requests:

- **Compatible**: Client and server have matching major.minor versions (e.g., 1.0.x ↔ 1.0.y)
- **Warning**: Version mismatch logged but processing continues (backward compatibility)
- **Incompatible**: Major version difference (e.g., 1.x ↔ 2.x) - may cause processing failures

### Version Check Logs

```
INFO  - Metadata version check passed. Client: 1.0.0, Server: 1.0.0
WARN  - Metadata version mismatch detected. Client: 1.0.1, Server: 1.0.0
```

## When to Update Configuration

Update your DocSubmitter configuration when:

1. **After receiver deploys new service definition** - Field mappings may have changed
2. **When field mappings change** - New fields added or existing fields modified
3. **When validation rules change** - Business logic updates
4. **When grid/array structures change** - Subform schema modifications
5. **Major version upgrade** - Breaking changes require resync

## Manual Sync Process (Current)

### Step 1: Copy services.yml from ProcessingServer to DocSubmitter

```bash
# On development machine
cp /path/to/processing-server/src/main/resources/docs-metadata/services.yml \
   /path/to/doc-submitter/src/main/resources/docs-metadata/services.yml
```

### Step 2: Verify Metadata Version Matches

Check that `metadataVersion` is consistent:

```yaml
service:
  id: farmers_registry
  name: "Farmers Registry Service"
  version: "1.0"
  govstackVersion: "1.0"
  metadataVersion: "1.0.0"  # ← Must match on both sides
  metadataCompatibility: "1.0.x"
  lastUpdated: "2025-10-28"
```

### Step 3: Test with Sample Data

```bash
# Build DocSubmitter with new config
cd doc-submitter
mvn clean package -Dmaven.test.skip=true

# Deploy to sender Joget
cp target/doc-submitter-8.1-SNAPSHOT.jar \
   /path/to/sender-joget/wflow/app_plugins/

# Test submission and check logs
tail -f /path/to/receiver-joget/logs/joget.log | grep "Metadata version"
```

### Step 4: Deploy

Once verified, deploy both plugins to production.

## Automatic Sync (Future - See TODO.md)

In a future release, DocSubmitter will support automatic metadata sync via API:

```
GET /jw/api/govstack/registration/services/{serviceId}/metadata
```

This will return the current server configuration for automatic synchronization.

## Breaking Changes Policy

### Semantic Versioning

We use semantic versioning (major.minor.patch) for metadata:

- **Major (X.0.0)** - Breaking changes:
  - Required fields change
  - Field data types change
  - Field names change
  - Grid structure changes

- **Minor (x.Y.0)** - Backward-compatible changes:
  - Optional fields added
  - Validation rules change (non-breaking)
  - Documentation updates

- **Patch (x.y.Z)** - Bug fixes:
  - Fix incorrect mappings
  - Fix transformation logic
  - Documentation fixes

### Upgrade Path

**Major Version Upgrade (e.g., 1.x → 2.x)**:
1. Review breaking changes in release notes
2. Update both ProcessingServer and DocSubmitter
3. Test thoroughly with production-like data
4. Deploy server first, then clients
5. Monitor for compatibility issues

**Minor Version Upgrade (e.g., 1.0.x → 1.1.x)**:
1. Update ProcessingServer first
2. Update DocSubmitter at convenience
3. Backward compatibility maintained

## Configuration Validation

The ProcessingServer validates its `services.yml` configuration at startup (metadata/structure validation only):

### Validation Checks

- **Column existence**: Verifies database columns exist (except grid fields)
- **Form mappings**: Ensures all referenced forms are configured
- **Field types**: Validates transform types are recognized
- **Grid configurations**: Checks grid/array structures are valid

**Note**: This validates configuration structure, NOT business data. Business validation is done in Joget forms.

### Validation Errors

```
ERROR - Services.yml validation failed:
  - [farmerCropsLivestock.cropManagement] Column not found in table
```

**Note**: Grid fields (transform: "grid") are excluded from column validation.

## Troubleshooting

### Version Mismatch Warning

**Symptom**: `WARN - Metadata version mismatch detected`

**Solution**:
1. Check current versions in both projects' `services.yml`
2. Copy latest `services.yml` from ProcessingServer to DocSubmitter
3. Rebuild and redeploy DocSubmitter
4. Verify warning disappears

### Grid Field Validation Errors

**Symptom**: `ERROR - Column not found in table: c_cropManagement`

**Solution**: This indicates the validator is checking grid fields incorrectly. Grid fields don't have physical columns. This should be fixed in ProcessingServer v8.1.0+.

### Data Not Saving

**Symptom**: HTTP 200 but data doesn't appear in database

**Solution**:
1. Check ProcessingServer logs for validation failures
2. Verify field mappings in `services.yml` match database schema
3. Check grid parent field references are correct
4. Verify table names match database

## Support

For issues or questions:
- Check logs: `/path/to/joget/logs/joget.log`
- Review services.yml configuration
- Verify database schema matches configuration
- See TODO.md for future improvements

## Related Documentation

- [GovStack Registration Building Block Specification](https://registration.govstack.global/)
- [TODO.md](TODO.md) - Future architectural improvements
- [README.md](README.md) - Project overview and setup
