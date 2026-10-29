package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.entity.ManagementPlan;
import com.pms.propertymanagement.repository.ManagementPlanRepository;
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
@RequestMapping("/admin/plans")
@RequiredArgsConstructor
@Slf4j
public class AdminPlanController {

    private final ManagementPlanRepository planRepository;

    @GetMapping
    public String listPlans(
            @RequestParam(value = "keyword", required = false) String keyword,
            Model model) {

        List<ManagementPlan> plans = planRepository.findAll();

        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim().toLowerCase();
            plans = plans.stream()
                    .filter(p -> (p.getName() != null && p.getName().toLowerCase().contains(kw))
                            || (p.getCode() != null && p.getCode().toLowerCase().contains(kw))
                            || (p.getDescription() != null && p.getDescription().toLowerCase().contains(kw)))
                    .collect(Collectors.toList());
        }

        plans.sort((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()));

        model.addAttribute("plans", plans);
        model.addAttribute("keyword", keyword);
        model.addAttribute("activePage", "plans");
        model.addAttribute("pageTitle", "Quản Lý Gói Dịch Vụ Chủ Trọ");
        model.addAttribute("content", "admin/plans/list");

        return "layout/admin-layout";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("activePage", "plans");
        model.addAttribute("pageTitle", "Tạo Gói Dịch Vụ Mới");
        model.addAttribute("content", "admin/plans/create");
        return "layout/admin-layout";
    }

    @PostMapping("/create")
    public String createPlan(
            @RequestParam("code") String code,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("monthlyPrice") int monthlyPrice,
            @RequestParam(value = "maxProperties", defaultValue = "1") int maxProperties,
            @RequestParam(value = "maxPosts", defaultValue = "1") int maxPosts,
            @RequestParam(value = "maxRoomsPerProperty", defaultValue = "3") int maxRoomsPerProperty,
            @RequestParam(value = "postDurationDays", defaultValue = "7") int postDurationDays,
            @RequestParam(value = "sortOrder", defaultValue = "1") int sortOrder,
            RedirectAttributes redirectAttributes) {

        String trimmedCode = code.trim().toUpperCase();
        if (planRepository.findByCode(trimmedCode).isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Mã gói '" + trimmedCode + "' đã tồn tại!");
            return "redirect:/admin/plans/create";
        }

        ManagementPlan plan = new ManagementPlan();
        plan.setCode(trimmedCode);
        plan.setName(name.trim());
        plan.setDescription(description != null ? description.trim() : null);
        plan.setMonthlyPrice(monthlyPrice);
        plan.setMaxProperties(maxProperties);
        plan.setMaxPosts(maxPosts);
        plan.setMaxRoomsPerProperty(maxRoomsPerProperty);
        plan.setPostDurationDays(postDurationDays);
        plan.setSortOrder(sortOrder);
        plan.setActive(true);

        planRepository.save(plan);
        redirectAttributes.addFlashAttribute("successMessage", "Đã tạo gói dịch vụ mới thành công: " + plan.getName());
        return "redirect:/admin/plans";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<ManagementPlan> planOpt = planRepository.findById(id);
        if (planOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy gói dịch vụ mã ID: " + id);
            return "redirect:/admin/plans";
        }

        ManagementPlan plan = planOpt.get();
        model.addAttribute("plan", plan);
        model.addAttribute("activePage", "plans");
        model.addAttribute("pageTitle", "Chỉnh Sửa Gói: " + plan.getName());
        model.addAttribute("content", "admin/plans/edit");
        return "layout/admin-layout";
    }

    @PostMapping("/{id}/edit")
    public String updatePlan(
            @PathVariable("id") Long id,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("monthlyPrice") int monthlyPrice,
            @RequestParam("maxProperties") int maxProperties,
            @RequestParam("maxPosts") int maxPosts,
            @RequestParam("maxRoomsPerProperty") int maxRoomsPerProperty,
            @RequestParam("postDurationDays") int postDurationDays,
            @RequestParam(value = "sortOrder", defaultValue = "1") int sortOrder,
            @RequestParam(value = "active", defaultValue = "false") boolean active,
            RedirectAttributes redirectAttributes) {

        Optional<ManagementPlan> planOpt = planRepository.findById(id);
        if (planOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy gói dịch vụ mã ID: " + id);
            return "redirect:/admin/plans";
        }

        ManagementPlan plan = planOpt.get();
        plan.setName(name.trim());
        plan.setDescription(description != null ? description.trim() : null);
        plan.setMonthlyPrice(monthlyPrice);
        plan.setMaxProperties(maxProperties);
        plan.setMaxPosts(maxPosts);
        plan.setMaxRoomsPerProperty(maxRoomsPerProperty);
        plan.setPostDurationDays(postDurationDays);
        plan.setSortOrder(sortOrder);
        plan.setActive(active);

        planRepository.save(plan);
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật gói dịch vụ '" + plan.getName() + "' thành công!");
        return "redirect:/admin/plans";
    }

    @PostMapping("/{id}/toggle-status")
    public String togglePlanStatus(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        Optional<ManagementPlan> planOpt = planRepository.findById(id);
        if (planOpt.isPresent()) {
            ManagementPlan plan = planOpt.get();
            plan.setActive(!plan.isActive());
            planRepository.save(plan);
            String status = plan.isActive() ? "Kích hoạt" : "Tạm ngưng";
            redirectAttributes.addFlashAttribute("successMessage", status + " gói dịch vụ '" + plan.getName() + "' thành công!");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Gói dịch vụ không tồn tại!");
        }
        return "redirect:/admin/plans";
    }

    @PostMapping("/{id}/delete")
    public String deletePlan(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        Optional<ManagementPlan> planOpt = planRepository.findById(id);
        if (planOpt.isPresent()) {
            ManagementPlan plan = planOpt.get();
            try {
                planRepository.deleteById(id);
                redirectAttributes.addFlashAttribute("successMessage", "Đã xóa gói dịch vụ: " + plan.getName());
            } catch (Exception ex) {
                log.error("Không thể xóa plan ID: {}", id, ex);
                plan.setActive(false);
                planRepository.save(plan);
                redirectAttributes.addFlashAttribute("errorMessage", "Gói dịch vụ đang có chủ trọ đăng ký. Hệ thống đã CHUYỂN SANG TRẠNG THÁI TẮT KHẢ DỤNG.");
            }
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Gói dịch vụ không tồn tại!");
        }
        return "redirect:/admin/plans";
    }
}
