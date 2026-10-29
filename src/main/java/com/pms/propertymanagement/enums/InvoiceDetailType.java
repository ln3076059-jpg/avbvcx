package com.pms.propertymanagement.enums;

public enum InvoiceDetailType {
    ROOM("Tiền phòng"),
    ELECTRICITY("Tiền điện"),
    WATER("Tiền nước"),
    SERVICE("Dịch vụ"),
    OTHER("Khoản thu khác");

    private final String displayName;

    InvoiceDetailType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
