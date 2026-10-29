package com.pms.propertymanagement.entity;

import com.pms.propertymanagement.enums.InvoiceStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "invoices",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_invoice_room_month_year", columnNames = {"room_id", "month", "year"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "invoice_code", unique = true, nullable = false, length = 50)
    private String invoiceCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private Integer month;

    @Column(nullable = false)
    private Integer year;

    @Column(name = "room_price", nullable = false)
    private Double roomPrice = 0.0;

    @Column(name = "is_prorated")
    private Boolean isProrated = false;

    @Column(name = "actual_days")
    private Integer actualDays;

    @Column(name = "electricity_usage", nullable = false)
    private Double electricityUsage = 0.0;

    @Column(name = "electricity_price", nullable = false)
    private Double electricityPrice = 0.0;

    @Column(name = "electricity_amount", nullable = false)
    private Double electricityAmount = 0.0;

    @Column(name = "water_usage", nullable = false)
    private Double waterUsage = 0.0;

    @Column(name = "water_price", nullable = false)
    private Double waterPrice = 0.0;

    @Column(name = "water_amount", nullable = false)
    private Double waterAmount = 0.0;

    @Column(name = "service_amount", nullable = false)
    private Double serviceAmount = 0.0;

    @Column(name = "other_amount", nullable = false)
    private Double otherAmount = 0.0;

    @Column(name = "total_amount", nullable = false)
    private Double totalAmount = 0.0;

    @Column(name = "paid_amount", nullable = false)
    private Double paidAmount = 0.0;

    @Column(name = "remaining_amount", nullable = false)
    private Double remainingAmount = 0.0;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InvoiceStatus status = InvoiceStatus.UNPAID;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceDetail> details = new ArrayList<>();

    @OneToMany(mappedBy = "invoice")
    private List<Payment> payments = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "transfer_notified_at")
    private LocalDateTime transferNotifiedAt;

    @Column(name = "transfer_note", length = 500)
    private String transferNote;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (invoiceCode == null || invoiceCode.isEmpty()) {
            int rand = java.util.concurrent.ThreadLocalRandom.current().nextInt(1000, 9999);
            this.invoiceCode = "INV-" + (room != null ? room.getId() : "0") + "-" + month + year + "-" + (System.currentTimeMillis() % 100000) + rand;
        }
        recalculateTotals();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        recalculateTotals();
    }

    public void recalculateTotals() {
        if (this.electricityUsage != null && this.electricityPrice != null && this.electricityPrice > 0) {
            this.electricityAmount = (double) Math.round(Math.max(0.0, this.electricityUsage) * this.electricityPrice);
        } else if (this.electricityAmount == null) {
            this.electricityAmount = 0.0;
        } else {
            this.electricityAmount = (double) Math.round(Math.max(0.0, this.electricityAmount));
        }

        if (this.waterUsage != null && this.waterPrice != null && this.waterPrice > 0) {
            this.waterAmount = (double) Math.round(Math.max(0.0, this.waterUsage) * this.waterPrice);
        } else if (this.waterAmount == null) {
            this.waterAmount = 0.0;
        } else {
            this.waterAmount = (double) Math.round(Math.max(0.0, this.waterAmount));
        }

        double rawTotal = (roomPrice != null ? roomPrice : 0.0)
                + this.electricityAmount
                + this.waterAmount
                + (serviceAmount != null ? serviceAmount : 0.0)
                + (otherAmount != null ? otherAmount : 0.0);
        this.totalAmount = (double) Math.round(Math.max(0.0, rawTotal));
        double currentPaid = (double) Math.round(paidAmount != null ? Math.max(0.0, paidAmount) : 0.0);
        this.paidAmount = currentPaid;
        this.remainingAmount = (double) Math.round(Math.max(0.0, this.totalAmount - currentPaid));
        updateStatus();
    }

    public void updateStatus() {
        if (status == InvoiceStatus.CANCELLED) return;

        double paid = (paidAmount != null ? paidAmount : 0.0);
        double total = (totalAmount != null ? totalAmount : 0.0);

        if (paid >= total && total > 0) {
            this.status = InvoiceStatus.PAID;
        } else if (paid > 0) {
            if (dueDate != null && LocalDate.now().isAfter(dueDate)) {
                this.status = InvoiceStatus.OVERDUE;
            } else {
                this.status = InvoiceStatus.PARTIALLY_PAID;
            }
        } else {
            if (dueDate != null && LocalDate.now().isAfter(dueDate)) {
                this.status = InvoiceStatus.OVERDUE;
            } else {
                this.status = InvoiceStatus.UNPAID;
            }
        }
    }
}
