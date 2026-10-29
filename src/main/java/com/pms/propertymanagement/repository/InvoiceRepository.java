package com.pms.propertymanagement.repository;

import com.pms.propertymanagement.entity.Invoice;
import com.pms.propertymanagement.enums.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByRoom_IdAndMonthAndYear(Long roomId, Integer month, Integer year);

    boolean existsByRoom_IdAndMonthAndYear(Long roomId, Integer month, Integer year);

    Optional<Invoice> findByInvoiceCode(String invoiceCode);

    List<Invoice> findByRoom_IdOrderByYearDescMonthDesc(Long roomId);

    List<Invoice> findByRoom_Property_Owner_IdOrderByCreatedAtDesc(Long ownerId);

    List<Invoice> findByTenant_IdOrderByYearDescMonthDesc(Long tenantId);

    List<Invoice> findByTenant_PhoneOrderByYearDescMonthDesc(String phone);

    List<Invoice> findByContract_IdAndStatus(Long contractId, InvoiceStatus status);

    @Query("SELECT i FROM Invoice i WHERE i.room.property.owner.id = :ownerId " +
           "AND (:propertyId IS NULL OR i.room.property.id = :propertyId) " +
           "AND (:roomId IS NULL OR i.room.id = :roomId) " +
           "AND (:tenantId IS NULL OR i.tenant.id = :tenantId) " +
           "AND (:month IS NULL OR i.month = :month) " +
           "AND (:year IS NULL OR i.year = :year) " +
           "AND (:status IS NULL OR i.status = :status) " +
           "ORDER BY i.year DESC, i.month DESC, i.id DESC")
    Page<Invoice> searchInvoices(
            @Param("ownerId") Long ownerId,
            @Param("propertyId") Long propertyId,
            @Param("roomId") Long roomId,
            @Param("tenantId") Long tenantId,
            @Param("month") Integer month,
            @Param("year") Integer year,
            @Param("status") InvoiceStatus status,
            Pageable pageable
    );

    @Query("SELECT i FROM Invoice i WHERE :phone IS NOT NULL AND :phone <> '' AND " +
           "(i.tenant.phone = :phone OR EXISTS (SELECT 1 FROM i.contract.tenants t WHERE t.phone = :phone)) " +
           "ORDER BY i.year DESC, i.month DESC, i.id DESC")
    List<Invoice> findInvoicesForTenant(@Param("tenantId") Long tenantId, @Param("phone") String phone);

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.room.property.owner.id = :ownerId AND i.status = :status")
    long countByOwnerIdAndStatus(@Param("ownerId") Long ownerId, @Param("status") InvoiceStatus status);

    @Query("SELECT COALESCE(SUM(i.totalAmount), 0) FROM Invoice i WHERE i.room.property.owner.id = :ownerId AND i.month = :month AND i.year = :year")
    Double sumTotalAmountByOwnerAndMonthAndYear(@Param("ownerId") Long ownerId, @Param("month") Integer month, @Param("year") Integer year);

    @Query("SELECT COALESCE(SUM(i.remainingAmount), 0) FROM Invoice i WHERE i.room.property.owner.id = :ownerId AND i.status <> 'PAID' AND i.status <> 'CANCELLED'")
    Double sumRemainingDebtByOwner(@Param("ownerId") Long ownerId);

    @Query("SELECT COALESCE(SUM(i.paidAmount), 0) FROM Invoice i WHERE i.room.property.owner.id = :ownerId AND i.year = :year")
    Double sumPaidRevenueByOwnerAndYear(@Param("ownerId") Long ownerId, @Param("year") Integer year);

    @Query("SELECT COALESCE(SUM(i.paidAmount), 0) FROM Invoice i WHERE i.room.property.owner.id = :ownerId AND i.month = :month AND i.year = :year")
    Double sumPaidRevenueByOwnerAndMonthAndYear(@Param("ownerId") Long ownerId, @Param("month") Integer month, @Param("year") Integer year);

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.room.property.owner.id = :ownerId")
    long countByOwnerId(@Param("ownerId") Long ownerId);

    @Query("SELECT i FROM Invoice i WHERE i.room.property.owner.id = :ownerId AND i.remainingAmount > 0 ORDER BY i.dueDate ASC")
    List<Invoice> findTopDebtorsByOwner(@Param("ownerId") Long ownerId, org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths = {"room", "room.property", "tenant"})
    @Query("SELECT i FROM Invoice i WHERE i.room.property.owner.id = :ownerId " +
           "AND (:propertyId IS NULL OR i.room.property.id = :propertyId) " +
           "AND (:status IS NULL OR i.status = :status) " +
           "AND (:month IS NULL OR i.month = :month) " +
           "AND (:year IS NULL OR i.year = :year) " +
           "AND (:keyword IS NULL OR :keyword = '' " +
           "     OR LOWER(i.invoiceCode) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(i.room.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(i.tenant.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Invoice> searchInvoicesForOwner(
            @Param("ownerId") Long ownerId,
            @Param("propertyId") Long propertyId,
            @Param("status") InvoiceStatus status,
            @Param("month") Integer month,
            @Param("year") Integer year,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.room.property.owner.id = :ownerId " +
           "AND i.transferNotifiedAt IS NOT NULL " +
           "AND i.status IN (com.pms.propertymanagement.enums.InvoiceStatus.UNPAID, " +
           "                 com.pms.propertymanagement.enums.InvoiceStatus.PARTIALLY_PAID, " +
           "                 com.pms.propertymanagement.enums.InvoiceStatus.OVERDUE)")
    long countTransferNotifiedByOwnerId(@Param("ownerId") Long ownerId);
}
