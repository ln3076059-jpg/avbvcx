package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.dto.chat.*;
import com.pms.propertymanagement.entity.*;
import com.pms.propertymanagement.enums.ChatPhase;
import com.pms.propertymanagement.model.ChatSessionState;
import com.pms.propertymanagement.repository.*;
import com.pms.propertymanagement.service.*;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST endpoint for the AI chat room-recommendation feature.
 *
 * POST /api/chat/message
 *   Request:  { "guestId": "...", "message": "..." }
 *   Response: ChatResponse (JSON)
 *
 * Session state (ChatSessionState) lives in HttpSession.
 * Rate limit: max-requests per window-minutes, tracked per session.
 *
 * Phase flow:
 *   SLOT_FILLING    → Gemini.extractParams → LocationResolver → DB query → SHOWING_RESULTS
 *   SHOWING_RESULTS → Gemini.detectAction  → handle (refine/question/interest/exit/new_search)
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final GeminiService geminiService;
    private final LocationResolverService locationResolverService;
    private final PropertyRecommendationService recommendationService;

    private final AmenityRepository amenityRepository;
    private final SurroundingRepository surroundingRepository;
    private final TargetTenantsRepository targetTenantsRepository;
    private final CategoryRepository categoryRepository;
    private final ProvinceRepository provinceRepository;

    @Value("${chat.rate-limit.max-requests:20}")
    private int rateLimitMaxRequests;

    @Value("${chat.rate-limit.window-minutes:10}")
    private int rateLimitWindowMinutes;

    // =========================================================================
    // Static templates
    // =========================================================================

    private static final String OFF_TOPIC_REPLY =
            "Ạ, mình chỉ hỗ trợ tìm phòng trọ thôi ạ. " +
            "Bạn có thể mô tả nhu cầu như: ngân sách, khu vực muốn ở, " +
            "tiện ích cần có — mình sẽ tìm giúp bạn ngay!";

    private static final Map<String, String> MISSING_FIELD_QUESTION = Map.of(
            "location", "Bạn muốn tìm phòng ở tỉnh/thành nào ạ? (ví dụ: Hồ Chí Minh, Hà Nội)",
            "budget",   "Ngân sách dự kiến của bạn khoảng bao nhiêu một tháng ạ?"
    );

    /** Thresholds controlling REFINING entry */
    private static final int REFINING_SKIP_THRESHOLD = 5;   // ≤5 results → skip REFINING, query directly
    private static final int REFINING_AREA_THRESHOLD = 20;  // >20 → ask area + amenity together

    /** Quick-reply chips — contextual per slot being collected */
    private static final List<String> QUICK_REPLIES_TYPE = List.of(
            "Căn hộ", "Ký túc xá / KTX", "Nhà trọ / Phòng trọ", "Nhà nguyên căn"
    );
    private static final List<String> QUICK_REPLIES_BUDGET = List.of(
            "Dưới 2 triệu/tháng", "2 – 4 triệu/tháng", "4 – 7 triệu/tháng", "7 – 15 triệu/tháng"
    );
    private static final List<String> QUICK_REPLIES_LOCATION = List.of(
            "Hồ Chí Minh", "Hà Nội", "Đà Nẵng"
    );
    /** Shown on welcome / ambiguous (nothing known yet) — property types only, max 5 */
    private static final List<String> QUICK_REPLIES_INITIAL = QUICK_REPLIES_TYPE;
    private static final List<String> QUICK_REPLIES_REFINING_MANY  = List.of("Tìm ngay luôn");
    private static final List<String> QUICK_REPLIES_REFINING_FEW   = List.of("Không cần, tìm ngay");
    private static final List<String> QUICK_REPLIES_REFINING_CONFIRM = List.of("Tìm ngay", "Sửa lại");

    private static final String SESSION_KEY = "chatState";

    // =========================================================================
    // Main endpoint
    // =========================================================================

    @PostMapping("/message")
    public ResponseEntity<ChatResponse> handleMessage(
            @RequestBody ChatMessageRequest request,
            HttpSession session) {

        String message = request.message() == null ? "" : request.message().trim();
        String guestId = request.guestId() == null ? "anon" : request.guestId().trim();

        if (message.isEmpty()) {
            return ResponseEntity.ok(ChatResponse.ofMessage("Bạn vừa gửi tin nhắn trống ạ, bạn cần tìm phòng như thế nào?"));
        }

        // Get or create session state
        ChatSessionState state = getOrCreateState(session, guestId);

        // Rate limit check
        if (isRateLimited(state)) {
            log.warn("Rate limit hit for guestId={}", guestId);
            return ResponseEntity.ok(ChatResponse.ofMessage(
                    "Bạn đã gửi quá nhiều tin nhắn. Vui lòng thử lại sau " + rateLimitWindowMinutes + " phút ạ."));
        }
        incrementRequestCount(state);

        ChatResponse response;
        try {
            if (state.getPhase() == ChatPhase.SLOT_FILLING) {
                response = handleSlotFilling(message, state);
            } else if (state.getPhase() == ChatPhase.REFINING) {
                response = handleRefining(message, state);
            } else {
                response = handleShowingResults(message, state, session);
            }
        } catch (RuntimeException e) {
            log.error("Chat error for guestId={}: {}", guestId, e.getMessage(), e);
            try {
                if (state.getBudget() == null) state.setBudget(5_000_000.0);
                List<PropertySummaryForChat> fallbackResults = recommendationService.findRecommendations(state);
                if (!fallbackResults.isEmpty()) {
                    state.setCachedResults(fallbackResults);
                    state.setPhase(ChatPhase.SHOWING_RESULTS);
                    response = ChatResponse.ofResults(
                            "Mình gợi ý cho bạn một số phòng phù hợp nhất hiện có nhé:", fallbackResults);
                } else {
                    response = ChatResponse.ofMessageWithReplies(
                            "Bạn muốn tìm phòng ở khu vực nào và ngân sách khoảng bao nhiêu một tháng ạ?",
                            QUICK_REPLIES_LOCATION);
                }
            } catch (Exception ignored) {
                response = ChatResponse.ofMessageWithReplies(
                        "Bạn muốn tìm phòng ở khu vực nào và ngân sách khoảng bao nhiêu một tháng ạ?",
                        QUICK_REPLIES_LOCATION);
            }
        }

        session.setAttribute(SESSION_KEY, state);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetChat(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã làm mới cuộc trò chuyện"));
    }

    // =========================================================================
    // Phase 1 — SLOT_FILLING
    // =========================================================================

    private ChatResponse handleSlotFilling(String message, ChatSessionState state) {
        // Load reference data for Gemini prompt (small tables — all fit in prompt)
        List<Amenity> amenities         = amenityRepository.findAll();
        List<Surrounding> surroundings  = surroundingRepository.findAll();
        List<TargetTenant> targetTenants = targetTenantsRepository.findAll();
        List<Category> categories       = categoryRepository.findAll();
        List<Province> provinces        = provinceRepository.findAll();

        GeminiExtractResult geminiResult = geminiService.extractParams(
                message, state, amenities, surroundings, targetTenants, categories, provinces);

        state.addTurn("user", message);

        String intent = geminiResult.getIntent();

        // --- Off-topic ---
        if ("off_topic".equals(intent)) {
            state.addTurn("model", OFF_TOPIC_REPLY);
            return ChatResponse.ofMessage(OFF_TOPIC_REPLY);
        }

        // --- Ambiguous OR find_room: always merge non-null extracted fields.
        // For ambiguous, only the unambiguous parts (e.g. budget) are non-null in extracted;
        // the unclear location fields remain null so mergeExtracted leaves them unchanged.
        // This prevents losing "budget=5tr" on subsequent turns when the user later clarifies location.
        mergeExtracted(state, geminiResult.getExtracted());

        // --- Ambiguous: ask clarification (Gemini already wrote it), stay in SLOT_FILLING ---
        if ("ambiguous".equals(intent)) {
            String clarification = geminiResult.getClarificationNeeded() != null
                    ? geminiResult.getClarificationNeeded()
                    : "Bạn có thể mô tả rõ hơn nhu cầu của mình không ạ?";
            state.addTurn("model", clarification);
            return ChatResponse.ofMessageWithReplies(clarification, pickContextualChips(state));
        }

        // --- Validate province code ---
        if (state.getProvinceCode() != null) {
            Set<String> validCodes = provinces.stream()
                    .map(Province::getCode).collect(Collectors.toSet());
            if (!validCodes.contains(state.getProvinceCode())) {
                log.warn("Invalid provinceCode '{}' from Gemini — clearing", state.getProvinceCode());
                state.setProvinceCode(null);
            }
        }

        // --- Still missing required fields → ask (template, no extra Gemini call) ---
        if (!state.hasRequiredFields()) {
            List<String> missing = buildMissingList(state);
            String question = pickFirstQuestion(missing);
            if (question == null) question = "Bạn có thể cho mình biết thêm về nhu cầu của bạn không ạ?";
            state.addTurn("model", question);
            return ChatResponse.ofMessageWithReplies(question, pickChipsForMissing(missing));
        }

        // --- Required fields complete: resolve location then decide REFINING or query ---
        String provinceName = resolveProvinceName(state.getProvinceCode(), provinces);
        locationResolverService.resolve(state, provinceName);

        long countEstimate = recommendationService.countProvinceResults(state);
        log.debug("Province count estimate: {} (province={}, budget={})",
                countEstimate, state.getProvinceCode(), state.getBudget());

        if (countEstimate <= REFINING_SKIP_THRESHOLD) {
            // Few results → query directly without asking more
            return executeQueryAndReturn(state, provinceName);
        }

        // Many results → enter REFINING to collect optional filters
        state.setPhase(ChatPhase.REFINING);
        state.setRefiningConfirmStep(false);

        String refiningQuestion;
        List<String> chips;
        if (countEstimate > REFINING_AREA_THRESHOLD) {
            refiningQuestion = String.format(
                    "Mình tìm thấy khoảng %d phòng ở %s trong ngân sách của bạn ạ. " +
                    "Để tìm chính xác hơn, bạn muốn ở khu vực/phường cụ thể nào không? " +
                    "Và bạn cần tiện ích gì (máy lạnh, wifi, giường nệm...) " +
                    "hoặc muốn gần chợ, công viên, trường học không ạ?",
                    countEstimate, provinceName);
            chips = QUICK_REPLIES_REFINING_MANY;
        } else {
            refiningQuestion = String.format(
                    "Mình tìm thấy %d phòng ở %s ạ. Bạn có muốn thêm tiện ích " +
                    "(máy lạnh, wifi...) hoặc muốn gần chợ, công viên, trường học không ạ?",
                    countEstimate, provinceName);
            chips = QUICK_REPLIES_REFINING_FEW;
        }
        state.addTurn("model", refiningQuestion);
        return ChatResponse.ofMessageWithReplies(refiningQuestion, chips);
    }

    // =========================================================================
    // Phase REFINING
    // =========================================================================

    private ChatResponse handleRefining(String message, ChatSessionState state) {
        if (state.isRefiningConfirmStep()) {
            return handleRefiningConfirm(message, state);
        } else {
            return handleRefiningCollect(message, state);
        }
    }

    private ChatResponse handleRefiningCollect(String message, ChatSessionState state) {
        state.addTurn("user", message);

        List<Amenity>     amenities    = amenityRepository.findAll();
        List<Surrounding> surroundings = surroundingRepository.findAll();
        List<Category>    categories   = categoryRepository.findAll();
        List<Province>    provinces    = provinceRepository.findAll();

        if (!isRefiningSkip(message)) {
            // Use Gemini to extract any optional fields the user mentioned
            List<TargetTenant> targets = targetTenantsRepository.findAll();
            GeminiExtractResult geminiResult = geminiService.extractParams(
                    message, state, amenities, surroundings, targets, categories, provinces);
            mergeExtracted(state, geminiResult.getExtracted());
        }

        // Build summary and move to confirm step
        String provinceName = resolveProvinceName(state.getProvinceCode(), provinces);
        if (state.getPlaceName() != null || state.getLocalKeyword() != null) {
            locationResolverService.resolve(state, provinceName);
        }

        String summary = buildConfirmSummary(state, provinceName, amenities, surroundings, categories);
        state.setRefiningConfirmStep(true);
        state.addTurn("model", summary);
        return ChatResponse.ofMessageWithReplies(summary, QUICK_REPLIES_REFINING_CONFIRM);
    }

    private ChatResponse handleRefiningConfirm(String message, ChatSessionState state) {
        state.addTurn("user", message);
        String lower = message.toLowerCase().trim();
        boolean wantsToEdit = lower.contains("sửa") || lower.contains("đổi") || lower.equals("sửa lại");

        if (wantsToEdit) {
            // Go back to collecting
            state.setRefiningConfirmStep(false);
            String q = "Bạn muốn thay đổi điều gì? Hãy nói thêm về khu vực, tiện ích hoặc ngân sách ạ.";
            state.addTurn("model", q);
            return ChatResponse.ofMessageWithReplies(q, QUICK_REPLIES_REFINING_FEW);
        }

        // Any other message (including "Tìm ngay") → execute the real query
        List<Province> provinces   = provinceRepository.findAll();
        String provinceName = resolveProvinceName(state.getProvinceCode(), provinces);
        return executeQueryAndReturn(state, provinceName);
    }

    /** Execute the real DB query and transition to SHOWING_RESULTS */
    private ChatResponse executeQueryAndReturn(ChatSessionState state, String provinceName) {
        List<PropertySummaryForChat> results = recommendationService.findRecommendations(state);
        state.setCachedResults(results);
        state.setPhase(ChatPhase.SHOWING_RESULTS);

        if (results.isEmpty()) {
            String noResultMsg = "Hiện tại chưa tìm thấy phòng phù hợp với yêu cầu của bạn ạ. " +
                    "Bạn có muốn điều chỉnh ngân sách hoặc khu vực không?";
            state.addTurn("model", noResultMsg);
            return ChatResponse.ofMessage(noResultMsg);
        }

        String resultMsg = String.format("Mình tìm được %d phòng phù hợp tại %s ạ. Bạn xem thử nhé!",
                results.size(), provinceName);
        state.addTurn("model", resultMsg);
        return ChatResponse.ofResults(resultMsg, results);
    }

    /** Returns true when user message is a skip signal (wants results without adding filters) */
    private boolean isRefiningSkip(String message) {
        String lower = message.toLowerCase().trim();
        return lower.contains("tìm ngay") || lower.contains("không cần") || lower.equals("ok");
    }

    /** Build a human-readable summary for the REFINING confirm step */
    private String buildConfirmSummary(ChatSessionState state, String provinceName,
                                        List<Amenity> amenities, List<Surrounding> surroundings,
                                        List<Category> categories) {
        StringBuilder sb = new StringBuilder("Mình sẽ tìm ");

        if (state.getCategoryId() != null) {
            categories.stream().filter(c -> c.getId().equals(state.getCategoryId())).findFirst()
                    .ifPresentOrElse(c -> sb.append(c.getName().toLowerCase()), () -> sb.append("phòng"));
        } else {
            sb.append("phòng");
        }

        sb.append(" tại ").append(provinceName);
        if (state.getLocalKeyword() != null) sb.append(", khu ").append(state.getLocalKeyword());
        else if (state.getPlaceName() != null) sb.append(", gần ").append(state.getPlaceName());
        sb.append(", ngân sách ").append(formatBudget(state.getBudget()));

        if (state.getAmenityIds() != null && !state.getAmenityIds().isEmpty()) {
            String names = amenities.stream()
                    .filter(a -> state.getAmenityIds().contains(a.getId()))
                    .map(Amenity::getName)
                    .collect(Collectors.joining(", "));
            if (!names.isEmpty()) sb.append(", có: ").append(names);
        }
        if (state.getSurroundingIds() != null && !state.getSurroundingIds().isEmpty()) {
            String names = surroundings.stream()
                    .filter(s -> state.getSurroundingIds().contains(s.getId()))
                    .map(Surrounding::getName)
                    .collect(Collectors.joining(", "));
            if (!names.isEmpty()) sb.append(", gần: ").append(names);
        }
        sb.append(". Tìm ngay nhé?");
        return sb.toString();
    }

    private String formatBudget(Double budget) {
        if (budget == null) return "chưa rõ";
        long millions = Math.round(budget / 1_000_000.0);
        if (millions >= 1) return millions + " triệu/tháng";
        return Math.round(budget / 1_000.0) + "k/tháng";
    }

    // =========================================================================
    // Phase 2 — SHOWING_RESULTS
    // =========================================================================

    private ChatResponse handleShowingResults(String message, ChatSessionState state, HttpSession session) {
        List<Amenity> allAmenities = amenityRepository.findAll();
        List<Province> provinces   = provinceRepository.findAll();
        String provinceName = resolveProvinceName(state.getProvinceCode(), provinces);

        GeminiActionResult actionResult = geminiService.detectAction(
                message, state, state.getCachedResults(), provinceName, allAmenities);

        state.addTurn("user", message);
        // Null-guard: if Gemini returns malformed JSON with missing "action", treat as question
        String action = actionResult.getAction() != null ? actionResult.getAction() : "question";
        String reply  = actionResult.getReply() != null ? actionResult.getReply() : "";

        return switch (action) {
            case "refine"      -> handleRefine(actionResult, state, reply, provinceName);
            case "new_search"  -> handleNewSearch(message, state);
            case "question"    -> {
                state.addTurn("model", reply);
                yield ChatResponse.ofMessage(reply);
            }
            case "interest"    -> handleInterest(actionResult, state, reply);
            case "exit"        -> {
                session.removeAttribute(SESSION_KEY);
                yield ChatResponse.ofExit(reply.isEmpty()
                        ? "Cảm ơn bạn đã sử dụng dịch vụ. Chúc bạn tìm được phòng ưng ý ạ!" : reply);
            }
            default -> {
                // Unknown action — treat as question
                state.addTurn("model", reply);
                yield ChatResponse.ofMessage(reply);
            }
        };
    }

    // =========================================================================
    // Action handlers
    // =========================================================================

    private ChatResponse handleRefine(GeminiActionResult actionResult,
                                       ChatSessionState state, String reply, String provinceName) {
        GeminiActionResult.Refinement ref = actionResult.getRefinement();
        if (ref != null) {
            if (ref.getNewBudget() != null) state.setBudget(ref.getNewBudget());
            if (ref.getNewCategoryId() != null) state.setCategoryId(ref.getNewCategoryId());

            // Merge amenity IDs: add new (validated against DB), remove removed
            List<Long> current = state.getAmenityIds() != null
                    ? new ArrayList<>(state.getAmenityIds()) : new ArrayList<>();
            if (ref.getAddAmenityIds() != null && !ref.getAddAmenityIds().isEmpty()) {
                // Design §10: validate IDs from Gemini — silently drop non-existent ones
                Set<Long> validIds = amenityRepository.findAllById(ref.getAddAmenityIds())
                        .stream().map(a -> a.getId()).collect(Collectors.toSet());
                ref.getAddAmenityIds().stream()
                        .filter(validIds::contains)
                        .filter(id -> !current.contains(id))
                        .forEach(current::add);
            }
            if (ref.getRemoveAmenityIds() != null) current.removeAll(ref.getRemoveAmenityIds());
            state.setAmenityIds(current.isEmpty() ? null : current);
        }

        // Validate updated categoryId
        if (state.getCategoryId() != null) {
            boolean valid = categoryRepository.existsById(state.getCategoryId());
            if (!valid) {
                log.warn("Invalid categoryId {} from Gemini refine — clearing", state.getCategoryId());
                state.setCategoryId(null);
            }
        }

        List<PropertySummaryForChat> results = recommendationService.findRecommendations(state);
        state.setCachedResults(results);

        String msg = results.isEmpty()
                ? "Không tìm thấy phòng phù hợp sau khi điều chỉnh ạ. Bạn có muốn thử tiêu chí khác không?"
                : reply.isEmpty()
                    ? String.format("Mình tìm được %d phòng sau khi cập nhật yêu cầu ạ.", results.size())
                    : reply;

        state.addTurn("model", msg);
        return results.isEmpty() ? ChatResponse.ofMessage(msg) : ChatResponse.ofResults(msg, results);
    }

    private ChatResponse handleNewSearch(String originalMessage, ChatSessionState state) {
        Double previousBudget = state.getBudget();
        Long previousCategoryId = state.getCategoryId();
        // Reset all mutable fields in-place so the outer handleMessage's
        // session.setAttribute(SESSION_KEY, state) saves the fresh state correctly.
        state.reset();
        if (previousBudget != null) {
            state.setBudget(previousBudget);
        }
        if (previousCategoryId != null) {
            state.setCategoryId(previousCategoryId);
        }
        // Design §9 kịch bản 5b: re-process the SAME message through Phase 1 immediately
        // so the user doesn't need to repeat location/budget they already typed.
        return handleSlotFilling(originalMessage, state);
    }

    private ChatResponse handleInterest(GeminiActionResult actionResult,
                                         ChatSessionState state, String reply) {
        // Use server-side index into cachedResults — NEVER trust Gemini's ID directly
        Integer idx = actionResult.getTargetPropertyIndex();
        List<PropertySummaryForChat> cached = state.getCachedResults();

        if (idx != null && idx >= 1 && idx <= cached.size()) {
            String slug = cached.get(idx - 1).postSlug();
            String msg = reply.isEmpty()
                    ? "Bạn có thể xem chi tiết phòng này tại đây ạ!"
                    : reply;
            state.addTurn("model", msg);
            return ChatResponse.ofInterest(msg, slug);
        }

        // Index out of bounds — graceful fallback
        String fallback = "Mình không xác định được phòng bạn đề cập. " +
                "Bạn có thể cho mình biết số thứ tự phòng trong danh sách không ạ?";
        state.addTurn("model", fallback);
        return ChatResponse.ofMessage(fallback);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Merges non-null fields from Gemini's Extracted into the session state.
     * Existing values are NOT overwritten by null — allows incremental collection across turns.
     *
     * When locationType changes, the conflicting location field is cleared so that
     * formatKnownLocation() and LocationResolverService always see consistent state:
     *   "ward"           → clear placeName  (ward uses localKeyword, not placeName)
     *   "landmark"/"street" → clear localKeyword (these use placeName, not localKeyword)
     *   "province_only"  → clear both
     */
    private void mergeExtracted(ChatSessionState state,
                                 GeminiExtractResult.Extracted extracted) {
        if (extracted == null) return;

        if (extracted.getBudget() != null)       state.setBudget(extracted.getBudget());
        if (extracted.getProvinceCode() != null) state.setProvinceCode(extracted.getProvinceCode());

        // When locationType changes, clear the now-irrelevant sibling field
        if (extracted.getLocationType() != null) {
            state.setLocationType(extracted.getLocationType());
            switch (extracted.getLocationType()) {
                case "ward"     -> state.setPlaceName(null);       // ward uses localKeyword
                case "landmark",
                     "street"  -> state.setLocalKeyword(null);    // these use placeName
                case "province_only" -> {
                    state.setPlaceName(null);
                    state.setLocalKeyword(null);
                }
            }
        }

        if (extracted.getPlaceName() != null)    state.setPlaceName(extracted.getPlaceName());
        if (extracted.getLocalKeyword() != null) state.setLocalKeyword(extracted.getLocalKeyword());
        if (extracted.getCategoryId() != null)   state.setCategoryId(extracted.getCategoryId());
        if (extracted.getTargetTenantId() != null) state.setTargetTenantId(extracted.getTargetTenantId());

        // Validate and merge amenityIds
        if (extracted.getAmenityIds() != null && !extracted.getAmenityIds().isEmpty()) {
            Set<Long> validIds = amenityRepository.findAllById(extracted.getAmenityIds())
                    .stream().map(Amenity::getId).collect(Collectors.toSet());
            List<Long> merged = state.getAmenityIds() != null ? new ArrayList<>(state.getAmenityIds()) : new ArrayList<>();
            extracted.getAmenityIds().stream()
                    .filter(validIds::contains)
                    .filter(id -> !merged.contains(id))
                    .forEach(merged::add);
            state.setAmenityIds(merged.isEmpty() ? null : merged);
        }

        // Merge surroundingIds (constrained extraction — no DB validation needed)
        if (extracted.getSurroundingIds() != null && !extracted.getSurroundingIds().isEmpty()) {
            List<Long> merged = state.getSurroundingIds() != null
                    ? new ArrayList<>(state.getSurroundingIds()) : new ArrayList<>();
            extracted.getSurroundingIds().stream()
                    .filter(id -> !merged.contains(id))
                    .forEach(merged::add);
            state.setSurroundingIds(merged.isEmpty() ? null : merged);
        }
    }

    private List<String> buildMissingList(ChatSessionState state) {
        List<String> missing = new ArrayList<>();
        if (state.getProvinceCode() == null) missing.add("location");
        if (state.getBudget() == null)       missing.add("budget");
        return missing;
    }

    private String pickFirstQuestion(List<String> missing) {
        if (missing.contains("location")) return MISSING_FIELD_QUESTION.get("location");
        if (missing.contains("budget"))   return MISSING_FIELD_QUESTION.get("budget");
        return null;
    }

    /** Chips matching the first field that still needs to be collected. */
    private List<String> pickChipsForMissing(List<String> missing) {
        if (missing.contains("location")) return QUICK_REPLIES_LOCATION;
        if (missing.contains("budget"))   return QUICK_REPLIES_BUDGET;
        return null;
    }

    /** Chips for ambiguous intent — show what we still don't know. */
    private List<String> pickContextualChips(ChatSessionState state) {
        boolean needLocation = state.getProvinceCode() == null;
        boolean needBudget   = state.getBudget() == null;
        if (needLocation && needBudget) return QUICK_REPLIES_INITIAL;   // nothing known
        if (needLocation)               return QUICK_REPLIES_LOCATION;
        if (needBudget)                 return QUICK_REPLIES_BUDGET;
        return null; // all slots filled, ambiguous on something else
    }

    private String resolveProvinceName(String provinceCode, List<Province> provinces) {
        return provinces.stream()
                .filter(p -> p.getCode().equals(provinceCode))
                .map(Province::getName)
                .findFirst()
                .orElse(provinceCode);
    }

    private ChatSessionState getOrCreateState(HttpSession session, String guestId) {
        Object existing = session.getAttribute(SESSION_KEY);
        if (existing instanceof ChatSessionState s) return s;
        ChatSessionState fresh = new ChatSessionState(guestId);
        session.setAttribute(SESSION_KEY, fresh);
        return fresh;
    }

    // =========================================================================
    // Rate limiting (sliding-window per session)
    // =========================================================================

    private boolean isRateLimited(ChatSessionState state) {
        long now = System.currentTimeMillis();
        long windowMs = (long) rateLimitWindowMinutes * 60 * 1000;

        // Reset window if expired
        if (now - state.getRateLimitWindowStart() > windowMs) {
            state.setRateLimitWindowStart(now);
            state.setRequestCount(0);
        }
        return state.getRequestCount() >= rateLimitMaxRequests;
    }

    private void incrementRequestCount(ChatSessionState state) {
        state.setRequestCount(state.getRequestCount() + 1);
    }

    // =========================================================================
    // Request DTO
    // =========================================================================

    public record ChatMessageRequest(String guestId, String message) {}
}
