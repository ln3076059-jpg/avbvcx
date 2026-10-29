package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.dto.request.BatchInvoiceRequest;
import com.pms.propertymanagement.dto.request.InvoiceRequest;
import com.pms.propertymanagement.dto.request.PaymentRequest;
import com.pms.propertymanagement.entity.Contract;
import com.pms.propertymanagement.entity.Invoice;
import com.pms.propertymanagement.entity.MeterReading;
import com.pms.propertymanagement.entity.Room;
import com.pms.propertymanagement.entity.ServiceItem;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.enums.ContractStatus;
import com.pms.propertymanagement.enums.InvoiceStatus;
import com.pms.propertymanagement.enums.PaymentMethod;
import com.pms.propertymanagement.enums.ServiceType;
import com.pms.propertymanagement.repository.ContractRepository;
import com.pms.propertymanagement.repository.InvoiceRepository;
import com.pms.propertymanagement.repository.MeterReadingRepository;
import com.pms.propertymanagement.service.InvoiceService;
import com.pms.propertymanagement.service.PaymentService;
import com.pms.propertymanagement.service.PropertyService;
import com.pms.propertymanagement.service.RoomService;
import com.pms.propertymanagement.service.TenantService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/owner/invoices")
@RequiredArgsConstructor
public class OwnerInvoiceController {

    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final PropertyService propertyService;
    private final RoomService roomService;
    private final TenantService tenantService;
    private final ContractRepository contractRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final InvoiceRepository invoiceRepository;

    @GetMapping
    public String listInvoices(
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpSession session,
            Model model
    ) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        Pageable pageable = PageRequest.of(page - 1, size);
        Page<Invoice> invoicePage = invoiceService.searchInvoices(user.getId(), propertyId, roomId, tenantId, month, year, status, pageable);

        model.addAttribute("invoices", invoicePage);
        model.addAttribute("properties", propertyService.getPropertiesByOwner(user));
        model.addAttribute("tenants", tenantService.getAllTenantsByOwner(user));
        if (propertyId != null) {
            boolean ownsProperty = propertyService.getPropertiesByOwner(user).stream()
                    .anyMatch(p -> p.getId().equals(propertyId));
            if (ownsProperty) {
                model.addAttribute("rooms", roomService.getRoomsByPropertyId(propertyId));
            }
        }

        model.addAttribute("selectedPropertyId", propertyId);
        model.addAttribute("selectedRoomId", roomId);
        model.addAttribute("selectedTenantId", tenantId);
        model.addAttribute("selectedMonth", month);
        model.addAttribute("selectedYear", year);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("currentPage", page);

        model.addAttribute("content", "owner/invoice/list");
        model.addAttribute("activeMenu", "invoices");
        return "layout/owner-layout";
    }

    @GetMapping("/export")
    public void exportInvoicesCsv(@RequestParam(required = false) Long propertyId,
                                  @RequestParam(required = false) InvoiceStatus status,
                                  @RequestParam(required = false) Integer month,
                                  @RequestParam(required = false) Integer year,
                                  @RequestParam(required = false) String keyword,
                                  HttpSession session,
                                  HttpServletResponse response) throws java.io.IOException {
        User owner = (User) session.getAttribute("user");
        if (owner == null) {
            response.sendRedirect("/login/owner");
            return;
        }

        Page<Invoice> page = invoiceService.searchInvoicesForOwner(
                owner.getId(),
                propertyId,
                status,
                month,
                year,
                keyword,
                PageRequest.of(0, 5000, Sort.by(Sort.Direction.DESC, "year", "month", "createdAt"))
        );

        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"hoa-don-nha-tro.csv\"");

        java.io.PrintWriter writer = response.getWriter();
        writer.write('\uFEFF');
        writer.println("Mã Hóa Đơn,Phòng,Nhà Trọ,Khách Thuê,SĐT,Kỳ,Tiền Phòng,Tiền Điện,Tiền Nước,Dịch Vụ,Khoản Khác,Tổng Tiền,Đã Thu,Còn Nợ,Trạng Thái,Hạn Thanh Toán");

        for (Invoice inv : page.getContent()) {
            String code = csvEscape(inv.getInvoiceCode());
            String roomName = csvEscape(inv.getRoom() != null ? inv.getRoom().getName() : "");
            String propName = csvEscape(inv.getRoom() != null && inv.getRoom().getProperty() != null ? inv.getRoom().getProperty().getName() : "");
            String tenantName = csvEscape(inv.getTenant() != null ? inv.getTenant().getFullName() : "");
            String tenantPhone = csvEscape(inv.getTenant() != null ? inv.getTenant().getPhone() : "");
            String period = inv.getMonth() + "/" + inv.getYear();
            long roomPrice = Math.round(inv.getRoomPrice() != null ? inv.getRoomPrice() : 0);
            long elecAmt = Math.round(inv.getElectricityAmount() != null ? inv.getElectricityAmount() : 0);
            long waterAmt = Math.round(inv.getWaterAmount() != null ? inv.getWaterAmount() : 0);
            long serviceAmt = Math.round(inv.getServiceAmount() != null ? inv.getServiceAmount() : 0);
            long otherAmt = Math.round(inv.getOtherAmount() != null ? inv.getOtherAmount() : 0);
            long totalAmt = Math.round(inv.getTotalAmount() != null ? inv.getTotalAmount() : 0);
            long paidAmt = Math.round(inv.getPaidAmount() != null ? inv.getPaidAmount() : 0);
            long remainAmt = Math.round(inv.getRemainingAmount() != null ? inv.getRemainingAmount() : 0);
            String statusText = csvEscape(inv.getStatus() != null ? inv.getStatus().getDisplayName() : "");
            String dueDate = inv.getDueDate() != null ? inv.getDueDate().toString() : "";

            writer.printf("%s,%s,%s,%s,%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%s,%s%n",
                    code, roomName, propName, tenantName, tenantPhone, period,
                    roomPrice, elecAmt, waterAmt, serviceAmt, otherAmt, totalAmt, paidAmt, remainAmt,
                    statusText, dueDate);
        }
        writer.flush();
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        String cleaned = value.replace("\"", "\"\"");
        if (cleaned.contains(",") || cleaned.contains("\"") || cleaned.contains("\n") || cleaned.contains("\r")) {
            return "\"" + cleaned + "\"";
        }
        return cleaned;
    }

    @GetMapping("/create")
    public String createForm(
            @RequestParam(required = false) Long roomId,
            HttpSession session,
            Model model
    ) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        InvoiceRequest request = new InvoiceRequest();
        LocalDate now = LocalDate.now();
        request.setMonth(now.getMonthValue());
        request.setYear(now.getYear());
        request.setDueDate(now.plusDays(5));

        if (roomId != null) {
            Room room = roomService.getRoomById(roomId);
            if (room != null && room.getProperty() != null && room.getProperty().getOwner() != null
                    && room.getProperty().getOwner().getId().equals(user.getId())) {
                request.setRoomId(roomId);
                contractRepository.findFirstByRoom_IdAndStatus(roomId, ContractStatus.ACTIVE)
                        .ifPresent(c -> request.setRoomPrice(c.getRentPrice()));
            }
        }

        List<Contract> activeContracts = contractRepository.findByRoom_Property_Owner_IdAndStatus(user.getId(), ContractStatus.ACTIVE);

        model.addAttribute("invoiceRequest", request);
        model.addAttribute("activeContracts", activeContracts);
        model.addAttribute("properties", propertyService.getPropertiesByOwner(user));
        model.addAttribute("content", "owner/invoice/create");
        model.addAttribute("activeMenu", "invoices");
        return "layout/owner-layout";
    }

    @PostMapping("/create")
    public String saveInvoice(
            @Valid @ModelAttribute("invoiceRequest") InvoiceRequest invoiceRequest,
            BindingResult bindingResult,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        if (bindingResult.hasErrors()) {
            List<Contract> activeContracts = contractRepository.findByRoom_Property_Owner_IdAndStatus(user.getId(), ContractStatus.ACTIVE);
            model.addAttribute("activeContracts", activeContracts);
            model.addAttribute("properties", propertyService.getPropertiesByOwner(user));
            model.addAttribute("content", "owner/invoice/create");
            model.addAttribute("activeMenu", "invoices");
            return "layout/owner-layout";
        }

        try {
            Invoice saved = invoiceService.createInvoice(invoiceRequest, user.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Lập hóa đơn thành công cho phòng " + saved.getRoom().getName() + " (Mã: " + saved.getInvoiceCode() + ")!");
            return "redirect:/owner/invoices";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            List<Contract> activeContracts = contractRepository.findByRoom_Property_Owner_IdAndStatus(user.getId(), ContractStatus.ACTIVE);
            model.addAttribute("activeContracts", activeContracts);
            model.addAttribute("properties", propertyService.getPropertiesByOwner(user));
            model.addAttribute("content", "owner/invoice/create");
            model.addAttribute("activeMenu", "invoices");
            return "layout/owner-layout";
        }
    }

    @GetMapping("/batch-create")
    public String batchCreateForm(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        BatchInvoiceRequest request = new BatchInvoiceRequest();
        LocalDate now = LocalDate.now();
        request.setMonth(now.getMonthValue());
        request.setYear(now.getYear());
        request.setDueDate(now.plusDays(5));

        model.addAttribute("batchRequest", request);
        model.addAttribute("properties", propertyService.getPropertiesByOwner(user));
        model.addAttribute("content", "owner/invoice/batch-create");
        model.addAttribute("activeMenu", "invoices");
        return "layout/owner-layout";
    }

    @PostMapping("/batch-create")
    public String executeBatchCreate(
            @Valid @ModelAttribute("batchRequest") BatchInvoiceRequest batchRequest,
            BindingResult bindingResult,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        if (bindingResult.hasErrors()) {
            model.addAttribute("properties", propertyService.getPropertiesByOwner(user));
            model.addAttribute("content", "owner/invoice/batch-create");
            model.addAttribute("activeMenu", "invoices");
            return "layout/owner-layout";
        }

        int count = invoiceService.generateBatchInvoices(batchRequest, user.getId());
        if (count > 0) {
            redirectAttributes.addFlashAttribute("successMessage", "Đã sinh thành công " + count + " hóa đơn cho kỳ tháng " + batchRequest.getMonth() + "/" + batchRequest.getYear() + "!");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Không có hóa đơn nào mới được tạo (có thể tất cả các phòng đã có hóa đơn hoặc không có hợp đồng đang hoạt động).");
        }
        return "redirect:/owner/invoices";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        Invoice invoice = invoiceService.getInvoiceById(id, user.getId());
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setInvoiceId(invoice.getId());
        paymentRequest.setAmount(invoice.getRemainingAmount());

        model.addAttribute("invoice", invoice);
        model.addAttribute("paymentRequest", paymentRequest);
        model.addAttribute("content", "owner/invoice/detail");
        model.addAttribute("activeMenu", "invoices");
        return "layout/owner-layout";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        Invoice invoice = invoiceService.getInvoiceById(id, user.getId());
        InvoiceRequest request = new InvoiceRequest(
                invoice.getId(),
                invoice.getRoom().getId(),
                invoice.getMonth(),
                invoice.getYear(),
                invoice.getRoomPrice(),
                invoice.getOtherAmount(),
                "",
                invoice.getDueDate()
        );

        model.addAttribute("invoiceRequest", request);
        model.addAttribute("invoice", invoice);
        model.addAttribute("content", "owner/invoice/edit");
        model.addAttribute("activeMenu", "invoices");
        return "layout/owner-layout";
    }

    @PostMapping("/{id}/edit")
    public String updateInvoice(
            @PathVariable Long id,
            @Valid @ModelAttribute("invoiceRequest") InvoiceRequest invoiceRequest,
            BindingResult bindingResult,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        if (bindingResult.hasErrors()) {
            Invoice invoice = invoiceService.getInvoiceById(id, user.getId());
            model.addAttribute("invoice", invoice);
            model.addAttribute("content", "owner/invoice/edit");
            model.addAttribute("activeMenu", "invoices");
            return "layout/owner-layout";
        }

        try {
            invoiceService.updateInvoice(id, invoiceRequest, user.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật hóa đơn thành công!");
            return "redirect:/owner/invoices/" + id;
        } catch (IllegalArgumentException e) {
            Invoice invoice = invoiceService.getInvoiceById(id, user.getId());
            model.addAttribute("invoice", invoice);
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("content", "owner/invoice/edit");
            model.addAttribute("activeMenu", "invoices");
            return "layout/owner-layout";
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteInvoice(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        try {
            invoiceService.deleteInvoice(id, user.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa hóa đơn thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/owner/invoices";
    }

    @PostMapping("/{id}/pay")
    public String quickPayInvoice(
            @PathVariable Long id,
            @RequestParam Double amount,
            @RequestParam(required = false, defaultValue = "BANK_TRANSFER") PaymentMethod paymentMethod,
            @RequestParam(required = false) String note,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        try {
            PaymentRequest req = new PaymentRequest();
            req.setInvoiceId(id);
            req.setAmount(amount);
            req.setPaymentMethod(paymentMethod);
            req.setNote(note);
            paymentService.createPayment(req, user.getId());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã xác nhận thu tiền " + String.format("%,.0f", amount) + " đ thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể ghi nhận thu tiền: " + e.getMessage());
        }
        return "redirect:/owner/invoices";
    }

    @GetMapping("/api/room-details")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getRoomInvoiceDetails(
            @RequestParam Long roomId,
            @RequestParam Integer month,
            @RequestParam Integer year,
            HttpSession session
    ) {
        User user = (User) session.getAttribute("user");
        if (user == null) return ResponseEntity.status(401).build();

        Room room = roomService.getRoomById(roomId);
        if (room == null || room.getProperty() == null || room.getProperty().getOwner() == null
                || !room.getProperty().getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        Map<String, Object> res = new HashMap<>();

        // 1. Contract & Price
        Optional<Contract> contractOpt = contractRepository.findFirstByRoom_IdAndStatus(roomId, ContractStatus.ACTIVE);
        if (contractOpt.isEmpty()) {
            res.put("hasContract", false);
            return ResponseEntity.ok(res);
        }
        Contract contract = contractOpt.get();
        res.put("hasContract", true);
        res.put("rentPrice", contract.getRentPrice());
        res.put("tenantName", contract.getRepresentative() != null ? contract.getRepresentative().getFullName() : "N/A");
        res.put("tenantPhone", contract.getRepresentative() != null ? contract.getRepresentative().getPhone() : "");
        res.put("roomName", contract.getRoom().getName());
        res.put("propertyName", contract.getRoom().getProperty().getName());
        res.put("propertyId", contract.getRoom().getProperty().getId());

        // 2. Check if invoice already exists
        boolean invoiceExists = invoiceRepository.existsByRoom_IdAndMonthAndYear(roomId, month, year);
        res.put("invoiceExists", invoiceExists);

        // 3. Meter Reading
        Optional<MeterReading> readingOpt = meterReadingRepository.findByRoom_IdAndMonthAndYear(roomId, month, year);
        if (readingOpt.isPresent()) {
            MeterReading mr = readingOpt.get();
            res.put("hasMeterReading", true);
            res.put("oldElectricityIndex", mr.getOldElectricityIndex());
            res.put("newElectricityIndex", mr.getNewElectricityIndex());
            res.put("electricityUsage", mr.getElectricityUsage());
            res.put("electricityPrice", mr.getElectricityPrice());
            res.put("electricityAmount", mr.getElectricityUsage() * (mr.getElectricityPrice() != null ? mr.getElectricityPrice() : 0));

            res.put("oldWaterIndex", mr.getOldWaterIndex());
            res.put("newWaterIndex", mr.getNewWaterIndex());
            res.put("waterUsage", mr.getWaterUsage());
            res.put("waterPrice", mr.getWaterPrice());
            res.put("waterAmount", mr.getWaterUsage() * (mr.getWaterPrice() != null ? mr.getWaterPrice() : 0));
        } else {
            res.put("hasMeterReading", false);
        }

        // 4. Fixed services
        Set<ServiceItem> services = contract.getServices();
        if (services == null || services.isEmpty()) {
            services = contract.getRoom().getServices();
        }
        List<Map<String, Object>> serviceList = new ArrayList<>();
        double totalService = 0.0;
        if (services != null) {
            for (ServiceItem s : services) {
                if (s.getType() == ServiceType.METERED) continue;
                if (readingOpt.isPresent()) {
                    String sName = s.getName() != null ? s.getName().toLowerCase() : "";
                    if (sName.contains("điện") && readingOpt.get().getElectricityUsage() > 0) continue;
                    if (sName.contains("nước") && readingOpt.get().getWaterUsage() > 0) continue;
                }
                double price = s.getPrice() != null ? s.getPrice() : 0.0;
                totalService += price;
                Map<String, Object> sMap = new HashMap<>();
                sMap.put("name", s.getName());
                sMap.put("price", price);
                sMap.put("unit", s.getUnit() != null ? s.getUnit() : "tháng");
                serviceList.add(sMap);
            }
        }
        res.put("services", serviceList);
        res.put("totalServiceAmount", totalService);

        // 5. Check previous debt
        int prevMonth = (month == 1) ? 12 : (month - 1);
        int prevYear = (month == 1) ? (year - 1) : year;
        Optional<Invoice> prevInvoiceOpt = invoiceRepository.findByRoom_IdAndMonthAndYear(roomId, prevMonth, prevYear);
        if (prevInvoiceOpt.isPresent() && prevInvoiceOpt.get().getRemainingAmount() != null && prevInvoiceOpt.get().getRemainingAmount() > 0) {
            res.put("hasPreviousDebt", true);
            res.put("previousDebtAmount", prevInvoiceOpt.get().getRemainingAmount());
            res.put("previousInvoiceCode", prevInvoiceOpt.get().getInvoiceCode());
        } else {
            res.put("hasPreviousDebt", false);
            res.put("previousDebtAmount", 0.0);
        }

        return ResponseEntity.ok(res);
    }

    @GetMapping("/api/batch-preview")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> previewBatchInvoices(
            @RequestParam(required = false) Long propertyId,
            @RequestParam Integer month,
            @RequestParam Integer year,
            HttpSession session) {
        User owner = (User) session.getAttribute("user");
        if (owner == null) {
            return ResponseEntity.status(401).build();
        }

        if (propertyId != null) {
            boolean ownsProperty = propertyService.getPropertiesByOwner(owner).stream()
                    .anyMatch(p -> p.getId().equals(propertyId));
            if (!ownsProperty) {
                return ResponseEntity.status(403).build();
            }
        }

        List<Contract> activeContracts = (propertyId != null)
                ? contractRepository.findByRoom_Property_IdAndStatus(propertyId, ContractStatus.ACTIVE)
                : contractRepository.findByRoom_Property_Owner_IdAndStatus(owner.getId(), ContractStatus.ACTIVE);

        int totalActiveContracts = 0;
        int alreadyInvoicedCount = 0;
        int willCreateCount = 0;
        int recordedMetersCount = 0;
        double estimatedRentTotal = 0.0;
        List<Map<String, Object>> missingMetersRooms = new ArrayList<>();

        for (Contract contract : activeContracts) {
            Room room = contract.getRoom();
            if (room == null) continue;
            totalActiveContracts++;

            boolean hasInvoice = invoiceRepository.existsByRoom_IdAndMonthAndYear(room.getId(), month, year);
            if (hasInvoice) {
                alreadyInvoicedCount++;
            } else {
                willCreateCount++;
                estimatedRentTotal += (contract.getRentPrice() != null ? contract.getRentPrice() : 0.0);

                boolean hasMeterReading = meterReadingRepository.existsByRoom_IdAndMonthAndYear(room.getId(), month, year);
                if (hasMeterReading) {
                    recordedMetersCount++;
                } else if (!Boolean.TRUE.equals(contract.getIsElectricityWaterIncluded())) {
                    Map<String, Object> missingRoom = new HashMap<>();
                    missingRoom.put("roomId", room.getId());
                    missingRoom.put("roomName", room.getName());
                    missingRoom.put("propertyName", room.getProperty() != null ? room.getProperty().getName() : "");
                    missingMetersRooms.add(missingRoom);
                }
            }
        }

        Map<String, Object> res = new HashMap<>();
        res.put("totalActiveContracts", totalActiveContracts);
        res.put("alreadyInvoicedCount", alreadyInvoicedCount);
        res.put("willCreateCount", willCreateCount);
        res.put("recordedMetersCount", recordedMetersCount);
        res.put("missingMetersRooms", missingMetersRooms);
        res.put("estimatedRentTotal", estimatedRentTotal);

        return ResponseEntity.ok(res);
    }
}
