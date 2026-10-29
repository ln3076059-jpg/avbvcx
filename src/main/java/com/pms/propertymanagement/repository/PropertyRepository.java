package com.pms.propertymanagement.repository;

import com.pms.propertymanagement.entity.Property;
import com.pms.propertymanagement.enums.PropertyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyRepository extends JpaRepository<Property,Long> {
    List<Property> findByOwnerUsername(String ownerUsername);
    List<Property> findByOwnerId(Long ownerId);

    List<Property> findByCategory_Id(Long categoryId);

    // NEW ARCHITECTURE: Slug belongs to Post, not Property
    // Use PostRepository.findBySlug() to find posts by slug, then get property via post.getProperty()
    
    Long countByOwnerId(Long ownerId);

    // === NEW ARCHITECTURE: PropertyStatus-based methods ===

    /**
     * Find properties by owner and status
     */
    List<Property> findByOwnerIdAndStatus(Long ownerId, PropertyStatus status);

    /**
     * Find properties by IDs, owner, and status (for bulk operations)
     */
    @Query("SELECT p FROM Property p WHERE p.id IN :propertyIds AND p.owner.id = :ownerId AND p.status = :status")
    List<Property> findByIdInAndOwnerIdAndStatus(@Param("propertyIds") List<Long> propertyIds, 
                                                @Param("ownerId") Long ownerId, 
                                                @Param("status") PropertyStatus status);

    /**
     * Count properties by owner and status
     */
    int countByOwnerIdAndStatus(Long ownerId, PropertyStatus status);

    /**
     * Bulk update property status for specific owner and current status
     * IDEMPOTENT: Only updates properties with specified current status
     */
    @Modifying
    @Query("UPDATE Property p SET p.status = :newStatus, p.managementLockedAt = " +
           "CASE WHEN :newStatus = 'PLAN_LOCKED' THEN CURRENT_TIMESTAMP ELSE NULL END " +
           "WHERE p.owner.id = :ownerId AND p.status = :currentStatus")
    int updateStatusByOwnerIdAndCurrentStatus(@Param("ownerId") Long ownerId, 
                                            @Param("currentStatus") PropertyStatus currentStatus,
                                            @Param("newStatus") PropertyStatus newStatus);

    /**
     * Find all properties for owner (regardless of status) - for admin/analytics
     */
    List<Property> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    /**
     * Find properties by owner ordered by status (active first)
     */
    @Query("SELECT p FROM Property p WHERE p.owner.id = :ownerId ORDER BY " +
           "CASE p.status WHEN 'ACTIVE' THEN 0 WHEN 'PLAN_LOCKED' THEN 1 " +
           "WHEN 'ADMIN_LOCKED' THEN 2 ELSE 3 END, p.createdAt DESC")
    List<Property> findByOwnerIdOrderByStatusAndCreated(@Param("ownerId") Long ownerId);

    /**
     * Check if user has any active properties
     */
    boolean existsByOwnerIdAndStatus(Long ownerId, PropertyStatus status);

    /**
     * Find recently locked properties (for notifications/undo features)
     */
    @Query("SELECT p FROM Property p WHERE p.owner.id = :ownerId AND p.status = 'PLAN_LOCKED' " +
           "AND p.managementLockedAt > :since ORDER BY p.managementLockedAt DESC")
    List<Property> findRecentlyLockedProperties(@Param("ownerId") Long ownerId, 
                                              @Param("since") java.time.LocalDateTime since);

    /**
     * Find properties owned by user that do NOT have an associated Post yet
     * Used for post creation form - only show properties that can still have a post
     */
    @Query("SELECT p FROM Property p WHERE p.owner.id = :ownerId " +
           "AND p.status = 'ACTIVE' " +
           "AND NOT EXISTS (SELECT 1 FROM Post post WHERE post.property.id = p.id) " +
           "ORDER BY p.createdAt DESC")
    List<Property> findActivePropertiesWithoutPost(@Param("ownerId") Long ownerId);

    @Query(value = "SELECT p.* FROM properties p " +
            "WHERE p.category_id IS NULL " +
            "OR NOT EXISTS (SELECT 1 FROM categories c WHERE c.id = p.category_id)", nativeQuery = true)
    List<Property> findPropertiesWithInvalidCategory();
}
