package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.entity.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UserController {

    @GetMapping("/user/profile")
    public String redirectProfileByRole(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        boolean isAdmin = user.getRoles() != null && user.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getName()));
        if (isAdmin) {
            return "redirect:/admin/dashboard";
        }
        boolean isOwner = user.getRoles() != null && user.getRoles().stream().anyMatch(r -> "OWNER".equals(r.getName()));
        if (isOwner) {
            return "redirect:/owner/dashboard";
        }
        return "redirect:/tenant/dashboard";
    }
}
