package com.pms.propertymanagement.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Post Package - Gói đăng tin marketplace (transaction-based)
 * Khác với PostingPackage cũ (usage-based), đây là time-based
 */
@Entity
@Table(name = "post_packages")
@Getter
@Setter
public class PostPackage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false, length = 50)
    private String code; // 15_DAYS, 30_DAYS, 60_DAYS
    
    @Column(nullable = false, length = 255)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(nullable = false, name = "duration_days")
    private int durationDays; // Số ngày hiển thị
    
    @Column(nullable = false)
    private int price; // Giá (VND)
    
    // === BENEFITS ===
    @Column(nullable = false, name = "free_boosts")
    private int freeBoosts = 0; // Số lần boost miễn phí
    
    @Column(nullable = false, name = "has_vip_badge")
    private boolean hasVipBadge = false; // Badge VIP
    
    @Column(nullable = false, name = "has_priority_support")
    private boolean hasPrioritySupport = false; // Hỗ trợ ưu tiên
    
    @Column(nullable = false, name = "has_advanced_analytics")
    private boolean hasAdvancedAnalytics = false; // Thống kê nâng cao
    
    @Column(nullable = false, name = "has_search_priority")
    private boolean hasSearchPriority = false; // Ưu tiên trong tìm kiếm
    
    // === METADATA ===
    @Column(nullable = false, name = "is_active")
    private boolean isActive = true;
    
    @Column(nullable = false, name = "sort_order")
    private int sortOrder = 0;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // === HELPER METHODS ===
    public BigDecimal getPriceAsBigDecimal() {
        return BigDecimal.valueOf(price);
    }
    
    public BigDecimal getPricePerDay() {
        return BigDecimal.valueOf(price)
                .divide(BigDecimal.valueOf(durationDays), 2, RoundingMode.HALF_UP);
    }
    
    public boolean is15Days() {
        return "15_DAYS".equals(code);
    }
    
    public boolean is30Days() {
        return "30_DAYS".equals(code);
    }
    
    public boolean is60Days() {
        return "60_DAYS".equals(code);
    }
    
    public String getDisplayPrice() {
        return String.format("%,d đ", price);
    }
    
    public String getDisplayPricePerDay() {
        return String.format("%,.0f đ/ngày", getPricePerDay());
    }
}