package global.govstack.diagnostic;

import global.govstack.diagnostic.analyzer.*;
import global.govstack.diagnostic.checker.AlignmentChecker;
import global.govstack.diagnostic.report.ConsoleReporter;
import java.io.File;
import java.util.*;

/**
 * Standalone diagnostic tool to check alignment between Joget forms,
 * services.yml mappings, and test data
 */
public class AlignmentDiagnosticTool {

    private static final String DEFAULT_FORMS_DIR = "doc-forms";
    private static final String DEFAULT_SERVICES_FILE = "docs-metadata/services.yml";
    private static final String DEFAULT_TEST_DATA_FILE = "docs-metadata/test-data.json";

    private FormAnalyzer formAnalyzer;
    private MappingAnalyzer mappingAnalyzer;
    private TestDataAnalyzer testDataAnalyzer;
    private AlignmentChecker alignmentChecker;
    private ConsoleReporter reporter;

    public AlignmentDiagnosticTool() {
        this.formAnalyzer = new FormAnalyzer();
        this.mappingAnalyzer = new MappingAnalyzer();
        this.testDataAnalyzer = new TestDataAnalyzer();
        this.alignmentChecker = new AlignmentChecker();
        this.reporter = new ConsoleReporter();
    }

    public static void main(String[] args) {
        AlignmentDiagnosticTool tool = new AlignmentDiagnosticTool();

        // Parse command line arguments
        boolean verbose = false;
        boolean jsonOutput = false;
        String specificForm = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--verbose":
                case "-v":
                    verbose = true;
                    break;
                case "--json":
                    jsonOutput = true;
                    break;
                case "--form":
                    if (i + 1 < args.length) {
                        specificForm = args[++i];
                    }
                    break;
                case "--help":
                case "-h":
                    printUsage();
                    return;
            }
        }

        // Run diagnostics
        tool.runDiagnostics(verbose, jsonOutput, specificForm);
    }

    public void runDiagnostics(boolean verbose, boolean jsonOutput, String specificForm) {
        System.out.println("=".repeat(80));
        System.out.println("GOVSTACK ALIGNMENT DIAGNOSTIC TOOL");
        System.out.println("=".repeat(80));
        System.out.println();

        try {
            // Step 1: Analyze Joget Forms
            System.out.println("📋 Analyzing Joget forms...");
            File formsDir = new File(DEFAULT_FORMS_DIR);
            Map<String, FormAnalyzer.FormDefinition> forms = formAnalyzer.analyzeForms(formsDir);
            System.out.println("   Found " + forms.size() + " form definitions");

            // Step 2: Analyze Services.yml Mappings
            System.out.println("🔗 Analyzing services.yml mappings...");
            File servicesFile = new File(DEFAULT_SERVICES_FILE);
            MappingAnalyzer.ServiceMappings mappings = mappingAnalyzer.analyzeMappings(servicesFile);
            System.out.println("   Found " + mappings.getTotalFieldCount() + " field mappings");

            // Step 3: Analyze Test Data
            System.out.println("📊 Analyzing test data structure...");
            File testDataFile = new File(DEFAULT_TEST_DATA_FILE);
            TestDataAnalyzer.TestDataStructure testData = testDataAnalyzer.analyzeTestData(testDataFile);
            System.out.println("   Found " + testData.getFieldPaths().size() + " data fields");

            // Step 4: Cross-reference and Check Alignment
            System.out.println("🔍 Checking alignment...");
            AlignmentChecker.AlignmentResult result = alignmentChecker.checkAlignment(
                forms, mappings, testData, specificForm
            );

            // Step 5: Generate Report
            System.out.println();
            if (jsonOutput) {
                System.out.println(result.toJson());
            } else {
                reporter.printReport(result, verbose);
            }

            // Exit with appropriate code
            System.exit(result.hasErrors() ? 1 : 0);

        } catch (Exception e) {
            System.err.println("❌ Error running diagnostics: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            System.exit(2);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar alignment-diagnostic.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --verbose, -v     Show detailed information");
        System.out.println("  --json           Output results as JSON");
        System.out.println("  --form <formId>  Check specific form only");
        System.out.println("  --help, -h       Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar alignment-diagnostic.jar");
        System.out.println("  java -jar alignment-diagnostic.jar --verbose");
        System.out.println("  java -jar alignment-diagnostic.jar --form farmerAgriculture");
        System.out.println("  java -jar alignment-diagnostic.jar --json > report.json");
    }
}