package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.entity.Post;
import com.pms.propertymanagement.enums.PostStatus;
import com.pms.propertymanagement.entity.Property;
import com.pms.propertymanagement.repository.PostRepository;
import com.pms.propertymanagement.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/posts")
@RequiredArgsConstructor
@Slf4j
public class AdminPostController {

    private final PostRepository postRepository;
    private final PropertyRepository propertyRepository;

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("properties", propertyRepository.findAll());
        model.addAttribute("statuses", PostStatus.values());
        model.addAttribute("activePage", "posts");
        model.addAttribute("pageTitle", "Đăng Tin Mới (Quản Trị Viên)");
        model.addAttribute("content", "admin/posts/create");
        return "layout/admin-layout";
    }

    @PostMapping("/create")
    public String createPost(
            @RequestParam("propertyId") Long propertyId,
            @RequestParam("title") String title,
            @RequestParam("slug") String slug,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "status", defaultValue = "ACTIVE") PostStatus status,
            RedirectAttributes redirectAttributes) {

        Optional<Property> propertyOpt = propertyRepository.findById(propertyId);
        if (propertyOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy cơ sở nhà trọ!");
            return "redirect:/admin/posts/create";
        }

        String finalSlug = slug.trim().toLowerCase().replaceAll("[^a-z0-9-]", "-");
        if (postRepository.findBySlug(finalSlug).isPresent()) {
            finalSlug += "-" + System.currentTimeMillis();
        }

        Post post = new Post();
        post.setProperty(propertyOpt.get());
        post.setTitle(title.trim());
        post.setSlug(finalSlug);
        post.setDescription(description != null ? description.trim() : null);
        post.setStatus(status);
        post.setCreatedAt(LocalDateTime.now());
        post.setPostExpiredAt(LocalDateTime.now().plusDays(30));
        post.setViewCount(0);

        postRepository.save(post);
        redirectAttributes.addFlashAttribute("successMessage", "Đã tạo tin đăng mới thành công: " + post.getTitle());
        return "redirect:/admin/posts";
    }

    @GetMapping
    public String listPosts(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String statusFilter,
            Model model) {

        List<Post> posts = postRepository.findAll();

        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim().toLowerCase();
            posts = posts.stream()
                    .filter(p -> (p.getTitle() != null && p.getTitle().toLowerCase().contains(kw))
                            || (p.getSlug() != null && p.getSlug().toLowerCase().contains(kw))
                            || (p.getProperty() != null && p.getProperty().getName() != null && p.getProperty().getName().toLowerCase().contains(kw)))
                    .collect(Collectors.toList());
        }

        if (statusFilter != null && !statusFilter.trim().isEmpty()) {
            try {
                PostStatus filterEnum = PostStatus.valueOf(statusFilter.trim().toUpperCase());
                posts = posts.stream()
                        .filter(p -> p.getStatus() == filterEnum)
                        .collect(Collectors.toList());
            } catch (IllegalArgumentException ignored) {}
        }

        // Sort descending by ID
        posts.sort((a, b) -> Long.compare(b.getId(), a.getId()));

        model.addAttribute("posts", posts);
        model.addAttribute("keyword", keyword);
        model.addAttribute("statusFilter", statusFilter);
        model.addAttribute("statuses", PostStatus.values());
        model.addAttribute("activePage", "posts");
        model.addAttribute("pageTitle", "Quản Lý Tin Đăng");
        model.addAttribute("content", "admin/posts/list");

        return "layout/admin-layout";
    }

    @GetMapping("/{id}")
    public String viewPostDetail(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Post> postOpt = postRepository.findById(id);
        if (postOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy bài đăng mã ID: " + id);
            return "redirect:/admin/posts";
        }

        Post post = postOpt.get();
        model.addAttribute("post", post);
        model.addAttribute("activePage", "posts");
        model.addAttribute("pageTitle", "Chi Tiết Bài Đăng: " + post.getTitle());
        model.addAttribute("content", "admin/posts/detail");
        return "layout/admin-layout";
    }

    @PostMapping("/{id}/approve")
    public String approvePost(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        Optional<Post> postOpt = postRepository.findById(id);
        if (postOpt.isPresent()) {
            Post post = postOpt.get();
            post.setStatus(PostStatus.ACTIVE);
            if (post.getPostExpiredAt() == null || post.getPostExpiredAt().isBefore(LocalDateTime.now())) {
                post.setPostExpiredAt(LocalDateTime.now().plusDays(30)); // Gia hạn mặc định 30 ngày
            }
            postRepository.save(post);
            redirectAttributes.addFlashAttribute("successMessage", "Đã phê duyệt hiển thị bài đăng: " + post.getTitle());
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Bài đăng không tồn tại!");
        }
        return "redirect:/admin/posts";
    }

    @PostMapping("/{id}/reject")
    public String rejectPost(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        Optional<Post> postOpt = postRepository.findById(id);
        if (postOpt.isPresent()) {
            Post post = postOpt.get();
            post.setStatus(PostStatus.REJECTED);
            postRepository.save(post);
            redirectAttributes.addFlashAttribute("successMessage", "Đã hạ bài đăng: " + post.getTitle());
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Bài đăng không tồn tại!");
        }
        return "redirect:/admin/posts";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Post> postOpt = postRepository.findById(id);
        if (postOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy bài đăng mã ID: " + id);
            return "redirect:/admin/posts";
        }

        Post post = postOpt.get();
        model.addAttribute("post", post);
        model.addAttribute("statuses", PostStatus.values());
        model.addAttribute("activePage", "posts");
        model.addAttribute("pageTitle", "Chỉnh Sửa Bài Đăng: " + post.getTitle());
        model.addAttribute("content", "admin/posts/edit");
        return "layout/admin-layout";
    }

    @PostMapping("/{id}/edit")
    public String updatePost(
            @PathVariable("id") Long id,
            @RequestParam("title") String title,
            @RequestParam("slug") String slug,
            @RequestParam("status") PostStatus status,
            @RequestParam(value = "description", required = false) String description,
            RedirectAttributes redirectAttributes) {

        Optional<Post> postOpt = postRepository.findById(id);
        if (postOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy bài đăng mã ID: " + id);
            return "redirect:/admin/posts";
        }

        Post post = postOpt.get();
        post.setTitle(title.trim());
        post.setSlug(slug.trim());
        post.setStatus(status);
        if (description != null) {
            post.setDescription(description.trim());
        }

        postRepository.save(post);
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật bài đăng '" + post.getTitle() + "' thành công!");
        return "redirect:/admin/posts";
    }

    @PostMapping("/{id}/delete")
    public String deletePost(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        Optional<Post> postOpt = postRepository.findById(id);
        if (postOpt.isPresent()) {
            Post post = postOpt.get();
            try {
                postRepository.deleteById(id);
                redirectAttributes.addFlashAttribute("successMessage", "Đã xóa vĩnh viễn tin đăng: " + post.getTitle());
            } catch (Exception ex) {
                log.error("Không thể xóa post ID: {}", id, ex);
                post.setStatus(PostStatus.HIDDEN);
                postRepository.save(post);
                redirectAttributes.addFlashAttribute("errorMessage", "Không thể xóa trực tiếp bài đăng do có dữ liệu liên quan. Hệ thống đã tự động CHUYỂN TRẠNG THÁI ẨN.");
            }
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Bài đăng không tồn tại!");
        }
        return "redirect:/admin/posts";
    }
}
