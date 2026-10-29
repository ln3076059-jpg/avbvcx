package com.pms.propertymanagement.enums;

public enum InvoiceStatus {
    UNPAID("Chưa thanh toán", "bg-yellow-100 text-yellow-800 border-yellow-200"),
    PARTIALLY_PAID("Thanh toán 1 phần", "bg-blue-100 text-blue-800 border-blue-200"),
    PAID("Đã thanh toán", "bg-green-100 text-green-800 border-green-200"),
    OVERDUE("Quá hạn", "bg-red-100 text-red-800 border-red-200"),
    CANCELLED("Đã hủy", "bg-gray-100 text-gray-800 border-gray-200");

    private final String displayName;
    private final String badgeClass;

    InvoiceStatus(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeClass() {
        return badgeClass;
    }
}
