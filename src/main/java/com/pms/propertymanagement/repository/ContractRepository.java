package com.pms.propertymanagement.repository;

import com.pms.propertymanagement.entity.Contract;
import com.pms.propertymanagement.entity.Room;
import com.pms.propertymanagement.enums.ContractStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {
    List<Contract> findByStatus(ContractStatus status);
    List<Contract> findByRoom_Property_Owner_Id(Long ownerId);
    Page<Contract> findByRoom_Property_Owner_Id(Long ownerId, Pageable pageable);
    
    @Query("SELECT c FROM Contract c WHERE c.room.property.owner.id = :ownerId AND (:status IS NULL OR c.status = :status)")
    Page<Contract> findByOwnerIdAndStatus(Long ownerId, ContractStatus status, Pageable pageable);

    @Query("SELECT c FROM Contract c WHERE c.room.property.owner.id = :ownerId AND (:status IS NULL OR c.status = :status) AND (c.code LIKE %:keyword% OR c.room.name LIKE %:keyword% OR c.representative.fullName LIKE %:keyword%)")
    Page<Contract> searchByOwnerIdAndStatusAndKeyword(Long ownerId, ContractStatus status, String keyword, Pageable pageable);

    @Query("""
            select distinct r
            from Contract c
            join c.room r
            join fetch r.property
            left join c.tenants t
            where (t.id = :tenantId or c.representative.id = :tenantId)
              and c.status = com.pms.propertymanagement.enums.ContractStatus.ACTIVE
            """)
    List<Room> findActiveRoomsByTenantId(@Param("tenantId") Long tenantId);

    @Query("""
            select distinct r
            from Contract c
            join c.room r
            join fetch r.property
            left join c.tenants t
            where ((:tenantId IS NOT NULL AND (t.id = :tenantId or c.representative.id = :tenantId))
                   or (:phone IS NOT NULL AND :phone <> '' AND (t.phone = :phone or c.representative.phone = :phone)))
              and c.status = com.pms.propertymanagement.enums.ContractStatus.ACTIVE
            """)
    List<Room> findActiveRoomsByTenantIdOrPhone(@Param("tenantId") Long tenantId, @Param("phone") String phone);

    @Query("""
            select count(c)
            from Contract c
            left join c.tenants t
            where c.room.id = :roomId
              and (t.id = :tenantId or c.representative.id = :tenantId)
              and c.status = com.pms.propertymanagement.enums.ContractStatus.ACTIVE
            """)
    long countActiveContractsByTenantIdAndRoomId(@Param("tenantId") Long tenantId, @Param("roomId") Long roomId);

    @Query("""
            select count(c)
            from Contract c
            left join c.tenants t
            where c.room.id = :roomId
              and ((:tenantId IS NOT NULL AND (t.id = :tenantId or c.representative.id = :tenantId))
                   or (:phone IS NOT NULL AND :phone <> '' AND (t.phone = :phone or c.representative.phone = :phone)))
              and c.status = com.pms.propertymanagement.enums.ContractStatus.ACTIVE
            """)
    long countActiveContractsByTenantOrPhoneAndRoomId(@Param("tenantId") Long tenantId, @Param("phone") String phone, @Param("roomId") Long roomId);

    @Query("""
            select count(c)
            from Contract c
            left join c.tenants t
            where (t.id = :tenantId or c.representative.id = :tenantId)
              and c.status = com.pms.propertymanagement.enums.ContractStatus.ACTIVE
            """)
    long countActiveContractsByTenantId(@Param("tenantId") Long tenantId);

    @Query("""
            select count(c)
            from Contract c
            where c.room.id = :roomId
              and c.status = com.pms.propertymanagement.enums.ContractStatus.ACTIVE
            """)
    long countActiveContractsByRoomId(@Param("roomId") Long roomId);
    @Query("SELECT COUNT(c) FROM Contract c " +
           "WHERE c.room.property.owner.id = :ownerId " +
           "AND c.status = :status")
    long countContractsByOwnerAndStatus(@Param("ownerId") Long ownerId, @Param("status") ContractStatus status);

    @Query("SELECT SUM(c.rentPrice) FROM Contract c " +
           "WHERE c.room.property.owner.id = :ownerId " +
           "AND c.status = :status")
    Double sumRentPriceByOwnerAndStatus(@Param("ownerId") Long ownerId, @Param("status") ContractStatus status);

    java.util.Optional<Contract> findFirstByRoom_IdAndStatus(Long roomId, ContractStatus status);

    List<Contract> findByRoom_Property_IdAndStatus(Long propertyId, ContractStatus status);

    List<Contract> findByRoom_Property_Owner_IdAndStatus(Long ownerId, ContractStatus status);

    List<Contract> findByRepresentative_Id(Long tenantId);

    List<Contract> findByRepresentative_Phone(String phone);
}
