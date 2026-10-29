package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.dto.request.InvoiceRequest;
import com.pms.propertymanagement.dto.request.MeterReadingRequest;
import com.pms.propertymanagement.entity.Invoice;
import com.pms.propertymanagement.entity.MeterReading;
import com.pms.propertymanagement.entity.Room;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.enums.RoomStatus;
import com.pms.propertymanagement.repository.MeterReadingRepository;
import com.pms.propertymanagement.repository.RoomRepository;
import com.pms.propertymanagement.service.InvoiceService;
import com.pms.propertymanagement.service.MeterReadingService;
import com.pms.propertymanagement.service.PropertyService;
import com.pms.propertymanagement.service.RoomService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/owner/meters")
@RequiredArgsConstructor
public class MeterReadingController {

    private final MeterReadingService meterReadingService;
    private final PropertyService propertyService;
    private final RoomService roomService;
    private final InvoiceService invoiceService;
    private final RoomRepository roomRepository;
    private final MeterReadingRepository meterReadingRepository;

    @GetMapping
    public String listReadings(
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpSession session,
            Model model
    ) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        Pageable pageable = PageRequest.of(page - 1, size);
        Page<MeterReading> readingPage = meterReadingService.searchReadings(user.getId(), propertyId, roomId, month, year, pageable);

        model.addAttribute("readings", readingPage);
        model.addAttribute("properties", propertyService.getPropertiesByOwner(user));
        if (propertyId != null) {
            boolean ownsProperty = propertyService.getPropertiesByOwner(user).stream()
                    .anyMatch(p -> p.getId().equals(propertyId));
            if (ownsProperty) {
                model.addAttribute("rooms", roomService.getRoomsByPropertyId(propertyId));
            }
        }

        model.addAttribute("selectedPropertyId", propertyId);
        model.addAttribute("selectedRoomId", roomId);
        model.addAttribute("selectedMonth", month);
        model.addAttribute("selectedYear", year);
        model.addAttribute("currentPage", page);

        model.addAttribute("content", "owner/meter/list");
        model.addAttribute("activeMenu", "meters");
        return "layout/owner-layout";
    }

    @GetMapping("/create")
    public String createForm(
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) Long roomId,
            HttpSession session,
            Model model
    ) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        MeterReadingRequest request = new MeterReadingRequest();
        LocalDate now = LocalDate.now();
        request.setMonth(now.getMonthValue());
        request.setYear(now.getYear());

        if (roomId != null) {
            Room room = roomService.getRoomById(roomId);
            if (room != null && room.getProperty() != null && room.getProperty().getOwner() != null
                    && room.getProperty().getOwner().getId().equals(user.getId())) {
                request.setRoomId(roomId);
                Optional<MeterReading> latest = meterReadingService.getLatestReading(roomId);
                if (latest.isPresent()) {
                    request.setOldElectricityIndex(latest.get().getNewElectricityIndex());
                    request.setOldWaterIndex(latest.get().getNewWaterIndex());
                    request.setElectricityPrice(latest.get().getElectricityPrice());
                    request.setWaterPrice(latest.get().getWaterPrice());
                }
            }
        }

        model.addAttribute("meterRequest", request);
        model.addAttribute("properties", propertyService.getPropertiesByOwner(user));
        model.addAttribute("selectedPropertyId", propertyId);
        model.addAttribute("selectedRoomId", roomId);
        model.addAttribute("content", "owner/meter/create");
        model.addAttribute("activeMenu", "meters");
        return "layout/owner-layout";
    }

    @PostMapping("/create")
    public String saveReading(
            @Valid @ModelAttribute("meterRequest") MeterReadingRequest meterRequest,
            BindingResult bindingResult,
            @RequestParam(required = false, defaultValue = "false") boolean saveAndNext,
            @RequestParam(required = false, defaultValue = "false") boolean createInvoiceNow,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        if (bindingResult.hasErrors()) {
            if (bindingResult.getFieldError() != null) {
                model.addAttribute("errorMessage", bindingResult.getFieldError().getDefaultMessage());
            }
            model.addAttribute("properties", propertyService.getPropertiesByOwner(user));
            model.addAttribute("content", "owner/meter/create");
            model.addAttribute("activeMenu", "meters");
            return "layout/owner-layout";
        }

        try {
            MeterReading saved = meterReadingService.saveReading(meterRequest, user.getId());
            if (createInvoiceNow) {
                try {
                    Optional<Invoice> existingOpt = invoiceService.getInvoiceByRoomAndMonthAndYear(
                            saved.getRoom().getId(), saved.getMonth(), saved.getYear());
                    if (existingOpt.isPresent()) {
                        Invoice existingInvoice = existingOpt.get();
                        redirectAttributes.addFlashAttribute("successMessage",
                                "Đã lưu chỉ số điện nước! Phòng này đã có hóa đơn kỳ " + saved.getMonth() + "/" + saved.getYear() + ".");
                        return "redirect:/owner/invoices/" + existingInvoice.getId();
                    }

                    InvoiceRequest invReq = new InvoiceRequest();
                    invReq.setRoomId(saved.getRoom().getId());
                    invReq.setMonth(saved.getMonth());
                    invReq.setYear(saved.getYear());
                    invReq.setDueDate(LocalDate.now().plusDays(5));

                    Invoice createdInvoice = invoiceService.createInvoice(invReq, user.getId());
                    redirectAttributes.addFlashAttribute("successMessage",
                            "Đã chốt chỉ số điện nước và tự động lập hóa đơn " + createdInvoice.getInvoiceCode() + " thành công!");
                    return "redirect:/owner/invoices/" + createdInvoice.getId();
                } catch (Exception invEx) {
                    String msg = "Đã lưu chỉ số điện nước cho phòng " + saved.getRoom().getName()
                            + ", nhưng chưa thể tự động lập hóa đơn: " + invEx.getMessage();
                    redirectAttributes.addFlashAttribute("warningMessage", msg);
                    redirectAttributes.addFlashAttribute("errorMessage", msg);
                    return "redirect:/owner/meters";
                }
            }

            redirectAttributes.addFlashAttribute("successMessage", "Ghi chỉ số điện nước thành công cho phòng " + saved.getRoom().getName() + "!");
            if (saveAndNext) {
                Long propId = (saved.getRoom() != null && saved.getRoom().getProperty() != null) ? saved.getRoom().getProperty().getId() : null;
                return "redirect:/owner/meters/create" + (propId != null ? "?propertyId=" + propId : "");
            }
            return "redirect:/owner/meters";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("properties", propertyService.getPropertiesByOwner(user));
            model.addAttribute("content", "owner/meter/create");
            model.addAttribute("activeMenu", "meters");
            return "layout/owner-layout";
        }
    }

    @PostMapping("/{id}/quick-invoice")
    public String createQuickInvoiceFromReading(@PathVariable Long id,
                                                HttpSession session,
                                                RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        try {
            MeterReading reading = meterReadingService.getReadingById(id, user.getId());
            Optional<Invoice> existingOpt = invoiceService.getInvoiceByRoomAndMonthAndYear(
                    reading.getRoom().getId(), reading.getMonth(), reading.getYear());
            if (existingOpt.isPresent()) {
                Invoice existingInvoice = existingOpt.get();
                ra.addFlashAttribute("successMessage",
                        "Phòng " + reading.getRoom().getName() + " đã có hóa đơn kỳ " + reading.getMonth() + "/" + reading.getYear() + " (" + existingInvoice.getInvoiceCode() + ").");
                return "redirect:/owner/invoices/" + existingInvoice.getId();
            }

            InvoiceRequest invReq = new InvoiceRequest();
            invReq.setRoomId(reading.getRoom().getId());
            invReq.setMonth(reading.getMonth());
            invReq.setYear(reading.getYear());
            invReq.setDueDate(LocalDate.now().plusDays(5));

            Invoice createdInvoice = invoiceService.createInvoice(invReq, user.getId());
            ra.addFlashAttribute("successMessage",
                    "Đã tự động lập hóa đơn " + createdInvoice.getInvoiceCode() + " từ chỉ số điện nước phòng " + reading.getRoom().getName() + "!");
            return "redirect:/owner/invoices/" + createdInvoice.getId();
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Không thể lập hóa đơn tự động: " + e.getMessage());
            return "redirect:/owner/meters";
        }
    }

    @GetMapping({"/{id}/edit", "/edit/{id}"})
    public String editForm(@PathVariable Long id, HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        MeterReading reading = meterReadingService.getReadingById(id, user.getId());
        MeterReadingRequest request = new MeterReadingRequest(
                reading.getId(),
                reading.getRoom().getId(),
                reading.getMonth(),
                reading.getYear(),
                reading.getOldElectricityIndex(),
                reading.getNewElectricityIndex(),
                reading.getOldWaterIndex(),
                reading.getNewWaterIndex(),
                reading.getElectricityPrice(),
                reading.getWaterPrice()
        );

        model.addAttribute("meterRequest", request);
        model.addAttribute("reading", reading);
        model.addAttribute("properties", propertyService.getPropertiesByOwner(user));
        model.addAttribute("content", "owner/meter/edit");
        model.addAttribute("activeMenu", "meters");
        return "layout/owner-layout";
    }

    @PostMapping({"/{id}/edit", "/edit/{id}"})
    public String updateReading(
            @PathVariable Long id,
            @Valid @ModelAttribute("meterRequest") MeterReadingRequest meterRequest,
            BindingResult bindingResult,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        meterRequest.setId(id);
        if (bindingResult.hasErrors()) {
            MeterReading reading = meterReadingService.getReadingById(id, user.getId());
            model.addAttribute("reading", reading);
            model.addAttribute("properties", propertyService.getPropertiesByOwner(user));
            model.addAttribute("content", "owner/meter/edit");
            model.addAttribute("activeMenu", "meters");
            return "layout/owner-layout";
        }

        try {
            meterReadingService.saveReading(meterRequest, user.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật chỉ số điện nước thành công!");
            return "redirect:/owner/meters";
        } catch (IllegalArgumentException e) {
            MeterReading reading = meterReadingService.getReadingById(id, user.getId());
            model.addAttribute("reading", reading);
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("properties", propertyService.getPropertiesByOwner(user));
            model.addAttribute("content", "owner/meter/edit");
            model.addAttribute("activeMenu", "meters");
            return "layout/owner-layout";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        MeterReading reading = meterReadingService.getReadingById(id, user.getId());
        model.addAttribute("reading", reading);
        model.addAttribute("content", "owner/meter/detail");
        model.addAttribute("activeMenu", "meters");
        return "layout/owner-layout";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        try {
            meterReadingService.deleteReading(id, user.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa chỉ số điện nước!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/owner/meters";
    }

    @GetMapping("/api/latest-index")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getLatestIndex(@RequestParam Long roomId, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return ResponseEntity.status(401).build();

        Room room = roomService.getRoomById(roomId);
        if (room == null || room.getProperty() == null || room.getProperty().getOwner() == null
                || !room.getProperty().getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        Map<String, Object> res = new HashMap<>();
        Optional<MeterReading> latest = meterReadingService.getLatestReading(roomId);
        if (latest.isPresent()) {
            res.put("hasPrevious", true);
            res.put("oldElectricityIndex", latest.get().getNewElectricityIndex());
            res.put("oldWaterIndex", latest.get().getNewWaterIndex());
            res.put("electricityPrice", latest.get().getElectricityPrice());
            res.put("waterPrice", latest.get().getWaterPrice());
        } else {
            res.put("hasPrevious", false);
            res.put("oldElectricityIndex", 0.0);
            res.put("oldWaterIndex", 0.0);
            res.put("electricityPrice", 3500.0);
            res.put("waterPrice", 20000.0);
        }
        return ResponseEntity.ok(res);
    }

    @GetMapping("/api/bulk-rooms")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getBulkRoomsForMeter(
            @RequestParam(required = false) Long propertyId,
            @RequestParam Integer month,
            @RequestParam Integer year,
            HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return ResponseEntity.status(401).build();

        List<Room> rooms = roomRepository.findByProperty_Owner_Id(user.getId());
        if (propertyId != null) {
            rooms = rooms.stream()
                    .filter(r -> r.getProperty() != null && r.getProperty().getId().equals(propertyId))
                    .toList();
        }

        List<Room> sortedRooms = new ArrayList<>(rooms);
        sortedRooms.sort((a, b) -> {
            int statusCmp = Boolean.compare(b.getStatus() == RoomStatus.RENTED, a.getStatus() == RoomStatus.RENTED);
            if (statusCmp != 0) return statusCmp;
            String pA = a.getProperty() != null && a.getProperty().getName() != null ? a.getProperty().getName() : "";
            String pB = b.getProperty() != null && b.getProperty().getName() != null ? b.getProperty().getName() : "";
            int propCmp = pA.compareToIgnoreCase(pB);
            if (propCmp != 0) return propCmp;
            String rA = a.getName() != null ? a.getName() : "";
            String rB = b.getName() != null ? b.getName() : "";
            return rA.compareToIgnoreCase(rB);
        });

        List<Map<String, Object>> result = new ArrayList<>();
        for (Room room : sortedRooms) {
            Optional<MeterReading> currentOpt = meterReadingRepository.findByRoom_IdAndMonthAndYear(room.getId(), month, year);
            Optional<MeterReading> prevOpt = meterReadingRepository.findLatestBefore(room.getId(), month, year);
            if (prevOpt.isEmpty()) {
                prevOpt = meterReadingRepository.findTopByRoom_IdOrderByYearDescMonthDesc(room.getId());
            }

            Double oldElec = 0.0;
            Double oldWater = 0.0;
            Double newElec = null;
            Double newWater = null;
            boolean alreadyRecorded = false;

            if (currentOpt.isPresent()) {
                MeterReading cur = currentOpt.get();
                oldElec = cur.getOldElectricityIndex() != null ? cur.getOldElectricityIndex() : 0.0;
                oldWater = cur.getOldWaterIndex() != null ? cur.getOldWaterIndex() : 0.0;
                newElec = cur.getNewElectricityIndex();
                newWater = cur.getNewWaterIndex();
                alreadyRecorded = true;
            } else if (prevOpt.isPresent()) {
                MeterReading prev = prevOpt.get();
                oldElec = prev.getNewElectricityIndex() != null ? prev.getNewElectricityIndex() : 0.0;
                oldWater = prev.getNewWaterIndex() != null ? prev.getNewWaterIndex() : 0.0;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("roomId", room.getId());
            item.put("roomName", room.getName());
            item.put("propertyName", room.getProperty() != null ? room.getProperty().getName() : "");
            item.put("status", room.getStatus() != null ? room.getStatus().name() : "AVAILABLE");
            item.put("oldElectricityIndex", oldElec);
            item.put("oldWaterIndex", oldWater);
            item.put("newElectricityIndex", newElec);
            item.put("newWaterIndex", newWater);
            item.put("alreadyRecorded", alreadyRecorded);
            result.add(item);
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping("/bulk-save")
    public String saveBulkReadings(
            @RequestParam Integer month,
            @RequestParam Integer year,
            @RequestParam(name = "roomIds", required = false) List<Long> roomIds,
            @RequestParam(name = "oldElectricityIndices", required = false) List<Double> oldElectricityIndices,
            @RequestParam(name = "newElectricityIndices", required = false) List<Double> newElectricityIndices,
            @RequestParam(name = "oldWaterIndices", required = false) List<Double> oldWaterIndices,
            @RequestParam(name = "newWaterIndices", required = false) List<Double> newWaterIndices,
            @RequestParam(defaultValue = "false") boolean autoCreateInvoices,
            HttpSession session,
            RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        int savedCount = 0;
        if (roomIds != null && !roomIds.isEmpty()) {
            for (int i = 0; i < roomIds.size(); i++) {
                Long roomId = roomIds.get(i);
                if (roomId == null) continue;

                Double newElec = (newElectricityIndices != null && i < newElectricityIndices.size()) ? newElectricityIndices.get(i) : null;
                Double newWater = (newWaterIndices != null && i < newWaterIndices.size()) ? newWaterIndices.get(i) : null;
                if (newElec == null || newWater == null) continue;

                Double oldElec = (oldElectricityIndices != null && i < oldElectricityIndices.size() && oldElectricityIndices.get(i) != null)
                        ? oldElectricityIndices.get(i) : 0.0;
                Double oldWater = (oldWaterIndices != null && i < oldWaterIndices.size() && oldWaterIndices.get(i) != null)
                        ? oldWaterIndices.get(i) : 0.0;

                if (newElec >= oldElec && newWater >= oldWater) {
                    try {
                        MeterReadingRequest req = new MeterReadingRequest();
                        meterReadingRepository.findByRoom_IdAndMonthAndYear(roomId, month, year)
                                .ifPresent(existing -> req.setId(existing.getId()));
                        req.setRoomId(roomId);
                        req.setMonth(month);
                        req.setYear(year);
                        req.setOldElectricityIndex(oldElec);
                        req.setNewElectricityIndex(newElec);
                        req.setOldWaterIndex(oldWater);
                        req.setNewWaterIndex(newWater);

                        MeterReading saved = meterReadingService.saveReading(req, user.getId());
                        savedCount++;

                        if (autoCreateInvoices) {
                            try {
                                Optional<Invoice> existingOpt = invoiceService.getInvoiceByRoomAndMonthAndYear(
                                        saved.getRoom().getId(), month, year);
                                if (existingOpt.isEmpty()) {
                                    InvoiceRequest invReq = new InvoiceRequest();
                                    invReq.setRoomId(saved.getRoom().getId());
                                    invReq.setMonth(month);
                                    invReq.setYear(year);
                                    invReq.setDueDate(LocalDate.now().plusDays(5));
                                    invoiceService.createInvoice(invReq, user.getId());
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        ra.addFlashAttribute("successMessage", "Đã chốt chỉ số điện nước hàng loạt thành công cho " + savedCount + " phòng!");
        return "redirect:/owner/meters?month=" + month + "&year=" + year;
    }
}
