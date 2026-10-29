package com.pms.propertymanagement.enums;

/**
 * Post Status enum - Production Ready
 * Controls post lifecycle and marketplace visibility
 *
 * Flow: PENDING_APPROVAL → ACTIVE → EXPIRED (or HIDDEN by owner)
 *       PENDING_APPROVAL → REJECTED (staff rejects) → owner resubmits → PENDING_REVISION → ACTIVE
 */
public enum PostStatus {
    PENDING_APPROVAL("Chờ duyệt"),
    REJECTED("Bị từ chối"),
    PENDING_REVISION("Chờ duyệt lại"),
    DRAFT("Bản nháp"),
    ACTIVE("Đang hiển thị"),
    EXPIRED("Hết hạn"),
    HIDDEN("Đang ẩn");

    private final String displayName;

    PostStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
