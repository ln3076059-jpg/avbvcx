package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.entity.Contact;
import com.pms.propertymanagement.repository.ContactRepository;
import com.pms.propertymanagement.service.ContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/bookings")
@RequiredArgsConstructor
public class AdminBookingController {

    private final ContactRepository contactRepository;
    private final ContactService contactService;

    @GetMapping
    public String listBookings(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String statusFilter,
            Model model) {

        List<Contact> allContacts = contactRepository.findAllByOrderByIdDesc();

        long totalCount = allContacts.size();
        long rentOrderCount = allContacts.stream()
                .filter(c -> "RENT_ORDER".equalsIgnoreCase(c.getBookingType())
                        || (c.getNote() != null && c.getNote().startsWith("[ĐĂNG KÝ THUÊ PHÒNG")))
                .count();
        long pendingCount = allContacts.stream()
                .filter(c -> !Boolean.TRUE.equals(c.getIsChecked()) && !"CANCELLED".equalsIgnoreCase(c.getBookingStatus()))
                .count();
        long approvedCount = allContacts.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsChecked()))
                .count();

        List<Contact> filtered = allContacts;
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim().toLowerCase();
            filtered = filtered.stream()
                    .filter(c -> (c.getName() != null && c.getName().toLowerCase().contains(kw))
                            || (c.getPhone() != null && c.getPhone().toLowerCase().contains(kw))
                            || (c.getNote() != null && c.getNote().toLowerCase().contains(kw))
                            || (c.getProperty() != null && c.getProperty().getName() != null && c.getProperty().getName().toLowerCase().contains(kw)))
                    .collect(Collectors.toList());
        }

        if ("RENT_ORDER".equalsIgnoreCase(statusFilter)) {
            filtered = filtered.stream()
                    .filter(c -> "RENT_ORDER".equalsIgnoreCase(c.getBookingType())
                            || (c.getNote() != null && c.getNote().startsWith("[ĐĂNG KÝ THUÊ PHÒNG")))
                    .collect(Collectors.toList());
        } else if ("PENDING".equalsIgnoreCase(statusFilter)) {
            filtered = filtered.stream()
                    .filter(c -> !Boolean.TRUE.equals(c.getIsChecked()) && !"CANCELLED".equalsIgnoreCase(c.getBookingStatus()))
                    .collect(Collectors.toList());
        } else if ("APPROVED".equalsIgnoreCase(statusFilter)) {
            filtered = filtered.stream()
                    .filter(c -> Boolean.TRUE.equals(c.getIsChecked()))
                    .collect(Collectors.toList());
        }

        model.addAttribute("bookings", filtered);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("rentOrderCount", rentOrderCount);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("approvedCount", approvedCount);
        model.addAttribute("keyword", keyword);
        model.addAttribute("statusFilter", statusFilter);
        model.addAttribute("activePage", "bookings");
        model.addAttribute("pageTitle", "Quản Lý Đơn Đặt Thuê Phòng & Liên Hệ");
        model.addAttribute("content", "admin/bookings/list");

        return "layout/admin-layout";
    }

    @PostMapping("/{id}/toggle")
    public String toggleBookingStatus(@PathVariable("id") Long id, RedirectAttributes ra) {
        contactRepository.findById(id).ifPresent(c -> {
            boolean nextChecked = !Boolean.TRUE.equals(c.getIsChecked());
            c.setIsChecked(nextChecked);
            c.setBookingStatus(nextChecked ? "APPROVED" : "PENDING");
            contactRepository.save(c);
            ra.addFlashAttribute("successMessage",
                    nextChecked
                            ? "Đã phê duyệt đơn đặt thuê #" + c.getId() + " (" + c.getName() + ")"
                            : "Đã chuyển đơn #" + c.getId() + " về trạng thái Chờ duyệt");
        });
        return "redirect:/admin/bookings";
    }

    @PostMapping("/{id}/approve-auto")
    public String approveAndCreateContractAuto(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            var contract = contactService.approveAndCreateContractAutomatically(id, null, true);
            ra.addFlashAttribute("successMessage", contract != null
                    ? "Đã phê duyệt đơn #" + id + " và tự động kích hoạt Hợp đồng " + contract.getCode() + "!"
                    : "Đã phê duyệt đơn đặt thuê #" + id + " thành công!");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings";
    }

    @PostMapping("/{id}/reject")
    public String rejectBooking(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            contactService.updateBookingStatus(id, "CANCELLED", null, true);
            ra.addFlashAttribute("successMessage", "Đã chuyển đơn #" + id + " sang trạng thái Đã hủy / Từ chối (Soft Cancel)");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings";
    }

    @PostMapping("/{id}/delete")
    public String deleteBooking(@PathVariable("id") Long id, RedirectAttributes ra) {
        if (contactRepository.existsById(id)) {
            contactRepository.deleteById(id);
            ra.addFlashAttribute("successMessage", "Đã xóa đơn đặt thuê / liên hệ #" + id);
        }
        return "redirect:/admin/bookings";
    }
}
