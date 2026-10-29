package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.dto.OwnerStatisticsDTO;
import com.pms.propertymanagement.entity.Contract;
import com.pms.propertymanagement.entity.Invoice;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.enums.ContractStatus;
import com.pms.propertymanagement.enums.InvoiceStatus;
import com.pms.propertymanagement.enums.MaintenanceStatus;
import com.pms.propertymanagement.repository.ContractRepository;
import com.pms.propertymanagement.repository.InvoiceRepository;
import com.pms.propertymanagement.repository.MaintenanceRequestRepository;
import com.pms.propertymanagement.repository.MeterReadingRepository;
import com.pms.propertymanagement.service.StatisticsService;
import com.pms.propertymanagement.service.UserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/owner")
@RequiredArgsConstructor
public class OwnerDashboardController {

    private final StatisticsService statisticsService;
    private final UserService userService;
    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final InvoiceRepository invoiceRepository;
    private final ContractRepository contractRepository;
    private final MeterReadingRepository meterReadingRepository;

    @GetMapping("/dashboard")
    public String dashboard(
            Model model,
            HttpSession session,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login/owner";
        }

        user = userService.findById(user.getId()).orElse(user);

        OwnerStatisticsDTO stats = statisticsService.getOwnerStatistics(user.getId());
        model.addAttribute("stats", stats);

        if (startDate == null) {
            startDate = LocalDate.now().minusMonths(5).withDayOfMonth(1);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        model.addAttribute("incomeChartData", statisticsService.getOwnerIncomeChart(user.getId(), startDate, endDate));
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        // Add Recent Activities
        model.addAttribute("recentActivities", statisticsService.getRecentActivities(user.getId()));
        model.addAttribute("topDebtors", statisticsService.getTopDebtors(user.getId()));
        model.addAttribute("pendingMaintenanceCount",
                maintenanceRequestRepository.countByRoom_Property_Owner_IdAndStatusIn(
                        user.getId(), List.of(MaintenanceStatus.PENDING, MaintenanceStatus.REOPENED)));

        // Owner Invoices Metrics
        List<Invoice> ownerInvoices = invoiceRepository.findByRoom_Property_Owner_IdOrderByCreatedAtDesc(user.getId());
        long overdueInvoiceCount = ownerInvoices.stream()
                .filter(i -> i.getStatus() != InvoiceStatus.PAID && i.getStatus() != InvoiceStatus.CANCELLED)
                .count();
        double totalReceivableAmount = ownerInvoices.stream()
                .filter(i -> i.getStatus() != InvoiceStatus.PAID && i.getStatus() != InvoiceStatus.CANCELLED)
                .mapToDouble(i -> i.getRemainingAmount() != null ? i.getRemainingAmount()
                        : Math.max(0.0, (i.getTotalAmount() != null ? i.getTotalAmount() : 0.0) - (i.getPaidAmount() != null ? i.getPaidAmount() : 0.0)))
                .sum();
        long transferNotifiedCount = ownerInvoices.stream()
                .filter(i -> i.getStatus() != InvoiceStatus.PAID && i.getStatus() != InvoiceStatus.CANCELLED)
                .filter(i -> i.getTransferNotifiedAt() != null
                        || (i.getTransferNote() != null && !i.getTransferNote().isBlank()))
                .count();

        // Smart Portfolio Advisor Metrics
        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysFromNow = today.plusDays(30);
        int currentMonth = today.getMonthValue();
        int currentYear = today.getYear();

        List<Contract> activeContracts = contractRepository.findByRoom_Property_Owner_IdAndStatus(user.getId(), ContractStatus.ACTIVE);
        long expiringContractsCount = activeContracts.stream()
                .filter(c -> c.getEndDate() != null && !c.getEndDate().isBefore(today) && !c.getEndDate().isAfter(thirtyDaysFromNow))
                .count();
        long tenantRequestContractCount = activeContracts.stream()
                .filter(c -> c.getNote() != null && (c.getNote().contains("GIA HẠN") || c.getNote().contains("TRẢ PHÒNG")))
                .count();

        long unrecordedMeterRoomsCount = activeContracts.stream()
                .filter(c -> c.getRoom() != null && !Boolean.TRUE.equals(c.getIsElectricityWaterIncluded()))
                .filter(c -> !meterReadingRepository.existsByRoom_IdAndMonthAndYear(c.getRoom().getId(), currentMonth, currentYear))
                .count();

        double projectedIncome = (stats != null && stats.getProjectedIncome() != null) ? stats.getProjectedIncome() : 0.0;
        double totalIncome = (stats != null && stats.getTotalIncome() != null) ? stats.getTotalIncome() : 0.0;
        double collectionRate = projectedIncome > 0
                ? Math.min(100.0, Math.round((totalIncome * 1000.0) / projectedIncome) / 10.0)
                : 0.0;

        model.addAttribute("overdueInvoiceCount", overdueInvoiceCount);
        model.addAttribute("totalReceivableAmount", totalReceivableAmount);
        model.addAttribute("transferNotifiedCount", transferNotifiedCount);
        model.addAttribute("expiringContractsCount", expiringContractsCount);
        model.addAttribute("expiringContractCount", expiringContractsCount);
        model.addAttribute("tenantRequestContractCount", tenantRequestContractCount);
        model.addAttribute("unrecordedMeterRoomsCount", unrecordedMeterRoomsCount);
        model.addAttribute("collectionRate", collectionRate);

        model.addAttribute("content", "owner/dashboard");
        return "layout/owner-layout";
    }
}
