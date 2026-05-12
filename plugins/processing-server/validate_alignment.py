#!/usr/bin/env python3
"""
Comprehensive validation script to check alignment between:
1. Joget form definitions (doc-forms)
2. YAML metadata mappings (services.yml)
3. Test data (test-data.json)
4. Our code expectations
"""

import json
import re
from pathlib import Path

def load_yaml_simple(filepath):
    """Simple YAML parser for our specific format"""
    with open(filepath, 'r') as f:
        content = f.read()

    sections = {}
    current_section = None
    current_subsection = None

    for line in content.split('\n'):
        # Top level section (no indent)
        if line and not line.startswith(' ') and line.endswith(':'):
            current_section = line[:-1]
            sections[current_section] = {}
            current_subsection = None
        # Second level (2 spaces)
        elif line.startswith('  ') and not line.startswith('    ') and line.strip().endswith(':'):
            if current_section:
                subsection = line.strip()[:-1]
                sections[current_section][subsection] = {'fields': []}
                current_subsection = subsection
        # Field mapping (contains 'joget:')
        elif 'joget:' in line and current_section == 'formMappings' and current_subsection:
            field = line.split('"')[1] if '"' in line else line.split(':')[1].strip()
            sections[current_section][current_subsection]['fields'].append(field)
        # Check for array type
        elif 'type: "array"' in line and current_section == 'formMappings' and current_subsection:
            sections[current_section][current_subsection]['type'] = 'array'

    return sections

def load_test_data():
    """Load and analyze test data"""
    with open('src/main/resources/docs-metadata/test-data.json', 'r') as f:
        return json.load(f)

def analyze_joget_forms():
    """Analyze Joget form definitions"""
    forms = {}
    form_files = Path('doc-forms').glob('*.json')

    for file in form_files:
        with open(file, 'r') as f:
            try:
                data = json.load(f)
                form_id = data.get('properties', {}).get('id', 'unknown')
                form_name = data.get('properties', {}).get('name', 'unknown')

                # Extract field IDs from form definition
                fields = extract_form_fields(data)

                forms[file.name] = {
                    'id': form_id,
                    'name': form_name,
                    'field_count': len(fields),
                    'fields': fields
                }
            except json.JSONDecodeError:
                forms[file.name] = {'error': 'Invalid JSON'}

    return forms

def extract_form_fields(form_data, fields=None):
    """Recursively extract field IDs from form structure"""
    if fields is None:
        fields = []

    if isinstance(form_data, dict):
        # Check for field ID - be more inclusive
        if 'id' in form_data:
            form_id = form_data.get('id', '')
            class_name = form_data.get('className', '')

            # Include fields, grids, and other input elements
            # Exclude sections and columns
            if (('Field' in class_name or
                 'Grid' in class_name or
                 'SelectBox' in class_name or
                 'CheckBox' in class_name or
                 'Radio' in class_name or
                 'TextArea' in class_name or
                 'DatePicker' in class_name) or
                (form_id and
                 not form_id.startswith('section') and
                 not form_id.startswith('column') and
                 form_id not in ['farmerBasicInfo', 'farmerLocation', 'farmerAgriculture',
                                 'farmerHousehold', 'farmerCropsLivestock',
                                 'farmerIncomePrograms', 'farmerDeclaration',
                                 'farmerRegistrationForm'])):
                if form_id and form_id not in fields:
                    fields.append(form_id)

        # Recurse through structure
        for key, value in form_data.items():
            if isinstance(value, list):
                for item in value:
                    extract_form_fields(item, fields)
            elif isinstance(value, dict):
                extract_form_fields(value, fields)

    elif isinstance(form_data, list):
        for item in form_data:
            extract_form_fields(item, fields)

    return fields

def main():
    print("=" * 80)
    print("GOVSTACK FARMER REGISTRATION - ALIGNMENT VALIDATION REPORT")
    print("=" * 80)

    # Load all data
    yaml_data = load_yaml_simple('src/main/resources/docs-metadata/services.yml')
    test_data = load_test_data()
    joget_forms = analyze_joget_forms()

    # 1. YAML Analysis
    print("\n1. YAML METADATA ANALYSIS (services.yml)")
    print("-" * 40)

    total_fields = 0
    array_sections = []
    regular_sections = []

    if 'formMappings' in yaml_data:
        for section, data in yaml_data['formMappings'].items():
            field_count = len(data.get('fields', []))
            total_fields += field_count

            if data.get('type') == 'array':
                array_sections.append(f"  - {section}: {field_count} fields")
            else:
                regular_sections.append(f"  - {section}: {field_count} fields")

    print(f"Total sections: {len(yaml_data.get('formMappings', {}))}")
    print(f"Total field mappings: {total_fields}")
    print(f"\nRegular sections ({len(regular_sections)}):")
    for s in regular_sections:
        print(s)
    print(f"\nArray sections ({len(array_sections)}):")
    for s in array_sections:
        print(s)

    # 2. Test Data Analysis
    print("\n2. TEST DATA ANALYSIS (test-data.json)")
    print("-" * 40)

    def count_fields(obj, path=""):
        count = 0
        if isinstance(obj, dict):
            for key, value in obj.items():
                if not isinstance(value, (dict, list)):
                    count += 1
                else:
                    count += count_fields(value, f"{path}.{key}" if path else key)
        elif isinstance(obj, list) and obj:
            if not isinstance(obj[0], (dict, list)):
                count += 1
        return count

    # Check specific paths
    print(f"Has relatedPerson: {len(test_data.get('relatedPerson', []))} members")
    if 'extension' in test_data:
        ext = test_data['extension']
        if 'agriculturalData' in ext:
            agri = ext['agriculturalData']
            print(f"Has crops: {len(agri.get('crops', []))} items")
            print(f"Has livestock: {len(agri.get('livestock', []))} items")

    total_test_fields = count_fields(test_data)
    print(f"\nTotal fields in test data (including nested): ~{total_test_fields}")

    # 3. Joget Forms Analysis
    print("\n3. JOGET FORMS ANALYSIS (doc-forms)")
    print("-" * 40)

    main_forms = ['farmers-01.json', 'farmers-01.01.json', 'farmers-01.02.json',
                  'farmers-01.03.json', 'farmers-01.04.json', 'farmers-01.05.json',
                  'farmers-01.06.json', 'farmers-01.07.json']

    for form_file in main_forms:
        if form_file in joget_forms:
            form = joget_forms[form_file]
            print(f"  {form_file}: {form['name']}")
            print(f"    Form ID: {form['id']}")
            print(f"    Fields: {form['field_count']}")
            if form['field_count'] > 0 and form['field_count'] < 20:
                print(f"    Field IDs: {', '.join(form['fields'][:10])}")

    # 4. Critical Alignment Issues
    print("\n4. CRITICAL ALIGNMENT CHECKS")
    print("-" * 40)

    issues = []

    # Check if YAML field names exist in Joget forms
    yaml_fields = set()
    for section, data in yaml_data.get('formMappings', {}).items():
        yaml_fields.update(data.get('fields', []))

    joget_fields = set()
    for form in joget_forms.values():
        joget_fields.update(form.get('fields', []))

    # Find mismatches
    yaml_only = yaml_fields - joget_fields
    joget_only = joget_fields - yaml_fields
    matched = yaml_fields & joget_fields

    print(f"✓ Fields in both YAML and Joget: {len(matched)}")
    print(f"⚠ Fields only in YAML: {len(yaml_only)}")
    if len(yaml_only) > 0 and len(yaml_only) < 20:
        print(f"  Examples: {', '.join(list(yaml_only)[:10])}")
    print(f"⚠ Fields only in Joget: {len(joget_only)}")
    if len(joget_only) > 0 and len(joget_only) < 20:
        print(f"  Examples: {', '.join(list(joget_only)[:10])}")

    # 5. Known Issues
    print("\n5. IDENTIFIED ISSUES")
    print("-" * 40)

    if len(matched) < 20:
        print("❌ CRITICAL: Very few fields match between YAML and Joget forms!")
        print("   This explains why only 24 fields are being mapped.")

    if total_fields > 70 and len(matched) < 30:
        print("❌ Field ID mismatch: YAML defines fields that don't exist in Joget forms")

    if len(array_sections) != 3:
        print(f"⚠ Expected 3 array sections, found {len(array_sections)}")

    # 6. Recommendations
    print("\n6. RECOMMENDATIONS")
    print("-" * 40)

    if len(matched) < total_fields * 0.5:
        print("1. Review YAML field mappings - many fields don't match Joget form IDs")
        print("2. Check if Joget forms use different field naming conventions")
        print("3. Consider creating a field mapping document")

    print("\n" + "=" * 80)
    print("END OF VALIDATION REPORT")
    print("=" * 80)

if __name__ == "__main__":
    main()