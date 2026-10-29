package com.pms.propertymanagement.repository;

import com.pms.propertymanagement.entity.Post;
import com.pms.propertymanagement.enums.PostStatus;
import com.pms.propertymanagement.enums.PropertyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Post entity with marketplace-optimized queries
 * Uses composite indexes for performance
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    // === BASIC QUERIES ===

    /**
     * Find post by property ID (1:1 relationship)
     */
    Optional<Post> findByPropertyId(Long propertyId);

    List<Post> findByProperty_IdIn(java.util.Collection<Long> propertyIds);

    /**
     * Check if post exists for property
     */
    boolean existsByPropertyId(Long propertyId);

    /**
     * Find posts by property owner
     */
    List<Post> findByPropertyOwnerId(Long ownerId);

    // === SLUG QUERIES (NEW ARCHITECTURE - for SEO-friendly URLs) ===

    /**
     * Find post by unique slug
     * Uses unique index: idx_post_slug_unique
     */
    Optional<Post> findBySlug(String slug);

    List<Post> findByStatusIn(List<PostStatus> statuses);

    List<Post> findByStatusInOrderByCreatedAtDesc(List<PostStatus> statuses);

    List<Post> findByStatusInAndProperty_Category_IdOrderByCreatedAtDesc(List<PostStatus> statuses, Long categoryId);

    Optional<Post> findBySlugAndStatusIn(String slug, List<PostStatus> statuses);

    /**
     * Find visible marketplace post by slug (for public detail page)
     * OPTIMIZED: Checks status for marketplace visibility
     */
    @Query("SELECT p FROM Post p WHERE p.slug = :slug AND p.status = 'ACTIVE' " +
           "AND p.postExpiredAt > :currentTime")
    Optional<Post> findMarketplacePostBySlug(@Param("slug") String slug, 
                                            @Param("currentTime") LocalDateTime currentTime);

    /**
     * Check if slug already exists (for duplicate validation)
     */
    boolean existsBySlug(String slug);

    /**
     * Check if slug exists excluding specific post ID (for edit validation)
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Post p " +
           "WHERE p.slug = :slug AND p.id != :postId")
    boolean existsBySlugExcludingId(@Param("slug") String slug, @Param("postId") Long postId);

    // === MARKETPLACE QUERIES (using composite indexes) ===

    /**
     * Find active posts that haven't expired (marketplace listing)
     * Uses composite index: idx_marketplace_active (status, postExpiredAt)
     */
    @Query("SELECT p FROM Post p WHERE p.status = :status AND p.postExpiredAt > :currentTime")
    List<Post> findActiveNonExpiredPosts(@Param("status") PostStatus status, 
                                        @Param("currentTime") LocalDateTime currentTime);

    /**
     * Find boosted posts for marketplace priority
     * Uses composite index: idx_marketplace_boost (status, postExpiredAt, boostExpiredAt)
     */
    @Query("SELECT p FROM Post p WHERE p.status = :status " +
           "AND p.postExpiredAt > :currentTime " +
           "AND p.boostExpiredAt > :currentTime " +
           "ORDER BY p.boostExpiredAt DESC")
    List<Post> findBoostedPosts(@Param("status") PostStatus status,
                               @Param("currentTime") LocalDateTime currentTime);

    /**
     * Find all marketplace-visible posts (active + non-expired)
     * OPTIMIZED: Uses idx_marketplace_boost_query for best performance
     * Query pattern matches index: (status, postExpiredAt DESC, boostExpiredAt DESC)
     */
    @Query("SELECT p FROM Post p WHERE p.status = 'ACTIVE' AND p.postExpiredAt > :currentTime " +
           "ORDER BY " +
           "CASE WHEN p.boostExpiredAt IS NOT NULL AND p.boostExpiredAt > :currentTime THEN 0 ELSE 1 END, " +
           "p.boostExpiredAt DESC NULLS LAST, " +
           "p.postExpiredAt DESC")
    List<Post> findMarketplacePosts(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Find posts by specific property category for marketplace
     * OPTIMIZED: Query designed for idx_marketplace_boost_query performance  
     */
    @Query("SELECT p FROM Post p JOIN p.property prop WHERE p.status = 'ACTIVE' " +
           "AND p.postExpiredAt > :currentTime AND prop.category.id = :categoryId " +
           "ORDER BY " +
           "CASE WHEN p.boostExpiredAt IS NOT NULL AND p.boostExpiredAt > :currentTime THEN 0 ELSE 1 END, " +
           "p.boostExpiredAt DESC NULLS LAST, " +
           "p.postExpiredAt DESC")
    List<Post> findMarketplacePostsByCategory(@Param("categoryId") Long categoryId,
                                             @Param("currentTime") LocalDateTime currentTime);

    /**
     * Search marketplace posts with database-level keyword filtering
     */
    @Query("SELECT p FROM Post p JOIN p.property prop " +
           "LEFT JOIN prop.ward w " +
           "LEFT JOIN w.province prov " +
           "WHERE p.status = 'ACTIVE' AND p.postExpiredAt > :currentTime " +
           "AND (:keyword IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(prop.addressNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(w.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(prov.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY " +
           "CASE WHEN p.boostExpiredAt IS NOT NULL AND p.boostExpiredAt > :currentTime THEN 0 ELSE 1 END, " +
           "p.boostExpiredAt DESC NULLS LAST, " +
           "p.postExpiredAt DESC")
    List<Post> searchMarketplacePosts(@Param("keyword") String keyword,
                                      @Param("currentTime") LocalDateTime currentTime);

    // === OWNER MANAGEMENT QUERIES ===

    /**
     * Find posts by owner with all statuses (for owner dashboard)
     */
    @Query("SELECT p FROM Post p WHERE p.property.owner.id = :ownerId " +
           "ORDER BY p.status, p.createdAt DESC")
    List<Post> findByOwnerOrderByStatus(@Param("ownerId") Long ownerId);

    /**
     * Find expired posts for cleanup/notification
     * OPTIMIZED: Uses idx_post_expiration_job (postExpiredAt, status)
     */
    @Query("SELECT p FROM Post p WHERE p.postExpiredAt < :currentTime AND p.status = 'ACTIVE'")
    List<Post> findExpiredActivePosts(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Find posts expiring within specified days (for renewal notifications)
     */
    @Query("SELECT p FROM Post p WHERE p.status = 'ACTIVE' " +
           "AND p.postExpiredAt BETWEEN :currentTime AND :futureTime")
    List<Post> findPostsExpiringWithin(@Param("currentTime") LocalDateTime currentTime,
                                      @Param("futureTime") LocalDateTime futureTime);

    // === BULK OPERATIONS ===

    /**
     * Extend postExpiredAt for ACTIVE posts owned by this user where current expiry is shorter
     * than newExpiredAt. Used when owner upgrades to a plan with longer postDurationDays.
     * Only extends (never shortens) posts.
     */
    @Modifying
    @Query("UPDATE Post p SET p.postExpiredAt = :newExpiredAt " +
           "WHERE p.property.owner.id = :ownerId AND p.status = 'ACTIVE' " +
           "AND p.postExpiredAt > CURRENT_TIMESTAMP " +
           "AND p.postExpiredAt < :newExpiredAt")
    int extendActivePostsExpiry(@Param("ownerId") Long ownerId,
                                @Param("newExpiredAt") LocalDateTime newExpiredAt);

    /**
     * Reactivate EXPIRED posts for a user when owner upgrades plan.
     * Sets status back to ACTIVE and extends postExpiredAt = now + newPlan.postDurationDays.
     */
    @Modifying
    @Query("UPDATE Post p SET p.status = 'ACTIVE', p.postExpiredAt = :newExpiredAt " +
           "WHERE p.property.owner.id = :ownerId AND p.status = 'EXPIRED'")
    int reactivateExpiredPosts(@Param("ownerId") Long ownerId,
                               @Param("newExpiredAt") LocalDateTime newExpiredAt);

    /**
     * Hide all posts for a user (when properties are locked)
     */
    @Modifying
    @Query("UPDATE Post p SET p.status = 'HIDDEN' WHERE p.property.owner.id = :ownerId " +
           "AND p.status = 'ACTIVE'")
    int hidePostsByOwnerId(@Param("ownerId") Long ownerId);

    /**
     * Show all posts for a user (when properties are unlocked)
     */
    @Modifying
    @Query("UPDATE Post p SET p.status = 'ACTIVE' WHERE p.property.owner.id = :ownerId " +
           "AND p.status = 'HIDDEN' AND p.postExpiredAt > :currentTime")
    int showNonExpiredPostsByOwnerId(@Param("ownerId") Long ownerId, 
                                    @Param("currentTime") LocalDateTime currentTime);

    // === ANALYTICS QUERIES ===

    /**
     * Count active posts by owner
     */
    @Query("SELECT COUNT(p) FROM Post p WHERE p.property.owner.id = :ownerId " +
           "AND p.status = 'ACTIVE' AND p.postExpiredAt > :currentTime")
    int countActivePostsByOwner(@Param("ownerId") Long ownerId, 
                               @Param("currentTime") LocalDateTime currentTime);

    /**
     * Count boosted posts by owner
     */
    @Query("SELECT COUNT(p) FROM Post p WHERE p.property.owner.id = :ownerId " +
           "AND p.status = 'ACTIVE' AND p.postExpiredAt > :currentTime " +
           "AND p.boostExpiredAt > :currentTime")
    int countBoostedPostsByOwner(@Param("ownerId") Long ownerId,
                                @Param("currentTime") LocalDateTime currentTime);

    /**
     * Find posts by property status (for property management integration)
     */
    @Query("SELECT p FROM Post p WHERE p.property.status = :propertyStatus")
    List<Post> findByPropertyStatus(@Param("propertyStatus") PropertyStatus propertyStatus);

    /**
     * Find all posts with a specific PostStatus
     * Used for staff moderation: findByStatus(PostStatus.PENDING_APPROVAL)
     */
    List<Post> findByStatus(PostStatus status);

    /**
     * Find posts by status ordered by creation date (oldest first for fairness)
     */
    List<Post> findByStatusOrderByCreatedAtAsc(PostStatus status);

    // === CHAT RECOMMENDATION QUERIES ===

    /**
     * Main recommendation query: filter by budget + ward codes + amenities + category.
     * GROUP BY p ensures unique results (JPQL must GROUP BY entity, not p.id).
     * HAVING checks minimum amenity match count when amenityIds is set.
     * Boost-first ordering: boosted posts appear before non-boosted.
     *
     * ⚠️ amenityMatchCount: pass amenityIds.size() when amenityIds != null, pass 0L when amenityIds == null.
     * (Prevents NPE from calling .size() on null; 0L is safe because HAVING is skipped when amenityIds IS NULL)
     */
    @Query("SELECT p FROM Post p " +
           "JOIN p.property prop " +
           "JOIN prop.rooms r " +
           "LEFT JOIN prop.amenities a " +
           "WHERE p.status = 'ACTIVE' AND p.postExpiredAt > :now " +
           "AND r.status = 'AVAILABLE' " +
           "AND (:maxPrice IS NULL OR r.price <= :maxPrice) " +
           "AND (:provinceCode IS NULL OR prop.ward.province.code = :provinceCode) " +
           "AND (:wardCodes IS NULL OR prop.ward.code IN :wardCodes) " +
           "AND (:categoryId IS NULL OR prop.category.id = :categoryId) " +
           "AND (:amenityIds IS NULL OR a.id IN :amenityIds) " +
           "GROUP BY p " +
           "HAVING (:amenityIds IS NULL OR COUNT(DISTINCT a.id) >= :amenityMatchCount) " +
           "ORDER BY CASE WHEN p.boostExpiredAt > :now THEN 0 ELSE 1 END, p.postExpiredAt DESC")
    List<Post> findRecommendedPosts(
            @Param("now") LocalDateTime now,
            @Param("maxPrice") Double maxPrice,
            @Param("provinceCode") String provinceCode,
            @Param("wardCodes") List<String> wardCodes,
            @Param("categoryId") Long categoryId,
            @Param("amenityIds") List<Long> amenityIds,
            @Param("amenityMatchCount") long amenityMatchCount);

    /**
     * Relaxed recommendation: drops ward filter, loosens budget constraint.
     * SELECT DISTINCT p avoids duplicates from the JOIN across multiple rooms.
     * ORDER BY postExpiredAt because boostExpiredAt cannot be selected after DISTINCT.
     */
    @Query("SELECT DISTINCT p FROM Post p JOIN p.property prop JOIN prop.rooms r " +
           "WHERE p.status = 'ACTIVE' AND p.postExpiredAt > :now " +
           "AND r.status = 'AVAILABLE' AND r.price <= :maxPrice " +
           "AND (:provinceCode IS NULL OR prop.ward.province.code = :provinceCode) " +
           "ORDER BY p.postExpiredAt DESC")
    List<Post> findRelaxedRecommendedPosts(
            @Param("now") LocalDateTime now,
            @Param("maxPrice") Double maxPrice,
            @Param("provinceCode") String provinceCode);

    /**
     * Bounding box query for geo-based recommendation (Step 1 of 2: coarse DB filter).
     * Step 2: Haversine filter in Java trims the bounding box corners into a true circle.
     * Only returns posts where the property has coordinates set.
     */
    @Query("SELECT p FROM Post p JOIN p.property prop " +
           "WHERE p.status = 'ACTIVE' AND p.postExpiredAt > :now " +
           "AND prop.latitude IS NOT NULL AND prop.longitude IS NOT NULL " +
           "AND prop.latitude BETWEEN :minLat AND :maxLat " +
           "AND prop.longitude BETWEEN :minLng AND :maxLng")
    List<Post> findPostsInBoundingBox(
            @Param("now") LocalDateTime now,
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng,
            @Param("maxLng") Double maxLng);

    /**
     * Quick count of available posts in a province within budget — used by REFINING phase
     * to decide whether to ask the user for more filters.
     */
    @Query("SELECT COUNT(DISTINCT p) FROM Post p JOIN p.property prop JOIN prop.rooms r " +
           "WHERE p.status = 'ACTIVE' AND p.postExpiredAt > :now " +
           "AND r.status = 'AVAILABLE' AND r.price <= :maxPrice " +
           "AND prop.ward.province.code = :provinceCode")
    long countByProvinceAndBudget(@Param("now") LocalDateTime now,
                                   @Param("maxPrice") Double maxPrice,
                                   @Param("provinceCode") String provinceCode);
}
