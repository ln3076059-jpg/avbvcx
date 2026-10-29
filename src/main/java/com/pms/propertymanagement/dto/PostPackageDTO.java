package com.pms.propertymanagement.dto;

import lombok.Data;

@Data
public class PostPackageDTO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private Integer durationDays;
    private Integer price;
    private Integer freeBoosts;
    private Boolean hasVipBadge;
    private Boolean hasSearchPriority;
    private Boolean isActive;
    private Boolean isRecommended;
    
    public String getDisplayPrice() {
        return String.format("%,d VNĐ", price);
    }
    
    public String getDisplayDuration() {
        return String.format("%d ngày", durationDays);
    }
    
    public String getPricePerDay() {
        if (durationDays > 0) {
            double pricePerDayValue = (double) price / durationDays;
            return String.format("%.0f VNĐ/ngày", pricePerDayValue);
        }
        return "0 VNĐ/ngày";
    }
    
    public String getBenefitsText() {
        StringBuilder benefits = new StringBuilder();
        
        if (freeBoosts > 0) {
            benefits.append(String.format("%d lần đẩy tin miễn phí", freeBoosts));
        }
        
        if (hasVipBadge) {
            if (benefits.length() > 0) benefits.append(", ");
            benefits.append("Huy hiệu VIP");
        }
        
        if (hasSearchPriority) {
            if (benefits.length() > 0) benefits.append(", ");
            benefits.append("Ưu tiên tìm kiếm");
        }
        
        return benefits.toString();
    }
}