package global.govstack.processing.service.validation;

import org.junit.Test;
import org.junit.Ignore;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Analyzes all form fields and generates classification report
 */
public class FieldAnalysisTest {

    @Test
    public void analyzeAllFormFields() throws IOException {
        // Path to doc-forms directory
        String formDirectory = "/Users/aarelaponin/IdeaProjects/gs-plugins/doc-submitter/doc-forms";

        FieldTypeClassifier classifier = new FieldTypeClassifier();
        classifier.analyzeFormFiles(formDirectory);

        String report = classifier.generateReport();
        System.out.println(report);

        // Save report to file
        String reportPath = "/Users/aarelaponin/IdeaProjects/gs-plugins/processing-server/field-analysis-report.txt";
        Files.write(Paths.get(reportPath), report.getBytes());
        System.out.println("\nReport saved to: " + reportPath);

        // Generate lists for ValueNormalizer configuration
        System.out.println("\n=== Configuration for ValueNormalizer ===\n");

        System.out.println("LOV Yes/No fields:");
        for (String field : classifier.getLovYesNoFields()) {
            System.out.println("  fieldConfigs.put(\"" + field + "\", new NormalizationConfig(\"yes\", \"no\"));");
        }

        System.out.println("\nLOV 1/2 fields:");
        for (String field : classifier.getLovOneTwoFields()) {
            System.out.println("  fieldConfigs.put(\"" + field + "\", new NormalizationConfig(\"1\", \"2\"));");
        }

        System.out.println("\nMasterdata fields (DO NOT NORMALIZE):");
        for (String field : classifier.getMasterdataFields()) {
            System.out.println("  masterdataFields.add(\"" + field + "\");");
        }
    }

    @Test
    @Ignore("Manual test for verification")
    public void testSpecificField() throws IOException {
        String formDirectory = "/Users/aarelaponin/IdeaProjects/gs-plugins/doc-submitter/doc-forms";

        FieldTypeClassifier classifier = new FieldTypeClassifier();
        classifier.analyzeFormFiles(formDirectory);

        // Test specific fields
        String[] testFields = {
            "agriculturalManagementSkills",
            "livestockProduction",
            "cropProduction",
            "mainSourceFarmLabour"
        };

        for (String field : testFields) {
            FieldTypeClassifier.FieldType type = classifier.getFieldType(field);
            System.out.println(field + " -> " + type);
            System.out.println("  Is LOV: " + classifier.isLOVField(field));
            System.out.println("  Is Masterdata: " + classifier.isMasterdataField(field));
        }
    }
}