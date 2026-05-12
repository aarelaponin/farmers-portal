package global.govstack.application.model;

/**
 * One row from {@code sp_benefit_item} — a single benefit item on the
 * programme's Benefits tab.
 */
public final class BenefitItem {
    public final String id;
    public final String itemCode;
    public final String itemName;
    public final String itemType;        // e.g. INPUT / VOUCHER / CASH
    public final String categoryCode;
    public final String unit;            // e.g. kg, bag, voucher
    public final String unitCost;
    public final String quantity;
    public final String subsidyAmount;
    public final String subsidyPercent;
    public final String farmerContribution;
    public final String totalCost;
    public final String notes;

    public BenefitItem(String id, String itemCode, String itemName,
                       String itemType, String categoryCode, String unit,
                       String unitCost, String quantity,
                       String subsidyAmount, String subsidyPercent,
                       String farmerContribution, String totalCost, String notes) {
        this.id = id;
        this.itemCode = itemCode;
        this.itemName = itemName;
        this.itemType = itemType;
        this.categoryCode = categoryCode;
        this.unit = unit;
        this.unitCost = unitCost;
        this.quantity = quantity;
        this.subsidyAmount = subsidyAmount;
        this.subsidyPercent = subsidyPercent;
        this.farmerContribution = farmerContribution;
        this.totalCost = totalCost;
        this.notes = notes;
    }

    public String displayLabel() {
        if (itemName != null && !itemName.isEmpty()) return itemName;
        if (itemCode != null && !itemCode.isEmpty()) return itemCode;
        return "Item " + id;
    }
}
