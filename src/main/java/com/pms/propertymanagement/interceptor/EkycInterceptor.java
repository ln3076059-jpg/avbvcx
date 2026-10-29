package com.pms.propertymanagement.interceptor;

import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class EkycInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();

        if (uri.startsWith("/ekyc") || uri.startsWith("/login") || uri.startsWith("/register") || uri.startsWith("/logout")) {
            return true;
        }

        if (uri.startsWith("/owner/contact/") && !uri.startsWith("/owner/contact/toggle/") && "POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (!(uri.startsWith("/owner") || uri.startsWith("/tenant"))) {
            return true;
        }

        User sessionUser = (User) request.getSession().getAttribute("user");
        if (sessionUser == null) {
            response.sendRedirect(uri.startsWith("/owner") ? "/login/owner" : "/login");
            return false;
        }

        if (sessionUser.getRoles() == null || sessionUser.getRoles().isEmpty()) {
            User user = userRepository.findById(sessionUser.getId()).orElse(sessionUser);
            request.getSession().setAttribute("user", user);
            sessionUser = user;
        }

        // Gắn trạng thái cảnh báo eKYC vào request/session để hiển thị nhắc nhở nghiệp vụ cho Chủ trọ chưa xác thực CCCD
        request.setAttribute("ekycVerifiedStatus", sessionUser.isEkycVerified());
        if (!sessionUser.isEkycVerified() && uri.startsWith("/owner")) {
            request.setAttribute("ekycPendingWarning", "Tài khoản chưa xác thực danh tính CCCD (eKYC). Vui lòng hoàn tất tại mục Xác thực eKYC để nâng độ tin cậy pháp lý cho hợp đồng và tin đăng.");
        }

        return true;
    }
}
