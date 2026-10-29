package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.entity.Contact;
import com.pms.propertymanagement.entity.Post;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.enums.PostStatus;
import com.pms.propertymanagement.repository.ContactRepository;
import com.pms.propertymanagement.service.PostService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/moderator")
@RequiredArgsConstructor
@Slf4j
public class ModeratorController {

    private final PostService postService;
    private final ContactRepository contactRepository;

    // ========== COMMON: Inject badge counts into every moderator page ==========

    @ModelAttribute
    public void addBadgeCounts(Model model) {
        model.addAttribute("pendingCount",
                postService.getPostsByStatus(PostStatus.PENDING_APPROVAL).size());
        model.addAttribute("reReviewCount",
                postService.getPostsByStatus(PostStatus.PENDING_REVISION).size());
    }

    // ========== DASHBOARD ==========

    @GetMapping
    public String dashboard(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        List<Post> pendingPosts = postService.getPostsByStatus(PostStatus.PENDING_APPROVAL);
        long totalPending = pendingPosts.size();
        long totalReReview = postService.getPostsByStatus(PostStatus.PENDING_REVISION).size();
        long totalActive = postService.getPostsByStatus(PostStatus.ACTIVE).size();
        long totalRejected = postService.getPostsByStatus(PostStatus.REJECTED).size();
        List<Post> recentPendingPosts = pendingPosts.stream().limit(5).toList();

        model.addAttribute("totalPending", totalPending);
        model.addAttribute("totalReReview", totalReReview);
        model.addAttribute("totalActive", totalActive);
        model.addAttribute("totalRejected", totalRejected);
        model.addAttribute("recentPendingPosts", recentPendingPosts);
        model.addAttribute("activeMenu", "");
        model.addAttribute("activeSection", "manage");
        model.addAttribute("content", "moderator/management/dashboard");
        return "layout/moderator-layout";
    }

    // ========== POST LIST ==========

    @GetMapping("/posts/pending")
    public String pendingPosts(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        List<Post> posts = postService.getPostsByStatus(PostStatus.PENDING_APPROVAL);
        model.addAttribute("posts", posts);
        model.addAttribute("activeMenu", "posts");
        model.addAttribute("activeTab", "pending");
        model.addAttribute("activeSection", "manage");
        model.addAttribute("content", "moderator/management/posts/list");
        return "layout/moderator-layout";
    }

    @GetMapping("/posts/re-review")
    public String reReviewPosts(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        List<Post> posts = postService.getPostsByStatus(PostStatus.PENDING_REVISION);
        model.addAttribute("posts", posts);
        model.addAttribute("activeMenu", "posts");
        model.addAttribute("activeTab", "re-review");
        model.addAttribute("activeSection", "manage");
        model.addAttribute("content", "moderator/management/posts/list");
        return "layout/moderator-layout";
    }

    // ========== CHAT / LIVE SUPPORT CENTER ==========

    @GetMapping("/chat")
    public String chat(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        List<Contact> recentContacts = contactRepository.findAll().stream()
                .sorted((a, b) -> Long.compare(b.getId() != null ? b.getId() : 0L, a.getId() != null ? a.getId() : 0L))
                .limit(20)
                .toList();
        long totalPending = postService.getPostsByStatus(PostStatus.PENDING_APPROVAL).size();
        long totalReReview = postService.getPostsByStatus(PostStatus.PENDING_REVISION).size();

        model.addAttribute("recentContacts", recentContacts);
        model.addAttribute("totalPending", totalPending);
        model.addAttribute("totalReReview", totalReReview);
        model.addAttribute("activeMenu", "chat");
        model.addAttribute("activeSection", "chat");
        model.addAttribute("content", "moderator/chat/index");
        return "layout/moderator-layout";
    }

    // ========== POST DETAIL ==========

    @GetMapping("/posts/{id}")
    public String postDetail(@PathVariable Long id,
                             HttpSession session,
                             Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        Post post = postService.findPostById(id);

        int aiTrustScore = 60;
        List<String> aiInsights = new ArrayList<>();

        int imgCount = (post.getProperty() != null && post.getProperty().getImages() != null)
                ? post.getProperty().getImages().size() : 0;
        if (imgCount >= 4) {
            aiTrustScore += 15;
            aiInsights.add("Hình ảnh cơ sở: Đạt chuẩn (" + imgCount + " ảnh thực tế)");
        } else {
            aiInsights.add("Hình ảnh cơ sở: Khuyến nghị bổ sung thêm ảnh (" + imgCount + " ảnh)");
        }

        int descLen = post.getDescription() != null ? post.getDescription().trim().length() : 0;
        if (descLen >= 100) {
            aiTrustScore += 15;
            aiInsights.add("Mô tả chi tiết: Đầy đủ thông tin (" + descLen + " ký tự)");
        } else {
            aiInsights.add("Mô tả chi tiết: Nội dung ngắn (" + descLen + " ký tự)");
        }

        boolean ekyc = post.getProperty() != null && post.getProperty().getOwner() != null
                && post.getProperty().getOwner().isEkycVerified();
        if (ekyc) {
            aiTrustScore += 10;
            aiInsights.add("Chủ trọ: Đã xác thực eKYC chính chủ");
        } else {
            aiInsights.add("Chủ trọ: Chưa xác thực eKYC");
        }

        model.addAttribute("post", post);
        model.addAttribute("aiTrustScore", aiTrustScore);
        model.addAttribute("aiInsights", aiInsights);
        model.addAttribute("activeMenu", "posts");
        String tab = switch (post.getStatus()) {
            case PENDING_REVISION -> "re-review";
            default -> "pending";
        };
        model.addAttribute("activeTab", tab);
        model.addAttribute("activeSection", "manage");
        model.addAttribute("content", "moderator/management/posts/detail");
        return "layout/moderator-layout";
    }

    // ========== BULK APPROVE ==========

    @PostMapping("/posts/bulk-approve")
    public String bulkApprove(@RequestParam(name = "postIds", required = false) List<Long> postIds,
                              HttpSession session,
                              RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        int approved = 0;
        if (postIds != null && !postIds.isEmpty()) {
            for (Long postId : postIds) {
                if (postId == null) continue;
                try {
                    postService.approvePostByModerator(postId);
                    approved++;
                } catch (Exception e) {
                    log.warn("Could not bulk-approve post {}: {}", postId, e.getMessage());
                }
            }
        }

        ra.addFlashAttribute("successMessage", "Đã duyệt hàng loạt thành công " + approved + " bài đăng!");
        return "redirect:/moderator/posts/pending";
    }

    // ========== APPROVE ==========

    @PostMapping("/posts/{id}/approve")
    public String approvePost(@PathVariable Long id,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        try {
            postService.approvePostByModerator(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã duyệt bài đăng thành công.");
            log.info("Moderator {} approved post {}", user.getUsername(), id);
        } catch (Exception e) {
            log.error("Error approving post {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể duyệt bài: " + e.getMessage());
        }

        return "redirect:/moderator/posts/" + id;
    }

    // ========== REJECT ==========

    @PostMapping("/posts/{id}/reject")
    public String rejectPost(@PathVariable Long id,
                             @RequestParam(name = "reason", required = false) String reason,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        if (!StringUtils.hasText(reason)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng nhập lý do từ chối.");
            return "redirect:/moderator/posts/" + id;
        }

        try {
            postService.rejectPostByModerator(id, reason.trim());
            redirectAttributes.addFlashAttribute("successMessage", "Đã yêu cầu chỉnh sửa bài đăng.");
            log.info("Moderator {} rejected post {}", user.getUsername(), id);
        } catch (Exception e) {
            log.error("Error rejecting post {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể từ chối bài: " + e.getMessage());
        }

        return "redirect:/moderator/posts/" + id;
    }
}
