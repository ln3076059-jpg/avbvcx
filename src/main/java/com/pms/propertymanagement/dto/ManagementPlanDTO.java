package com.pms.propertymanagement.dto;

import lombok.Data;

@Data
public class ManagementPlanDTO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private Integer monthlyPrice;
    private Integer maxProperties;
    private Integer maxPosts;
    private Integer maxRoomsPerProperty;
    private Integer postDurationDays;
    private Boolean hasContracts;
    private Boolean hasInvoices;
    private Boolean hasAdvancedReports;
    private Boolean hasAutoReminders;
    private Boolean hasStaffManagement;
    private Boolean hasApiAccess;
    private Boolean hasExcelExport;
    private Boolean isActive;
    private Boolean isRecommended;

    public String getDisplayPrice() {
        if (monthlyPrice == null || monthlyPrice == 0) {
            return "Miễn phí";
        }
        return String.format("%,d VNĐ/tháng", monthlyPrice);
    }

    public String getPropertyLimitText() {
        if (maxProperties == null || maxProperties == -1) return "Không giới hạn";
        return "Tối đa " + maxProperties;
    }

    public String getPostLimitText() {
        if (maxPosts == null || maxPosts == -1) return "Không giới hạn";
        return "Tối đa " + maxPosts;
    }

    public String getRoomLimitText() {
        if (maxRoomsPerProperty == null || maxRoomsPerProperty == -1) return "Không giới hạn";
        return "Tối đa " + maxRoomsPerProperty + " phòng";
    }

    public String getPostDurationText() {
        if (postDurationDays == null) return "7 ngày";
        return postDurationDays + " ngày/bài";
    }
    
    /** Legacy alias for templates */
    public String getResourceLimitText() {
        return getPropertyLimitText() + " tài sản";
    }
}