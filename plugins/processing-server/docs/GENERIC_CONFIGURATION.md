# Generic Configuration Guide for GovStack Processing Server Plugin

## Overview

The GovStack Processing Server Plugin v8.1 now supports **generic service configuration**, allowing it to work with ANY type of service (not just farmers). This is achieved through a configuration-driven approach that eliminates hardcoded dependencies.

## Key Features

### 1. Configuration-Driven Architecture
- All service-specific mappings are now defined in `services.yml`
- No code changes required to support new service types
- Backward compatible with existing farmer registry deployments

### 2. Version Selection
The plugin supports both V2 (hardcoded) and V3 (configuration-driven) modes:
- **V2 Mode** (Default): Uses hardcoded farmer-specific mappings for backward compatibility
- **V3 Mode**: Reads all mappings from configuration, enabling generic service support

## Configuration Structure

### serviceConfig Section

The new `serviceConfig` section in `services.yml` defines all service-specific mappings:

```yaml
serviceConfig:
  # The parent form that serves as the main registration form
  parentFormId: "yourMainFormId"

  # Map section names to actual Joget form IDs
  sectionToFormMap:
    section1: "formId1"
    section2: "formId2"
    # ... more mappings

  # Grid configurations - maps grid names to their settings
  gridMappings:
    gridName1:
      formId: "gridFormId1"
      parentField: "parent_field_name"
    gridName2:
      formId: "gridFormId2"
      parentField: "parent_field_name"
    # ... more grid mappings
```

## Migration Guide

### For Existing Farmer Registry Users

**No action required!** The plugin remains backward compatible:

1. **Default behavior unchanged**: Without enabling V3 mode, the plugin continues using hardcoded farmer mappings
2. **Optional migration**: To use configuration-driven mode:
   - Add `useV3=true` in plugin configuration
   - The existing `services.yml` already includes the correct farmer mappings

### For New Service Types

To configure the plugin for a new service type:

1. **Copy the example configuration**:
   ```bash
   cp src/main/resources/docs-metadata/services-health-example.yml \
      src/main/resources/docs-metadata/services.yml
   ```

2. **Customize the configuration**:
   - Update `service.id` to match your service
   - Update `parentFormId` to your main form ID
   - Map your sections in `sectionToFormMap`
   - Configure grids in `gridMappings`
   - Define field mappings in `formMappings`

3. **Enable V3 mode** in Joget plugin configuration:
   - Set `useV3=true`
   - Set `serviceId` to match your service ID

## Example Configurations

### Farmer Registry (Default)

```yaml
serviceConfig:
  parentFormId: "farmerRegistrationForm"

  sectionToFormMap:
    farmerBasicInfo: "farmerBasicInfo"
    farmerLocation: "farmerLocation"
    farmerAgriculture: "farmerAgriculture"
    # ... more farmer sections

  gridMappings:
    householdMembers:
      formId: "householdMemberForm"
      parentField: "farmer_id"
    cropManagement:
      formId: "cropManagementForm"
      parentField: "farmer_id"
    # ... more farmer grids
```

### Health Registry (Example)

```yaml
serviceConfig:
  parentFormId: "healthRegistrationForm"

  sectionToFormMap:
    patientBasicInfo: "patientBasicInfo"
    patientContact: "patientContact"
    medicalHistory: "medicalHistory"
    # ... more health sections

  gridMappings:
    allergies:
      formId: "allergyForm"
      parentField: "patient_id"
    medications:
      formId: "medicationForm"
      parentField: "patient_id"
    # ... more health grids
```

### Education Registry (Example)

```yaml
serviceConfig:
  parentFormId: "studentRegistrationForm"

  sectionToFormMap:
    studentBasicInfo: "studentBasicInfo"
    guardianInfo: "guardianInfo"
    academicHistory: "academicHistory"
    # ... more education sections

  gridMappings:
    courses:
      formId: "courseEnrollmentForm"
      parentField: "student_id"
    grades:
      formId: "gradeRecordForm"
      parentField: "student_id"
    # ... more education grids
```

## Configuration Reference

### serviceConfig Properties

| Property | Description | Default | Required |
|----------|-------------|---------|----------|
| `parentFormId` | The main form ID for the service | `farmerRegistrationForm` | No |
| `sectionToFormMap` | Maps section names to form IDs | Farmer defaults | No |
| `gridMappings` | Configures grid/subform relationships | Farmer defaults | No |

### Grid Mapping Properties

| Property | Description | Example |
|----------|-------------|---------|
| `formId` | The Joget form ID for the grid | `householdMemberForm` |
| `parentField` | The field that links to parent record | `farmer_id` |

## Plugin Configuration in Joget

### Properties

| Property | Description | Default |
|----------|-------------|---------|
| `useGovStack` | Enable GovStack mode | `true` |
| `useV3` | Enable configuration-driven mode | `false` |
| `serviceId` | The service ID from services.yml | `farmers_registry` |

### Configuration Steps

1. **Upload the plugin JAR** to Joget
2. **Configure the plugin** in API settings:
   - Enable GovStack mode: `useGovStack=true`
   - Enable V3 mode for generic support: `useV3=true`
   - Set service ID: `serviceId=your_service_id`
3. **Deploy services.yml** with your configuration
4. **Test the API** with your service data

## Troubleshooting

### Issue: Forms not being populated

**Check**:
1. Verify `sectionToFormMap` contains correct mappings
2. Ensure form IDs match Joget form definitions
3. Check logs for configuration loading messages

### Issue: Grid data not saving

**Check**:
1. Verify `gridMappings` configuration
2. Ensure `parentField` matches database column name (without `c_` prefix)
3. Confirm grid form IDs are correct

### Issue: Configuration not loading

**Check**:
1. Verify `useV3=true` is set in plugin configuration
2. Check that `services.yml` is in the correct location
3. Review logs for YAML parsing errors

## Best Practices

1. **Test incrementally**: Start with basic forms before adding grids
2. **Use validation**: The validation framework helps catch configuration issues early
3. **Keep defaults**: Don't remove default mappings if you might need farmer registry later
4. **Document mappings**: Comment your `services.yml` to explain each mapping
5. **Version control**: Keep your `services.yml` in version control

## Advanced Configuration

### Multiple Service Support

To support multiple services in one deployment:

1. Create separate YAML files for each service
2. Use environment variables or system properties to select active configuration
3. Consider using a configuration management system

### Dynamic Configuration Loading

For enterprise deployments:

1. Store configurations in a database
2. Implement custom configuration loader
3. Enable hot-reload of configurations without restart

## Support

For questions or issues:
1. Check the [Field Mapping Documentation](FIELD_MAPPING_FIXES.md)
2. Review the [Deployment Guide](DEPLOYMENT.md)
3. Open an issue on GitHub with:
   - Your `services.yml` configuration
   - Error logs
   - Expected vs actual behavior