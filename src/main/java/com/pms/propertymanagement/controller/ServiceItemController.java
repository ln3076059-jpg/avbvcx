package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.dto.request.ServiceItemRequest;
import com.pms.propertymanagement.dto.response.PropertyOwnerResponse;
import com.pms.propertymanagement.dto.response.ServiceItemResponse;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.enums.ServiceType;
import com.pms.propertymanagement.service.PropertyService;
import com.pms.propertymanagement.service.ServiceItemService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/owner/services")
@RequiredArgsConstructor
public class ServiceItemController {

    private final ServiceItemService serviceItemService;
    private final PropertyService propertyService;

    @GetMapping
    public String index(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        // 1. Lấy danh sách dịch vụ đã tạo (để hiển thị bảng)
        List<ServiceItemResponse> services = serviceItemService.getAllServicesByOwner(user);
        model.addAttribute("services", services);
        model.addAttribute("properties", propertyService.getPropertiesByOwner(user));

        model.addAttribute("content", "owner/service/list");
        model.addAttribute("activeMenu", "services");
        return "layout/owner-layout";
    }

    @PostMapping("/quick-preset")
    public String createQuickPresetServices(
            @RequestParam(required = false) Long propertyId,
            HttpSession session,
            RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        List<Long> targetPropertyIds;
        if (propertyId != null) {
            targetPropertyIds = List.of(propertyId);
        } else {
            targetPropertyIds = propertyService.getPropertiesByOwner(user).stream()
                    .map(PropertyOwnerResponse::getId)
                    .toList();
        }

        if (targetPropertyIds.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Vui lòng tạo ít nhất 1 nhà trọ trước khi khởi tạo dịch vụ mẫu!");
            return "redirect:/owner/services";
        }

        Object[][] presets = {
                {"Điện sinh hoạt", 3500.0, "kWh", ServiceType.METERED},
                {"Nước sinh hoạt", 20000.0, "m³", ServiceType.METERED},
                {"Internet Wifi tốc độ cao", 100000.0, "Phòng/Tháng", ServiceType.FIXED},
                {"Vệ sinh & Thu gom rác", 30000.0, "Phòng/Tháng", ServiceType.FIXED}
        };

        for (Long propId : targetPropertyIds) {
            java.util.Set<String> existingNames = serviceItemService.getServicesByPropertyId(propId).stream()
                    .map(s -> s.getName() != null ? s.getName().trim().toLowerCase() : "")
                    .collect(java.util.stream.Collectors.toSet());

            for (Object[] preset : presets) {
                String name = (String) preset[0];
                if (existingNames.contains(name.trim().toLowerCase())) {
                    continue;
                }
                ServiceItemRequest req = new ServiceItemRequest();
                req.setPropertyId(propId);
                req.setName(name);
                req.setPrice((Double) preset[1]);
                req.setUnit((String) preset[2]);
                req.setType((ServiceType) preset[3]);
                try {
                    serviceItemService.createService(req, user);
                    existingNames.add(name.trim().toLowerCase());
                } catch (Exception ignored) {
                }
            }
        }

        ra.addFlashAttribute("successMessage", "Đã khởi tạo nhanh bộ 4 dịch vụ tiêu chuẩn thành công!");
        return "redirect:/owner/services?success=true";
    }

    @GetMapping("/create")
    public String create(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        // 1. Lấy danh sách nhà trọ (để hiển thị dropdown chọn nhà)
        List<PropertyOwnerResponse> properties = propertyService.getPropertiesByOwner(user);
        model.addAttribute("properties", properties);

        // 2. Object binding cho form
        model.addAttribute("serviceRequest", new ServiceItemRequest());

        model.addAttribute("content", "owner/service/create");
        model.addAttribute("activeMenu", "properties");
        return "layout/owner-layout";
    }

    @PostMapping("/create")
    public String store(@ModelAttribute("serviceRequest") ServiceItemRequest request,
                        HttpSession session,
                        RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        try {
            serviceItemService.createService(request, user);
            return "redirect:/owner/services?success";
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/owner/services";
        }
    }

    @PostMapping("/update/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam String name,
                         @RequestParam Double price,
                         @RequestParam String unit,
                         @RequestParam(required = false) String type,
                         HttpSession session,
                         RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        try {
            serviceItemService.updateService(id, name, price, unit, type, user);
            ra.addFlashAttribute("successMessage", "Cập nhật dịch vụ thành công!");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/owner/services";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        try {
            serviceItemService.deleteService(id, user);
            return "redirect:/owner/services?deleted";
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/owner/services";
        }
    }
}
