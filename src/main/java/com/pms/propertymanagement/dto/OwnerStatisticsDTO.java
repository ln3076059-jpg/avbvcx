package com.pms.propertymanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerStatisticsDTO {
    private Double totalIncome; // Thực thu (từ Payments)
    private Double projectedIncome; // Dự kiến (từ Contracts)
    private long rentedRooms;
    
    // Room distribution
    private long totalRooms;
    private long availableRooms;
    private long maintenanceRooms;

    // Debt and invoice metrics
    private Double totalDebt;
    private Double monthlyBilledRevenue;
    private Double monthlyCollectedRevenue;
    private long totalInvoices;
    private long paidInvoices;
    private long unpaidInvoices;
    private long overdueInvoices;

    public OwnerStatisticsDTO(Double totalIncome, Double projectedIncome, long rentedRooms, long totalRooms, long availableRooms, long maintenanceRooms) {
        this.totalIncome = totalIncome;
        this.projectedIncome = projectedIncome;
        this.rentedRooms = rentedRooms;
        this.totalRooms = totalRooms;
        this.availableRooms = availableRooms;
        this.maintenanceRooms = maintenanceRooms;
        this.totalDebt = 0.0;
        this.monthlyBilledRevenue = 0.0;
        this.monthlyCollectedRevenue = 0.0;
        this.totalInvoices = 0;
        this.paidInvoices = 0;
        this.unpaidInvoices = 0;
        this.overdueInvoices = 0;
    }
}
