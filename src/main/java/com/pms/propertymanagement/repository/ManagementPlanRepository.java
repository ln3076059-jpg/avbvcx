package com.pms.propertymanagement.repository;

import com.pms.propertymanagement.entity.ManagementPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ManagementPlanRepository extends JpaRepository<ManagementPlan, Long> {
    
    Optional<ManagementPlan> findByCode(String code);
    
    List<ManagementPlan> findByActiveTrueOrderBySortOrderAsc();
    
    List<ManagementPlan> findAllByOrderBySortOrderAsc();
    
    @Query("SELECT mp FROM ManagementPlan mp WHERE mp.isActive = true ORDER BY mp.sortOrder")
    List<ManagementPlan> findAllActiveOrderBySortOrder();
    
    @Query("SELECT COUNT(mp) FROM ManagementPlan mp WHERE mp.isActive = true")
    Long countActivePlans();
}