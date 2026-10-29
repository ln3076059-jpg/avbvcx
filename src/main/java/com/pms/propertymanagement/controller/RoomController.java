package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.dto.request.RoomRequest;
import com.pms.propertymanagement.dto.response.PropertyOwnerResponse;
import com.pms.propertymanagement.dto.response.RoomResponse;
import com.pms.propertymanagement.dto.response.ServiceItemResponse;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.enums.RoomStatus;
import com.pms.propertymanagement.service.PropertyService;
import com.pms.propertymanagement.service.RoomService;
import com.pms.propertymanagement.service.ServiceItemService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping("/owner/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final PropertyService propertyService;
    private final ServiceItemService serviceItemService;

    @GetMapping
    public String listRooms(@RequestParam(required = false) Long propertyId, Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";
        List<PropertyOwnerResponse> properties = propertyService.getPropertiesByOwner(user);
        List<RoomResponse> rooms = roomService.getAllRoomsByOwner(user);
        if (propertyId != null) {
            rooms = rooms.stream()
                    .filter(r -> propertyId.equals(r.getPropertyId()))
                    .toList();
        }
        model.addAttribute("rooms", rooms);
        model.addAttribute("properties", properties);
        model.addAttribute("selectedPropertyId", propertyId);
        model.addAttribute("content", "owner/room/list");
        model.addAttribute("activeMenu", "rooms");
        return "layout/owner-layout";
    }

    @PostMapping("/batch-create")
    public String batchCreateRooms(
            @RequestParam Long propertyId,
            @RequestParam(defaultValue = "Phòng ") String prefix,
            @RequestParam(defaultValue = "101") int startNumber,
            @RequestParam(defaultValue = "5") int count,
            @RequestParam Double price,
            @RequestParam(defaultValue = "20") Double area,
            @RequestParam(defaultValue = "2") Integer maxOccupancy,
            @RequestParam(name = "serviceIds", required = false) List<Long> serviceIds,
            HttpSession session,
            RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        int cappedCount = Math.max(1, Math.min(30, count));
        String safePrefix = (prefix != null) ? prefix : "Phòng ";

        java.util.Set<String> existingNames = roomService.getRoomsByPropertyId(propertyId).stream()
                .map(r -> r.getName() != null ? r.getName().trim().toLowerCase() : "")
                .collect(java.util.stream.Collectors.toSet());

        int createdCount = 0;
        String lastError = null;
        for (int i = 0; i < cappedCount; i++) {
            String roomName = safePrefix + (startNumber + i);
            if (existingNames.contains(roomName.trim().toLowerCase())) {
                continue;
            }
            RoomRequest req = new RoomRequest();
            req.setName(roomName);
            req.setPropertyId(propertyId);
            req.setPrice(price);
            req.setDeposit(price);
            req.setArea(area);
            req.setMaxOccupancy(maxOccupancy);
            req.setBedCount(1);
            req.setPaymentCycle(1);
            req.setIsElectricityWaterIncluded(false);
            req.setServiceIds(serviceIds);
            try {
                roomService.createRoom(req, user);
                existingNames.add(roomName.trim().toLowerCase());
                createdCount++;
            } catch (Exception e) {
                lastError = e.getMessage();
                break;
            }
        }

        if (createdCount > 0) {
            ra.addFlashAttribute("successMessage", "Đã tạo hàng loạt thành công " + createdCount + " phòng mới!");
        } else if (lastError != null) {
            ra.addFlashAttribute("errorMessage", lastError);
        } else {
            ra.addFlashAttribute("successMessage", "Đã tạo hàng loạt thành công " + createdCount + " phòng mới!");
        }
        return "redirect:/owner/rooms?propertyId=" + propertyId;
    }

    @GetMapping("/create")
    public String showCreateForm(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";
        List<PropertyOwnerResponse> properties = propertyService.getPropertiesByOwner(user);
        
        model.addAttribute("roomRequest", new RoomRequest());
        model.addAttribute("properties", properties);
        model.addAttribute("content", "owner/room/create");
        model.addAttribute("activeMenu", "rooms");
        return "layout/owner-layout";
    }

    @PostMapping("/create")
    public String createRoom(@ModelAttribute("roomRequest") RoomRequest roomRequest,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";
        try {
            boolean ownsProperty = propertyService.getPropertiesByOwner(user).stream()
                    .anyMatch(p -> p.getId().equals(roomRequest.getPropertyId()));
            if (!ownsProperty) {
                redirectAttributes.addFlashAttribute("errorMessage", "Bạn không có quyền thêm phòng cho nhà trọ này!");
                redirectAttributes.addAttribute("error", true);
                return "redirect:/owner/rooms/create";
            }
            roomService.createRoom(roomRequest, user);
            redirectAttributes.addAttribute("success", true);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            redirectAttributes.addAttribute("error", true);
            return "redirect:/owner/rooms/create";
        }
        return "redirect:/owner/rooms";
    }

    @PostMapping("/delete/{id}")
    public String deleteRoom(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";
        try {
            roomService.deleteRoom(id, user);
            redirectAttributes.addAttribute("deleted", true);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/owner/rooms";
    }

    @PostMapping("/update/{id}")
    public String updateRoom(@PathVariable Long id,
                             @RequestParam String name,
                             @RequestParam Double price,
                             @RequestParam(required = false) Double deposit,
                             @RequestParam(required = false) Double area,
                             @RequestParam(required = false) Integer maxOccupancy,
                             @RequestParam(required = false) String description,
                             HttpSession session,
                             RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";
        try {
            roomService.updateRoom(id, name, price, deposit, area, maxOccupancy, description, user);
            ra.addFlashAttribute("successMessage", "Cập nhật thông tin phòng thành công!");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/owner/rooms";
    }

    @PostMapping("/{id}/status")
    public String updateRoomStatus(@PathVariable Long id,
                                   @RequestParam String status,
                                   HttpSession session,
                                   RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";
        try {
            RoomStatus targetStatus = RoomStatus.valueOf(status.trim().toUpperCase());
            roomService.updateRoomStatus(id, targetStatus, user);
            ra.addFlashAttribute("successMessage", "Cập nhật trạng thái phòng thành công!");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/owner/rooms";
    }
    
    // API to get services by property ID (for AJAX in create form)
    @GetMapping("/api/properties/{propertyId}/services")
    @ResponseBody
    public List<ServiceItemResponse> getServicesByProperty(@PathVariable Long propertyId, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return Collections.emptyList();
        boolean ownsProperty = propertyService.getPropertiesByOwner(user).stream()
                .anyMatch(p -> p.getId().equals(propertyId));
        if (!ownsProperty) {
            return Collections.emptyList();
        }
        return serviceItemService.getServicesByPropertyId(propertyId);
    }
}
