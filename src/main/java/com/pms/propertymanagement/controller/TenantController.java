package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.dto.request.TenantRequest;
import com.pms.propertymanagement.dto.response.TenantResponse;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.enums.Gender;
import com.pms.propertymanagement.service.ContactService;
import com.pms.propertymanagement.service.TenantService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
@RequestMapping("/owner/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;
    private final ContactService contactService;

    @GetMapping
    public String index(HttpSession session, Model model) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) return "redirect:/login/owner";
        
        List<TenantResponse> tenants = tenantService.getAllTenantsByOwner(sessionUser);
        model.addAttribute("tenants", tenants);
        model.addAttribute("content", "owner/tenant/list");
        model.addAttribute("activeMenu", "tenants");
        return "layout/owner-layout";
    }

    @GetMapping("/create")
    public String create(@RequestParam(required = false) String fullName,
                         @RequestParam(required = false) String phone,
                         @RequestParam(required = false) Long contactId,
                         @RequestParam(required = false) Long roomId,
                         HttpSession session,
                         Model model) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) return "redirect:/login/owner";

        TenantRequest tenantRequest = new TenantRequest();
        if (fullName != null && !fullName.isBlank()) {
            tenantRequest.setFullName(fullName.trim());
        }
        if (phone != null && !phone.isBlank()) {
            tenantRequest.setPhone(phone.trim());
        }
        model.addAttribute("tenantRequest", tenantRequest);
        if (contactId != null) {
            model.addAttribute("contactId", contactId);
        }
        if (roomId != null) {
            model.addAttribute("roomId", roomId);
        }
        model.addAttribute("content", "owner/tenant/create");
        model.addAttribute("activeMenu", "tenants");
        return "layout/owner-layout";
    }

    @PostMapping("/create")
    public String store(@ModelAttribute("tenantRequest") TenantRequest request,
                        @RequestParam(required = false) Long contactId,
                        @RequestParam(required = false) Long roomId,
                        HttpSession session,
                        RedirectAttributes ra) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) return "redirect:/login/owner";
        
        try {
            tenantService.createTenant(request, sessionUser);
            if (contactId != null) {
                contactService.markAsContacted(contactId, sessionUser.getId());
            }
            if (roomId != null) {
                ra.addFlashAttribute("successMessage", "Đã duyệt tạo hồ sơ khách thuê thành công! Tiếp tục lập hợp đồng cho phòng khách đã đăng ký.");
                return "redirect:/owner/contracts/create?roomId=" + roomId;
            }
            ra.addFlashAttribute("successMessage", "Tạo hồ sơ khách thuê thành công!");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/owner/tenants/create";
        }

        return "redirect:/owner/tenants?success";
    }

    @PostMapping("/update/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute TenantRequest request,
                         HttpSession session,
                         RedirectAttributes ra) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) return "redirect:/login/owner";

        try {
            tenantService.updateTenant(id, request, sessionUser);
            ra.addFlashAttribute("successMessage", "Cập nhật hồ sơ khách thuê thành công!");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/owner/tenants";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) return "redirect:/login/owner";

        try {
            tenantService.deleteTenant(id, sessionUser.getId());
            ra.addFlashAttribute("successMessage", "Đã xóa hồ sơ khách thuê thành công!");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/owner/tenants";
    }

    @GetMapping("/export-ct01")
    public void exportCt01(HttpSession session, HttpServletResponse response) throws IOException {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            response.sendRedirect("/login/owner");
            return;
        }

        List<TenantResponse> tenants = tenantService.getTenantsByOwner(user);
        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"mau-ct01-khai-bao-tam-tru.csv\"");

        try (PrintWriter writer = response.getWriter()) {
            writer.write('\uFEFF');
            writer.println("STT,Họ và Tên,Số ĐDCN / CCCD,Giới Tính,Số Điện Thoại,Email,Địa Chỉ Thường Trú,Nghề Nghiệp");

            int stt = 1;
            for (TenantResponse t : tenants) {
                String genderStr = "Khác";
                if (t.getGender() == Gender.MALE) genderStr = "Nam";
                else if (t.getGender() == Gender.FEMALE) genderStr = "Nữ";

                writer.printf("%d,%s,%s,%s,%s,%s,%s,%s%n",
                        stt++,
                        escapeCsv(t.getFullName()),
                        escapeCsv(t.getCitizenId()),
                        escapeCsv(genderStr),
                        escapeCsv(t.getPhone()),
                        escapeCsv(t.getEmail()),
                        escapeCsv(t.getPermanentAddress()),
                        escapeCsv(t.getCareer())
                );
            }
        }
    }

    @PostMapping("/api/parse-cccd")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> parseCccdString(@RequestParam String rawInput, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Vui lòng đăng nhập"));
        }

        if (rawInput == null || rawInput.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Dữ liệu đầu vào trống"));
        }

        String trimmed = rawInput.trim();
        String citizenId = "";
        String fullName = "";
        String birthday = "";
        String gender = "MALE";
        String permanentAddress = "";
        String phone = "";
        String issueDate = "";
        String placeOfIssue = "Cục Cảnh sát QLHC về TTXH";

        if (trimmed.contains("|")) {
            // Standard Vietnamese chip CCCD QR format:
            // 079203001234|123456789|Nguyễn Văn A|15082001|Nam|Số 12 Nguyễn Trãi, Quận 1, TP.HCM|01012022
            String[] parts = trimmed.split("\\|", -1);
            if (parts.length >= 1) citizenId = parts[0].trim();
            if (parts.length >= 3) fullName = parts[2].trim();
            if (parts.length >= 4) birthday = normalizeDateString(parts[3].trim());
            if (parts.length >= 5) {
                String g = parts[4].trim().toLowerCase();
                if (g.contains("nữ") || g.contains("nu") || g.contains("female")) {
                    gender = "FEMALE";
                } else if (g.contains("nam") || g.contains("male")) {
                    gender = "MALE";
                } else {
                    gender = "OTHER";
                }
            }
            if (parts.length >= 6) permanentAddress = parts[5].trim();
            if (parts.length >= 7) issueDate = normalizeDateString(parts[6].trim());
        } else {
            // Free-text parsing
            Matcher cccdMatcher = Pattern.compile("\\b(\\d{12}|\\d{9})\\b").matcher(trimmed);
            if (cccdMatcher.find()) {
                citizenId = cccdMatcher.group(1);
            }

            Matcher phoneMatcher = Pattern.compile("\\b(0[35789]\\d{8})\\b").matcher(trimmed);
            if (phoneMatcher.find()) {
                phone = phoneMatcher.group(1);
            }

            Matcher dateMatcher = Pattern.compile("\\b(\\d{2}[/-]\\d{2}[/-]\\d{4}|\\d{4}-\\d{2}-\\d{2}|\\d{8})\\b").matcher(trimmed);
            while (dateMatcher.find()) {
                String candidate = dateMatcher.group(1);
                if (!candidate.equals(citizenId) && !candidate.equals(phone)) {
                    birthday = normalizeDateString(candidate);
                    break;
                }
            }

            String lower = trimmed.toLowerCase();
            if (lower.contains("nữ") || lower.contains("giới tính: nữ")) {
                gender = "FEMALE";
            } else if (lower.contains("nam") || lower.contains("giới tính: nam")) {
                gender = "MALE";
            }

            String[] lines = trimmed.split("[\\r\\n,;]+");
            for (String line : lines) {
                String l = line.trim();
                if (l.isEmpty()) continue;
                String lLower = l.toLowerCase();
                if (lLower.startsWith("họ tên") || lLower.startsWith("họ và tên") || lLower.startsWith("tên")) {
                    int idx = l.indexOf(':');
                    if (idx >= 0 && idx + 1 < l.length()) fullName = l.substring(idx + 1).trim();
                } else if (lLower.startsWith("địa chỉ") || lLower.startsWith("thường trú") || lLower.startsWith("quê quán")) {
                    int idx = l.indexOf(':');
                    if (idx >= 0 && idx + 1 < l.length()) permanentAddress = l.substring(idx + 1).trim();
                } else if (fullName.isEmpty() && !l.matches(".*\\d{4,}.*") && l.split("\\s+").length >= 2 && l.split("\\s+").length <= 6) {
                    fullName = l;
                } else if (permanentAddress.isEmpty() && (lLower.contains("quận") || lLower.contains("huyện") || lLower.contains("phường") || lLower.contains("xã") || lLower.contains("tỉnh") || lLower.contains("tp") || lLower.contains("đường") || lLower.contains("số"))) {
                    permanentAddress = l;
                }
            }
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("citizenId", citizenId);
        res.put("fullName", fullName);
        res.put("birthday", birthday);
        res.put("gender", gender);
        res.put("permanentAddress", permanentAddress);
        res.put("phone", phone);
        res.put("issueDate", issueDate);
        res.put("placeOfIssue", placeOfIssue);
        return ResponseEntity.ok(res);
    }

    private String normalizeDateString(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String s = raw.trim();
        if (s.matches("\\d{8}")) {
            // ddMMyyyy -> yyyy-MM-dd
            return s.substring(4, 8) + "-" + s.substring(2, 4) + "-" + s.substring(0, 2);
        }
        if (s.matches("\\d{2}[/-]\\d{2}[/-]\\d{4}")) {
            String[] p = s.split("[/-]");
            return p[2] + "-" + p[1] + "-" + p[0];
        }
        if (s.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return s;
        }
        return "";
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
