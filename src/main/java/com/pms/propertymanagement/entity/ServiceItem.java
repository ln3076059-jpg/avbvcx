package com.pms.propertymanagement.entity;

import com.pms.propertymanagement.enums.ServiceType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "service_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ServiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name; // Ví dụ: Tiền điện, Tiền rác

    @Column(nullable = false)
    private Double price;

    @Column(nullable = false, length = 50)
    private String unit; // Cái, Chiếc, Bình, Lần, Tháng, Người, Kwh, Khối

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServiceType type; // FIXED hoặc METERED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;
    
    private LocalDateTime createdAt = LocalDateTime.now();

}
