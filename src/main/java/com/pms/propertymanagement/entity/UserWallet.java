package com.pms.propertymanagement.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_wallets")
@Getter
@Setter
public class UserWallet {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;
    
    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;
    
    @Column(name = "total_deposited", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalDeposited = BigDecimal.ZERO;
    
    @Column(name = "total_spent", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalSpent = BigDecimal.ZERO;
    
    // Auto-recharge settings (future feature)
    @Column(name = "auto_recharge_threshold", precision = 15, scale = 2)
    private BigDecimal autoRechargeThreshold;
    
    @Column(name = "auto_recharge_amount", precision = 15, scale = 2)
    private BigDecimal autoRechargeAmount;
    
    @Column(name = "auto_recharge_enabled")
    private boolean autoRechargeEnabled = false;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "last_transaction_at")
    private LocalDateTime lastTransactionAt;
    
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
    
    // Business methods
    public void addBalance(BigDecimal amount) {
        this.balance = this.balance.add(amount);
        this.totalDeposited = this.totalDeposited.add(amount);
        this.lastTransactionAt = LocalDateTime.now();
    }

    public void refundBalance(BigDecimal amount) {
        this.balance = this.balance.add(amount);
        this.totalSpent = this.totalSpent.subtract(amount).max(BigDecimal.ZERO);
        this.lastTransactionAt = LocalDateTime.now();
    }
    
    public void deductBalance(BigDecimal amount) {
        if (this.balance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }
        this.balance = this.balance.subtract(amount);
        this.totalSpent = this.totalSpent.add(amount);
        this.lastTransactionAt = LocalDateTime.now();
    }
    
    public boolean hasEnoughBalance(BigDecimal amount) {
        return this.balance.compareTo(amount) >= 0;
    }
}
