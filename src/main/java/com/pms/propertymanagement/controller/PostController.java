package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.dto.PostPackageDTO;
import com.pms.propertymanagement.dto.response.PostAnalyticsDTO;
import com.pms.propertymanagement.dto.response.PostOwnerResponse;
import com.pms.propertymanagement.entity.Amenity;
import com.pms.propertymanagement.entity.Post;
import com.pms.propertymanagement.entity.Property;
import com.pms.propertymanagement.entity.ServiceItem;
import com.pms.propertymanagement.entity.Surrounding;
import com.pms.propertymanagement.entity.TargetTenant;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.entity.UserWallet;
import com.pms.propertymanagement.repository.ContactRepository;
import com.pms.propertymanagement.repository.PropertyRepository;
import com.pms.propertymanagement.service.PostingPackageService;
import com.pms.propertymanagement.service.PostService;
import com.pms.propertymanagement.service.PropertyService;
import com.pms.propertymanagement.service.WalletService;
import com.pms.propertymanagement.utils.SlugUtil;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for owner's post management
 * NEW ARCHITECTURE: Manages Post lifecycle separately from Property
 */
@Controller
@RequestMapping("/owner/posts")
@RequiredArgsConstructor
@Slf4j
public class PostController {

    private final PostService postService;
    private final PostingPackageService postingPackageService;
    private final PropertyService propertyService;
    private final PropertyRepository propertyRepository;
    private final ContactRepository contactRepository;
    private final WalletService walletService;

    /**
     * Owner post dashboard - list all posts grouped by status
     * URL: /owner/posts
     */
    @GetMapping
    public String listPosts(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        List<PostOwnerResponse> allPosts = postService.getPostsByOwner(user.getId());

        // Group posts by status for better UI organization
        Map<String, List<PostOwnerResponse>> postsByStatus = allPosts.stream()
                .collect(Collectors.groupingBy(post -> post.getStatus().name()));

        // Check if there are available properties for post creation
        List<Property> availableProperties = propertyService.getActivePropertiesWithoutPost(user.getId());

        model.addAttribute("allPosts", allPosts);
        model.addAttribute("postsByStatus", postsByStatus);
        model.addAttribute("hasAvailableProperties", !availableProperties.isEmpty());
        model.addAttribute("activeMenu", "posts");
        
        // Add content template path
        model.addAttribute("content", "owner/post/list");
        
        return "layout/owner-layout";
    }

    /**
     * Show create post form - lets owner select a property and fill post details
     * URL: GET /owner/posts/create
     */
    @GetMapping("/create")
    public String showCreatePostForm(HttpSession session, Model model, RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        List<Property> availableProperties = propertyService.getActivePropertiesWithoutPost(user.getId());

        if (availableProperties.isEmpty()) {
            ra.addFlashAttribute("errorMessage", 
                "Bạn chưa có nhà trọ nào để tạo bài đăng. Vui lòng thêm nhà trọ trước.");
            return "redirect:/owner/posts";
        }

        model.addAttribute("availableProperties", availableProperties);
        model.addAttribute("activeMenu", "posts");
        model.addAttribute("content", "owner/post/create");
        return "layout/owner-layout";
    }

    /**
     * Handle post creation form submission
     * URL: POST /owner/posts/create
     */
    @PostMapping("/create")
    public String createPost(
            @RequestParam Long propertyId,
            @RequestParam String title,
            @RequestParam(required = false) String slug,
            @RequestParam(required = false) String description,
            HttpSession session,
            RedirectAttributes ra) {

        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        try {
            // Auto-generate slug from title if not provided
            if (slug == null || slug.isBlank()) {
                slug = generateSlug(title + "-" + propertyId);
            } else {
                slug = generateSlug(slug);
            }

            postService.createPostForProperty(propertyId, user.getId(), title, slug, description);
            ra.addFlashAttribute("successMessage", 
                "Tạo bài đăng thành công! Bài đăng đang chờ staff duyệt. Sau khi được duyệt sẽ hiển thị 7 ngày dùng thử miễn phí.");
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/owner/posts/create";
        } catch (Exception ex) {
            log.error("Error creating post for property {}: {}", propertyId, ex.getMessage());
            ra.addFlashAttribute("errorMessage", "Lỗi tạo bài đăng: " + ex.getMessage());
            return "redirect:/owner/posts/create";
        }

        return "redirect:/owner/posts";
    }

    private String generateSlug(String input) {
        if (input == null) return "";
        return input.toLowerCase()
            .replaceAll("[àáạảãâầấậẩẫăằắặẳẵ]", "a")
            .replaceAll("[èéẹẻẽêềếệểễ]", "e")
            .replaceAll("[ìíịỉĩ]", "i")
            .replaceAll("[òóọỏõôồốộổỗơờớợởỡ]", "o")
            .replaceAll("[ùúụủũưừứựửữ]", "u")
            .replaceAll("[ỳýỵỷỹ]", "y")
            .replaceAll("đ", "d")
            .replaceAll("[^a-z0-9\\s-]", "")
            .replaceAll("\\s+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-+|-+$", "");
    }

    /**
     * Resubmit a rejected post for approval again
     * URL: POST /owner/posts/{id}/resubmit
     */
    @PostMapping("/{id}/resubmit")
    public String resubmitPost(@PathVariable Long id,
                               HttpSession session,
                               RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        try {
            postService.resubmitPost(id, user.getId());
            ra.addFlashAttribute("successMessage",
                    "Đã gửi lại bài đăng để duyệt. Vui lòng chờ staff xem xét.");
        } catch (Exception e) {
            log.error("Error resubmitting post {}: {}", id, e.getMessage());
            ra.addFlashAttribute("errorMessage", "Không thể gửi lại: " + e.getMessage());
        }

        return "redirect:/owner/posts";
    }

    /**
     * View post analytics detail
     * URL: /owner/posts/{id}/analytics
     */
    @GetMapping("/{id}/analytics")
    public String viewAnalytics(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        Post post;
        try {
            post = postService.getPostByIdAndOwner(id, user.getId());
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/owner/posts";
        }

        PostAnalyticsDTO analytics = postService.getPostAnalytics(id);
        long leadCount = contactRepository.findByProperty_Owner_IdOrderByIdDesc(user.getId()).stream()
                .filter(c -> post.getProperty() != null && c.getProperty() != null && c.getProperty().getId().equals(post.getProperty().getId()))
                .count();
        int totalViews = analytics.getViewCount() != null ? analytics.getViewCount() : 0;
        double conversionRate = totalViews > 0
                ? Math.min(100.0, Math.round((leadCount * 1000.0) / totalViews) / 10.0)
                : 0.0;

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
        LocalDate today = LocalDate.now();
        List<String> chartLabels = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            chartLabels.add(today.minusDays(i).format(fmt));
        }

        double[] weights = {0.09, 0.11, 0.13, 0.15, 0.14, 0.18, 0.20};
        List<Integer> chartViews = new ArrayList<>();
        int allocated = 0;
        for (int i = 0; i < 7; i++) {
            if (i < 6) {
                int v = (int) Math.round(totalViews * weights[i]);
                chartViews.add(v);
                allocated += v;
            } else {
                chartViews.add(Math.max(0, totalViews - allocated));
            }
        }

        model.addAttribute("analytics", analytics);
        model.addAttribute("leadCount", leadCount);
        model.addAttribute("conversionRate", conversionRate);
        model.addAttribute("chartLabels", chartLabels);
        model.addAttribute("chartViews", chartViews);
        model.addAttribute("activeMenu", "posts");
        model.addAttribute("content", "owner/post/analytics");
        
        return "layout/owner-layout";
    }

    /**
     * Hide post (owner action)
     * URL: POST /owner/posts/{id}/hide
     */
    @PostMapping("/{id}/hide")
    public String hidePost(@PathVariable Long id, 
                          HttpSession session, 
                          RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        try {
            postService.getPostByIdAndOwner(id, user.getId());
            postService.hidePost(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã ẩn bài đăng thành công");
        } catch (Exception e) {
            log.error("Error hiding post {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể ẩn bài đăng: " + e.getMessage());
        }

        return "redirect:/owner/posts";
    }

    /**
     * Show post (owner action)
     * URL: POST /owner/posts/{id}/show
     */
    @PostMapping("/{id}/show")
    public String showPost(@PathVariable Long id, 
                          HttpSession session, 
                          RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        try {
            postService.getPostByIdAndOwner(id, user.getId());
            postService.showPost(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã hiển thị bài đăng thành công");
        } catch (Exception e) {
            log.error("Error showing post {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể hiển thị bài đăng: " + e.getMessage());
        }

        return "redirect:/owner/posts";
    }

    /**
     * Show renew post form
     * URL: GET /owner/posts/{id}/renew
     */
    @GetMapping("/{id}/renew")
    public String showRenewForm(@PathVariable Long id, 
                                HttpSession session, 
                                Model model,
                                RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        try {
            postService.getPostByIdAndOwner(id, user.getId());
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/owner/posts";
        }

        PostAnalyticsDTO analytics = postService.getPostAnalytics(id);
        
        // Load available posting packages for renewal
        List<PostPackageDTO> packages = postingPackageService.getAllPackages();
        
        model.addAttribute("analytics", analytics);
        model.addAttribute("postPackages", packages);
        model.addAttribute("walletBalance", walletService.getOrCreateWallet(user).getBalance());
        model.addAttribute("activeMenu", "posts");
        model.addAttribute("content", "owner/post/renew");
        
        return "layout/owner-layout";
    }

    /**
     * Renew post with posting package (deducts wallet)
     * URL: POST /owner/posts/{id}/renew
     */
    @PostMapping("/{id}/renew")
    public String renewPost(@PathVariable Long id,
                           @RequestParam Long postPackageId,
                           HttpSession session,
                           RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        try {
            postService.getPostByIdAndOwner(id, user.getId());
            var postPackage = postingPackageService.getById(postPackageId);
            postService.purchaseDuration(id, postPackage);
            
            // CRITICAL: Refresh wallet in session after transaction
            UserWallet updatedWallet = walletService.getOrCreateWallet(user);
            session.setAttribute("userWallet", updatedWallet);
            session.setAttribute("lastWalletUpdate", System.currentTimeMillis());
            
            redirectAttributes.addFlashAttribute("successMessage", 
                    String.format("Đã gia hạn bài đăng thêm %d ngày. Đã trừ %,d VND từ ví.", 
                        postPackage.getUsageLimit(), postPackage.getPrice()));
        } catch (IllegalStateException e) {
            // Wallet balance insufficient
            log.error("Insufficient balance for user renewing post {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Error renewing post {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", 
                    "Không thể gia hạn bài đăng: " + e.getMessage());
        }

        return "redirect:/owner/posts";
    }

    /**
     * Show edit post content form
     * URL: GET /owner/posts/{id}/edit
     */
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, 
                               HttpSession session, 
                               Model model,
                               RedirectAttributes ra) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        try {
            postService.getPostByIdAndOwner(id, user.getId());
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/owner/posts";
        }

        PostOwnerResponse post = postService.getPostsByOwner(user.getId())
                .stream()
                .filter(p -> p.getPostId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Post not found"));

        model.addAttribute("post", post);
        model.addAttribute("activeMenu", "posts");
        model.addAttribute("content", "owner/post/edit");
        
        return "layout/owner-layout";
    }

    /**
     * Update post marketing content
     * URL: POST /owner/posts/{id}/update
     */
    @PostMapping("/{id}/update")
    public String updatePost(@PathVariable Long id,
                            @RequestParam String title,
                            @RequestParam String slug,
                            @RequestParam String description,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        try {
            postService.getPostByIdAndOwner(id, user.getId());
            postService.updatePostContent(id, title, slug, description);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật bài đăng. Bài đăng đang tạm dừng và chờ moderator duyệt lại trước khi tiếp tục hiển thị.");
        } catch (Exception e) {
            log.error("Error updating post {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", 
                    "Không thể cập nhật bài đăng: " + e.getMessage());
        }

        return "redirect:/owner/posts";
    }

    /**
     * AI & Smart SEO Copywriter API for Posts
     * URL: POST /owner/posts/api/ai-generate-content
     */
    @PostMapping("/api/ai-generate-content")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> generateSmartPostContent(
            @RequestParam Long propertyId,
            @RequestParam(required = false, defaultValue = "CHUYEN_NGHIEP") String style,
            HttpSession session) {

        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Vui lòng đăng nhập"));
        }

        Property property = propertyRepository.findById(propertyId).orElse(null);
        if (property == null || property.getOwner() == null || !property.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "Không tìm thấy nhà trọ hoặc bạn không có quyền truy cập"));
        }

        String name = property.getName() != null ? property.getName() : "Khu trọ tiện nghi";
        String categoryName = property.getCategory() != null && property.getCategory().getName() != null
                ? property.getCategory().getName() : "Phòng trọ";
        String addressNumber = property.getAddressNumber() != null ? property.getAddressNumber() : "";
        String wardName = property.getWard() != null && property.getWard().getName() != null
                ? property.getWard().getName() : "";
        String provinceName = (property.getWard() != null && property.getWard().getProvince() != null
                && property.getWard().getProvince().getName() != null)
                ? property.getWard().getProvince().getName() : "";

        StringBuilder locationBuilder = new StringBuilder();
        if (!addressNumber.isBlank()) locationBuilder.append(addressNumber);
        if (!wardName.isBlank()) {
            if (locationBuilder.length() > 0) locationBuilder.append(", ");
            locationBuilder.append(wardName);
        }
        if (!provinceName.isBlank()) {
            if (locationBuilder.length() > 0) locationBuilder.append(", ");
            locationBuilder.append(provinceName);
        }
        String fullAddress = locationBuilder.length() > 0 ? locationBuilder.toString() : "Vị trí trung tâm thuận tiện";
        String shortArea = !wardName.isBlank() ? (wardName + (!provinceName.isBlank() ? ", " + provinceName : ""))
                : (!provinceName.isBlank() ? provinceName : name);

        int price = property.getPrice();
        double acreage = property.getAcreage();
        int numberOfRooms = property.getNumberOfRooms();

        String priceText;
        if (price >= 1_000_000) {
            double millions = price / 1_000_000.0;
            if (price % 1_000_000 == 0) {
                priceText = String.format("%d triệu/tháng", price / 1_000_000);
            } else {
                priceText = String.format("%.1f triệu/tháng", millions);
            }
        } else if (price > 0) {
            priceText = String.format("%,d đ/tháng", price);
        } else {
            priceText = "Giá thỏa thuận";
        }
        String formattedVndPrice = price > 0 ? String.format("%,d VNĐ/tháng", price) : "Thỏa thuận";
        String acreageText = acreage > 0 ? String.format("%.0f m²", acreage) : "Rộng rãi";

        String amenitiesText = (property.getAmenities() != null && !property.getAmenities().isEmpty())
                ? property.getAmenities().stream().map(Amenity::getName).collect(Collectors.joining(", "))
                : "Điều hòa, bình nóng lạnh, Wifi tốc độ cao, khu để xe rộng rãi, khóa vân tay an toàn";

        String surroundingsText = (property.getSurroundings() != null && !property.getSurroundings().isEmpty())
                ? property.getSurroundings().stream().map(Surrounding::getName).collect(Collectors.joining(", "))
                : "Gần các trường đại học, chợ dân sinh, siêu thị tiện lợi, bến xe buýt và trục đường chính";

        String targetTenantsText = (property.getTargetTenants() != null && !property.getTargetTenants().isEmpty())
                ? property.getTargetTenants().stream().map(TargetTenant::getName).collect(Collectors.joining(", "))
                : "Sinh viên, người đi làm văn phòng, gia đình trẻ";

        StringBuilder servicesBuilder = new StringBuilder();
        if (property.getServiceItems() != null && !property.getServiceItems().isEmpty()) {
            for (ServiceItem item : property.getServiceItems()) {
                double itemPrice = item.getPrice() != null ? item.getPrice() : 0.0;
                String unit = item.getUnit() != null ? item.getUnit() : "tháng";
                servicesBuilder.append(String.format("• %s: %,.0f đ / %s\n", item.getName(), itemPrice, unit));
            }
        } else {
            servicesBuilder.append("• Điện, nước: Tính theo chỉ số công tơ riêng từng phòng, minh bạch tuyệt đối\n");
            servicesBuilder.append("• Dịch vụ chung (Internet, vệ sinh, an ninh): Chi phí hợp lý, công khai rõ ràng\n");
        }

        String ownerName = (user.getFullName() != null && !user.getFullName().isBlank()) ? user.getFullName() : "Chính chủ";
        String ownerPhone = (user.getPhone() != null && !user.getPhone().isBlank()) ? user.getPhone() : "Liên hệ trực tiếp";

        String normalizedStyle = style != null ? style.trim().toUpperCase() : "CHUYEN_NGHIEP";
        String title;
        String description;

        if ("SINH_VIEN".equals(normalizedStyle)) {
            title = String.format("[Giá Sinh Viên] Cho thuê %s %s tại %s - Chỉ %s, %s, Full Tiện Ích",
                    categoryName, name, shortArea, priceText, acreageText);
            description = String.format(
                    "🎒 CƠ HỘI THUÊ %s GIÁ TỐT DÀNH CHO SINH VIÊN & NGƯỜI MỚI ĐI LÀM TẠI %s!\n\n" +
                    "🌟 TẠI SAO BẠN NÊN CHỌN %s?\n" +
                    "• Mức giá cực kỳ tiết kiệm: Chỉ %s (%s) cho không gian rộng %s, ở nhóm bạn cực kỳ thoải mái.\n" +
                    "• Giờ giấc tự do 24/7, không chung chủ, bạn bè đến chơi thoải mái, an ninh đảm bảo.\n" +
                    "• Quy mô khu trọ %d phòng văn minh, yên tĩnh, rất phù hợp học tập và nghỉ ngơi.\n" +
                    "• Đối tượng phù hợp: %s.\n\n" +
                    "📍 VỊ TRÍ THUẬN TIỆN DI CHUYỂN:\n" +
                    "• Địa chỉ: %s.\n" +
                    "• Tiện ích xung quanh: %s — đi học hay đi làm thêm đều cực gần!\n\n" +
                    "🛋️ TIỆN NGHI ĐẦY ĐỦ - CHỈ VIỆC XÁCH VALI VÀO Ở:\n" +
                    "• Trang bị sẵn: %s.\n\n" +
                    "💰 CHI PHÍ & DỊCH VỤ RÕ RÀNG, KHÔNG PHÍ ẨN:\n" +
                    "• Giá thuê phòng: %s\n" +
                    "%s\n" +
                    "📞 LIÊN HỆ XEM PHÒNG NGAY (CHÍNH CHỦ - KHÔNG MÔI GIỚI):\n" +
                    "• Chủ nhà: %s\n" +
                    "• Số điện thoại / Zalo: %s\n" +
                    "👉 Nhanh tay liên hệ để giữ phòng đẹp nhất ngay hôm nay!",
                    categoryName.toUpperCase(), shortArea.toUpperCase(), name.toUpperCase(),
                    priceText, formattedVndPrice, acreageText,
                    numberOfRooms, targetTenantsText,
                    fullAddress, surroundingsText,
                    amenitiesText,
                    formattedVndPrice, servicesBuilder.toString().trim(),
                    ownerName, ownerPhone
            );
        } else if ("CAO_CAP".equals(normalizedStyle)) {
            title = String.format("Cho Thuê %s Cao Cấp %s (%s) tại %s - Nội Thất Hiện Đại, An Ninh 24/7, Giá %s",
                    categoryName, name, acreageText, shortArea, priceText);
            description = String.format(
                    "✨ KHÔNG GIAN SỐNG HIỆN ĐẠI & ĐẲNG CẤP TẠI %s - %s\n\n" +
                    "🌟 ĐIỂM NHẤN KHÔNG GIAN SỐNG:\n" +
                    "• Phân khúc: %s cao cấp với thiết kế thoáng sáng, tối ưu công năng trên diện tích %s.\n" +
                    "• Không gian riêng tư tuyệt đối, khu dân trí cao, hệ thống quản lý chuyên nghiệp (%d phòng tiêu chuẩn).\n" +
                    "• Phù hợp hoàn hảo cho: %s mong muốn một nơi an cư chất lượng, lâu dài.\n\n" +
                    "📍 VỊ TRÍ ĐẮC ĐỊA & KẾT NỐI ĐA CHIỀU:\n" +
                    "• Tọa lạc tại: %s.\n" +
                    "• Môi trường xung quanh: %s. Giao thông thuận lợi, kết nối nhanh chóng tới các khu văn phòng và trung tâm thương mại.\n\n" +
                    "🛋️ NỘI THẤT & TIỆN ÍCH TIÊU CHUẨN CAO:\n" +
                    "• Tiện nghi trang bị: %s.\n" +
                    "• Hệ thống an ninh giám sát 24/7, PCCC đạt chuẩn, vệ sinh định kỳ khu vực chung.\n\n" +
                    "💰 BẢNG GIÁ & DỊCH VỤ CHUYÊN NGHIỆP:\n" +
                    "• Giá thuê niêm yết: %s (%s)\n" +
                    "%s\n" +
                    "📞 ĐẶT LỊCH THAM QUAN PHÒNG TRỰC TIẾP:\n" +
                    "• Quản lý / Chủ tòa nhà: %s\n" +
                    "• Hotline hỗ trợ 24/7: %s\n" +
                    "✨ Cam kết hình ảnh và thông tin thực tế 100%%. Hân hạnh đón tiếp quý khách đến xem phòng!",
                    name.toUpperCase(), shortArea.toUpperCase(),
                    categoryName, acreageText, numberOfRooms, targetTenantsText,
                    fullAddress, surroundingsText,
                    amenitiesText,
                    formattedVndPrice, priceText, servicesBuilder.toString().trim(),
                    ownerName, ownerPhone
            );
        } else {
            title = String.format("Chính Chủ Cho Thuê %s %s tại %s - %s, Rộng %s, Đầy Đủ Tiện Nghi",
                    categoryName, name, shortArea, priceText, acreageText);
            description = String.format(
                    "🌟 ĐIỂM NỔI BẬT:\n" +
                    "• Chính chủ cho thuê %s tại %s với không gian sạch đẹp, thoáng mát, diện tích %s.\n" +
                    "• Giá thuê hợp lý chỉ %s (%s), quy mô %d phòng được quản lý bài bản, an ninh tốt.\n" +
                    "• Đối tượng thuê phù hợp: %s.\n\n" +
                    "📍 VỊ TRÍ & GIAO THÔNG:\n" +
                    "• Địa chỉ chi tiết: %s.\n" +
                    "• Tiện ích ngoại khu xung quanh: %s. Đường xá đi lại thuận tiện, không ngập lụt.\n\n" +
                    "🛋️ TIỆN NGHI & NỘI THẤT:\n" +
                    "• Tiện ích có sẵn: %s.\n" +
                    "• Phòng có cửa sổ/ánh sáng tự nhiên, khu vực để xe an toàn, giờ giấc linh hoạt.\n\n" +
                    "💰 CHI PHÍ & DỊCH VỤ MINH BẠCH:\n" +
                    "• Tiền thuê phòng: %s\n" +
                    "%s\n" +
                    "• Hợp đồng rõ ràng, hỗ trợ thanh toán chuyển khoản VietQR tiện lợi hàng tháng.\n\n" +
                    "📞 LIÊN HỆ XEM PHÒNG:\n" +
                    "• Người liên hệ: %s (Chính chủ)\n" +
                    "• Điện thoại / Zalo: %s\n" +
                    "👉 Liên hệ ngay để hẹn lịch xem phòng trực tiếp hoàn toàn miễn phí!",
                    categoryName, name, acreageText,
                    priceText, formattedVndPrice, numberOfRooms, targetTenantsText,
                    fullAddress, surroundingsText,
                    amenitiesText,
                    formattedVndPrice, servicesBuilder.toString().trim(),
                    ownerName, ownerPhone
            );
        }

        String slug = SlugUtil.toSlug(title);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("title", title);
        response.put("slug", slug);
        response.put("description", description);
        return ResponseEntity.ok(response);
    }
}
