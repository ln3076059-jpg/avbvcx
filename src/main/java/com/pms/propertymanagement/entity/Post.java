package com.pms.propertymanagement.entity;

import com.pms.propertymanagement.enums.PostStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Post entity - Represents marketplace posts for properties
 * 1:1 relationship with Property - one property can have max one active post
 * Optimized for marketplace queries with composite indexes
 * 
 * NEW ARCHITECTURE: Separation of Concerns
 * - Post: Marketing content (title, slug, description, viewCount)
 * - Property: Real estate data (name, rooms, price, amenities)
 */
@Entity
@Table(name = "posts", indexes = {
        // OPTIMIZED indexes for actual query patterns
        @Index(name = "idx_marketplace_query", 
               columnList = "status, postExpiredAt DESC"),  // For marketplace listing
        @Index(name = "idx_marketplace_boost_query", 
               columnList = "status, postExpiredAt DESC, boostExpiredAt DESC"),  // For boost priority
        @Index(name = "idx_post_property_unique", 
               columnList = "property_id", unique = true),  // 1:1 constraint
        @Index(name = "idx_post_expiration_job", 
               columnList = "postExpiredAt, status"),  // For scheduled expiration job
        @Index(name = "idx_post_slug_unique", 
               columnList = "slug", unique = true)  // For SEO URL lookups
})
@Getter
@Setter
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // === MARKETING CONTENT (migrated from Property) ===
    
    /**
     * Marketing title for marketplace display
     * Can be different from Property.name (internal management name)
     */
    @Column(nullable = false, length = 255)
    private String title;

    /**
     * SEO-friendly URL slug (unique across all posts)
     * Used for public marketplace URLs: /properties/{slug}
     */
    @Column(unique = true, nullable = false, length = 255)
    private String slug;

    /**
     * Marketing description for marketplace listing
     * Rich text content optimized for tenant conversion
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * View count for analytics and trending calculations
     * Incremented each time marketplace detail page is viewed
     */
    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;

    // === PROPERTY RELATIONSHIP ===
    
    // 1:1 relationship with Property - UNIQUE constraint enforced
    @OneToOne
    @JoinColumn(name = "property_id", nullable = false, unique = true)
    private Property property;

    // === MARKETPLACE VISIBILITY MANAGEMENT ===

    // Post duration management (from PostPackage subscription)
    // NULL when post is PENDING_APPROVAL or REJECTED (timer starts on staff approval)
    @Column(name = "post_expired_at")
    private LocalDateTime postExpiredAt;

    // Boost management (direct purchase, not subscription-based)
    @Column(name = "boost_expired_at")
    private LocalDateTime boostExpiredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostStatus status = PostStatus.PENDING_APPROVAL;

    /**
     * Staff rejection reason - populated when status = REJECTED
     * Owner can see this to understand what needs to be fixed
     */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /**
     * Tracks when the post timer was paused (set by submitRevision()).
     * Used by moderator on approval to compensate: postExpiredAt += (approvalTime - pausedAt).
     * Cleared on approval. Preserved on rejection (stays paused until owner re-submits).
     */
    @Column(name = "paused_at")
    private LocalDateTime pausedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // === Business Logic Methods ===

    /**
     * Check if post has expired and should be automatically hidden
     * Returns false if postExpiredAt is null (not yet approved)
     */
    public boolean isPostExpired() {
        return postExpiredAt != null && postExpiredAt.isBefore(LocalDateTime.now());
    }
    
    /**
     * CRITICAL: Auto-expire post if needed (called by scheduler)
     * This ensures PostStatus stays synchronized with expiration time
     */
    public boolean autoExpireIfNeeded() {
        if (status == PostStatus.ACTIVE && isPostExpired()) {
            this.status = PostStatus.EXPIRED;
            return true; // Status was changed
        }
        return false; // No change needed
    }

    /**
     * Check if boost has expired
     */
    public boolean isBoostExpired() {
        return boostExpiredAt == null || boostExpiredAt.isBefore(LocalDateTime.now());
    }

    /**
     * Check if post is visible in marketplace
     * Must be ACTIVE status and not expired
     */
    public boolean isVisibleInMarketplace() {
        return status == PostStatus.ACTIVE && !isPostExpired();
    }

    /**
     * Approve post: set ACTIVE and start trial timer based on owner's plan
     * Called by staff when approving a PENDING_APPROVAL post
     * @param durationDays number of days from owner's current management plan
     */
    public void approve(int durationDays) {
        this.status = PostStatus.ACTIVE;
        this.postExpiredAt = LocalDateTime.now().plusDays(durationDays);
        this.rejectionReason = null;
    }

    /** Fallback approve with default 7-day trial (used when plan lookup fails) */
    public void approve() {
        approve(7);
    }

    /**
     * Reject post: set REJECTED with reason
     * Called by staff when rejecting a PENDING_APPROVAL post
     */
    public void reject(String reason) {
        this.status = PostStatus.REJECTED;
        this.rejectionReason = reason;
    }

    /**
     * Resubmit rejected post: moves to PENDING_REVISION (chờ duyệt lại)
     * Called by owner after a post was rejected and they resubmit it.
     * pausedAt is left null — moderator approval will grant a fresh timer via the normal approval path.
     */
    public void resubmit() {
        this.status = PostStatus.PENDING_REVISION;
        this.rejectionReason = null;
    }

    /**
     * Submit revision: owner edited an active/expired/hidden post.
     * Sets PENDING_REVISION and records pausedAt = now.
     * On moderator approval, the pause duration (approvalTime - pausedAt) is added back to postExpiredAt.
     * Distinct from resubmit() which is for REJECTED posts.
     */
    public void submitRevision() {
        this.status = PostStatus.PENDING_REVISION;
        this.pausedAt = LocalDateTime.now();
    }

    /**
     * Check if post is boosted and visible
     */
    public boolean isBoosted() {
        return isVisibleInMarketplace() && !isBoostExpired();
    }

    /**
     * Extend post duration (when purchasing new PostPackage)
     */
    public void extendDuration(int additionalDays) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime newExpiry = (this.postExpiredAt != null && this.postExpiredAt.isAfter(now)) 
            ? this.postExpiredAt.plusDays(additionalDays)
            : now.plusDays(additionalDays);
        this.postExpiredAt = newExpiry;
    }

    /**
     * Add boost duration
     */
    public void addBoost(int boostDays) {
        LocalDateTime newBoostExpiry = (boostExpiredAt != null && boostExpiredAt.isAfter(LocalDateTime.now()))
            ? boostExpiredAt.plusDays(boostDays)
            : LocalDateTime.now().plusDays(boostDays);
        this.boostExpiredAt = newBoostExpiry;
    }

    /**
     * Hide post manually (owner action)
     */
    public void hide() {
        this.status = PostStatus.HIDDEN;
    }

    /**
     * Show post manually (owner action)
     */
    public void show() {
        this.status = PostStatus.ACTIVE;
    }

    // === MARKETING ANALYTICS ===

    /**
     * Increment view count when marketplace detail page is viewed
     */
    public void incrementViewCount() {
        this.viewCount = (this.viewCount == null ? 0 : this.viewCount) + 1;
    }
}
