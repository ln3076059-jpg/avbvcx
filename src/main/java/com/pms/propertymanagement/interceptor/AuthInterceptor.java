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
public class AuthInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        String uri = request.getRequestURI();

        if (uri.equals("/admin/users/impersonate/exit") && request.getSession().getAttribute("originalAdminUser") != null) {
            return true;
        }

        User user = (User) request.getSession().getAttribute("user");

        // Bảo vệ CSRF cơ bản bằng kiểm tra Origin / Referer cùng host cho các request POST thay đổi dữ liệu
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            String origin = request.getHeader("Origin");
            String host = request.getServerName();
            if (origin != null && !origin.isBlank() && host != null && !origin.contains(host) && !origin.contains("localhost") && !origin.contains("127.0.0.1")) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid Cross-Origin POST Request");
                return false;
            }
        }

        // Đồng bộ trạng thái khóa tài khoản (isActive) từ DB có bộ đệm 30 giây trong HttpSession để tránh truy vấn DB mọi request
        if (user != null && user.getId() != null && request.getSession().getAttribute("originalAdminUser") == null) {
            Long lastSync = (Long) request.getSession().getAttribute("lastUserSyncAt");
            long now = System.currentTimeMillis();
            if (lastSync == null || (now - lastSync) > 30_000L) {
                User freshUser = userRepository.findById(user.getId()).orElse(null);
                if (freshUser == null || !freshUser.isActive()) {
                    request.getSession().invalidate();
                    response.sendRedirect(uri.startsWith("/owner") || uri.startsWith("/admin") ? "/login/owner" : "/login");
                    return false;
                }
                user.setActive(freshUser.isActive());
                user.setEkycVerified(freshUser.getEkycVerified());
                request.getSession().setAttribute("lastUserSyncAt", now);
            }
        }

        if (user != null && !user.isActive() && request.getSession().getAttribute("originalAdminUser") == null) {
            request.getSession().invalidate();
            response.sendRedirect(uri.startsWith("/owner") || uri.startsWith("/admin") ? "/login/owner" : "/login");
            return false;
        }

        if (uri.startsWith("/ai/v1") || uri.startsWith("/face-service") || uri.equals("/upload") || uri.startsWith("/api/images/upload")) {
            if (user == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return false;
            }
            return true;
        }

        // Phân quyền theo 3 vai trò chính: ADMIN (Toàn quyền), OWNER (Chủ nhà trọ), USER (Khách hàng)
        boolean isAdmin = user != null && user.getRoles() != null &&
                user.getRoles().stream().anyMatch(r -> r.getName().equals("ADMIN"));

        // ADMIN có toàn quyền truy cập tất cả các phân hệ trong hệ thống
        if (isAdmin) {
            return true;
        }

        if (uri.startsWith("/owner/contact/")
                && !uri.startsWith("/owner/contact/toggle/")
                && !uri.startsWith("/owner/contact/approve-auto/")
                && !uri.startsWith("/owner/contact/reject/")
                && "POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (uri.startsWith("/owner")) {
            if (user == null) {
                response.sendRedirect("/login/owner");
                return false;
            }
            boolean isOwner = user.getRoles().stream().anyMatch(role -> role.getName().equals("OWNER"));
            if (!isOwner) {
                response.sendRedirect("/403");
                return false;
            }
        }

        if (uri.startsWith("/tenant")) {
            if (user == null) {
                response.sendRedirect("/login");
                return false;
            }
            boolean isCustomer = user.getRoles().stream().anyMatch(role -> role.getName().equals("USER") || role.getName().equals("TENANT"));
            if (!isCustomer) {
                response.sendRedirect("/403");
                return false;
            }
        }

        if (uri.startsWith("/admin") || uri.startsWith("/staff") || uri.startsWith("/moderator")) {
            if (user == null) {
                response.sendRedirect("/login/owner");
                return false;
            }
            // Chỉ ADMIN mới có quyền quản trị, kiểm duyệt và vận hành hệ thống
            response.sendRedirect("/403");
            return false;
        }

        return HandlerInterceptor.super.preHandle(request, response, handler);
    }
}
