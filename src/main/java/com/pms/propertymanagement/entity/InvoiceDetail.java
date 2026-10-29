package com.pms.propertymanagement.entity;

import com.pms.propertymanagement.enums.InvoiceDetailType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "invoice_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false)
    private Double quantity = 1.0;

    @Column(name = "unit_price", nullable = false)
    private Double unitPrice = 0.0;

    @Column(nullable = false)
    private Double amount = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InvoiceDetailType type;

    public InvoiceDetail(Invoice invoice, String description, Double quantity, Double unitPrice, Double amount, InvoiceDetailType type) {
        this.invoice = invoice;
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.amount = amount;
        this.type = type;
    }
}
