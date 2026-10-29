package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.entity.*;
import com.pms.propertymanagement.enums.ContractStatus;
import com.pms.propertymanagement.repository.ContactRepository;
import com.pms.propertymanagement.repository.ContractRepository;
import com.pms.propertymanagement.service.InvoiceService;
import com.pms.propertymanagement.service.PaymentService;
import com.pms.propertymanagement.service.TenantMaintenanceService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/tenant")
@RequiredArgsConstructor
public class TenantPortalController {

    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final ContractRepository contractRepository;
    private final ContactRepository contactRepository;
    private final TenantMaintenanceService tenantMaintenanceService;

    @GetMapping("/dashboard")
    public String dashboard(@org.springframework.web.bind.annotation.RequestParam(value = "roomId", required = false) Long roomId,
                            HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        // Lấy danh sách phòng đang thuê
        List<Room> rentedRooms = tenantMaintenanceService.getRoomsForTenant(user);
        Room currentRoom = null;
        if (!rentedRooms.isEmpty()) {
            if (roomId != null) {
                currentRoom = rentedRooms.stream().filter(r -> r.getId().equals(roomId)).findFirst().orElse(rentedRooms.get(0));
            } else {
                currentRoom = rentedRooms.get(0);
            }
        }

        Contract activeContract = null;
        if (currentRoom != null) {
            activeContract = contractRepository.findFirstByRoom_IdAndStatus(currentRoom.getId(), ContractStatus.ACTIVE).orElse(null);
        }

        // Lấy danh sách hóa đơn của tenant
        List<Invoice> invoices = invoiceService.getInvoicesForTenant(user);
        Invoice latestInvoice = !invoices.isEmpty() ? invoices.get(0) : null;

        // Tính tổng tiền nợ hiện tại
        double totalDebt = invoices.stream().mapToDouble(i -> i.getRemainingAmount() != null ? i.getRemainingAmount() : 0.0).sum();

        // Lấy các yêu cầu sửa chữa gần đây
        List<MaintenanceRequest> maintenanceRequests = tenantMaintenanceService.getRequestsForTenant(user);

        // Lấy các đơn đăng ký thuê phòng / lịch hẹn xem phòng của khách hàng (gộp theo tài khoản account + SĐT)
        java.util.LinkedHashMap<Long, Contact> bookingMap = new java.util.LinkedHashMap<>();
        if (user.getId() != null) {
            for (Contact c : contactRepository.findByCustomer_IdOrderByIdDesc(user.getId())) {
                bookingMap.put(c.getId(), c);
            }
        }
        if (user.getPhone() != null && !user.getPhone().isBlank()) {
            for (Contact c : contactRepository.findByPhoneOrderByIdDesc(user.getPhone().trim())) {
                bookingMap.putIfAbsent(c.getId(), c);
            }
        }
        List<Contact> myRentRegistrations = new java.util.ArrayList<>(bookingMap.values());

        model.addAttribute("tenantUser", user);
        model.addAttribute("rentedRooms", rentedRooms);
        model.addAttribute("currentRoom", currentRoom);
        model.addAttribute("selectedRoomId", currentRoom != null ? currentRoom.getId() : null);
        model.addAttribute("activeContract", activeContract);
        model.addAttribute("latestInvoice", latestInvoice);
        model.addAttribute("totalDebt", totalDebt);
        model.addAttribute("recentInvoices", invoices.stream().limit(6).toList());
        model.addAttribute("recentRequests", maintenanceRequests.stream().limit(5).toList());
        model.addAttribute("myRentRegistrations", myRentRegistrations);

        model.addAttribute("content", "tenant/dashboard");
        model.addAttribute("activeTenantMenu", "dashboard");
        return "layout/tenant-layout";
    }

    @GetMapping("/contract")
    public String myContract(@org.springframework.web.bind.annotation.RequestParam(value = "roomId", required = false) Long roomId,
                             HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        List<Room> rentedRooms = tenantMaintenanceService.getRoomsForTenant(user);
        Room currentRoom = null;
        if (!rentedRooms.isEmpty()) {
            if (roomId != null) {
                currentRoom = rentedRooms.stream().filter(r -> r.getId().equals(roomId)).findFirst().orElse(rentedRooms.get(0));
            } else {
                currentRoom = rentedRooms.get(0);
            }
        }

        Contract activeContract = null;
        if (currentRoom != null) {
            activeContract = contractRepository.findFirstByRoom_IdAndStatus(currentRoom.getId(), ContractStatus.ACTIVE).orElse(null);
        }

        model.addAttribute("rentedRooms", rentedRooms);
        model.addAttribute("contract", activeContract);
        model.addAttribute("room", currentRoom);
        model.addAttribute("selectedRoomId", currentRoom != null ? currentRoom.getId() : null);
        model.addAttribute("content", "tenant/contract/detail");
        model.addAttribute("activeTenantMenu", "contract");
        return "layout/tenant-layout";
    }

    @PostMapping("/contract/request-action")
    public String requestContractAction(@RequestParam String actionType,
                                        @RequestParam(required = false) String reason,
                                        @RequestParam(required = false) String note,
                                        @RequestParam(required = false) Long roomId,
                                        HttpSession session,
                                        org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        String effectiveReason = (reason != null && !reason.isBlank())
                ? reason.trim()
                : (note != null ? note.trim() : "Cư dân gửi yêu cầu trực tuyến");

        List<Room> rentedRooms = tenantMaintenanceService.getRoomsForTenant(user);
        Room currentRoom = null;
        if (!rentedRooms.isEmpty()) {
            if (roomId != null) {
                currentRoom = rentedRooms.stream().filter(r -> r.getId().equals(roomId)).findFirst().orElse(rentedRooms.get(0));
            } else {
                currentRoom = rentedRooms.get(0);
            }
        }

        if (currentRoom != null) {
            Contract activeContract = contractRepository.findFirstByRoom_IdAndStatus(currentRoom.getId(), ContractStatus.ACTIVE).orElse(null);
            if (activeContract != null) {
                String actionLabel = "RENEW".equalsIgnoreCase(actionType) ? "GIA HẠN HỢP ĐỒNG" : "BÁO TRẢ PHÒNG";
                String logEntry = "[Khách thuê gửi yêu cầu " + actionLabel + " ngày " + java.time.LocalDate.now() + "]: " + effectiveReason;
                String existingNote = activeContract.getNote();
                if (existingNote != null && !existingNote.isBlank()) {
                    activeContract.setNote(existingNote + "\n" + logEntry);
                } else {
                    activeContract.setNote(logEntry);
                }
                contractRepository.save(activeContract);

                // Tạo bản ghi thông báo yêu cầu sang danh sách Đơn & Yêu cầu của Chủ trọ để Chủ trọ xét duyệt
                if (currentRoom.getProperty() != null) {
                    Contact actionContact = new Contact(
                            user.getFullName() != null ? user.getFullName() : user.getUsername(),
                            user.getPhone() != null ? user.getPhone() : "0901234567",
                            "[ĐĂNG KÝ THUÊ PHÒNG #" + currentRoom.getId() + " - " + currentRoom.getName() + "] Yêu cầu " + actionLabel + ": " + effectiveReason,
                            currentRoom.getProperty().getOwner(),
                            currentRoom.getProperty()
                    );
                    actionContact.setRoom(currentRoom);
                    actionContact.setCustomer(user);
                    actionContact.setBookingType("RENT_ORDER");
                    actionContact.setBookingStatus("PENDING");
                    contactRepository.save(actionContact);
                }
            }
        }

        ra.addFlashAttribute("successMessage", "Đã gửi yêu cầu (" + effectiveReason + ") tới chủ nhà trọ thành công!");
        return "redirect:/tenant/contract" + (roomId != null ? "?roomId=" + roomId : "");
    }

    @GetMapping("/invoices")
    public String myInvoices(@org.springframework.web.bind.annotation.RequestParam(value = "roomId", required = false) Long roomId,
                             @org.springframework.web.bind.annotation.RequestParam(value = "status", required = false) String status,
                             HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        List<Room> rentedRooms = tenantMaintenanceService.getRoomsForTenant(user);
        List<Invoice> allInvoices = invoiceService.getInvoicesForTenant(user);

        // Lọc theo phòng nếu được chọn
        List<Invoice> filtered = allInvoices;
        if (roomId != null) {
            filtered = filtered.stream()
                    .filter(i -> i.getRoom() != null && i.getRoom().getId().equals(roomId))
                    .toList();
        }

        // Lọc theo trạng thái nếu được chọn
        if (status != null && !status.isBlank()) {
            if ("UNPAID".equalsIgnoreCase(status)) {
                filtered = filtered.stream().filter(i -> i.getRemainingAmount() != null && i.getRemainingAmount() > 0).toList();
            } else if ("PAID".equalsIgnoreCase(status)) {
                filtered = filtered.stream().filter(i -> i.getStatus() == com.pms.propertymanagement.enums.InvoiceStatus.PAID).toList();
            }
        }

        model.addAttribute("invoices", filtered);
        model.addAttribute("rentedRooms", rentedRooms);
        model.addAttribute("selectedRoomId", roomId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("content", "tenant/invoice/list");
        model.addAttribute("activeTenantMenu", "invoices");
        return "layout/tenant-layout";
    }

    @GetMapping("/invoices/{id}")
    public String myInvoiceDetail(@PathVariable Long id, HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        try {
            Invoice invoice = invoiceService.getInvoiceByIdForTenant(id, user);
            model.addAttribute("invoice", invoice);
            model.addAttribute("content", "tenant/invoice/detail");
            model.addAttribute("activeTenantMenu", "invoices");
            return "layout/tenant-layout";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "redirect:/tenant/invoices";
        }
    }

    @PostMapping("/invoices/{id}/notify-transfer")
    public String notifyTransfer(@PathVariable Long id,
                                 @RequestParam(required = false) String transferNote,
                                 HttpSession session,
                                 org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        try {
            invoiceService.notifyTransfer(id, user, transferNote);
            ra.addFlashAttribute("successMessage", "Đã gửi thông báo chuyển khoản cho chủ trọ! Chủ trọ sẽ kiểm tra và xác nhận hóa đơn của bạn.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/tenant/invoices/" + id;
    }

    @GetMapping("/invoices/{id}/pay-vnpay")
    public String payInvoiceWithVnPay(@PathVariable Long id, 
                                      jakarta.servlet.http.HttpServletRequest request, 
                                      HttpSession session, 
                                      org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        try {
            String returnUrl = com.pms.propertymanagement.utils.VnPayUtil.getBaseUrl(request) + "/payment/vnpay/return";
            String ipAddress = com.pms.propertymanagement.utils.VnPayUtil.getIpAddress(request);
            String paymentUrl = paymentService.createInvoiceVnpayUrl(id, user, returnUrl, ipAddress);

            return "redirect:" + paymentUrl;
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Không thể tạo liên kết thanh toán: " + e.getMessage());
            return "redirect:/tenant/invoices/" + id;
        }
    }

    @GetMapping("/payments")
    public String myPaymentHistory(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        List<Payment> payments = paymentService.getPaymentsForTenant(user);
        model.addAttribute("payments", payments);
        model.addAttribute("content", "tenant/payment/history");
        model.addAttribute("activeTenantMenu", "payments");
        return "layout/tenant-layout";
    }
}
