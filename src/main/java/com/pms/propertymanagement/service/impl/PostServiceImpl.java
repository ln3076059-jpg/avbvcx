package com.pms.propertymanagement.service.impl;

import com.pms.propertymanagement.dto.response.PostAnalyticsDTO;
import com.pms.propertymanagement.dto.response.PostOwnerResponse;
import com.pms.propertymanagement.entity.Post;
import com.pms.propertymanagement.entity.PostingPackage;
import com.pms.propertymanagement.entity.Property;
import com.pms.propertymanagement.entity.Subscription;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.enums.PostStatus;
import com.pms.propertymanagement.enums.TransactionType;
import com.pms.propertymanagement.exception.ResourceNotFoundException;
import com.pms.propertymanagement.repository.PostRepository;
import com.pms.propertymanagement.repository.PropertyRepository;
import com.pms.propertymanagement.service.ManagementPlanService;
import com.pms.propertymanagement.service.PostService;
import com.pms.propertymanagement.service.SubscriptionManagementService;
import com.pms.propertymanagement.service.WalletService;
import com.pms.propertymanagement.utils.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of PostService
 * NEW ARCHITECTURE: Manages Post lifecycle separately from Property
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl implements PostService {

    private static final List<PostStatus> MARKETPLACE_FALLBACK_STATUSES =
            List.of(PostStatus.ACTIVE, PostStatus.EXPIRED);

    private final PostRepository postRepository;
    private final PropertyRepository propertyRepository;
    private final WalletService walletService;
    private final SubscriptionManagementService subscriptionManagementService;
    private final ManagementPlanService managementPlanService;

    // === POST CREATION & CONTENT MANAGEMENT ===

    @Override
    @Transactional
    public Post createPost(Property property, String title, String slug, String description) {
        // Check if property already has a post
        if (postRepository.existsByPropertyId(property.getId())) {
            throw new IllegalStateException("Property already has a post. One property can only have one post.");
        }

        // Check slug uniqueness
        if (postRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("Slug already exists. Please use a different slug.");
        }

        Post post = new Post();
        post.setProperty(property);
        post.setTitle(title);
        post.setSlug(slug);
        post.setDescription(description);
        post.setViewCount(0);
        
        // NEW: Post starts as PENDING_APPROVAL - no timer until staff approves
        post.setStatus(PostStatus.PENDING_APPROVAL);
        // postExpiredAt intentionally left null - will be set on approval

        post = postRepository.save(post);
        log.info("Created post {} (PENDING_APPROVAL) for property {}", post.getId(), property.getId());
        
        return post;
    }

    @Override
    @Transactional
    public Post createPostForProperty(Long propertyId, Long ownerId, String title, String slug, String description) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));

        // Validate ownership
        if (!property.getOwner().getId().equals(ownerId)) {
            throw new IllegalStateException("You do not own this property.");
        }

        return createPost(property, title, slug, description);
    }

    @Override
    @Transactional
    public void updatePostContent(Long postId, String title, String slug, String description) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        // Check slug uniqueness (excluding current post)
        if (!post.getSlug().equals(slug) && postRepository.existsBySlugExcludingId(slug, postId)) {
            throw new IllegalArgumentException("Slug already exists. Please use a different slug.");
        }

        post.setTitle(title);
        post.setSlug(slug);
        post.setDescription(description);

        // When owner edits, pause the post and require re-approval
        // REJECTED → PENDING_REVISION (re-review, fresh timer granted on approval)
        // ACTIVE / EXPIRED / HIDDEN → PENDING_REVISION (re-review, existing timer preserved)
        com.pms.propertymanagement.enums.PostStatus currentStatus = post.getStatus();
        if (currentStatus == com.pms.propertymanagement.enums.PostStatus.REJECTED) {
            post.resubmit();
            log.info("Post {} resubmitted to PENDING_REVISION after rejection", postId);
        } else if (currentStatus != com.pms.propertymanagement.enums.PostStatus.PENDING_APPROVAL
                && currentStatus != com.pms.propertymanagement.enums.PostStatus.PENDING_REVISION) {
            post.submitRevision();
            log.info("Post {} set to PENDING_REVISION after owner edit", postId);
        }

        postRepository.save(post);
        log.info("Updated post {} content", postId);
    }

    // === DURATION MANAGEMENT ===

    @Override
    @Transactional
    public void purchaseDuration(Long postId, PostingPackage postPackage) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        // Get owner to check wallet balance
        User owner = post.getProperty().getOwner();
        if (owner == null) {
            throw new IllegalStateException("Post owner not found");
        }

        // Check wallet balance
        BigDecimal packagePrice = BigDecimal.valueOf(postPackage.getPrice());
        if (!walletService.hasEnoughBalance(owner, packagePrice)) {
            throw new IllegalStateException("Số dư ví không đủ. Cần " + String.format("%,d", postPackage.getPrice()) + " VND để gia hạn bài đăng.");
        }

        // Deduct wallet balance
        walletService.deduct(owner, packagePrice, 
            TransactionType.EXTENSION,
            "Gia hạn bài đăng: " + post.getTitle(),
            "POST_RENEW_" + postId);

        // Calculate duration from package (usageLimit represents days)
        int additionalDays = postPackage.getUsageLimit();

        // Extend post duration
        post.extendDuration(additionalDays);
        
        // If post was EXPIRED, reactivate it
        if (post.getStatus() == PostStatus.EXPIRED) {
            post.setStatus(PostStatus.ACTIVE);
        }

        postRepository.save(post);
        log.info("Extended post {} duration by {} days using package {} (deducted {} VND)", 
                postId, additionalDays, postPackage.getCode(), packagePrice);
    }

    @Override
    @Transactional
    public void renewPost(Long postId, int additionalDays) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        post.extendDuration(additionalDays);
        
        // If post was EXPIRED, reactivate it
        if (post.getStatus() == PostStatus.EXPIRED) {
            post.setStatus(PostStatus.ACTIVE);
        }

        postRepository.save(post);
        log.info("Renewed post {} with {} additional days", postId, additionalDays);
    }

    // === VISIBILITY MANAGEMENT ===

    @Override
    @Transactional
    public void hidePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (post.getStatus() != PostStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE posts can be hidden");
        }

        post.hide();
        postRepository.save(post);
        log.info("Hidden post {}", postId);
    }

    @Override
    @Transactional
    public void showPost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (post.getStatus() != PostStatus.HIDDEN) {
            throw new IllegalStateException("Only HIDDEN posts can be shown");
        }

        // Don't show if expired
        if (post.isPostExpired()) {
            throw new IllegalStateException("Cannot show expired post. Please renew first.");
        }

        post.show();
        postRepository.save(post);
        log.info("Showed post {}", postId);
    }

    @Override
    @Transactional
    public void resubmitPost(Long postId, Long ownerId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (!post.getProperty().getOwner().getId().equals(ownerId)) {
            throw new IllegalStateException("You do not own this post.");
        }

        if (post.getStatus() != PostStatus.REJECTED) {
            throw new IllegalStateException("Only REJECTED posts can be resubmitted.");
        }

        post.resubmit();
        postRepository.save(post);
        log.info("Post {} resubmitted to PENDING_REVISION by owner {}", postId, ownerId);
    }

    // === STAFF APPROVAL WORKFLOW ===

    @Override
    @Transactional
    public void approvePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (post.getStatus() != PostStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Only PENDING_APPROVAL posts can be approved.");
        }

        // Determine duration from owner's active management plan
        int durationDays = 7; // default fallback
        try {
            Long ownerId = post.getProperty().getOwner().getId();
            Subscription sub = subscriptionManagementService.getActiveManagementSubscription(ownerId);
            if (sub != null) {
                durationDays = managementPlanService.getById(sub.getManagementPlanId()).getPostDurationDays();
            }
        } catch (Exception e) {
            log.warn("Could not determine plan duration for post {}, using default 7 days: {}", postId, e.getMessage());
        }

        post.approve(durationDays);
        postRepository.save(post);
        log.info("Post {} approved - {}-day trial started, expires at {}", postId, durationDays, post.getPostExpiredAt());
    }

    @Override
    @Transactional
    public void rejectPost(Long postId, String reason) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (post.getStatus() != PostStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Only PENDING_APPROVAL posts can be rejected.");
        }

        post.reject(reason);
        postRepository.save(post);
        log.info("Post {} rejected. Reason: {}", postId, reason);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostOwnerResponse> getPendingApprovalPosts() {
        return postRepository.findByStatus(PostStatus.PENDING_APPROVAL)
                .stream()
                .map(this::convertToOwnerResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Post> getPostsByStatus(PostStatus status) {
        return postRepository.findByStatusOrderByCreatedAtAsc(status);
    }

    @Override
    @Transactional(readOnly = true)
    public Post findPostById(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bài đăng không tồn tại"));
    }

    @Override
    @Transactional(readOnly = true)
    public Post getPostByIdAndOwner(Long postId, Long ownerId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Bài đăng không tồn tại"));
        if (post.getProperty() == null || post.getProperty().getOwner() == null
                || !post.getProperty().getOwner().getId().equals(ownerId)) {
            throw new IllegalArgumentException("Bạn không có quyền truy cập bài đăng này");
        }
        return post;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Post> findPostByPropertyId(Long propertyId) {
        return postRepository.findByPropertyId(propertyId);
    }

    @Override
    @Transactional
    public void approvePostByModerator(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Bài đăng không tồn tại"));

        if (post.getStatus() != PostStatus.PENDING_APPROVAL && post.getStatus() != PostStatus.PENDING_REVISION) {
            throw new IllegalStateException("Chỉ duyệt được bài đang ở trạng thái chờ duyệt.");
        }

        if (post.getStatus() == PostStatus.PENDING_REVISION && post.getPausedAt() != null) {
            // Compensate the pause duration so owner doesn't lose display time during review
            LocalDateTime pausedAt = post.getPausedAt();
            LocalDateTime now = LocalDateTime.now();
            long pausedSeconds = java.time.temporal.ChronoUnit.SECONDS.between(pausedAt, now);
            LocalDateTime baseExpiry = (post.getPostExpiredAt() != null) ? post.getPostExpiredAt() : now;
            post.setPostExpiredAt(baseExpiry.plusSeconds(pausedSeconds));
            post.setPausedAt(null);
            post.setStatus(PostStatus.ACTIVE);
            post.setRejectionReason(null);
            log.info("Post {} (PENDING_REVISION) re-approved by moderator, compensated {} seconds", postId, pausedSeconds);
        } else {
            // PENDING_APPROVAL: grant fresh days from owner's active management plan
            int durationDays = 7;
            try {
                Long ownerId = post.getProperty().getOwner().getId();
                Subscription sub = subscriptionManagementService.getActiveManagementSubscription(ownerId);
                if (sub != null) {
                    durationDays = managementPlanService.getById(sub.getManagementPlanId()).getPostDurationDays();
                }
            } catch (Exception e) {
                log.warn("Could not determine plan duration for post {}, using default 7 days: {}", postId, e.getMessage());
            }
            post.approve(durationDays);
            log.info("Post {} approved by moderator with {} days", postId, durationDays);
        }

        postRepository.save(post);
    }

    @Override
    @Transactional
    public void rejectPostByModerator(Long postId, String reason) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Bài đăng không tồn tại"));

        if (post.getStatus() != PostStatus.PENDING_APPROVAL && post.getStatus() != PostStatus.PENDING_REVISION) {
            throw new IllegalStateException("Chỉ từ chối được bài đang ở trạng thái chờ duyệt.");
        }

        post.reject(reason);
        postRepository.save(post);
        log.info("Post {} rejected by moderator. Reason: {}", postId, reason);
    }

    // === QUERY METHODS ===

    @Override
    @Transactional(readOnly = true)
    public Optional<Post> getMarketplacePostBySlug(String slug) {
        Optional<Post> livePost = postRepository.findMarketplacePostBySlug(slug, LocalDateTime.now());
        if (livePost.isPresent()) {
            return livePost;
        }

        Optional<Post> fallbackPost = postRepository.findBySlugAndStatusIn(slug, MARKETPLACE_FALLBACK_STATUSES);
        fallbackPost.ifPresent(post -> log.warn(
                "Falling back to demo marketplace post for slug={} because no active non-expired post was found",
                slug
        ));
        return fallbackPost;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostOwnerResponse> getPostsByOwner(Long ownerId) {
        List<Post> posts = postRepository.findByPropertyOwnerId(ownerId);
        
        return posts.stream()
                .map(this::convertToOwnerResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PostAnalyticsDTO getPostAnalytics(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        return convertToAnalyticsDTO(post);
    }

    // === VIEW TRACKING ===

    @Override
    @Transactional
    public void incrementView(String slug) {
        Optional<Post> postOpt = postRepository.findBySlug(slug);
        
        if (postOpt.isPresent()) {
            Post post = postOpt.get();
            post.incrementViewCount();
            postRepository.save(post);
            log.debug("Incremented view count for post {} (slug: {})", post.getId(), slug);
        }
    }

    // === MARKETPLACE QUERIES ===

    @Override
    @Transactional(readOnly = true)
    public List<Post> getAllMarketplacePosts() {
        List<Post> livePosts = postRepository.findMarketplacePosts(LocalDateTime.now());
        if (!livePosts.isEmpty()) {
            return livePosts;
        }

        List<Post> fallbackPosts = postRepository.findByStatusInOrderByCreatedAtDesc(MARKETPLACE_FALLBACK_STATUSES);
        if (!fallbackPosts.isEmpty()) {
            log.warn("Marketplace fallback activated: showing ACTIVE/EXPIRED demo posts because no live post is currently visible");
        }
        return fallbackPosts;
    }

    @Override
    public List<Post> searchMarketplacePosts(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllMarketplacePosts();
        }
        return postRepository.searchMarketplacePosts(keyword.trim(), LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Post> getMarketplacePostsByCategory(Long categoryId) {
        List<Post> livePosts = postRepository.findMarketplacePostsByCategory(categoryId, LocalDateTime.now());
        if (!livePosts.isEmpty()) {
            return livePosts;
        }

        List<Post> fallbackPosts = postRepository.findByStatusInAndProperty_Category_IdOrderByCreatedAtDesc(
                MARKETPLACE_FALLBACK_STATUSES,
                categoryId
        );
        if (!fallbackPosts.isEmpty()) {
            log.warn(
                    "Marketplace category fallback activated for categoryId={}: showing ACTIVE/EXPIRED demo posts",
                    categoryId
            );
        }
        return fallbackPosts;
    }

    // === HELPER METHODS ===

    /**
     * Convert Post entity to PostOwnerResponse DTO
     */
    private PostOwnerResponse convertToOwnerResponse(Post post) {
        Property property = post.getProperty();
        LocalDateTime now = LocalDateTime.now();
        
        long daysRemaining = 0;
        if (post.getPostExpiredAt() != null && post.getPostExpiredAt().isAfter(now)) {
            daysRemaining = Duration.between(now, post.getPostExpiredAt()).toDays();
        }

        return PostOwnerResponse.builder()
                .postId(post.getId())
                .propertyId(property.getId())
                .title(post.getTitle())
                .slug(post.getSlug())
                .description(post.getDescription())
                .propertyName(property.getName())
                .addressNumber(property.getAddressNumber())
                .wardName(property.getWard() != null ? property.getWard().getName() : null)
                .provinceName(property.getWard() != null && property.getWard().getProvince() != null 
                        ? property.getWard().getProvince().getName() : null)
                .imageUrl(!property.getImages().isEmpty() ? property.getImages().get(0).getImageUrl() : null)
                .status(post.getStatus())
                .postExpiredAt(post.getPostExpiredAt())
                .boostExpiredAt(post.getBoostExpiredAt())
                .viewCount(post.getViewCount())
                .rejectionReason(post.getRejectionReason())
                .isExpired(post.isPostExpired())
                .isBoosted(post.isBoosted())
                .isActive(post.isVisibleInMarketplace())
                .daysRemaining(daysRemaining)
                .formattedExpiry(post.getPostExpiredAt() != null ? DateUtil.formatDateTime(post.getPostExpiredAt()) : null)
                .formattedCreatedAt(DateUtil.formatDateTime(post.getCreatedAt()))
                .build();
    }

    /**
     * Convert Post entity to PostAnalyticsDTO
     */
    private PostAnalyticsDTO convertToAnalyticsDTO(Post post) {
        LocalDateTime now = LocalDateTime.now();
        
        long daysRemaining = 0;
        long hoursRemaining = 0;
        
        if (post.getPostExpiredAt() != null && post.getPostExpiredAt().isAfter(now)) {
            Duration duration = Duration.between(now, post.getPostExpiredAt());
            daysRemaining = duration.toDays();
            hoursRemaining = duration.toHours();
        }

        String statusLabel;
        String statusColorClass;

        switch (post.getStatus()) {
            case PENDING_APPROVAL -> {
                statusLabel = "Chờ duyệt";
                statusColorClass = "bg-yellow-100 text-yellow-800";
            }
            case REJECTED -> {
                statusLabel = "Bị từ chối";
                statusColorClass = "bg-red-100 text-red-800";
            }
            case ACTIVE -> {
                if (post.isPostExpired()) {
                    statusLabel = "Hết hạn";
                    statusColorClass = "bg-red-100 text-red-800";
                } else {
                    statusLabel = "Đang hiển thị";
                    statusColorClass = "bg-green-100 text-green-800";
                }
            }
            case EXPIRED -> {
                statusLabel = "Đã hết hạn";
                statusColorClass = "bg-red-100 text-red-800";
            }
            case HIDDEN -> {
                statusLabel = "Đã ẩn";
                statusColorClass = "bg-gray-100 text-gray-800";
            }
            case DRAFT -> {
                statusLabel = "Bản nháp";
                statusColorClass = "bg-yellow-100 text-yellow-800";
            }
            default -> {
                statusLabel = "Không xác định";
                statusColorClass = "bg-gray-100 text-gray-800";
            }
        }

        return PostAnalyticsDTO.builder()
                .postId(post.getId())
                .title(post.getTitle())
                .slug(post.getSlug())
                .status(post.getStatus())
                .isVisible(post.isVisibleInMarketplace())
                .isExpired(post.isPostExpired())
                .isBoosted(post.isBoosted())
                .postExpiredAt(post.getPostExpiredAt())
                .boostExpiredAt(post.getBoostExpiredAt())
                .daysRemaining(daysRemaining)
                .hoursRemaining(hoursRemaining)
                .viewCount(post.getViewCount())
                .statusLabel(statusLabel)
                .statusColorClass(statusColorClass)
                .formattedExpiry(post.getPostExpiredAt() != null ? DateUtil.formatDateTime(post.getPostExpiredAt()) : null)
                .formattedBoostExpiry(post.getBoostExpiredAt() != null 
                        ? DateUtil.formatDateTime(post.getBoostExpiredAt()) : null)
                .build();
    }
}
