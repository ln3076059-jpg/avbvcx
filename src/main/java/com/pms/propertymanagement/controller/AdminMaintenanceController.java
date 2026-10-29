package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.entity.MaintenanceRequest;
import com.pms.propertymanagement.enums.MaintenanceStatus;
import com.pms.propertymanagement.repository.MaintenanceRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/maintenance")
@RequiredArgsConstructor
@Slf4j
public class AdminMaintenanceController {

    private final MaintenanceRequestRepository maintenanceRequestRepository;

    @GetMapping
    public String listRequests(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String statusFilter,
            Model model) {

        List<MaintenanceRequest> requests = maintenanceRequestRepository.findAll();

        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim().toLowerCase();
            requests = requests.stream()
                    .filter(r -> (r.getCode() != null && r.getCode().toLowerCase().contains(kw))
                            || (r.getDescription() != null && r.getDescription().toLowerCase().contains(kw))
                            || (r.getRoom() != null && r.getRoom().getName() != null && r.getRoom().getName().toLowerCase().contains(kw))
                            || (r.getTenant() != null && r.getTenant().getFullName() != null && r.getTenant().getFullName().toLowerCase().contains(kw)))
                    .collect(Collectors.toList());
        }

        if (statusFilter != null && !statusFilter.trim().isEmpty()) {
            try {
                MaintenanceStatus st = MaintenanceStatus.valueOf(statusFilter.trim().toUpperCase());
                requests = requests.stream()
                        .filter(r -> r.getStatus() == st)
                        .collect(Collectors.toList());
            } catch (IllegalArgumentException ignored) {}
        }

        requests.sort((a, b) -> Long.compare(b.getId(), a.getId()));

        model.addAttribute("requests", requests);
        model.addAttribute("keyword", keyword);
        model.addAttribute("statusFilter", statusFilter);
        model.addAttribute("statuses", MaintenanceStatus.values());
        model.addAttribute("activePage", "maintenance");
        model.addAttribute("pageTitle", "Quản Lý Yêu Cầu Sửa Chữa");
        model.addAttribute("content", "admin/maintenance/list");

        return "layout/admin-layout";
    }

    @GetMapping("/{id}")
    public String viewRequestDetail(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<MaintenanceRequest> reqOpt = maintenanceRequestRepository.findById(id);
        if (reqOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy yêu cầu sửa chữa ID: " + id);
            return "redirect:/admin/maintenance";
        }

        MaintenanceRequest req = reqOpt.get();
        model.addAttribute("req", req);
        model.addAttribute("statuses", MaintenanceStatus.values());
        model.addAttribute("activePage", "maintenance");
        model.addAttribute("pageTitle", "Chi Tiết Yêu Cầu: " + (req.getCode() != null ? req.getCode() : "YC-" + req.getId()));
        model.addAttribute("content", "admin/maintenance/detail");
        return "layout/admin-layout";
    }

    @PostMapping("/{id}/update-status")
    public String updateStatus(
            @PathVariable("id") Long id,
            @RequestParam("status") MaintenanceStatus status,
            @RequestParam(value = "completionNote", required = false) String completionNote,
            RedirectAttributes redirectAttributes) {

        Optional<MaintenanceRequest> reqOpt = maintenanceRequestRepository.findById(id);
        if (reqOpt.isPresent()) {
            MaintenanceRequest req = reqOpt.get();
            req.setStatus(status);
            if (completionNote != null && !completionNote.trim().isEmpty()) {
                req.setCompletionNote(completionNote.trim());
            }
            maintenanceRequestRepository.save(req);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật trạng thái yêu cầu sửa chữa thành công!");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Yêu cầu không tồn tại!");
        }
        return "redirect:/admin/maintenance/" + id;
    }

    @PostMapping("/{id}/delete")
    public String deleteRequest(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        Optional<MaintenanceRequest> reqOpt = maintenanceRequestRepository.findById(id);
        if (reqOpt.isPresent()) {
            maintenanceRequestRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa yêu cầu sửa chữa thành công!");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Yêu cầu không tồn tại!");
        }
        return "redirect:/admin/maintenance";
    }
}
