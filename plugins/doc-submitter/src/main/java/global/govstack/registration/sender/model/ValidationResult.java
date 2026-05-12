package global.govstack.registration.sender.model;

import java.util.ArrayList;
import java.util.List;

public class ValidationResult {
    private int totalTables;
    private int validatedTables;
    private int totalColumns;
    private int validatedColumns;
    private List<TableValidation> foundTables;
    private List<String> missingTables;
    private List<String> missingColumns;

    public ValidationResult() {
        this.foundTables = new ArrayList<>();
        this.missingTables = new ArrayList<>();
        this.missingColumns = new ArrayList<>();
    }

    public int getTotalTables() {
        return totalTables;
    }

    public void setTotalTables(int totalTables) {
        this.totalTables = totalTables;
    }

    public int getValidatedTables() {
        return validatedTables;
    }

    public void setValidatedTables(int validatedTables) {
        this.validatedTables = validatedTables;
    }

    public int getTotalColumns() {
        return totalColumns;
    }

    public void setTotalColumns(int totalColumns) {
        this.totalColumns = totalColumns;
    }

    public int getValidatedColumns() {
        return validatedColumns;
    }

    public void setValidatedColumns(int validatedColumns) {
        this.validatedColumns = validatedColumns;
    }

    public List<TableValidation> getFoundTables() {
        return foundTables;
    }

    public void setFoundTables(List<TableValidation> foundTables) {
        this.foundTables = foundTables;
    }

    public List<String> getMissingTables() {
        return missingTables;
    }

    public void setMissingTables(List<String> missingTables) {
        this.missingTables = missingTables;
    }

    public List<String> getMissingColumns() {
        return missingColumns;
    }

    public void setMissingColumns(List<String> missingColumns) {
        this.missingColumns = missingColumns;
    }

    public void addFoundTable(TableValidation table) {
        this.foundTables.add(table);
    }

    public void addMissingTable(String tableName) {
        this.missingTables.add(tableName);
    }

    public void addMissingColumn(String tableName, String columnName) {
        this.missingColumns.add(tableName + "." + columnName);
    }

    public boolean hasErrors() {
        return !missingTables.isEmpty() || !missingColumns.isEmpty();
    }

    public int getErrorCount() {
        return missingTables.size() + missingColumns.size();
    }
}
