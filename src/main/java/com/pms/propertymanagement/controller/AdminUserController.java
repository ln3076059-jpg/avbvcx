package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.entity.Role;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.repository.RoleRepository;
import com.pms.propertymanagement.repository.UserRepository;
import com.pms.propertymanagement.utils.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@Slf4j
public class AdminUserController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    private void normalizeLegacyRoles(List<User> allUsers) {
        Optional<Role> userRoleOpt = roleRepository.findByName("USER");
        Optional<Role> adminRoleOpt = roleRepository.findByName("ADMIN");
        Optional<Role> ownerRoleOpt = roleRepository.findByName("OWNER");

        for (User u : allUsers) {
            if (u.getRoles() == null) continue;
            boolean changed = false;

            boolean wasAdmin = u.getRoles().stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()));
            boolean wasOwner = u.getRoles().stream().anyMatch(r -> "OWNER".equalsIgnoreCase(r.getName()));
            boolean hadLegacyRole = u.getRoles().stream().anyMatch(r ->
                    "STAFF".equalsIgnoreCase(r.getName()) || "MODERATOR".equalsIgnoreCase(r.getName()) || "TENANT".equalsIgnoreCase(r.getName()));

            if (hadLegacyRole) {
                u.getRoles().removeIf(r ->
                        "STAFF".equalsIgnoreCase(r.getName()) || "MODERATOR".equalsIgnoreCase(r.getName()) || "TENANT".equalsIgnoreCase(r.getName()));
                if (wasAdmin && adminRoleOpt.isPresent()) {
                    u.getRoles().add(adminRoleOpt.get());
                } else if (wasOwner && ownerRoleOpt.isPresent()) {
                    u.getRoles().add(ownerRoleOpt.get());
                } else if (userRoleOpt.isPresent()) {
                    u.getRoles().add(userRoleOpt.get());
                }
                changed = true;
            }
            if (changed) {
                try {
                    userRepository.save(u);
                } catch (Exception ignored) {}
            }
        }
    }

    @GetMapping
    public String listUsers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "role", required = false) String roleFilter,
            Model model) {

        List<User> users = userRepository.findAll();
        normalizeLegacyRoles(users);

        // Ẩn các tài khoản demo phụ cũ (staff1, moderator1) để danh sách chuẩn 3 vai trò
        users = users.stream()
                .filter(u -> u.getUsername() == null
                        || (!u.getUsername().equalsIgnoreCase("staff1") && !u.getUsername().equalsIgnoreCase("moderator1")))
                .collect(Collectors.toList());

        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim().toLowerCase();
            users = users.stream()
                    .filter(u -> (u.getUsername() != null && u.getUsername().toLowerCase().contains(kw))
                            || (u.getFullName() != null && u.getFullName().toLowerCase().contains(kw))
                            || (u.getEmail() != null && u.getEmail().toLowerCase().contains(kw))
                            || (u.getPhone() != null && u.getPhone().toLowerCase().contains(kw)))
                    .collect(Collectors.toList());
        }

        if (roleFilter != null && !roleFilter.trim().isEmpty()) {
            String rf = roleFilter.trim().toUpperCase();
            users = users.stream()
                    .filter(u -> {
                        if (u.getRoles() == null) return false;
                        if ("ADMIN".equals(rf)) {
                            return u.getRoles().stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()));
                        }
                        if ("OWNER".equals(rf)) {
                            return u.getRoles().stream().noneMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()))
                                    && u.getRoles().stream().anyMatch(r -> "OWNER".equalsIgnoreCase(r.getName()));
                        }
                        if ("USER".equals(rf) || "TENANT".equals(rf)) {
                            return u.getRoles().stream().noneMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()) || "OWNER".equalsIgnoreCase(r.getName()))
                                    && u.getRoles().stream().anyMatch(r -> "USER".equalsIgnoreCase(r.getName()) || "TENANT".equalsIgnoreCase(r.getName()));
                        }
                        return true;
                    })
                    .collect(Collectors.toList());
        }

        users.sort((a, b) -> Long.compare(b.getId(), a.getId()));

        model.addAttribute("users", users);
        model.addAttribute("keyword", keyword);
        model.addAttribute("roleFilter", roleFilter);
        model.addAttribute("activePage", "users");
        model.addAttribute("pageTitle", "Quản Lý Người Dùng");
        model.addAttribute("content", "admin/users/list");

        return "layout/admin-layout";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("activePage", "users");
        model.addAttribute("pageTitle", "Thêm Người Dùng Mới");
        model.addAttribute("content", "admin/users/create");
        return "layout/admin-layout";
    }

    @PostMapping("/create")
    public String createUser(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            @RequestParam(value = "fullName", required = false) String fullName,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "roleName", required = false) String roleName,
            RedirectAttributes redirectAttributes) {

        if (userRepository.findByUsername(username).isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tên đăng nhập '" + username + "' đã tồn tại trên hệ thống!");
            return "redirect:/admin/users/create";
        }

        String normalizedFullName = fullName != null && !fullName.trim().isEmpty() ? fullName.trim() : null;
        String normalizedEmail = email != null && !email.trim().isEmpty() ? email.trim() : null;
        String normalizedPhone = phone != null && !phone.trim().isEmpty() ? phone.trim() : null;

        if (normalizedEmail != null && userRepository.findByEmail(normalizedEmail).isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Email '" + normalizedEmail + "' đã tồn tại trên hệ thống!");
            return "redirect:/admin/users/create";
        }
        if (normalizedPhone != null && userRepository.findByPhone(normalizedPhone).isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Số điện thoại '" + normalizedPhone + "' đã tồn tại trên hệ thống!");
            return "redirect:/admin/users/create";
        }

        User user = new User();
        user.setUsername(username.trim());
        user.setPassword(PasswordUtil.encode(password.trim()));
        user.setFullName(normalizedFullName);
        user.setEmail(normalizedEmail);
        user.setPhone(normalizedPhone);
        user.setActive(true);
        user.setCreateAt(LocalDateTime.now());

        assignThreeRoleSet(user, roleName);

        userRepository.save(user);
        redirectAttributes.addFlashAttribute("successMessage", "Đã tạo tài khoản thành công cho người dùng: " + username);
        return "redirect:/admin/users";
    }

    private void assignThreeRoleSet(User user, String roleName) {
        user.getRoles().clear();
        String normalized = roleName != null ? roleName.trim().toUpperCase() : "USER";
        if ("ADMIN".equals(normalized)) {
            // Admin có toàn quyền (Full quyền)
            roleRepository.findByName("ADMIN").ifPresent(r -> user.getRoles().add(r));
            roleRepository.findByName("OWNER").ifPresent(r -> user.getRoles().add(r));
            roleRepository.findByName("USER").ifPresent(r -> user.getRoles().add(r));
        } else if ("OWNER".equals(normalized)) {
            roleRepository.findByName("OWNER").ifPresent(r -> user.getRoles().add(r));
        } else {
            roleRepository.findByName("USER").ifPresent(r -> user.getRoles().add(r));
        }
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy người dùng có mã ID: " + id);
            return "redirect:/admin/users";
        }

        User user = userOpt.get();
        model.addAttribute("user", user);
        model.addAttribute("activePage", "users");
        model.addAttribute("pageTitle", "Chỉnh Sửa Người Dùng - " + user.getUsername());
        model.addAttribute("content", "admin/users/edit");
        return "layout/admin-layout";
    }

    @PostMapping("/{id}/edit")
    public String updateUser(
            @PathVariable("id") Long id,
            @RequestParam(value = "fullName", required = false) String fullName,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "newPassword", required = false) String newPassword,
            @RequestParam(value = "roleName", required = false) String roleName,
            @RequestParam(value = "active", defaultValue = "false") boolean active,
            RedirectAttributes redirectAttributes) {

        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy người dùng có mã ID: " + id);
            return "redirect:/admin/users";
        }

        User user = userOpt.get();
        String normalizedFullName = fullName != null && !fullName.trim().isEmpty() ? fullName.trim() : null;
        String normalizedEmail = email != null && !email.trim().isEmpty() ? email.trim() : null;
        String normalizedPhone = phone != null && !phone.trim().isEmpty() ? phone.trim() : null;

        if (normalizedEmail != null && userRepository.findByEmail(normalizedEmail).filter(u -> !u.getId().equals(id)).isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Email '" + normalizedEmail + "' đã được sử dụng bởi tài khoản khác!");
            return "redirect:/admin/users/" + id + "/edit";
        }
        if (normalizedPhone != null && userRepository.findByPhone(normalizedPhone).filter(u -> !u.getId().equals(id)).isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Số điện thoại '" + normalizedPhone + "' đã được sử dụng bởi tài khoản khác!");
            return "redirect:/admin/users/" + id + "/edit";
        }

        user.setFullName(normalizedFullName);
        user.setEmail(normalizedEmail);
        user.setPhone(normalizedPhone);
        user.setActive(active);

        if (newPassword != null && !newPassword.trim().isEmpty()) {
            user.setPassword(PasswordUtil.encode(newPassword.trim()));
        }

        if (roleName != null && !roleName.trim().isEmpty()) {
            assignThreeRoleSet(user, roleName);
        }

        userRepository.save(user);
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật tài khoản " + user.getUsername() + " thành công!");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/toggle-status")
    public String toggleUserStatus(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setActive(!user.isActive());
            userRepository.save(user);
            String statusText = user.isActive() ? "Mở khóa tài khoản" : "Khóa tài khoản";
            redirectAttributes.addFlashAttribute("successMessage", statusText + " thành công cho " + user.getUsername());
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Người dùng không tồn tại!");
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    public String deleteUser(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            try {
                userRepository.deleteById(id);
                redirectAttributes.addFlashAttribute("successMessage", "Đã xóa vĩnh viễn tài khoản: " + user.getUsername());
            } catch (Exception ex) {
                log.warn("Không thể xóa user do ràng buộc dữ liệu: {}", ex.getMessage());
                user.setActive(false);
                userRepository.save(user);
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Tài khoản đang có dữ liệu phòng trọ/hóa đơn liên kết, hệ thống đã tự động CHUYỂN SANG KHÓA TÀI KHOẢN để đảm bảo toàn vẹn dữ liệu!");
            }
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Người dùng không tồn tại!");
        }
        return "redirect:/admin/users";
    }

    // === ADMIN FULL PRIVILEGE: IMPERSONATION (ĐĂNG NHẬP DƯỚI QUYỀN USER) ===
    @GetMapping("/{id}/impersonate")
    public String impersonateUser(
            @PathVariable("id") Long id,
            jakarta.servlet.http.HttpSession session,
            RedirectAttributes redirectAttributes) {

        Optional<User> targetUserOpt = userRepository.findById(id);
        if (targetUserOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy người dùng có ID: " + id);
            return "redirect:/admin/users";
        }

        User targetUser = targetUserOpt.get();
        User currentAdmin = (User) session.getAttribute("user");

        if (session.getAttribute("originalAdminUser") == null) {
            session.setAttribute("originalAdminUser", currentAdmin);
        }

        session.setAttribute("user", targetUser);
        log.info("ADMIN [{}] đã đăng nhập giả lập dưới quyền người dùng [{}]",
                currentAdmin != null ? currentAdmin.getUsername() : "admin", targetUser.getUsername());

        if (targetUser.getRoles() != null) {
            if (targetUser.getRoles().stream().anyMatch(r -> r.getName().equals("ADMIN"))) {
                return "redirect:/admin/dashboard";
            }
            if (targetUser.getRoles().stream().anyMatch(r -> r.getName().equals("OWNER"))) {
                return "redirect:/owner/dashboard";
            }
            if (targetUser.getRoles().stream().anyMatch(r -> r.getName().equals("USER") || r.getName().equals("TENANT"))) {
                return "redirect:/tenant/dashboard";
            }
        }
        return "redirect:/";
    }

    // === ADMIN FULL PRIVILEGE: EXIT IMPERSONATION (THOÁT QUYỀN GIẢ LẬP) ===
    @GetMapping("/impersonate/exit")
    public String exitImpersonation(
            jakarta.servlet.http.HttpSession session,
            RedirectAttributes redirectAttributes) {

        User originalAdmin = (User) session.getAttribute("originalAdminUser");
        if (originalAdmin != null) {
            session.setAttribute("user", originalAdmin);
            session.removeAttribute("originalAdminUser");
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã quay trở lại phiên làm việc Quản trị viên cấp cao (" + originalAdmin.getUsername() + ")!");
        }
        return "redirect:/admin/users";
    }

    // === ADMIN FULL PRIVILEGE: QUICK PASSWORD RESET (ĐẶT LẠI MẬT KHẨU) ===
    @PostMapping("/{id}/reset-password")
    public String resetPassword(
            @PathVariable("id") Long id,
            @RequestParam(value = "defaultPass", defaultValue = "123456") String defaultPass,
            RedirectAttributes redirectAttributes) {

        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setPassword(PasswordUtil.encode(defaultPass));
            userRepository.save(user);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã đặt lại mật khẩu cho tài khoản '" + user.getUsername() + "' thành: " + defaultPass);
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Người dùng không tồn tại!");
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/export")
    public void exportUsersCsv(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        List<User> users = userRepository.findAll();
        users.sort((a, b) -> Long.compare(b.getId(), a.getId()));

        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"danh-sach-nguoi-dung.csv\"");

        try (java.io.PrintWriter writer = response.getWriter()) {
            writer.write('\uFEFF');
            writer.println("ID,Tên Đăng Nhập,Họ và Tên,Email,Số Điện Thoại,Vai Trò,Trạng Thái,Xác Thực eKYC");
            for (User u : users) {
                String roleLabel = "Khách hàng (USER)";
                if (u.getRoles() != null) {
                    if (u.getRoles().stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()))) {
                        roleLabel = "Quản trị viên (ADMIN - Toàn quyền)";
                    } else if (u.getRoles().stream().anyMatch(r -> "OWNER".equalsIgnoreCase(r.getName()))) {
                        roleLabel = "Chủ nhà trọ (OWNER)";
                    }
                }
                writer.printf("%d,%s,%s,%s,%s,%s,%s,%s%n",
                        u.getId(),
                        escapeCsv(u.getUsername()),
                        escapeCsv(u.getFullName()),
                        escapeCsv(u.getEmail()),
                        escapeCsv(u.getPhone()),
                        escapeCsv(roleLabel),
                        u.isActive() ? "Hoạt động" : "Đã khóa",
                        u.isEkycVerified() ? "Đã xác thực" : "Chưa xác thực"
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
