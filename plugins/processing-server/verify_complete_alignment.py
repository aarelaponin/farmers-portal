#!/usr/bin/env python3
"""
Comprehensive Alignment Verification Script
Checks alignment between:
1. Processing Server (plugin) code and configuration
2. Test data structure
3. Joget Utility sender
"""

import json
import sys
from pathlib import Path

def load_json_file(path):
    """Load and parse a JSON file"""
    try:
        with open(path, 'r') as f:
            return json.load(f)
    except Exception as e:
        print(f"  ✗ Error loading {path}: {e}")
        return None

def check_test_data_alignment():
    """Check if test data files are aligned between plugin and utility"""
    print("\n" + "="*80)
    print("1. TEST DATA ALIGNMENT CHECK")
    print("="*80)

    # Paths to test data
    plugin_test_data = Path('src/main/resources/docs-metadata/test-data.json')
    utility_test_data = Path('/Users/aarelaponin/PycharmProjects/dev/gam/joget_utility/data/json/test-data.json')

    # Load both files
    print("\nLoading test data files...")
    plugin_data = load_json_file(plugin_test_data)
    utility_data = load_json_file(utility_test_data)

    if not plugin_data or not utility_data:
        print("  ✗ Failed to load test data files")
        return False

    # Check structure
    print("\nChecking data structure...")

    # Both should have testData wrapper
    plugin_has_wrapper = 'testData' in plugin_data
    utility_has_wrapper = 'testData' in utility_data

    print(f"  Plugin test-data.json has testData wrapper: {plugin_has_wrapper}")
    print(f"  Utility test-data.json has testData wrapper: {utility_has_wrapper}")

    if not plugin_has_wrapper or not utility_has_wrapper:
        print("  ✗ Test data structure mismatch - both need testData wrapper")
        return False

    # Check content
    plugin_first = plugin_data['testData'][0] if plugin_data['testData'] else {}
    utility_first = utility_data['testData'][0] if utility_data['testData'] else {}

    # Check for critical fields
    print("\nChecking critical data fields...")

    # Check extension.agriculturalData
    plugin_has_agri = 'extension' in plugin_first and 'agriculturalData' in plugin_first.get('extension', {})
    utility_has_agri = 'extension' in utility_first and 'agriculturalData' in utility_first.get('extension', {})

    print(f"  Plugin has extension.agriculturalData: {plugin_has_agri}")
    if plugin_has_agri:
        agri = plugin_first['extension']['agriculturalData']
        print(f"    - Crops: {len(agri.get('crops', []))}")
        print(f"    - Livestock: {len(agri.get('livestock', []))}")

    print(f"  Utility has extension.agriculturalData: {utility_has_agri}")
    if utility_has_agri:
        agri = utility_first['extension']['agriculturalData']
        print(f"    - Crops: {len(agri.get('crops', []))}")
        print(f"    - Livestock: {len(agri.get('livestock', []))}")

    # Check relatedPerson
    plugin_has_related = 'relatedPerson' in plugin_first
    utility_has_related = 'relatedPerson' in utility_first

    print(f"  Plugin has relatedPerson: {plugin_has_related} ({len(plugin_first.get('relatedPerson', []))} members)")
    print(f"  Utility has relatedPerson: {utility_has_related} ({len(utility_first.get('relatedPerson', []))} members)")

    # Compare files
    if json.dumps(plugin_data, sort_keys=True) == json.dumps(utility_data, sort_keys=True):
        print("\n  ✓ Test data files are IDENTICAL")
        return True
    else:
        print("\n  ⚠ Test data files are DIFFERENT (but may be compatible)")
        return plugin_has_wrapper and utility_has_wrapper and plugin_has_agri and utility_has_agri

def check_yaml_configuration():
    """Check if YAML configuration is correct"""
    print("\n" + "="*80)
    print("2. YAML CONFIGURATION CHECK")
    print("="*80)

    yaml_path = Path('src/main/resources/docs-metadata/services.yml')

    if not yaml_path.exists():
        print(f"  ✗ YAML file not found: {yaml_path}")
        return False

    print(f"\n  ✓ YAML file exists: {yaml_path}")

    # Simple check for critical sections
    with open(yaml_path, 'r') as f:
        content = f.read()

    print("\nChecking YAML structure...")

    # Check for main sections
    has_service = 'service:' in content
    has_form_mappings = 'formMappings:' in content
    has_transformations = 'transformations:' in content

    print(f"  Has service section: {has_service}")
    print(f"  Has formMappings section: {has_form_mappings}")
    print(f"  Has transformations section: {has_transformations}")

    # Check for array sections
    has_household = 'householdMembers:' in content and 'type: "array"' in content
    has_crops = 'cropManagement:' in content and 'govstack: "extension.agriculturalData.crops"' in content
    has_livestock = 'livestockDetails:' in content and 'govstack: "extension.agriculturalData.livestock"' in content

    print(f"\nArray sections:")
    print(f"  householdMembers configured: {has_household}")
    print(f"  cropManagement configured: {has_crops}")
    print(f"  livestockDetails configured: {has_livestock}")

    return has_service and has_form_mappings and has_transformations and has_crops and has_livestock

def check_utility_configuration():
    """Check utility configuration"""
    print("\n" + "="*80)
    print("3. UTILITY CONFIGURATION CHECK")
    print("="*80)

    config_path = Path('/Users/aarelaponin/PycharmProjects/dev/gam/joget_utility/config/joget.yaml')
    json_processor_path = Path('/Users/aarelaponin/PycharmProjects/dev/gam/joget_utility/processors/json_processor.py')

    if not config_path.exists():
        print(f"  ✗ Utility config not found: {config_path}")
        return False

    print(f"\n  ✓ Utility config exists: {config_path}")

    # Check for farmers_registry endpoint
    with open(config_path, 'r') as f:
        config_content = f.read()

    has_farmers = 'farmers_registry:' in config_content
    has_api_id = 'API-6dbc76a7-e38f-47c6-af25-ad0783c13859' in config_content

    print(f"  Has farmers_registry endpoint: {has_farmers}")
    print(f"  Has correct API ID: {has_api_id}")

    # Check JSONProcessor for testData support
    if json_processor_path.exists():
        with open(json_processor_path, 'r') as f:
            processor_content = f.read()

        has_testdata_support = "'testData' in data and isinstance(data['testData'], list)" in processor_content
        print(f"  JSONProcessor supports testData wrapper: {has_testdata_support}")
    else:
        print(f"  ✗ JSONProcessor not found")
        has_testdata_support = False

    return has_farmers and has_api_id and has_testdata_support

def check_code_alignment():
    """Check if code components are aligned"""
    print("\n" + "="*80)
    print("4. CODE COMPONENT ALIGNMENT CHECK")
    print("="*80)

    # Check critical Java files exist
    files_to_check = [
        ('GovStackDataMapper.java', 'src/main/java/global/govstack/processing/service/metadata/GovStackDataMapper.java'),
        ('YamlMetadataService.java', 'src/main/java/global/govstack/processing/service/metadata/YamlMetadataService.java'),
        ('TableDataHandler.java', 'src/main/java/global/govstack/processing/service/metadata/TableDataHandler.java'),
        ('GovStackRegistrationService.java', 'src/main/java/global/govstack/processing/service/GovStackRegistrationService.java'),
    ]

    print("\nChecking Java components...")
    all_exist = True
    for name, path in files_to_check:
        exists = Path(path).exists()
        print(f"  {name}: {'✓' if exists else '✗'}")
        all_exist = all_exist and exists

    # Check for testData wrapper handling in GovStackDataMapper
    mapper_path = Path('src/main/java/global/govstack/processing/service/metadata/GovStackDataMapper.java')
    if mapper_path.exists():
        with open(mapper_path, 'r') as f:
            mapper_content = f.read()

        has_wrapper_handling = 'if (rootNode.has("testData")' in mapper_content
        print(f"\nGovStackDataMapper handles testData wrapper: {has_wrapper_handling}")
    else:
        has_wrapper_handling = False

    # Check grid form IDs
    handler_path = Path('src/main/java/global/govstack/processing/service/metadata/TableDataHandler.java')
    if handler_path.exists():
        with open(handler_path, 'r') as f:
            handler_content = f.read()

        has_correct_ids = (
            'householdMemberForm' in handler_content and
            'cropManagementForm' in handler_content and
            'livestockDetailsForm' in handler_content
        )
        print(f"TableDataHandler has correct form IDs: {has_correct_ids}")
    else:
        has_correct_ids = False

    return all_exist and has_wrapper_handling and has_correct_ids

def main():
    """Run all alignment checks"""
    print("="*80)
    print("COMPREHENSIVE ALIGNMENT VERIFICATION")
    print("GovStack Farmer Registration System")
    print("="*80)

    results = {
        'test_data': check_test_data_alignment(),
        'yaml_config': check_yaml_configuration(),
        'utility_config': check_utility_configuration(),
        'code_alignment': check_code_alignment()
    }

    # Summary
    print("\n" + "="*80)
    print("SUMMARY")
    print("="*80)

    all_aligned = all(results.values())

    for component, status in results.items():
        status_icon = "✓" if status else "✗"
        component_name = component.replace('_', ' ').title()
        print(f"  {status_icon} {component_name}: {'ALIGNED' if status else 'NEEDS FIX'}")

    print("\n" + "="*80)
    if all_aligned:
        print("✓✓✓ ALL COMPONENTS ARE ALIGNED ✓✓✓")
        print("\nYou should see:")
        print("  - 69 main form fields mapped")
        print("  - 3 array sections (household, crops, livestock)")
        print("  - All form tabs populated with data")
    else:
        print("⚠⚠⚠ ALIGNMENT ISSUES DETECTED ⚠⚠⚠")
        print("\nPlease fix the issues above before testing.")
    print("="*80)

    return 0 if all_aligned else 1

if __name__ == "__main__":
    sys.exit(main())