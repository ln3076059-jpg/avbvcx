package com.pms.propertymanagement.repository;

import com.pms.propertymanagement.entity.Payment;
import com.pms.propertymanagement.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("SELECT SUM(p.amount) FROM Payment p " +
           "WHERE p.owner.id = :ownerId " +
           "AND p.status = :status " +
           "AND p.paymentDate BETWEEN :startDate AND :endDate")
    Double calculateTotalIncomeByOwnerAndDateRange(
            @Param("ownerId") Long ownerId,
            @Param("status") PaymentStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT new com.pms.propertymanagement.dto.ChartDataDTO(" +
           "CONCAT(FUNCTION('MONTH', p.paymentDate), '/', FUNCTION('YEAR', p.paymentDate)), " +
           "SUM(p.amount)) " +
           "FROM Payment p " +
           "WHERE p.owner.id = :ownerId " +
           "AND p.status = :status " +
           "AND p.paymentDate >= :startDate " +
           "GROUP BY FUNCTION('YEAR', p.paymentDate), FUNCTION('MONTH', p.paymentDate) " +
           "ORDER BY FUNCTION('YEAR', p.paymentDate), FUNCTION('MONTH', p.paymentDate)")
    List<com.pms.propertymanagement.dto.ChartDataDTO> findMonthlyIncome(
            @Param("ownerId") Long ownerId,
            @Param("status") PaymentStatus status,
            @Param("startDate") LocalDateTime startDate
    );
    
    List<Payment> findTop5ByOwnerIdOrderByPaymentDateDesc(Long ownerId);

    List<Payment> findTop5ByOwnerIdAndPaymentDateLessThanEqualOrderByPaymentDateDesc(Long ownerId, LocalDateTime date);

    List<Payment> findByInvoice_IdOrderByPaymentDateDesc(Long invoiceId);

    List<Payment> findByOwner_IdOrderByPaymentDateDesc(Long ownerId);

    java.util.Optional<Payment> findByTransactionCode(String transactionCode);

    @Query("SELECT p FROM Payment p WHERE :phone IS NOT NULL AND :phone <> '' AND " +
           "(p.invoice.tenant.phone = :phone OR EXISTS (SELECT 1 FROM p.contract.tenants t WHERE t.phone = :phone)) " +
           "ORDER BY p.paymentDate DESC")
    List<Payment> findByTenantIdOrPhone(@Param("tenantId") Long tenantId, @Param("phone") String phone);

    @Query("SELECT p FROM Payment p WHERE p.owner.id = :ownerId " +
           "AND (:propertyId IS NULL OR p.invoice.room.property.id = :propertyId) " +
           "AND (:roomId IS NULL OR p.invoice.room.id = :roomId) " +
           "AND (:tenantId IS NULL OR p.invoice.tenant.id = :tenantId) " +
           "ORDER BY p.paymentDate DESC")
    Page<Payment> searchPayments(
            @Param("ownerId") Long ownerId,
            @Param("propertyId") Long propertyId,
            @Param("roomId") Long roomId,
            @Param("tenantId") Long tenantId,
            Pageable pageable
    );
}
