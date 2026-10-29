package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.dto.request.ContactRequest;
import com.pms.propertymanagement.dto.request.PropertyRequest;
import com.pms.propertymanagement.dto.response.PropertyOwnerResponse;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.service.*;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/owner")
@RequiredArgsConstructor
public class OwnerController {

    private final PropertyService propertyService;
    private final CategoryService categoryService;
    private final ContactService contactService;
    private final PropertyManagementService propertyManagementService;

    @GetMapping
    public String dashboard() {
        return "redirect:/owner/dashboard";
    }

    @GetMapping("/properties")
    public String listProperties(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        List<PropertyOwnerResponse> properties = propertyService.getPropertiesByOwner(user);
        model.addAttribute("properties", properties);
        model.addAttribute("activeMenu", "properties");

        boolean canPost = propertyManagementService.canCreateProperty(user.getId());
        model.addAttribute("canPost", canPost);

        model.addAttribute("content", "owner/property/list");
        return "layout/owner-layout";
    }

    @GetMapping("/properties/create")
    public String showCreatePropertyForm(Model model, HttpSession session, RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        // NEW ARCHITECTURE: Check if user can create property based on management plan
        if (!propertyManagementService.canCreateProperty(user.getId())) {
            ra.addFlashAttribute("errorMessage", "Bạn cần có gói quản lý đang hoạt động để tạo nhà trọ.");
            return "redirect:/owner/properties";
        }

        model.addAttribute("propertyRequest", new PropertyRequest());
        model.addAttribute("actionUrl", "/owner/properties/create");

        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("amenities", propertyService.getAllAmenities());
        model.addAttribute("provinces", propertyService.getAllProvinces());
        model.addAttribute("surroundings", propertyService.getAllSurroundings());
        model.addAttribute("targetTenants", propertyService.getAllTargetTenants());

        model.addAttribute("content", "owner/property/create");
        model.addAttribute("activeMenu", "properties");
        return "layout/owner-layout";
    }

    @PostMapping("/properties/create")
    public String createProperty(
            @ModelAttribute("propertyRequest") PropertyRequest propertyRequest,
            HttpSession session,
            RedirectAttributes ra
    ) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        try {
            propertyService.createProperty(propertyRequest, user);
            ra.addFlashAttribute("successMessage", 
                "Tạo nhà trọ thành công! Hãy vào mục 'Quản lý bài đăng' để tạo bài đăng cho nhà trọ này.");
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", "Lỗi: " + ex.getMessage());
        }

        return "redirect:/owner/properties";
    }

    @GetMapping("/properties/edit/{id}")
    public String showEditPropertyForm(@PathVariable Long id, Model model, HttpSession session, RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        PropertyRequest propertyRequest;
        try {
            propertyRequest = propertyService.getPropertyForEdit(id, user.getId());
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/owner/properties";
        }
        model.addAttribute("propertyRequest", propertyRequest);
        model.addAttribute("actionUrl", "/owner/properties/edit/" + id);

        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("amenities", propertyService.getAllAmenities());
        model.addAttribute("provinces", propertyService.getAllProvinces());
        model.addAttribute("surroundings", propertyService.getAllSurroundings());
        model.addAttribute("targetTenants", propertyService.getAllTargetTenants());
        
        // Get province code from ward for edit mode
        if (propertyRequest.getWardCode() != null) {
            String provinceCode = propertyService.getProvinceCodeFromWard(propertyRequest.getWardCode());
            model.addAttribute("selectedProvinceCode", provinceCode);
        }

        model.addAttribute("content", "owner/property/create"); // Re-using create form
        model.addAttribute("activeMenu", "properties");
        return "layout/owner-layout";
    }

    @PostMapping("/properties/edit/{id}")
    public String updateProperty(@PathVariable Long id, 
                                @ModelAttribute("propertyRequest") PropertyRequest propertyRequest, 
                                HttpSession session,
                                RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        try {
            propertyService.updateProperty(id, propertyRequest, user.getId());
            ra.addFlashAttribute("successMessage", "Cập nhật thông tin nhà trọ thành công!");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", "Lỗi: " + ex.getMessage());
        }
        
        return "redirect:/owner/properties";
    }

    @PostMapping("/properties/delete/{id}")
    public String deleteProperty(@PathVariable Long id, 
                                HttpSession session,
                                RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        try {
            propertyService.deleteProperty(id, user.getId());
            ra.addFlashAttribute("successMessage", "Đã xóa nhà trọ thành công!");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", "Không thể xóa: " + ex.getMessage());
        }
        
        return "redirect:/owner/properties";
    }

    @PostMapping("/contact/{slug}")
    public String createContact(@ModelAttribute("contact") ContactRequest contact, @PathVariable("slug") String slug, Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng đăng nhập tài khoản để đặt lịch hẹn xem phòng.");
            return "redirect:/login";
        }
        contactService.createContact(slug, contact);
        redirectAttributes.addFlashAttribute("contactSuccess", true);
        return "redirect:/properties/" + slug;
    }

    @GetMapping("/contacts")
    public String listContacts(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        var contacts = contactService.getContactsByOwner(user.getId());
        long rentOrderCount = contacts.stream().filter(com.pms.propertymanagement.dto.response.ContactResponse::isRentRegistration).count();
        long pendingCount = contacts.stream().filter(c -> !Boolean.TRUE.equals(c.getStatus())).count();
        long approvedCount = contacts.stream().filter(c -> Boolean.TRUE.equals(c.getStatus())).count();
        long visitCount = contacts.size() - rentOrderCount;

        model.addAttribute("contacts", contacts);
        model.addAttribute("rentOrderCount", rentOrderCount);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("approvedCount", approvedCount);
        model.addAttribute("visitCount", visitCount);
        model.addAttribute("content", "owner/contact/list");
        model.addAttribute("activeMenu", "contacts");
        return "layout/owner-layout";
    }

    @RequestMapping(value = "/contact/toggle/{id}", method = {RequestMethod.GET, RequestMethod.POST})
    public String toggleContact(@PathVariable("id") Long id, HttpSession session, RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        try {
            contactService.changeStatus(id, user.getId());
            ra.addFlashAttribute("successMessage", "Đã cập nhật trạng thái đơn đặt thuê / liên hệ #" + id);
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/owner/contacts";
    }

    @PostMapping("/contact/approve-auto/{id}")
    public String approveAndCreateContractAuto(@PathVariable("id") Long id, HttpSession session, RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        boolean isAdmin = user.getRoles() != null && user.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getName()));
        try {
            var contract = contactService.approveAndCreateContractAutomatically(id, user.getId(), isAdmin);
            ra.addFlashAttribute("successMessage", contract != null
                    ? "Đã phê duyệt đơn #" + id + " và tự động kích hoạt Hợp đồng " + contract.getCode() + "!"
                    : "Đã phê duyệt đơn đặt thuê #" + id + " thành công!");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/owner/contacts";
    }

    @PostMapping("/contact/reject/{id}")
    public String rejectContactBooking(@PathVariable("id") Long id, HttpSession session, RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        boolean isAdmin = user.getRoles() != null && user.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getName()));
        try {
            contactService.updateBookingStatus(id, "CANCELLED", user.getId(), isAdmin);
            ra.addFlashAttribute("successMessage", "Đã từ chối / hủy đơn đặt phòng #" + id);
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/owner/contacts";
    }
}
