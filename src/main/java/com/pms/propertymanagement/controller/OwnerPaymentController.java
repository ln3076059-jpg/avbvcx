package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.dto.request.PaymentRequest;
import com.pms.propertymanagement.entity.Payment;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.service.PaymentService;
import com.pms.propertymanagement.service.PropertyService;
import com.pms.propertymanagement.service.RoomService;
import com.pms.propertymanagement.service.TenantService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/owner/payments")
@RequiredArgsConstructor
public class OwnerPaymentController {

    private final PaymentService paymentService;
    private final PropertyService propertyService;
    private final RoomService roomService;
    private final TenantService tenantService;
    private final com.pms.propertymanagement.repository.UserRepository userRepository;

    @GetMapping
    public String listPayments(
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpSession session,
            Model model
    ) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        Pageable pageable = PageRequest.of(page - 1, size);
        Page<Payment> paymentPage = paymentService.searchPayments(user.getId(), propertyId, roomId, tenantId, pageable);

        model.addAttribute("payments", paymentPage);
        model.addAttribute("properties", propertyService.getPropertiesByOwner(user));
        model.addAttribute("tenants", tenantService.getAllTenantsByOwner(user));
        if (propertyId != null) {
            model.addAttribute("rooms", roomService.getRoomsByPropertyId(propertyId));
        }

        model.addAttribute("selectedPropertyId", propertyId);
        model.addAttribute("selectedRoomId", roomId);
        model.addAttribute("selectedTenantId", tenantId);
        model.addAttribute("currentPage", page);

        model.addAttribute("content", "owner/payment/list");
        model.addAttribute("activeMenu", "payments");
        return "layout/owner-layout";
    }

    @PostMapping("/create")
    public String recordPayment(
            @Valid @ModelAttribute("paymentRequest") PaymentRequest paymentRequest,
            BindingResult bindingResult,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Dữ liệu thanh toán không hợp lệ!");
            return "redirect:/owner/invoices/" + paymentRequest.getInvoiceId();
        }

        try {
            Payment saved = paymentService.createPayment(paymentRequest, user.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Ghi nhận thanh toán " + 
                    String.format("%,.0f", saved.getAmount()) + " đ thành công!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/owner/invoices/" + paymentRequest.getInvoiceId();
    }

    @GetMapping("/settings")
    public String paymentSettings(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        User freshUser = userRepository.findById(user.getId()).orElse(user);
        model.addAttribute("ownerUser", freshUser);
        model.addAttribute("content", "owner/payment/settings");
        model.addAttribute("activeMenu", "payments");
        return "layout/owner-layout";
    }

    @PostMapping("/settings")
    public String updatePaymentSettings(
            @RequestParam(required = false) String bankCode,
            @RequestParam(required = false) String bankAccountNumber,
            @RequestParam(required = false) String bankAccountName,
            HttpSession session,
            RedirectAttributes ra
    ) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        User dbUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin chủ trọ"));

        dbUser.setBankCode(bankCode != null && !bankCode.isBlank() ? bankCode.trim().toUpperCase() : null);
        dbUser.setBankAccountNumber(bankAccountNumber != null && !bankAccountNumber.isBlank() ? bankAccountNumber.trim() : null);
        dbUser.setBankAccountName(bankAccountName != null && !bankAccountName.isBlank() ? bankAccountName.trim().toUpperCase() : null);

        userRepository.save(dbUser);
        session.setAttribute("user", dbUser);

        ra.addFlashAttribute("successMessage", "Đã cập nhật thông tin tài khoản ngân hàng nhận thanh toán VietQR thành công!");
        return "redirect:/owner/payments/settings";
    }
}
