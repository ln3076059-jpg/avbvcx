package com.pms.propertymanagement.entity;

import com.pms.propertymanagement.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * Owner Subscription - Gói owner đang sử dụng
 * Quản lý subscription của owner cho Management Plan
 */
@Entity
@Table(name = "owner_subscriptions", indexes = {
        @Index(name = "idx_owner_subscriptions_owner", columnList = "owner_id"),
        @Index(name = "idx_owner_subscriptions_status", columnList = "status")
})
@Getter
@Setter
public class OwnerSubscription {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, name = "owner_id")
    private Long ownerId; // Reference to User (Owner)
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "management_plan_id", nullable = false)
    private ManagementPlan managementPlan;
    
    @Column(nullable = false, name = "start_date")
    private LocalDateTime startDate = LocalDateTime.now();
    
    @Column(nullable = false, name = "next_billing_date") 
    private LocalDateTime nextBillingDate;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;
    
    // === USAGE TRACKING ===
    @Column(nullable = false, name = "used_property_slots")
    private int usedPropertySlots = 0; // Số property đang sử dụng slot
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // === BUSINESS LOGIC ===
    public boolean canCreateProperty() {
        if (managementPlan.isUnlimitedProperties()) {
            return true;
        }
        return usedPropertySlots < managementPlan.getMaxProperties();
    }
    
    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE;
    }
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(nextBillingDate) && 
               !managementPlan.isFree();
    }
    
    public int getAvailableSlots() {
        if (managementPlan.isUnlimitedProperties()) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, managementPlan.getMaxProperties() - usedPropertySlots);
    }
}