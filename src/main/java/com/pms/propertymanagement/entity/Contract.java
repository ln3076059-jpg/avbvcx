package com.pms.propertymanagement.entity;

import com.pms.propertymanagement.enums.ContractStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "contracts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code; // Mã hợp đồng

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private LocalDate startDate; // Ngày bắt đầu

    @Column(nullable = false)
    private LocalDate endDate; // Ngày hết hạn

    @Column(nullable = false)
    private Double rentPrice; // Giá thuê

    @Column(nullable = false)
    private Double deposit; // Giá cọc

    @Column(nullable = false)
    private Integer paymentCycle; // Chu kỳ thanh toán (tháng)

    @Column(nullable = false)
    private Boolean isElectricityWaterIncluded = false; // Giá thuê đã bao gồm điện nước?

    private Integer bedCount; // Số giường

    @Column(columnDefinition = "TEXT")
    private String note; // Ghi chú

    @Enumerated(EnumType.STRING)
    private ContractStatus status = ContractStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "representative_id")
    private Tenant representative; // Người đại diện

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "contract_tenants",
            joinColumns = @JoinColumn(name = "contract_id"),
            inverseJoinColumns = @JoinColumn(name = "tenant_id")
    )
    private Set<Tenant> tenants = new HashSet<>();

    // Dịch vụ kèm theo hợp đồng (snapshot tại thời điểm tạo)
    // Hoặc tham chiếu đến ServiceItem hiện tại.
    // Dựa trên UI, có vẻ nó chỉ hiển thị dịch vụ của phòng. 
    // Tuy nhiên, lưu lại để chắc chắn.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "contract_services",
            joinColumns = @JoinColumn(name = "contract_id"),
            inverseJoinColumns = @JoinColumn(name = "service_id")
    )
    private Set<ServiceItem> services = new HashSet<>();

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        if (this.code == null) {
            this.code = "HD-" + System.currentTimeMillis();
        }
    }
}
