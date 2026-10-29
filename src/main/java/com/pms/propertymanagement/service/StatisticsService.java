package com.pms.propertymanagement.service;

import com.pms.propertymanagement.dto.AdminStatisticsDTO;
import com.pms.propertymanagement.dto.ApiStatisticDTO;
import com.pms.propertymanagement.dto.OwnerStatisticsDTO;

import java.util.List;

import com.pms.propertymanagement.dto.DashboardActivityDTO;

public interface StatisticsService {
    OwnerStatisticsDTO getOwnerStatistics(Long ownerId);
    java.util.List<com.pms.propertymanagement.dto.ChartDataDTO> getOwnerIncomeChart(Long ownerId);
    java.util.List<com.pms.propertymanagement.dto.ChartDataDTO> getOwnerIncomeChart(Long ownerId, java.time.LocalDate startDate, java.time.LocalDate endDate);
    
    java.util.List<DashboardActivityDTO> getRecentActivities(Long ownerId);
    java.util.List<com.pms.propertymanagement.entity.Invoice> getTopDebtors(Long ownerId);
    
    AdminStatisticsDTO getAdminStatistics();
    List<ApiStatisticDTO> getApiStatistics();
}
