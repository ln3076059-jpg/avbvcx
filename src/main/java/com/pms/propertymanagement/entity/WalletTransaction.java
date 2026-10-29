package com.pms.propertymanagement.entity;

import com.pms.propertymanagement.enums.TransactionStatus;
import com.pms.propertymanagement.enums.TransactionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_transactions")
@Getter
@Setter
public class WalletTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private UserWallet wallet;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TransactionType type;
    
    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;
    
    @Column(name = "balance_after", precision = 15, scale = 2, nullable = false)
    private BigDecimal balanceAfter;
    
    @Column(length = 500)
    private String description;
    
    @Column(name = "reference_id", length = 100)
    private String referenceId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status = TransactionStatus.COMPLETED;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    // For VNPay integration
    @Column(name = "vnpay_txn_ref", length = 100)
    private String vnpayTxnRef;
    
    @Column(name = "vnpay_response_code", length = 20)
    private String vnpayResponseCode;
    
    @Column(name = "vnpay_transaction_no", length = 50)
    private String vnpayTransactionNo;
    
    @Column(name = "vnpay_bank_code", length = 20)
    private String vnpayBankCode;
    
    @Column(name = "vnpay_pay_date", length = 20)
    private String vnpayPayDate;
    
    @Column(name = "vnpay_raw_query", columnDefinition = "TEXT")
    private String vnpayRawQuery;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "remaining_balance", precision = 15, scale = 2)
    private BigDecimal remainingBalance;
    
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}