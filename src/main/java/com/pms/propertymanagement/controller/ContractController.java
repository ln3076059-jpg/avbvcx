package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.dto.request.ContractRequest;
import com.pms.propertymanagement.dto.response.RoomResponse;
import com.pms.propertymanagement.entity.Contract;
import com.pms.propertymanagement.entity.Invoice;
import com.pms.propertymanagement.entity.Room;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.enums.ContractStatus;
import com.pms.propertymanagement.enums.InvoiceStatus;
import com.pms.propertymanagement.repository.InvoiceRepository;
import com.pms.propertymanagement.service.ContractService;
import com.pms.propertymanagement.service.PropertyService;
import com.pms.propertymanagement.service.RoomService;
import com.pms.propertymanagement.service.TenantService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping("/owner/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;
    private final PropertyService propertyService;
    private final RoomService roomService;
    private final TenantService tenantService;
    private final InvoiceRepository invoiceRepository;

    @GetMapping
    public String listContracts(Model model, HttpSession session,
                                @RequestParam(required = false) ContractStatus status,
                                @RequestParam(required = false) String keyword,
                                @RequestParam(defaultValue = "1") int page,
                                @RequestParam(defaultValue = "10") int size) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner"; // Just in case

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());
        Page<Contract> contractPage = contractService.getContractsByOwner(user.getId(), status, keyword, pageable);

        model.addAttribute("contracts", contractPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status); // For tab active state
        
        model.addAttribute("content", "owner/contract/list");

        model.addAttribute("activeMenu", "contracts");
        return "layout/owner-layout";
    }

    @GetMapping("/create")
    public String createContractPage(@RequestParam(required = false) Long roomId,
                                     @RequestParam(required = false) Long tenantId,
                                     Model model,
                                     HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        if (roomId != null) {
            Room room = roomService.getRoomById(roomId);
            if (room != null && room.getProperty() != null && room.getProperty().getOwner() != null
                    && room.getProperty().getOwner().getId().equals(user.getId())) {
                model.addAttribute("preselectedRoomId", room.getId());
                model.addAttribute("preselectedPropertyId", room.getProperty().getId());
            }
        }
        if (tenantId != null) {
            model.addAttribute("preselectedTenantId", tenantId);
        }
        
        model.addAttribute("properties", propertyService.getPropertiesByOwner(user));
        model.addAttribute("tenants", tenantService.getAllTenantsByOwner(user));
        model.addAttribute("content", "owner/contract/create");

        model.addAttribute("activeMenu", "contracts");
        return "layout/owner-layout";
    }

    @PostMapping("/create")
    public String createContract(@ModelAttribute ContractRequest contractRequest,
                                 HttpSession session,
                                 RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        try {
            Room room = roomService.getRoomById(contractRequest.getRoomId());
            if (room == null || room.getProperty() == null || room.getProperty().getOwner() == null
                    || !room.getProperty().getOwner().getId().equals(user.getId())) {
                ra.addFlashAttribute("errorMessage", "Bạn không có quyền lập hợp đồng cho phòng này!");
                return "redirect:/owner/contracts/create";
            }
            contractService.createContract(contractRequest, user.getId());
            return "redirect:/owner/contracts?success=true";
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/owner/contracts/create";
        }
    }

    @GetMapping("/{id}/checkout")
    public String checkoutForm(@PathVariable Long id, HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        Contract contract = contractService.getContractById(id);
        if (!contract.getRoom().getProperty().getOwner().getId().equals(user.getId())) {
            return "redirect:/owner/contracts";
        }

        List<Invoice> unpaidInvoices = invoiceRepository.findByRoom_IdOrderByYearDescMonthDesc(contract.getRoom().getId()).stream()
                .filter(inv -> inv.getContract() != null && inv.getContract().getId().equals(contract.getId()))
                .filter(inv -> inv.getRemainingAmount() != null && inv.getRemainingAmount() > 0
                        && inv.getStatus() != InvoiceStatus.PAID && inv.getStatus() != InvoiceStatus.CANCELLED)
                .toList();
        double unpaidInvoicesTotal = unpaidInvoices.stream()
                .mapToDouble(Invoice::getRemainingAmount)
                .sum();

        com.pms.propertymanagement.dto.request.ContractCheckoutRequest request = new com.pms.propertymanagement.dto.request.ContractCheckoutRequest();
        request.setContractId(contract.getId());
        request.setCheckoutDate(java.time.LocalDate.now());
        request.setDeposit(contract.getDeposit());
        request.setUnpaidRentAmount(unpaidInvoicesTotal);

        model.addAttribute("contract", contract);
        model.addAttribute("unpaidInvoices", unpaidInvoices);
        model.addAttribute("unpaidInvoicesTotal", unpaidInvoicesTotal);
        model.addAttribute("checkoutRequest", request);
        model.addAttribute("activeMenu", "contracts");
        model.addAttribute("content", "owner/contract/checkout");
        return "layout/owner-layout";
    }

    @PostMapping("/{id}/checkout")
    public String processCheckout(@PathVariable Long id, 
                                  @ModelAttribute com.pms.propertymanagement.dto.request.ContractCheckoutRequest request, 
                                  HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        request.setContractId(id);
        contractService.checkoutContract(request, user.getId());
        return "redirect:/owner/contracts?checkoutSuccess=true";
    }

    @PostMapping("/{id}/terminate")
    public String terminateContract(@PathVariable Long id, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        Contract contract = contractService.getContractById(id);
        if (contract == null || contract.getRoom() == null ||
            contract.getRoom().getProperty() == null ||
            contract.getRoom().getProperty().getOwner() == null ||
            !contract.getRoom().getProperty().getOwner().getId().equals(user.getId())) {
            return "redirect:/owner/contracts?error=unauthorized";
        }

        contractService.updateContractStatus(id, ContractStatus.TERMINATED);
        return "redirect:/owner/contracts";
    }

    @PostMapping("/{id}/renew")
    public String renewContract(@PathVariable Long id,
                                @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate newEndDate,
                                @RequestParam(required = false) Double newRentPrice,
                                HttpSession session,
                                RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        try {
            contractService.renewContract(id, newEndDate, newRentPrice, user);
            ra.addFlashAttribute("successMessage", "Gia hạn hợp đồng thành công!");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/owner/contracts";
    }

    // AJAX helper endpoints
    @GetMapping("/api/rooms-by-property")
    @ResponseBody
    public List<RoomResponse> getRoomsByProperty(@RequestParam Long propertyId, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return Collections.emptyList();
        boolean ownsProperty = propertyService.getPropertiesByOwner(user).stream()
                .anyMatch(p -> p.getId().equals(propertyId));
        if (!ownsProperty) {
            return Collections.emptyList();
        }
        return roomService.getAvailableRoomsByProperty(propertyId);
    }
}
