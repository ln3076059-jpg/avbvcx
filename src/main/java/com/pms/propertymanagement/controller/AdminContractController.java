package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.entity.Contract;
import com.pms.propertymanagement.entity.Room;
import com.pms.propertymanagement.enums.ContractStatus;
import com.pms.propertymanagement.enums.RoomStatus;
import com.pms.propertymanagement.repository.ContractRepository;
import com.pms.propertymanagement.repository.RoomRepository;
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
@RequestMapping("/admin/contracts")
@RequiredArgsConstructor
@Slf4j
public class AdminContractController {

    private final ContractRepository contractRepository;
    private final RoomRepository roomRepository;

    @GetMapping
    public String listContracts(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String statusFilter,
            Model model) {

        List<Contract> contracts = contractRepository.findAll();

        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim().toLowerCase();
            contracts = contracts.stream()
                    .filter(c -> (c.getCode() != null && c.getCode().toLowerCase().contains(kw))
                            || (c.getRoom() != null && c.getRoom().getName() != null && c.getRoom().getName().toLowerCase().contains(kw))
                            || (c.getRoom() != null && c.getRoom().getProperty() != null && c.getRoom().getProperty().getName() != null && c.getRoom().getProperty().getName().toLowerCase().contains(kw))
                            || (c.getRepresentative() != null && c.getRepresentative().getFullName() != null && c.getRepresentative().getFullName().toLowerCase().contains(kw)))
                    .collect(Collectors.toList());
        }

        if (statusFilter != null && !statusFilter.trim().isEmpty()) {
            try {
                ContractStatus st = ContractStatus.valueOf(statusFilter.trim().toUpperCase());
                contracts = contracts.stream()
                         .filter(c -> c.getStatus() == st)
                         .collect(Collectors.toList());
            } catch (IllegalArgumentException ignored) {}
        }

        contracts.sort((a, b) -> Long.compare(b.getId(), a.getId()));

        model.addAttribute("contracts", contracts);
        model.addAttribute("keyword", keyword);
        model.addAttribute("statusFilter", statusFilter);
        model.addAttribute("statuses", ContractStatus.values());
        model.addAttribute("activePage", "contracts");
        model.addAttribute("pageTitle", "Quản Lý Hợp Đồng Thuê");
        model.addAttribute("content", "admin/contracts/list");

        return "layout/admin-layout";
    }

    @GetMapping("/{id}")
    public String viewContractDetail(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Contract> contractOpt = contractRepository.findById(id);
        if (contractOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy hợp đồng mã ID: " + id);
            return "redirect:/admin/contracts";
        }

        Contract contract = contractOpt.get();
        model.addAttribute("contract", contract);
        model.addAttribute("activePage", "contracts");
        model.addAttribute("pageTitle", "Chi Tiết Hợp Đồng: " + contract.getCode());
        model.addAttribute("content", "admin/contracts/detail");
        return "layout/admin-layout";
    }

    @PostMapping("/{id}/terminate")
    public String terminateContract(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        Optional<Contract> contractOpt = contractRepository.findById(id);
        if (contractOpt.isPresent()) {
            Contract contract = contractOpt.get();
            contract.setStatus(ContractStatus.TERMINATED);
            contractRepository.save(contract);
            if (contract.getRoom() != null) {
                Room room = contract.getRoom();
                room.setStatus(RoomStatus.AVAILABLE);
                roomRepository.save(room);
            }
            redirectAttributes.addFlashAttribute("successMessage", "Đã thanh lý chấm dứt hợp đồng: " + contract.getCode());
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Hợp đồng không tồn tại!");
        }
        return "redirect:/admin/contracts";
    }

    @PostMapping("/{id}/delete")
    public String deleteContract(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        Optional<Contract> contractOpt = contractRepository.findById(id);
        if (contractOpt.isPresent()) {
            Contract contract = contractOpt.get();
            Room room = contract.getRoom();
            try {
                contractRepository.deleteById(id);
                if (room != null) {
                    room.setStatus(RoomStatus.AVAILABLE);
                    roomRepository.save(room);
                }
                redirectAttributes.addFlashAttribute("successMessage", "Đã xóa vĩnh viễn hợp đồng: " + contract.getCode());
            } catch (Exception ex) {
                log.error("Không thể xóa contract ID: {}", id, ex);
                contract.setStatus(ContractStatus.TERMINATED);
                contractRepository.save(contract);
                if (room != null) {
                    room.setStatus(RoomStatus.AVAILABLE);
                    roomRepository.save(room);
                }
                redirectAttributes.addFlashAttribute("errorMessage", "Hợp đồng đang có dữ liệu hóa đơn liên quan. Hệ thống đã CHUYỂN TRẠNG THÁI KẾT THÚC.");
            }
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Hợp đồng không tồn tại!");
        }
        return "redirect:/admin/contracts";
    }

    @GetMapping("/export")
    public void exportContractsCsv(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        List<Contract> contracts = contractRepository.findAll();
        contracts.sort((a, b) -> Long.compare(b.getId(), a.getId()));

        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"danh-sach-hop-dong.csv\"");

        try (java.io.PrintWriter writer = response.getWriter()) {
            writer.write('\uFEFF');
            writer.println("Mã Hợp Đồng,Phòng,Nhà Trọ,Người Đại Diện,Số Điện Thoại,Giá Thuê (VNĐ),Tiền Cọc (VNĐ),Ngày Bắt Đầu,Ngày Kết Thúc,Trạng Thái");
            for (Contract c : contracts) {
                String roomName = c.getRoom() != null ? c.getRoom().getName() : "";
                String propName = (c.getRoom() != null && c.getRoom().getProperty() != null) ? c.getRoom().getProperty().getName() : "";
                String repName = c.getRepresentative() != null ? c.getRepresentative().getFullName() : "";
                String repPhone = c.getRepresentative() != null ? c.getRepresentative().getPhone() : "";
                writer.printf("%s,%s,%s,%s,%s,%.0f,%.0f,%s,%s,%s%n",
                        escapeCsv(c.getCode()),
                        escapeCsv(roomName),
                        escapeCsv(propName),
                        escapeCsv(repName),
                        escapeCsv(repPhone),
                        c.getRentPrice() != null ? c.getRentPrice() : 0.0,
                        c.getDeposit() != null ? c.getDeposit() : 0.0,
                        c.getStartDate() != null ? c.getStartDate().toString() : "",
                        c.getEndDate() != null ? c.getEndDate().toString() : "",
                        c.getStatus() != null ? c.getStatus().name() : ""
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
