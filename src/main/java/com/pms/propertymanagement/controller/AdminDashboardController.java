package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.dto.AdminStatisticsDTO;
import com.pms.propertymanagement.dto.ApiStatisticDTO;
import com.pms.propertymanagement.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final StatisticsService statisticsService;

    @GetMapping({"", "/"})
    public String index() {
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        AdminStatisticsDTO stats = statisticsService.getAdminStatistics();
        model.addAttribute("stats", stats);
        
        List<ApiStatisticDTO> apiStats = statisticsService.getApiStatistics();
        model.addAttribute("apiStats", apiStats);
        
        model.addAttribute("activePage", "dashboard");
        model.addAttribute("pageTitle", "Bảng Điều Khiển Tổng Quan");
        model.addAttribute("content", "admin/dashboard");
        return "layout/admin-layout";
    }
}
