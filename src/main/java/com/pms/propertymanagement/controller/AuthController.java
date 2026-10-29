package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Authentication Controller - handles login and logout for the 3 system roles:
 * 1. Khách hàng (USER)
 * 2. Chủ nhà trọ (OWNER)
 * 3. Quản trị viên - Toàn quyền (ADMIN)
 */
@Controller
@Slf4j
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    // ========== KHÁCH HÀNG (USER) LOGIN ==========

    @GetMapping("/login")
    public String showUserLoginForm(HttpSession session) {
        if (session.getAttribute("user") != null) return "redirect:/";
        return "public/login-user";
    }

    @PostMapping("/login")
    public String loginUser(@RequestParam("username") String username,
                            @RequestParam("password") String password,
                            HttpServletRequest request,
                            Model model) {
        User user = userService.authenticate(username, password);
        if (user == null) {
            model.addAttribute("error", "Sai username hoặc password");
            return "public/login-user";
        }
        HttpSession session = rotateSessionOnLogin(request);
        session.setAttribute("user", user);
        String target = resolveRoleTarget(user);
        return "redirect:" + target;
    }

    // ========== CHỦ NHÀ TRỌ (OWNER) & QUẢN TRỊ VIÊN (ADMIN) LOGIN ==========

    @GetMapping("/login/owner")
    public String showLoginForm() {
        return "public/login";
    }

    @PostMapping("/login/owner")
    public String login(@RequestParam("username") String username,
                        @RequestParam("password") String password,
                        HttpServletRequest request,
                        Model model) {
        User user = userService.authenticate(username, password);
        if (user != null) {
            HttpSession session = rotateSessionOnLogin(request);
            session.setAttribute("user", user);
            String target = resolveRoleTarget(user);
            return "redirect:" + target;
        }

        model.addAttribute("error", "Sai username hoặc password");
        return "public/login";
    }

    // ========== LOGOUT ==========

    @GetMapping("/logout")
    public String logout(HttpSession session,
                         @RequestParam(value = "redirect", required = false) String redirectUrl) {

        if (session != null) {
            session.invalidate();
        }

        if (redirectUrl != null && !redirectUrl.trim().isEmpty()) {
            if (isValidRedirectUrl(redirectUrl)) {
                return "redirect:" + redirectUrl;
            } else {
                log.warn("Invalid redirect URL attempted: {}", redirectUrl);
            }
        }

        return "redirect:/login";
    }

    private HttpSession rotateSessionOnLogin(HttpServletRequest request) {
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            try {
                request.changeSessionId();
                return request.getSession();
            } catch (IllegalStateException ignored) {
                return request.getSession(true);
            }
        }
        return request.getSession(true);
    }

    private boolean isValidRedirectUrl(String url) {
        return url.startsWith("/") && !url.startsWith("//");
    }

    private String resolveRoleTarget(User user) {
        if (user.getRoles().stream().anyMatch(role -> role.getName().equals("ADMIN"))) {
            return "/admin/dashboard";
        }
        if (user.getRoles().stream().anyMatch(role -> role.getName().equals("OWNER"))) {
            return "/owner/dashboard";
        }
        return "/tenant/dashboard";
    }
}
