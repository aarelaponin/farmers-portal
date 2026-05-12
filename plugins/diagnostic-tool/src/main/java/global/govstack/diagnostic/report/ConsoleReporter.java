package global.govstack.diagnostic.report;

import global.govstack.diagnostic.checker.AlignmentChecker;
import java.util.List;

/**
 * Generates console output report for alignment diagnostics
 */
public class ConsoleReporter {

    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GREEN = "\u001B[32m";
    private static final String BLUE = "\u001B[34m";

    public void printReport(AlignmentChecker.AlignmentResult result, boolean verbose) {
        System.out.println("=".repeat(80));
        System.out.println("ALIGNMENT DIAGNOSTIC REPORT");
        System.out.println("=".repeat(80));
        System.out.println();

        // Summary
        printSummary(result);

        // Issues by severity
        printIssues(result, verbose);

        // Recommendations
        if (result.hasErrors()) {
            printRecommendations(result);
        }

        System.out.println();
        System.out.println("=".repeat(80));
    }

    private void printSummary(AlignmentChecker.AlignmentResult result) {
        System.out.println("📊 SUMMARY");
        System.out.println("-".repeat(40));

        int errorCount = result.getIssuesBySeverity(AlignmentChecker.AlignmentIssue.Severity.ERROR).size();
        int warningCount = result.getIssuesBySeverity(AlignmentChecker.AlignmentIssue.Severity.WARNING).size();
        int infoCount = result.getIssuesBySeverity(AlignmentChecker.AlignmentIssue.Severity.INFO).size();

        System.out.println("✓ Aligned fields: " + GREEN + result.getAlignedFields() + RESET);

        if (errorCount > 0) {
            System.out.println("❌ Errors: " + RED + errorCount + RESET);
        }
        if (warningCount > 0) {
            System.out.println("⚠️  Warnings: " + YELLOW + warningCount + RESET);
        }
        if (infoCount > 0) {
            System.out.println("ℹ️  Info: " + BLUE + infoCount + RESET);
        }

        if (result.getIssues().isEmpty()) {
            System.out.println(GREEN + "✅ No alignment issues found!" + RESET);
        }

        System.out.println();
    }

    private void printIssues(AlignmentChecker.AlignmentResult result, boolean verbose) {
        if (result.getIssues().isEmpty()) {
            return;
        }

        System.out.println("🔍 ISSUES FOUND");
        System.out.println("-".repeat(40));

        // Group by severity
        printIssuesBySeverity(result.getIssuesBySeverity(AlignmentChecker.AlignmentIssue.Severity.ERROR),
                             "ERRORS", RED, verbose);
        printIssuesBySeverity(result.getIssuesBySeverity(AlignmentChecker.AlignmentIssue.Severity.WARNING),
                             "WARNINGS", YELLOW, verbose);

        if (verbose) {
            printIssuesBySeverity(result.getIssuesBySeverity(AlignmentChecker.AlignmentIssue.Severity.INFO),
                                 "INFORMATION", BLUE, verbose);
        }
    }

    private void printIssuesBySeverity(List<AlignmentChecker.AlignmentIssue> issues,
                                       String title, String color, boolean verbose) {
        if (issues.isEmpty()) {
            return;
        }

        System.out.println();
        System.out.println(color + title + ":" + RESET);

        for (int i = 0; i < issues.size(); i++) {
            AlignmentChecker.AlignmentIssue issue = issues.get(i);

            System.out.println();
            System.out.println("  " + (i + 1) + ". " + color + "[" + issue.getType() + "]" + RESET);
            System.out.println("     " + issue.getDescription());

            if (verbose && issue.getSuggestion() != null) {
                System.out.println("     " + GREEN + "Fix: " + RESET + issue.getSuggestion());
            }
        }
    }

    private void printRecommendations(AlignmentChecker.AlignmentResult result) {
        System.out.println();
        System.out.println("💡 RECOMMENDATIONS");
        System.out.println("-".repeat(40));

        List<AlignmentChecker.AlignmentIssue> errors =
            result.getIssuesBySeverity(AlignmentChecker.AlignmentIssue.Severity.ERROR);

        // Check for specific issue patterns
        boolean hasFieldNameMismatch = errors.stream()
            .anyMatch(e -> e.getType().equals("FIELD_NAME_MISMATCH"));

        if (hasFieldNameMismatch) {
            System.out.println();
            System.out.println("  " + RED + "Critical:" + RESET + " Field name mismatches found!");
            System.out.println("  These will cause data to be saved to wrong fields or not saved at all.");
            System.out.println("  " + GREEN + "Action:" + RESET + " Update test data or services.yml to use consistent field names.");
        }

        boolean hasMissingFields = errors.stream()
            .anyMatch(e -> e.getType().equals("FIELD_NOT_IN_FORM"));

        if (hasMissingFields) {
            System.out.println();
            System.out.println("  " + RED + "Important:" + RESET + " Some mapped fields don't exist in forms.");
            System.out.println("  " + GREEN + "Action:" + RESET + " Review form definitions and update field IDs or mappings.");
        }

        boolean hasPathMismatches = errors.stream()
            .anyMatch(e -> e.getType().equals("PATH_MISMATCH"));

        if (hasPathMismatches) {
            System.out.println();
            System.out.println("  " + RED + "Critical:" + RESET + " Fields found at wrong paths in test data!");
            System.out.println("  These fields exist but won't be saved because they're in wrong locations.");
            System.out.println("  " + GREEN + "Action:" + RESET + " Restructure test data to match expected paths.");
        }

        boolean hasStructuralMismatches = errors.stream()
            .anyMatch(e -> e.getType().equals("STRUCTURAL_MISMATCH"));

        if (hasStructuralMismatches) {
            System.out.println();
            System.out.println("  " + RED + "High Priority:" + RESET + " Structural mismatches detected!");
            System.out.println("  Multiple related fields need to be moved to correct parent objects.");
            System.out.println("  " + GREEN + "Action:" + RESET + " Apply structural changes to fix multiple fields at once.");
        }

        System.out.println();
        System.out.println("  Run with --verbose flag to see detailed fix suggestions for each issue.");
    }
}