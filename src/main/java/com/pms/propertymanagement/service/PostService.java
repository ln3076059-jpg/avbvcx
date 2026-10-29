package com.pms.propertymanagement.service;

import com.pms.propertymanagement.dto.response.PostAnalyticsDTO;
import com.pms.propertymanagement.dto.response.PostOwnerResponse;
import com.pms.propertymanagement.entity.Post;
import com.pms.propertymanagement.entity.PostingPackage;
import com.pms.propertymanagement.entity.Property;
import com.pms.propertymanagement.enums.PostStatus;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing Post lifecycle and marketplace operations
 * NEW ARCHITECTURE: Separates marketing content (Post) from real estate data (Property)
 */
public interface PostService {

    // === POST CREATION & CONTENT MANAGEMENT ===

    /**
     * Create new post for property
     * Post starts with ACTIVE status and 7-day free trial
     */
    Post createPost(Property property, String title, String slug, String description);

    /**
     * Create post by propertyId - validates ownership and creates post
     * Convenience method for PostController
     */
    Post createPostForProperty(Long propertyId, Long ownerId, String title, String slug, String description);

    /**
     * Update post marketing content (title, slug, description)
     * Owner can modify marketing content without affecting Property data
     */
    void updatePostContent(Long postId, String title, String slug, String description);

    // === DURATION MANAGEMENT ===

    /**
     * Purchase additional post duration using PostingPackage
     * Extends postExpiredAt by package duration
     */
    void purchaseDuration(Long postId, PostingPackage postPackage);

    /**
     * Renew post with specific number of days
     */
    void renewPost(Long postId, int additionalDays);

    // === VISIBILITY MANAGEMENT ===

    /**
     * Hide post manually (owner action)
     * Status: ACTIVE → HIDDEN
     */
    void hidePost(Long postId);

    /**
     * Show post manually (owner action)
     * Status: HIDDEN → ACTIVE (only if not expired)
     */
    void showPost(Long postId);

    /**
     * Resubmit rejected post for review again
     * Status: REJECTED → PENDING_APPROVAL
     */
    void resubmitPost(Long postId, Long ownerId);

    // === STAFF APPROVAL WORKFLOW ===

    /**
     * Approve a pending post (staff action)
     * Status: PENDING_APPROVAL → ACTIVE, starts 7-day free trial
     */
    void approvePost(Long postId);

    /**
     * Reject a pending post (staff action)
     * Status: PENDING_APPROVAL → REJECTED
     */
    void rejectPost(Long postId, String reason);

    /**
     * Get all posts with PENDING_APPROVAL status (for staff dashboard)
     */
    List<PostOwnerResponse> getPendingApprovalPosts();

    /**
     * Get all posts with specified status (for moderator dashboard)
     */
    List<Post> getPostsByStatus(PostStatus status);

    /**
     * Find a post by ID, throws ResourceNotFoundException if not found
     */
    Post findPostById(Long id);

    /**
     * Find a post by ID and verify it belongs to ownerId, throws IllegalArgumentException if not owned
     */
    Post getPostByIdAndOwner(Long postId, Long ownerId);

    /**
     * Find a post by property ID
     */
    Optional<Post> findPostByPropertyId(Long propertyId);

    /**
     * Moderator approve: handles both PENDING_APPROVAL and PENDING_REVISION.
     * For PENDING_REVISION compensates paused time; for PENDING_APPROVAL grants fresh days from plan.
     */
    void approvePostByModerator(Long postId);

    /**
     * Moderator reject: handles both PENDING_APPROVAL and PENDING_REVISION.
     */
    void rejectPostByModerator(Long postId, String reason);

    // === QUERY METHODS ===

    /**
     * Get post by slug for marketplace display
     * Only returns ACTIVE, non-expired posts
     */
    Optional<Post> getMarketplacePostBySlug(String slug);

    /**
     * Get all posts by owner (all statuses)
     * For owner dashboard
     */
    List<PostOwnerResponse> getPostsByOwner(Long ownerId);

    /**
     * Get post analytics (views, expiry, status)
     */
    PostAnalyticsDTO getPostAnalytics(Long postId);

    // === VIEW TRACKING ===

    /**
     * Increment view count when marketplace detail page is viewed
     * Called by public property detail controller
     */
    void incrementView(String slug);

    // === MARKETPLACE QUERIES ===

    /**
     * Get all marketplace-visible posts (ACTIVE + non-expired)
     * Ordered by boost status and expiry date
     */
    List<Post> getAllMarketplacePosts();

    List<Post> searchMarketplacePosts(String keyword);

    /**
     * Get marketplace posts by category
     */
    List<Post> getMarketplacePostsByCategory(Long categoryId);
}
