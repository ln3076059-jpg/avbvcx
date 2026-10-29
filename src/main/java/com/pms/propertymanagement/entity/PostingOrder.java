package com.pms.propertymanagement.entity;

import com.pms.propertymanagement.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "posting_orders", indexes = {
        @Index(name = "idx_posting_orders_owner", columnList = "owner_id"),
        @Index(name = "idx_posting_orders_txn_ref", columnList = "vnp_txn_ref")
})
@Getter
@Setter
public class PostingOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Owner
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    // Package
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id", nullable = false)
    private PostingPackage postingPackage;

    @Column(nullable = false)
    private int amount; // VND

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(nullable = false)
    private int remainingUses = 0; // paid -> set = usageLimit

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime paidAt;

    // VNPay fields
    @Column(name = "vnp_txn_ref", unique = true, length = 100)
    private String vnpTxnRef;

    @Column(name = "vnp_transaction_no", length = 50)
    private String vnpTransactionNo;

    @Column(name = "vnp_response_code", length = 10)
    private String vnpResponseCode;

    @Column(name = "vnp_bank_code", length = 20)
    private String vnpBankCode;

    @Column(name = "vnp_pay_date", length = 20)
    private String vnpPayDate;

    @Column(name = "vnp_raw_query", columnDefinition = "TEXT")
    private String vnpRawQuery;
}
