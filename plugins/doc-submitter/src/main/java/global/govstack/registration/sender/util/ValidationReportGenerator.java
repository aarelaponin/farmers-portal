package global.govstack.registration.sender.util;

import global.govstack.registration.sender.model.TableValidation;
import global.govstack.registration.sender.model.ValidationResult;

public class ValidationReportGenerator {

    public String generateReport(ValidationResult result) {
        StringBuilder report = new StringBuilder();

        report.append("\n");
        report.append("=".repeat(60)).append("\n");
        report.append("         DATABASE SCHEMA VALIDATION REPORT\n");
        report.append("=".repeat(60)).append("\n\n");

        // Summary
        report.append("VALIDATION RESULTS:\n");
        report.append("-".repeat(60)).append("\n\n");
        report.append(String.format("Tables Validated:  %d/%d%n",
            result.getValidatedTables(), result.getTotalTables()));
        report.append(String.format("Columns Validated: %d/%d%n%n",
            result.getValidatedColumns(), result.getTotalColumns()));

        // Found tables
        if (!result.getFoundTables().isEmpty()) {
            report.append("✓ FOUND TABLES:\n");
            for (TableValidation table : result.getFoundTables()) {
                report.append(String.format("  - %-30s (%d/%d columns)%n",
                    table.getTableName(),
                    table.getValidatedColumnCount(),
                    table.getTotalColumns()));
            }
            report.append("\n");
        }

        // Missing tables
        if (!result.getMissingTables().isEmpty()) {
            report.append("✗ MISSING TABLES:\n");
            for (String tableName : result.getMissingTables()) {
                report.append(String.format("  - %s%n", tableName));
            }
            report.append("\n");
        } else {
            report.append("✓ MISSING TABLES: (none)\n\n");
        }

        // Missing columns
        if (!result.getMissingColumns().isEmpty()) {
            report.append("✗ MISSING COLUMNS:\n");
            for (String column : result.getMissingColumns()) {
                report.append(String.format("  - %s%n", column));
            }
            report.append("\n");
        } else {
            report.append("✓ MISSING COLUMNS: (none)\n\n");
        }

        // Final summary
        report.append("-".repeat(60)).append("\n");
        if (result.hasErrors()) {
            report.append(String.format("✗ Summary: %d issues found%n", result.getErrorCount()));
        } else {
            report.append("✓ Summary: All schema elements validated successfully!\n");
        }
        report.append("=".repeat(60)).append("\n");

        return report.toString();
    }
}
