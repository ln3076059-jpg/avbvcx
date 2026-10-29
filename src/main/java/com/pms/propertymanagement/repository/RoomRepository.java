package com.pms.propertymanagement.repository;

import com.pms.propertymanagement.entity.Room;
import com.pms.propertymanagement.enums.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
    
    @Query("SELECT r FROM Room r WHERE r.property.owner.username = :username")
    List<Room> findAllByOwnerUsername(String username);

    List<Room> findByProperty_Id(Long propertyId);
    
    List<Room> findByPropertyIdAndStatus(Long propertyId, RoomStatus status);

    List<Room> findByProperty_Owner_Id(Long ownerId);

    @Query("SELECT COUNT(r) FROM Room r WHERE r.property.owner.id = :ownerId AND r.status = :status")
    long countByOwnerIdAndStatus(Long ownerId, RoomStatus status);

    @Query("SELECT COUNT(r) FROM Room r WHERE r.property.owner.id = :ownerId")
    long countByOwnerId(Long ownerId);

    long countByStatus(RoomStatus status);

    List<Room> findByStatus(RoomStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);
}
