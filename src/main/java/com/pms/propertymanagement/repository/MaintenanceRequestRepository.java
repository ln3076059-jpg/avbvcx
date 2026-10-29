package com.pms.propertymanagement.repository;

import com.pms.propertymanagement.entity.MaintenanceRequest;
import com.pms.propertymanagement.enums.MaintenanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Long> {
    @EntityGraph(attributePaths = {"room"})
    Optional<MaintenanceRequest> findByIdAndTenant_Id(Long id, Long tenantId);

    @EntityGraph(attributePaths = {"room"})
    List<MaintenanceRequest> findByTenant_IdOrderByCreatedAtDesc(Long tenantId);
    List<MaintenanceRequest> findByRoom_IdOrderByCreatedAtDesc(Long roomId);

    @EntityGraph(attributePaths = {"room", "room.property", "tenant", "staff"})
    List<MaintenanceRequest> findByRoom_Property_Owner_IdOrderByCreatedAtDesc(Long ownerId);

    @EntityGraph(attributePaths = {"room", "room.property", "tenant", "staff"})
    Optional<MaintenanceRequest> findByIdAndRoom_Property_Owner_Id(Long id, Long ownerId);

    @EntityGraph(attributePaths = {"room", "room.property", "tenant", "staff"})
    List<MaintenanceRequest> findByStaff_IdOrderByCreatedAtDesc(Long staffId);

    @EntityGraph(attributePaths = {"room", "room.property", "tenant", "staff"})
    Optional<MaintenanceRequest> findByIdAndStaff_Id(Long id, Long staffId);

    @EntityGraph(attributePaths = {"room", "room.property", "tenant", "staff"})
    List<MaintenanceRequest> findByRoom_Property_Owner_IdAndStatusOrderByCreatedAtDesc(Long ownerId, MaintenanceStatus status);

    long countByRoom_IdAndStatusIn(Long roomId, List<MaintenanceStatus> statuses);

    long countByRoom_Property_Owner_IdAndStatusIn(Long ownerId, List<MaintenanceStatus> statuses);
}
