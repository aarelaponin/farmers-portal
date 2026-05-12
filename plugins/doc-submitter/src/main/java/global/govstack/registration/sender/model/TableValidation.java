package global.govstack.registration.sender.model;

import java.util.ArrayList;
import java.util.List;

public class TableValidation {
    private String tableName;
    private boolean exists;
    private List<String> validatedColumns;
    private int totalColumns;

    public TableValidation(String tableName) {
        this.tableName = tableName;
        this.validatedColumns = new ArrayList<>();
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public boolean exists() {
        return exists;
    }

    public void setExists(boolean exists) {
        this.exists = exists;
    }

    public List<String> getValidatedColumns() {
        return validatedColumns;
    }

    public void setValidatedColumns(List<String> validatedColumns) {
        this.validatedColumns = validatedColumns;
    }

    public void addValidatedColumn(String columnName) {
        this.validatedColumns.add(columnName);
    }

    public int getTotalColumns() {
        return totalColumns;
    }

    public void setTotalColumns(int totalColumns) {
        this.totalColumns = totalColumns;
    }

    public int getValidatedColumnCount() {
        return validatedColumns.size();
    }
}
