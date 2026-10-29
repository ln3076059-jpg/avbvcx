package com.pms.propertymanagement.entity;

import com.pms.propertymanagement.enums.RoomStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false)
    private Double price;

    private Double deposit; // Giá cọc

    private Double area;

    private Integer maxOccupancy; // Số người ở tối đa

    private Integer bedCount; // Số giường

    private Integer paymentCycle; // Chu kỳ thu tiền (tháng)

    private Boolean isElectricityWaterIncluded = false; // Giá thuê đã bao gồm điện nước?

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "room_services",
            joinColumns = @JoinColumn(name = "room_id"),
            inverseJoinColumns = @JoinColumn(name = "service_id")
    )
    private Set<ServiceItem> services = new HashSet<>();

    @Enumerated(EnumType.STRING)
    private RoomStatus status = RoomStatus.AVAILABLE;

    @Column(columnDefinition = "TEXT")
    private String description; // Ghi chú

    private LocalDateTime createdAt = LocalDateTime.now();
}
