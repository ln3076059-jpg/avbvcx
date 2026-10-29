package com.pms.propertymanagement.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * Management Plan - Quản lý gói dịch vụ nội bộ (recurring monthly)
 * Điều khiển số lượng property và tính năng owner được sử dụng
 */
@Entity
@Table(name = "management_plans")
@Getter
@Setter
public class ManagementPlan {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false, length = 50)
    private String code; // FREE, PRO, BUSINESS
    
    @Column(nullable = false, length = 255)
    private String name; // "Free", "Pro", "Business"
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(nullable = false)
    private int monthlyPrice = 0; // Giá hàng tháng (VND)
    
    // === RESOURCE LIMITS ===
    @Column(nullable = false)
    private int maxProperties = 1; // Số property tối đa (-1 = unlimited)
    
    @Column(nullable = false)
    private int maxPosts = 1; // Số bài đăng tối đa (-1 = unlimited)
    
    @Column(nullable = false)
    private int maxRoomsPerProperty = 3; // Số room tối đa mỗi property (-1 = unlimited)
    
    @Column(nullable = false)
    private int postDurationDays = 7; // Số ngày hiển thị mỗi bài đăng theo gói này
    
    // === FEATURE FLAGS ===
    @Column(nullable = false)
    private boolean hasContracts = false; // Quản lý hợp đồng
    
    @Column(nullable = false)
    private boolean hasInvoices = false; // Hóa đơn tự động
    
    @Column(nullable = false)
    private boolean hasAdvancedReports = false; // Báo cáo nâng cao
    
    @Column(nullable = false)
    private boolean hasAutoReminders = false; // Nhắc tiền tự động
    
    @Column(nullable = false)
    private boolean hasStaffManagement = false; // Phân quyền nhân viên
    
    @Column(nullable = false)
    private boolean hasApiAccess = false; // API tích hợp
    
    @Column(nullable = false)
    private boolean hasExcelExport = false; // Xuất Excel
    
    // === METADATA ===
    @Column(nullable = false)
    private boolean isActive = true;
    
    @Column(nullable = false)
    private int sortOrder = 0; // Thứ tự hiển thị
    
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // === HELPER METHODS ===
    public boolean isUnlimitedProperties() {
        return maxProperties == -1;
    }
    
    public boolean isUnlimitedPosts() {
        return maxPosts == -1;
    }
    
    public boolean isUnlimitedRooms() {
        return maxRoomsPerProperty == -1;
    }
    
    public boolean isFree() {
        return "FREE".equals(code);
    }
    
    public boolean isPremium() {
        return "PREMIUM".equals(code);
    }
    
    public boolean isVip() {
        return "VIP".equals(code);
    }
    
    public boolean isEnterprise() {
        return "ENTERPRISE".equals(code);
    }
}