package com.pms.propertymanagement.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * Property Slot - Quản lý slot của từng property
 * Điều khiển property nào được quyền hoạt động (plan_slot = true/false)
 */
@Entity
@Table(name = "property_slots", indexes = {
        @Index(name = "idx_property_slots_property", columnList = "property_id"),
        @Index(name = "idx_property_slots_owner", columnList = "owner_id"),
        @Index(name = "idx_property_slots_active", columnList = "is_active")
})
@Getter
@Setter
public class PropertySlot {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, name = "property_id")
    private Long propertyId; // Reference to Property (1-1 relationship)
    
    @Column(nullable = false, name = "owner_id")
    private Long ownerId; // Reference to User (Owner)
    
    @Column(nullable = false, name = "is_active")
    private boolean isActive = true; // plan_slot = true/false
    
    @Column(length = 500, name = "deactivation_reason")
    private String deactivationReason; // Lý do bị vô hiệu hóa
    
    @Column(name = "activated_at")
    private LocalDateTime activatedAt = LocalDateTime.now();
    
    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (!isActive && deactivatedAt == null) {
            deactivatedAt = LocalDateTime.now();
        } else if (isActive && deactivatedAt != null) {
            activatedAt = LocalDateTime.now();
            deactivatedAt = null;
        }
    }
    
    // === BUSINESS LOGIC ===
    public void activate(String reason) {
        this.isActive = true;
        this.activatedAt = LocalDateTime.now();
        this.deactivatedAt = null;
        this.deactivationReason = null;
    }
    
    public void deactivate(String reason) {
        this.isActive = false;
        this.deactivatedAt = LocalDateTime.now();
        this.deactivationReason = reason;
    }
}