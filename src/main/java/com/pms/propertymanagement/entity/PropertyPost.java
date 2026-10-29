package com.pms.propertymanagement.entity;

import com.pms.propertymanagement.enums.PostStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Property Post - Tin đăng của property
 * 1 Property chỉ có thể có 1 Post tại một thời điểm
 */
@Entity
@Table(name = "property_posts", indexes = {
        @Index(name = "idx_property_posts_property", columnList = "property_id"),
        @Index(name = "idx_property_posts_owner", columnList = "owner_id"),
        @Index(name = "idx_property_posts_status", columnList = "status"),
        @Index(name = "idx_property_posts_expiry", columnList = "expiry_date")
})
@Getter
@Setter
public class PropertyPost {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, name = "property_id")
    private Long propertyId; // Reference to Property (1-1 relationship)
    
    @Column(nullable = false, name = "owner_id")
    private Long ownerId; // Reference to User (Owner)
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_package_id", nullable = false)
    private PostingPackage postPackage;
    
    @Column(nullable = false, name = "start_date")
    private LocalDateTime startDate = LocalDateTime.now();
    
    @Column(nullable = false, name = "expiry_date")
    private LocalDateTime expiryDate;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status = PostStatus.ACTIVE;
    
    // === USAGE TRACKING ===
    @Column(nullable = false, name = "used_boosts")
    private int usedBoosts = 0; // Số lần boost đã sử dụng
    
    @Column(nullable = false, name = "total_views")
    private long totalViews = 0L; // Tổng lượt xem
    
    @Column(nullable = false, name = "total_contacts")
    private long totalContacts = 0L; // Tổng lượt liên hệ
    
    // === AUTO-RENEWAL ===
    @Column(nullable = false, name = "auto_renew")
    private boolean autoRenew = true; // Tự động gia hạn
    
    @Column(name = "last_renewal_date")
    private LocalDateTime lastRenewalDate;
    
    // === METADATA ===
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // === BUSINESS LOGIC ===
    public boolean isActive() {
        return status == PostStatus.ACTIVE && 
               LocalDateTime.now().isBefore(expiryDate);
    }
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryDate);
    }
    
    public long getDaysLeft() {
        if (isExpired()) return 0;
        return Duration.between(LocalDateTime.now(), expiryDate).toDays();
    }
    
    public boolean canUseBoost() {
        // NEW ARCHITECTURE: Get free boosts based on package code
        int freeBoosts = switch (postPackage.getCode()) {
            case "POST_STANDARD" -> 3;
            case "POST_PREMIUM" -> 5;
            case "POST_ENTERPRISE" -> 10;
            default -> 0;
        };
        return usedBoosts < freeBoosts;
    }
    
    public boolean isEligibleForRenewal() {
        return autoRenew && getDaysLeft() <= 3; // Gia hạn khi còn 3 ngày
    }
    
    public void useBoost() {
        if (canUseBoost()) {
            usedBoosts++;
        }
    }
    
    public void renew(PostingPackage newPostPackage) {
        this.postPackage = newPostPackage;
        // NEW ARCHITECTURE: Calculate duration based on package code
        int durationDays = switch (newPostPackage.getCode()) {
            case "POST_NEW" -> 15;
            case "POST_STANDARD" -> 30;
            case "POST_PREMIUM" -> 60;
            case "POST_ENTERPRISE" -> 90;
            default -> 15;
        };
        this.expiryDate = this.expiryDate.plusDays(durationDays);
        this.lastRenewalDate = LocalDateTime.now();
    }
    
    public void incrementView() {
        totalViews++;
    }
    
    public void incrementContact() {
        totalContacts++;
    }
    
    public double getConversionRate() {
        return totalViews > 0 ? (double) totalContacts / totalViews * 100 : 0.0;
    }
}