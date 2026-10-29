package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.entity.MaintenanceRequest;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.service.MaintenanceWorkflowService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/staff/maintenance")
@RequiredArgsConstructor
public class StaffMaintenanceController {
    private final MaintenanceWorkflowService maintenanceWorkflowService;

    @GetMapping
    public String list(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";
        List<MaintenanceRequest> requests = maintenanceWorkflowService.getRequestsForStaff(user);
        model.addAttribute("requests", requests);
        model.addAttribute("activeMenu", "staff-maintenance");
        model.addAttribute("content", "staff/maintenance/list");
        return "layout/staff-layout";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, HttpSession session, Model model,
                         @RequestParam(name = "success", required = false) String success) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";
        MaintenanceRequest req = maintenanceWorkflowService.getRequestDetailForStaff(id, user);
        model.addAttribute("request", req);
        model.addAttribute("success", success != null);
        model.addAttribute("activeMenu", "staff-maintenance");
        model.addAttribute("content", "staff/maintenance/detail");
        return "layout/staff-layout";
    }

    @PostMapping("/{id}/start")
    public String start(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";
        try {
            maintenanceWorkflowService.startRequest(id, user);
            redirectAttributes.addAttribute("success", "true");
        } catch (IllegalStateException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorStart", ex.getMessage());
        }
        return "redirect:/staff/maintenance/" + id;
    }

    @PostMapping("/{id}/complete")
    public String complete(@PathVariable Long id,
                           @RequestParam(name = "note", required = false) String note,
                           HttpSession session,
                           RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";
        if (!StringUtils.hasText(note)) {
            redirectAttributes.addFlashAttribute("errorComplete", "Vui lòng nhập ghi chú");
            redirectAttributes.addFlashAttribute("completeNote", note);
            return "redirect:/staff/maintenance/" + id;
        }
        try {
            maintenanceWorkflowService.completeRequest(id, user, note.trim());
            redirectAttributes.addAttribute("success", "true");
        } catch (IllegalStateException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorComplete", ex.getMessage());
            redirectAttributes.addFlashAttribute("completeNote", note);
        }
        return "redirect:/staff/maintenance/" + id;
    }
}
