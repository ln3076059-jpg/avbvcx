package com.pms.propertymanagement.repository;

import com.pms.propertymanagement.entity.UserWallet;
import com.pms.propertymanagement.entity.WalletTransaction;
import com.pms.propertymanagement.enums.TransactionStatus;
import com.pms.propertymanagement.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    Page<WalletTransaction> findByWalletOrderByCreatedAtDesc(UserWallet wallet, Pageable pageable);

    List<WalletTransaction> findByWalletAndTypeOrderByCreatedAtDesc(UserWallet wallet, TransactionType type);

    @Query("SELECT SUM(wt.amount) FROM WalletTransaction wt WHERE wt.wallet = :wallet AND wt.type = :type AND wt.createdAt >= :fromDate")
    BigDecimal sumAmountByWalletAndTypeAndDateAfter(@Param("wallet") UserWallet wallet,
                                                   @Param("type") TransactionType type,
                                                   @Param("fromDate") LocalDateTime fromDate);

    Optional<WalletTransaction> findByVnpayTxnRef(String vnpayTxnRef);

    Optional<WalletTransaction> findByVnpayTxnRefAndStatus(String vnpayTxnRef, TransactionStatus status);

    @Query("SELECT COUNT(wt) FROM WalletTransaction wt WHERE wt.wallet.user.id = :userId AND wt.createdAt >= :fromDate")
    long countByUserIdAndDateAfter(@Param("userId") Long userId, @Param("fromDate") LocalDateTime fromDate);

    @Query("SELECT COALESCE(SUM(wt.amount), 0) FROM WalletTransaction wt WHERE wt.type IN :types AND wt.status = :status")
    BigDecimal sumAmountByTypesAndStatus(@Param("types") List<TransactionType> types,
                                         @Param("status") TransactionStatus status);

    @Query("SELECT COALESCE(SUM(wt.amount), 0) FROM WalletTransaction wt WHERE wt.type IN :types AND wt.status = :status AND wt.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal sumAmountByTypesAndStatusAndDateRange(@Param("types") List<TransactionType> types,
                                                     @Param("status") TransactionStatus status,
                                                     @Param("startDate") LocalDateTime startDate,
                                                     @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(DISTINCT wt.wallet.user.id) FROM WalletTransaction wt WHERE wt.type IN :types AND wt.status = :status")
    long countDistinctUsersByTypesAndStatus(@Param("types") List<TransactionType> types,
                                            @Param("status") TransactionStatus status);

    @Query("SELECT wt FROM WalletTransaction wt JOIN FETCH wt.wallet w JOIN FETCH w.user WHERE wt.type IN :types AND wt.status = :status ORDER BY wt.createdAt DESC")
    List<WalletTransaction> findRecentByTypesAndStatus(@Param("types") List<TransactionType> types,
                                                       @Param("status") TransactionStatus status,
                                                       Pageable pageable);
}
