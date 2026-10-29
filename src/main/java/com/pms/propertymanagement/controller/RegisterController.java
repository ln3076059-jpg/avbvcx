package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.dto.request.RegisterForm;
import com.pms.propertymanagement.entity.Role;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.repository.RoleRepository;
import com.pms.propertymanagement.repository.UserRepository;
import com.pms.propertymanagement.utils.PasswordUtil;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class RegisterController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @GetMapping("/register")
    public String showRegister(Model model, HttpSession session) {
        if (session.getAttribute("user") != null) return "redirect:/";
        if (!model.containsAttribute("registerForm")) {
            model.addAttribute("registerForm", new RegisterForm());
        }
        return "public/register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username,
                           @RequestParam String password,
                           @RequestParam(required = false) String fullName,
                           @RequestParam(required = false) String email,
                           @RequestParam(required = false) String phone,
                           HttpSession session,
                           RedirectAttributes ra) {
        RegisterForm form = new RegisterForm();
        form.setUsername(username);
        form.setPassword(password);
        form.setFullName(fullName);
        form.setEmail(email);
        form.setPhone(phone);

        boolean hasError = false;
        if (username == null || username.trim().isEmpty()) {
            ra.addFlashAttribute("errorUsername", "Vui lòng nhập username");
            hasError = true;
        }
        if (password == null || password.trim().isEmpty()) {
            ra.addFlashAttribute("errorPassword", "Vui lòng nhập password");
            hasError = true;
        }
        if (!hasError && userRepository.findByUsername(username.trim()).isPresent()) {
            ra.addFlashAttribute("errorUsername", "Username đã tồn tại");
            hasError = true;
        }
        if (!hasError && email != null && !email.trim().isEmpty() && userRepository.findByEmail(email.trim()).isPresent()) {
            ra.addFlashAttribute("errorEmail", "Email đã tồn tại");
            hasError = true;
        }
        if (!hasError && phone != null && !phone.trim().isEmpty() && userRepository.findByPhone(phone.trim()).isPresent()) {
            ra.addFlashAttribute("errorPhone", "Số điện thoại đã tồn tại");
            hasError = true;
        }

        ra.addFlashAttribute("registerForm", form);
        if (hasError) return "redirect:/register";

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new RuntimeException("Role USER chưa tồn tại"));

        String normalizedFullName = fullName != null && !fullName.trim().isEmpty() ? fullName.trim() : null;
        String normalizedEmail = email != null && !email.trim().isEmpty() ? email.trim() : null;
        String normalizedPhone = phone != null && !phone.trim().isEmpty() ? phone.trim() : null;

        User user = new User();
        user.setUsername(username.trim());
        user.setPassword(PasswordUtil.encode(password));
        user.setFullName(normalizedFullName);
        user.setEmail(normalizedEmail);
        user.setPhone(normalizedPhone);
        user.getRoles().add(userRole);

        userRepository.save(user);
        session.setAttribute("user", user);
        return "redirect:/ekyc?next=/tenant/rooms";
    }

    @GetMapping("/register/owner")
    public String showOwnerRegister(Model model, HttpSession session) {
        if (session.getAttribute("user") != null) return "redirect:/";
        if (!model.containsAttribute("registerForm")) {
            model.addAttribute("registerForm", new RegisterForm());
        }
        return "public/register-owner";
    }

    @PostMapping("/register/owner")
    public String registerOwner(@RequestParam String username,
                                @RequestParam String password,
                                @RequestParam(required = false) String fullName,
                                @RequestParam(required = false) String email,
                                @RequestParam(required = false) String phone,
                                HttpSession session,
                                RedirectAttributes ra) {
        RegisterForm form = new RegisterForm();
        form.setUsername(username);
        form.setPassword(password);
        form.setFullName(fullName);
        form.setEmail(email);
        form.setPhone(phone);

        boolean hasError = false;
        if (username == null || username.trim().isEmpty()) {
            ra.addFlashAttribute("errorUsername", "Vui lòng nhập username");
            hasError = true;
        }
        if (password == null || password.trim().isEmpty()) {
            ra.addFlashAttribute("errorPassword", "Vui lòng nhập password");
            hasError = true;
        }
        if (!hasError && userRepository.findByUsername(username.trim()).isPresent()) {
            ra.addFlashAttribute("errorUsername", "Username đã tồn tại");
            hasError = true;
        }
        if (!hasError && email != null && !email.trim().isEmpty() && userRepository.findByEmail(email.trim()).isPresent()) {
            ra.addFlashAttribute("errorEmail", "Email đã tồn tại");
            hasError = true;
        }
        if (!hasError && phone != null && !phone.trim().isEmpty() && userRepository.findByPhone(phone.trim()).isPresent()) {
            ra.addFlashAttribute("errorPhone", "Số điện thoại đã tồn tại");
            hasError = true;
        }

        ra.addFlashAttribute("registerForm", form);
        if (hasError) return "redirect:/register/owner";

        Role ownerRole = roleRepository.findByName("OWNER")
                .orElseThrow(() -> new RuntimeException("Role OWNER chưa tồn tại"));

        String normalizedFullName = fullName != null && !fullName.trim().isEmpty() ? fullName.trim() : null;
        String normalizedEmail = email != null && !email.trim().isEmpty() ? email.trim() : null;
        String normalizedPhone = phone != null && !phone.trim().isEmpty() ? phone.trim() : null;

        User user = new User();
        user.setUsername(username.trim());
        user.setPassword(PasswordUtil.encode(password));
        user.setFullName(normalizedFullName);
        user.setEmail(normalizedEmail);
        user.setPhone(normalizedPhone);
        user.getRoles().add(ownerRole);

        userRepository.save(user);
        session.setAttribute("user", user);
        return "redirect:/ekyc?next=/owner";
    }
}
