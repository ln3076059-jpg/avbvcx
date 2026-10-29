package com.pms.propertymanagement.service.impl;

import com.pms.propertymanagement.dto.chat.PropertySummaryForChat;
import com.pms.propertymanagement.entity.Post;
import com.pms.propertymanagement.entity.Property;
import com.pms.propertymanagement.entity.Room;
import com.pms.propertymanagement.enums.PostStatus;
import com.pms.propertymanagement.enums.RoomStatus;
import com.pms.propertymanagement.model.ChatSessionState;
import com.pms.propertymanagement.repository.PostRepository;
import com.pms.propertymanagement.service.PropertyRecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Translates ChatSessionState into DB queries, applies relaxed fallback,
 * computes scores, ranks, and returns at most maxResults items.
 *
 * Search strategy (stops at first non-empty tier):
 *   Tier 1 (strict)   — budget + location (ward/bbox) + amenities + category
 *   Tier 2 (relaxed1) — budget×1.2 + location, drop amenity filter
 *   Tier 3 (relaxed2) — province-wide, cheapest, no other filters
 *
 * Scoring (max 90 points):
 *   30  boost
 *   25  amenity match rate
 *   20  budget proximity
 *   10  location (Haversine or ward-match)
 *    5  post longevity
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PropertyRecommendationServiceImpl implements PropertyRecommendationService {

    private static final List<PostStatus> RECOMMENDATION_FALLBACK_STATUSES =
            List.of(PostStatus.ACTIVE);

    private final PostRepository postRepository;

    @Value("${chat.recommendation.max-results:5}")
    private int maxResults;

    @Value("${chat.recommendation.relaxed-budget-multiplier:1.2}")
    private double relaxedBudgetMultiplier;

    // =========================================================================
    // Public API
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public long countProvinceResults(ChatSessionState state) {
        if (state.getProvinceCode() == null) {
            return 0L;
        }
        try {
            List<Post> activePosts = postRepository.findByStatusIn(RECOMMENDATION_FALLBACK_STATUSES);
            long count = activePosts.stream()
                    .filter(post -> matchesProvinceBudgetFallback(post, state.getProvinceCode(), state.getBudget()))
                    .count();
            if (count > 0) {
                return count;
            }
            // Also check with relaxed budget so user isn't told 0 when slightly above budget
            Double relaxed = state.getBudget() != null ? state.getBudget() * relaxedBudgetMultiplier : null;
            return activePosts.stream()
                    .filter(post -> matchesProvinceBudgetFallback(post, state.getProvinceCode(), relaxed))
                    .count();
        } catch (Exception e) {
            log.warn("countProvinceResults fallback triggered: {}", e.getMessage());
            return 0L;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PropertySummaryForChat> findRecommendations(ChatSessionState state) {
        Double budget = state.getBudget();
        double relaxedBudget = budget != null ? budget * relaxedBudgetMultiplier : Double.MAX_VALUE;

        List<Post> posts = List.of();

        try {
            // Tier 1: strict match (province + ward/coordinates + budget + category + amenities)
            posts = findFallbackPosts(state, budget, true, false);
            log.debug("Tier1 strict in-memory -> {} posts", posts.size());

            // Tier 2: relaxed budget (x1.2) + drop amenity filter
            if (posts.isEmpty()) {
                posts = findFallbackPosts(state, relaxedBudget, false, false);
                log.debug("Tier2 relaxed-budget -> {} posts", posts.size());
            }

            // Tier 3: province-wide within relaxed budget (drop ward/coordinate filter)
            if (posts.isEmpty()) {
                posts = findFallbackPosts(state, relaxedBudget, false, true);
                log.debug("Tier3 province-wide relaxed-budget -> {} posts", posts.size());
            }

            // Tier 4: province-wide any budget (cheapest available in the requested province)
            if (posts.isEmpty() && state.getProvinceCode() != null) {
                ChatSessionState provinceWideState = new ChatSessionState(state.getGuestId());
                provinceWideState.setProvinceCode(state.getProvinceCode());
                posts = findFallbackPosts(provinceWideState, null, false, true);
                log.debug("Tier4 province-wide any-budget -> {} posts", posts.size());
            }

            // Tier 5: nationwide within relaxed budget if requested province has no demo data
            if (posts.isEmpty()) {
                ChatSessionState nationwideState = new ChatSessionState(state.getGuestId());
                posts = findFallbackPosts(nationwideState, relaxedBudget, false, true);
                log.debug("Tier5 nationwide fallback -> {} posts", posts.size());
            }
        } catch (Exception e) {
            log.error("Error in findRecommendations, returning empty list: {}", e.getMessage(), e);
            return List.of();
        }

        if (posts.isEmpty()) {
            return List.of();
        }

        // Convert -> score -> sort -> limit -> assign displayIndex
        List<PropertySummaryForChat> summaries = posts.stream()
                .map(post -> toSummary(post, state))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        summaries.sort(buildComparator(state));

        // Assign 1-based displayIndex after ranking
        List<PropertySummaryForChat> ranked = new ArrayList<>();
        int idx = 1;
        for (PropertySummaryForChat s : summaries.stream().limit(maxResults).toList()) {
            ranked.add(withIndex(s, idx++));
        }
        return ranked;
    }

    // =========================================================================
    // Bounding-box query (geocoded location path)
    // =========================================================================

    /**
     * Step 1: coarse bounding-box filter in DB.
     * Step 2: Haversine trim in Java to turn the square into a true circle.
     *
     * @param effectiveBudget   budget cap to apply (may be relaxed budget for Tier 2)
     * @param applyAmenityFilter false for Tier 2 (drop amenity requirement)
     */
    private List<Post> queryBoundingBox(ChatSessionState state, LocalDateTime now,
                                         Double effectiveBudget, boolean applyAmenityFilter) {
        double lat = state.getCenterLat();
        double lng = state.getCenterLng();
        double radiusKm = state.getSearchRadiusKm();

        // Approximate degree offsets for the bounding box
        double deltaLat = radiusKm / 111.0;
        double deltaLng = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));

        List<Post> candidates = postRepository.findPostsInBoundingBox(
                now,
                lat - deltaLat, lat + deltaLat,
                lng - deltaLng, lng + deltaLng);

        Long categoryId = state.getCategoryId();
        List<Long> amenityIds = state.getAmenityIds();

        return candidates.stream()
                .filter(p -> {
                    Property prop = p.getProperty();
                    // Haversine circle trim
                    if (prop.getLatitude() == null) return false;
                    double dist = haversine(lat, lng, prop.getLatitude(), prop.getLongitude());
                    if (dist > radiusKm) return false;
                    // Budget: at least one available room within budget
                    if (effectiveBudget != null && prop.getRooms().stream()
                            .noneMatch(r -> r.getStatus() == RoomStatus.AVAILABLE
                                    && r.getPrice() <= effectiveBudget)) return false;
                    // Category filter
                    if (categoryId != null && (prop.getCategory() == null
                            || !categoryId.equals(prop.getCategory().getId()))) return false;
                    // Amenity filter (skipped in Tier 2)
                    if (applyAmenityFilter && amenityIds != null && !amenityIds.isEmpty()) {
                        Set<Long> propAmenityIds = prop.getAmenities().stream()
                                .map(a -> a.getId()).collect(Collectors.toSet());
                        if (propAmenityIds.stream().noneMatch(amenityIds::contains)) return false;
                    }
                    return true;
                })
                .toList();
    }

    private List<Post> findFallbackPosts(
            ChatSessionState state,
            Double effectiveBudget,
            boolean requireAmenityMatch,
            boolean provinceOnly
    ) {
        return postRepository.findByStatusIn(RECOMMENDATION_FALLBACK_STATUSES).stream()
                .filter(post -> matchesFallbackFilters(post, state, effectiveBudget, requireAmenityMatch, provinceOnly))
                .toList();
    }

    private boolean matchesFallbackFilters(
            Post post,
            ChatSessionState state,
            Double effectiveBudget,
            boolean requireAmenityMatch,
            boolean provinceOnly
    ) {
        Property prop = post.getProperty();
        if (prop == null || prop.getWard() == null || prop.getWard().getProvince() == null) {
            return false;
        }

        if (state.getProvinceCode() != null
                && !state.getProvinceCode().equals(prop.getWard().getProvince().getCode())) {
            return false;
        }

        if (!hasMatchingAvailableRoom(prop, effectiveBudget)) {
            return false;
        }

        if (state.getCategoryId() != null
                && (prop.getCategory() == null || !state.getCategoryId().equals(prop.getCategory().getId()))) {
            return false;
        }

        if (requireAmenityMatch && state.getAmenityIds() != null && !state.getAmenityIds().isEmpty()) {
            Set<Long> propertyAmenityIds = prop.getAmenities().stream()
                    .map(amenity -> amenity.getId())
                    .collect(Collectors.toSet());
            if (!propertyAmenityIds.containsAll(state.getAmenityIds())) {
                return false;
            }
        }

        if (provinceOnly) {
            return true;
        }

        if (state.hasCoordinates() && state.getSearchRadiusKm() != null) {
            if (prop.getLatitude() == null || prop.getLongitude() == null) {
                return false;
            }
            double distanceKm = haversine(
                    state.getCenterLat(),
                    state.getCenterLng(),
                    prop.getLatitude(),
                    prop.getLongitude()
            );
            return distanceKm <= state.getSearchRadiusKm();
        }

        if (state.getResolvedWardCodes() != null && !state.getResolvedWardCodes().isEmpty()) {
            return state.getResolvedWardCodes().contains(prop.getWard().getCode());
        }

        return true;
    }

    private boolean matchesProvinceBudgetFallback(Post post, String provinceCode, Double maxPrice) {
        Property prop = post.getProperty();
        if (prop == null || prop.getWard() == null || prop.getWard().getProvince() == null) {
            return false;
        }
        if (provinceCode == null || !provinceCode.equals(prop.getWard().getProvince().getCode())) {
            return false;
        }
        return hasMatchingAvailableRoom(prop, maxPrice);
    }

    private boolean hasMatchingAvailableRoom(Property prop, Double maxPrice) {
        return prop.getRooms().stream()
                .anyMatch(room -> room.getStatus() == RoomStatus.AVAILABLE
                        && (maxPrice == null || room.getPrice() <= maxPrice));
    }

    // =========================================================================
    // DTO mapping
    // =========================================================================

    /**
     * Converts a Post + session state into a PropertySummaryForChat.
     * Returns null if the property has no available rooms (post stale, skip it).
     */
    private PropertySummaryForChat toSummary(Post post, ChatSessionState state) {
        Property prop = post.getProperty();

        // Find cheapest available room for display price
        Optional<Room> cheapestOpt = prop.getRooms().stream()
                .filter(r -> r.getStatus() == RoomStatus.AVAILABLE)
                .min(Comparator.comparingDouble(Room::getPrice));

        if (cheapestOpt.isEmpty()) return null; // no available room → skip

        Room cheapest = cheapestOpt.get();

        // Amenity names
        List<String> amenityNames = prop.getAmenities().stream()
                .map(a -> a.getName())
                .sorted()
                .toList();

        // Surrounding names
        List<String> surroundingNames = prop.getSurroundings().stream()
                .map(s -> s.getName())
                .sorted()
                .toList();

        // Amenity match count (how many requested amenities this property has)
        int amenityMatchCount = 0;
        if (state.getAmenityIds() != null && !state.getAmenityIds().isEmpty()) {
            Set<Long> propAmenityIds = prop.getAmenities().stream()
                    .map(a -> a.getId()).collect(Collectors.toSet());
            amenityMatchCount = (int) state.getAmenityIds().stream()
                    .filter(propAmenityIds::contains).count();
        }

        // Ward match flag
        boolean wardMatch = state.getResolvedWardCodes() != null
                && prop.getWard() != null
                && state.getResolvedWardCodes().contains(prop.getWard().getCode());

        // Boost flag
        boolean boosted = post.getBoostExpiredAt() != null
                && post.getBoostExpiredAt().isAfter(LocalDateTime.now());

        return new PropertySummaryForChat(
                0,                          // displayIndex — assigned after ranking
                post.getId(),
                cheapest.getPrice(),
                cheapest.getArea(),
                cheapest.getMaxOccupancy(),
                prop.getWard() != null ? prop.getWard().getName() : null,
                prop.getWard() != null && prop.getWard().getProvince() != null
                        ? prop.getWard().getProvince().getName() : null,
                post.getTitle(),
                post.getSlug(),
                amenityNames,
                surroundingNames,
                prop.getCategory() != null ? prop.getCategory().getName() : null,
                prop.getLatitude(),
                prop.getLongitude(),
                boosted,
                amenityMatchCount,
                wardMatch,
                post.getPostExpiredAt()
        );
    }

    // =========================================================================
    // Scoring & ranking
    // =========================================================================

    private Comparator<PropertySummaryForChat> buildComparator(ChatSessionState state) {
        return Comparator
                .comparingDouble((PropertySummaryForChat r) ->
                        -score(r, state.getBudget(), state.getAmenityIds(),
                                state.getCenterLat(), state.getCenterLng(), state.getSearchRadiusKm()))
                .thenComparing(PropertySummaryForChat::price,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PropertySummaryForChat::postId,
                        Comparator.nullsLast(Comparator.naturalOrder()));
    }

    /**
     * Computes a score in [0, 90] for ranking.
     *
     * 30  boost
     * 25  amenity match rate
     * 20  budget proximity (closest to, not cheapest)
     * 10  location (Haversine distance or ward-match)
     *  5  post longevity (days remaining, capped at 30 days)
     */
    private double score(PropertySummaryForChat r, Double budget, List<Long> amenityIds,
                         Double centerLat, Double centerLng, Double searchRadiusKm) {
        double s = 0;

        // 1. Boost
        if (r.boosted()) s += 30;

        // 2. Amenity match rate
        if (amenityIds != null && !amenityIds.isEmpty()) {
            double matchRate = (double) r.amenityMatchCount() / amenityIds.size();
            s += matchRate * 25;
        }

        // 3. Budget proximity — penalise deviation from stated budget
        if (budget != null && r.price() != null && budget > 0) {
            double proximity = 1 - Math.abs(r.price() - budget) / budget;
            s += Math.max(0, proximity) * 20;
        }

        // 4. Location score
        if (centerLat != null && centerLng != null && searchRadiusKm != null
                && r.latitude() != null && r.longitude() != null) {
            double distKm = haversine(centerLat, centerLng, r.latitude(), r.longitude());
            s += Math.max(0, (1 - distKm / searchRadiusKm)) * 10;
        } else if (r.wardMatch()) {
            s += 10;
        }

        // 5. Post longevity
        if (r.postExpiredAt() != null) {
            long daysLeft = ChronoUnit.DAYS.between(LocalDateTime.now(), r.postExpiredAt());
            s += Math.min(Math.max(daysLeft, 0), 30) * (5.0 / 30);
        }

        return s;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Haversine formula — great-circle distance in kilometres.
     */
    private static double haversine(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Returns a new record with displayIndex replaced — records are immutable. */
    private static PropertySummaryForChat withIndex(PropertySummaryForChat s, int index) {
        return new PropertySummaryForChat(
                index,
                s.postId(), s.price(), s.area(), s.maxOccupancy(),
                s.wardName(), s.provinceName(), s.postTitle(), s.postSlug(),
                s.amenityNames(), s.surroundingNames(), s.categoryName(),
                s.latitude(), s.longitude(),
                s.boosted(), s.amenityMatchCount(), s.wardMatch(), s.postExpiredAt()
        );
    }
}
