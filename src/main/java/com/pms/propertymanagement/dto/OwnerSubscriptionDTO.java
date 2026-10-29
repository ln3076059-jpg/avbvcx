package com.pms.propertymanagement.dto;

import com.pms.propertymanagement.enums.SubscriptionStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OwnerSubscriptionDTO {
    private Long id;
    private Long ownerId;
    private String ownerName;
    private ManagementPlanDTO managementPlan;
    private LocalDateTime subscriptionDate;
    private LocalDateTime nextBillingDate;
    private SubscriptionStatus status;
    private Integer usedPropertySlots;
    private Integer availablePropertySlots;
    private Boolean canCreateProperty;
    
    public String getStatusText() {
        switch (status) {
            case ACTIVE:
                return "Đang hoạt động";
            case EXPIRED:
                return "Hết hạn";
            case CANCELLED:
                return "Đã hủy";
            case SUSPENDED:
                return "Tạm dừng";
            default:
                return status.toString();
        }
    }
    
    public String getNextBillingText() {
        if (nextBillingDate == null) {
            return "Chưa xác định";
        }
        
        LocalDateTime now = LocalDateTime.now();
        if (nextBillingDate.isBefore(now)) {
            return "Đã quá hạn";
        }
        
        long days = java.time.Duration.between(now, nextBillingDate).toDays();
        if (days == 0) {
            return "Hôm nay";
        } else if (days == 1) {
            return "Ngày mai";
        } else {
            return String.format("Còn %d ngày", days);
        }
    }
    
    public Long getDaysUntilBilling() {
        if (nextBillingDate == null) {
            return null;
        }
        
        LocalDateTime now = LocalDateTime.now();
        if (nextBillingDate.isBefore(now)) {
            return 0L;
        }
        
        return java.time.Duration.between(now, nextBillingDate).toDays();
    }
    
    public String getUsageText() {
        if (managementPlan != null && managementPlan.getMaxProperties() == -1) {
            return String.format("Đang sử dụng: %d tài sản (Không giới hạn)", usedPropertySlots);
        }
        return String.format("Đang sử dụng: %d/%d tài sản", usedPropertySlots, usedPropertySlots + availablePropertySlots);
    }
}