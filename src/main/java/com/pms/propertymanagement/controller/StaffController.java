package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.dto.response.PostOwnerResponse;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.service.PostService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Controller for staff post moderation
 * Staff can approve or reject posts submitted by owners
 */
@Controller
@RequestMapping("/staff")
@RequiredArgsConstructor
@Slf4j
public class StaffController {

    private final PostService postService;

    /**
     * Staff post moderation dashboard - list all PENDING_APPROVAL posts
     * URL: GET /staff/posts
     */
    @GetMapping("/posts")
    public String listPendingPosts(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login/owner";
        }

        List<PostOwnerResponse> pendingPosts = postService.getPendingApprovalPosts();
        model.addAttribute("pendingPosts", pendingPosts);
        model.addAttribute("activeMenu", "staff-posts");
        model.addAttribute("content", "staff/post-approval");
        return "layout/staff-layout";
    }

    /**
     * Approve a pending post (start 7-day free trial)
     * URL: POST /staff/posts/{id}/approve
     */
    @PostMapping("/posts/{id}/approve")
    public String approvePost(@PathVariable Long id,
                              HttpSession session,
                              RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login/owner";
        }

        try {
            postService.approvePost(id);
            ra.addFlashAttribute("successMessage",
                    "Đã duyệt bài đăng #" + id + ". Bài đăng đang hiển thị với 7 ngày dùng thử.");
            log.info("Staff {} approved post {}", user.getUsername(), id);
        } catch (Exception e) {
            log.error("Error approving post {}: {}", id, e.getMessage());
            ra.addFlashAttribute("errorMessage", "Không thể duyệt bài: " + e.getMessage());
        }

        return "redirect:/staff/posts";
    }

    /**
     * Reject a pending post with reason
     * URL: POST /staff/posts/{id}/reject
     */
    @PostMapping("/posts/{id}/reject")
    public String rejectPost(@PathVariable Long id,
                             @RequestParam String reason,
                             HttpSession session,
                             RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login/owner";
        }

        try {
            postService.rejectPost(id, reason);
            ra.addFlashAttribute("successMessage",
                    "Đã từ chối bài đăng #" + id + ". Chủ nhà sẽ nhận được thông báo.");
            log.info("Staff {} rejected post {} with reason: {}", user.getUsername(), id, reason);
        } catch (Exception e) {
            log.error("Error rejecting post {}: {}", id, e.getMessage());
            ra.addFlashAttribute("errorMessage", "Không thể từ chối bài: " + e.getMessage());
        }

        return "redirect:/staff/posts";
    }
}
