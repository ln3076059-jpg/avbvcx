package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.entity.Invoice;
import com.pms.propertymanagement.enums.InvoiceStatus;
import com.pms.propertymanagement.repository.InvoiceRepository;
import com.pms.propertymanagement.service.PaymentService;
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
@RequestMapping("/admin/invoices")
@RequiredArgsConstructor
@Slf4j
public class AdminInvoiceController {

    private final InvoiceRepository invoiceRepository;
    private final PaymentService paymentService;

    @GetMapping
    public String listInvoices(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String statusFilter,
            Model model) {

        List<Invoice> invoices = invoiceRepository.findAll();

        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim().toLowerCase();
            invoices = invoices.stream()
                    .filter(i -> (i.getInvoiceCode() != null && i.getInvoiceCode().toLowerCase().contains(kw))
                            || (i.getRoom() != null && i.getRoom().getName() != null && i.getRoom().getName().toLowerCase().contains(kw))
                            || (i.getTenant() != null && i.getTenant().getFullName() != null && i.getTenant().getFullName().toLowerCase().contains(kw))
                            || (i.getTenant() != null && i.getTenant().getPhone() != null && i.getTenant().getPhone().toLowerCase().contains(kw)))
                    .collect(Collectors.toList());
        }

        if (statusFilter != null && !statusFilter.trim().isEmpty()) {
            try {
                InvoiceStatus st = InvoiceStatus.valueOf(statusFilter.trim().toUpperCase());
                invoices = invoices.stream()
                        .filter(i -> i.getStatus() == st)
                        .collect(Collectors.toList());
            } catch (IllegalArgumentException ignored) {}
        }

        invoices.sort((a, b) -> Long.compare(b.getId(), a.getId()));

        model.addAttribute("invoices", invoices);
        model.addAttribute("keyword", keyword);
        model.addAttribute("statusFilter", statusFilter);
        model.addAttribute("statuses", InvoiceStatus.values());
        model.addAttribute("activePage", "invoices");
        model.addAttribute("pageTitle", "Quản Lý Hóa Đơn & Thu Chi");
        model.addAttribute("content", "admin/invoices/list");

        return "layout/admin-layout";
    }

    @GetMapping("/{id}")
    public String viewInvoiceDetail(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Invoice> invoiceOpt = invoiceRepository.findById(id);
        if (invoiceOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy hóa đơn ID: " + id);
            return "redirect:/admin/invoices";
        }

        Invoice invoice = invoiceOpt.get();
        model.addAttribute("invoice", invoice);
        model.addAttribute("activePage", "invoices");
        model.addAttribute("pageTitle", "Chi Tiết Hóa Đơn: " + invoice.getInvoiceCode());
        model.addAttribute("content", "admin/invoices/detail");
        return "layout/admin-layout";
    }

    @PostMapping("/{id}/mark-paid")
    public String markPaid(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        Optional<Invoice> invoiceOpt = invoiceRepository.findById(id);
        if (invoiceOpt.isPresent()) {
            Invoice invoice = invoiceOpt.get();
            Double remaining = invoice.getRemainingAmount();
            Long ownerId = (invoice.getRoom() != null && invoice.getRoom().getProperty() != null
                    && invoice.getRoom().getProperty().getOwner() != null)
                    ? invoice.getRoom().getProperty().getOwner().getId() : null;

            if (remaining != null && remaining > 0 && ownerId != null) {
                paymentService.recordManualPayment(invoice.getId(), remaining, ownerId, "Admin xác nhận thanh toán đầy đủ");
            } else {
                invoice.setStatus(InvoiceStatus.PAID);
                invoice.setPaidAmount(invoice.getTotalAmount());
                invoice.setRemainingAmount(0.0);
                invoiceRepository.save(invoice);
            }
            redirectAttributes.addFlashAttribute("successMessage", "Đã xác nhận thanh toán đầy đủ cho hóa đơn: " + invoice.getInvoiceCode());
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Hóa đơn không tồn tại!");
        }
        return "redirect:/admin/invoices";
    }

    @GetMapping("/export")
    public void exportInvoicesCsv(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        List<Invoice> invoices = invoiceRepository.findAll();
        invoices.sort((a, b) -> Long.compare(b.getId(), a.getId()));

        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"danh-sach-hoa-don.csv\"");

        try (java.io.PrintWriter writer = response.getWriter()) {
            writer.write('\uFEFF');
            writer.println("Mã Hóa Đơn,Kỳ Thu,Phòng,Khách Thuê,Tổng Tiền (VNĐ),Đã Thu (VNĐ),Còn Nợ (VNĐ),Hạn Thanh Toán,Trạng Thái");
            for (Invoice inv : invoices) {
                String period = inv.getMonth() + "/" + inv.getYear();
                String roomName = inv.getRoom() != null ? inv.getRoom().getName() : "";
                String tenantName = inv.getTenant() != null ? inv.getTenant().getFullName() : "";
                writer.printf("%s,%s,%s,%s,%.0f,%.0f,%.0f,%s,%s%n",
                        escapeCsv(inv.getInvoiceCode()),
                        escapeCsv(period),
                        escapeCsv(roomName),
                        escapeCsv(tenantName),
                        inv.getTotalAmount() != null ? inv.getTotalAmount() : 0.0,
                        inv.getPaidAmount() != null ? inv.getPaidAmount() : 0.0,
                        inv.getRemainingAmount() != null ? inv.getRemainingAmount() : 0.0,
                        inv.getDueDate() != null ? inv.getDueDate().toString() : "",
                        inv.getStatus() != null ? inv.getStatus().name() : ""
                );
            }
        }
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        String cleaned = val.replace("\"", "\"\"");
        if (cleaned.contains(",") || cleaned.contains("\"") || cleaned.contains("\n") || cleaned.contains("\r")) {
            return "\"" + cleaned + "\"";
        }
        return cleaned;
    }
}
