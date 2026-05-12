# Embedded Datalist Form Element Plugin

A Joget DX8 form element plugin that displays a read-only datalist inline within a form, with the ability to filter based on form field values.

## Problem Solved

In Joget DX8:
- **ListGrid** + **JDBC Datalist Binder** don't work together (popup shows empty)
- No native way to embed a read-only datalist filtered by parent form values
- Common need: Show related records (e.g., farmer's land parcels) in a form tab

## Features

- Display any datalist embedded within a form
- Filter datalist based on form field values (e.g., show farmer's parcels by farmer ID)
- Works with both Form Data Binder and JDBC Datalist Binder
- Pagination support
- AJAX-based loading with configurable refresh triggers
- Customizable styling via CSS
- i18n support

## Use Cases

1. **Farmer's Land Parcels** - Display all parcels belonging to a farmer in the farmer registration form
2. **Order Line Items** - Show all line items for an order in the order details form
3. **Employee Training History** - Display completed trainings for an employee
4. **Audit Trail / History Log** - Show all history entries related to a record

## Configuration

### Basic Settings
- **Element ID**: Unique identifier for the element
- **Label**: Optional label displayed above the datalist
- **Datalist**: Select the datalist to embed from the current app

### Filter Parameters
Map form field values to datalist request parameters:
- **Parameter Name**: The request parameter name expected by the datalist filter
- **Form Field ID**: The form field to get the value from (use "id" for primary key)
- **Default Value**: Fallback value if field is empty

### Display Options
- **Height**: Container height (e.g., "400px", "auto")
- **Show Pagination**: Enable/disable pagination controls
- **Page Size**: Number of records per page
- **Empty Message**: Message displayed when no records found

### Advanced
- **Refresh on Field Change**: Comma-separated field IDs that trigger datalist reload
- **Custom CSS**: Additional CSS for styling customization

## Installation

1. Build with Maven:
   ```bash
   mvn clean package
   ```

2. Upload the generated JAR from `target/embedded-datalist-8.1-SNAPSHOT.jar` to:
   - **Joget Admin Console** → **Settings** → **Manage Plugins** → **Upload Plugin**

3. The plugin appears in the Form Builder under the **Advanced** category

## Example: Farmer's Parcels

### Step 1: Create the Datalist

Create a datalist `listFarmersParcels` with JDBC Datalist Binder. Use `#requestParam.xxx#` hash variable for filtering:

```sql
SELECT
    f01.id AS farm_id,
    f0101.c_national_id,
    f0201.c_district,
    f0202.c_area_hectares
FROM app_fd_farms_registry f01
JOIN app_fd_farmerBasicInfo f0101 ON f01.c_basic_data = f0101.id
JOIN app_fd_parcelLocation f0201 ON f0101.c_national_id = f0201.c_farmer_id
JOIN app_fd_parcelRegistration f02 ON f02.c_general_data = f0201.id
JOIN app_fd_parcelGeometry f0202 ON f02.c_location = f0202.id
WHERE ('#requestParam.farm_id#' = '' OR f01.id = '#requestParam.farm_id#')
```

**Important:** The conditional `WHERE` clause handles both:
- Design time (empty parameter) → returns all rows so UI can display columns
- Runtime (with parameter) → filters by the provided value

### Step 2: Configure the Form

In the Farmer form, add the "Embedded Datalist" element:
- **Datalist**: `listFarmersParcels`
- **Filter Parameters**:
  - Parameter Name: `farm_id`
  - Form Field ID: `parent_id` (or the field containing the farm ID)
  - Default Value: (empty)

### Step 3: Test

When viewing/editing a farmer record, their parcels appear inline filtered by the farm ID.

See `docs/f01.08.json` (form) and `docs/f01.08-list.json` (datalist) for complete working examples.

## Technical Details

- Uses Joget JSON API: `/jw/web/json/data/list/{appId}/{listId}`
- Filter parameters passed as URL query parameters (e.g., `?farm_id=123`)
- For JDBC Datalist Binder, use `#requestParam.paramName#` hash variable in SQL
- AJAX-based loading with jQuery
- FreeMarker template for HTML rendering

## Requirements

- Joget DX8 Enterprise Edition or higher
- Java 17+

## Version

8.1-SNAPSHOT

## Author

GovStack Farmers Registry Project
