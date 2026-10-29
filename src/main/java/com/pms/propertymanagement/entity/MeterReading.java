package com.pms.propertymanagement.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "meter_readings",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_meter_reading_room_month_year", columnNames = {"room_id", "month", "year"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MeterReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private Integer month;

    @Column(nullable = false)
    private Integer year;

    @Column(name = "old_electricity_index", nullable = false)
    private Double oldElectricityIndex = 0.0;

    @Column(name = "new_electricity_index", nullable = false)
    private Double newElectricityIndex = 0.0;

    @Column(name = "electricity_usage", nullable = false)
    private Double electricityUsage = 0.0;

    @Column(name = "old_water_index", nullable = false)
    private Double oldWaterIndex = 0.0;

    @Column(name = "new_water_index", nullable = false)
    private Double newWaterIndex = 0.0;

    @Column(name = "water_usage", nullable = false)
    private Double waterUsage = 0.0;

    @Column(name = "electricity_price", nullable = false)
    private Double electricityPrice = 0.0;

    @Column(name = "water_price", nullable = false)
    private Double waterPrice = 0.0;

    @Column(name = "is_reset")
    private Boolean isReset = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        calculateUsage();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        calculateUsage();
    }

    public void calculateUsage() {
        if (newElectricityIndex != null && oldElectricityIndex != null) {
            if (this.electricityUsage == null || !Boolean.TRUE.equals(this.isReset)) {
                if (newElectricityIndex >= oldElectricityIndex) {
                    this.electricityUsage = newElectricityIndex - oldElectricityIndex;
                } else if (Boolean.TRUE.equals(this.isReset)) {
                    this.electricityUsage = newElectricityIndex;
                } else {
                    this.electricityUsage = 0.0;
                }
            }
        }
        if (newWaterIndex != null && oldWaterIndex != null) {
            if (this.waterUsage == null || !Boolean.TRUE.equals(this.isReset)) {
                if (newWaterIndex >= oldWaterIndex) {
                    this.waterUsage = newWaterIndex - oldWaterIndex;
                } else if (Boolean.TRUE.equals(this.isReset)) {
                    this.waterUsage = newWaterIndex;
                } else {
                    this.waterUsage = 0.0;
                }
            }
        }
    }
}
