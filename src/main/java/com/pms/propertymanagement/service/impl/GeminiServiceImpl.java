package com.pms.propertymanagement.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pms.propertymanagement.config.GeminiProperties;
import com.pms.propertymanagement.dto.chat.GeminiActionResult;
import com.pms.propertymanagement.dto.chat.GeminiExtractResult;
import com.pms.propertymanagement.dto.chat.PropertySummaryForChat;
import com.pms.propertymanagement.entity.*;
import com.pms.propertymanagement.model.ChatSessionState;
import com.pms.propertymanagement.service.GeminiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.text.Normalizer;
import java.time.Duration;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calls Google Gemini 1.5 Flash API to extract search params (Phase 1)
 * and detect user actions (Phase 2), with a deterministic local rule-based fallback.
 */
@Service
@Slf4j
public class GeminiServiceImpl implements GeminiService {

    private static final Pattern RANGE_BUDGET_PATTERN = Pattern.compile(
            "(\\d+(?:[\\.,]\\d+)?)\\s*(?:-|den|toi)\\s*(\\d+(?:[\\.,]\\d+)?)\\s*(tr|trieu|m|k|nghin|ngan)?"
    );
    private static final Pattern SINGLE_BUDGET_PATTERN = Pattern.compile(
            "(\\d+(?:[\\.,]\\d+)?)\\s*(tr|trieu|m|k|nghin|ngan|vnd|dong)"
    );
    private static final List<String> EXIT_KEYWORDS = List.of(
            "thoat", "tam biet", "cam on", "khong can nua", "dung lai", "stop"
    );
    private static final List<String> NEW_SEARCH_KEYWORDS = List.of(
            "doi khu vuc", "doi sang", "chuyen sang", "tim lai", "tim o", "khu vuc khac", "noi khac"
    );

    private final GeminiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;

    public GeminiServiceImpl(GeminiProperties props) {
        this.props = props;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getTimeout().getConnect()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(props.getTimeout().getRead()));
        this.restClient = RestClient.builder()
                .baseUrl(props.getApi().getUrl())
                .requestFactory(requestFactory)
                .build();
    }

    // =========================================================================
    // Public API
    // =========================================================================

    @Override
    public GeminiExtractResult extractParams(String userMessage, ChatSessionState state,
                                              List<Amenity> amenities, List<Surrounding> surroundings,
                                              List<TargetTenant> targetTenants, List<Category> categories,
                                              List<Province> provinces) {
        try {
            String prompt = buildPhase1Prompt(userMessage, state,
                    toIdNameJson(categories, Category::getId, Category::getName),
                    toIdNameJson(amenities, Amenity::getId, Amenity::getName),
                    toIdNameJson(surroundings, Surrounding::getId, Surrounding::getName),
                    toIdNameJson(targetTenants, TargetTenant::getId, TargetTenant::getName),
                    toCodeNameJson(provinces));

            String raw = callGemini(prompt);
            GeminiExtractResult result = objectMapper.readValue(raw, GeminiExtractResult.class);
            log.debug("Gemini extractParams intent={}, missing={}", result.getIntent(), result.getMissingRequired());
            return result;
        } catch (Exception e) {
            log.warn("Gemini extractParams failed, falling back to rule-based parser: {}", e.getMessage());
            return fallbackExtractParams(userMessage, state, amenities, categories, provinces);
        }
    }

    @Override
    public GeminiActionResult detectAction(String userMessage, ChatSessionState state,
                                            List<PropertySummaryForChat> cachedResults,
                                            String provinceName, List<Amenity> allAmenities) {
        try {
            String prompt = buildPhase2Prompt(userMessage, state, cachedResults, provinceName, allAmenities);

            String raw = callGemini(prompt);
            GeminiActionResult result = objectMapper.readValue(raw, GeminiActionResult.class);
            log.debug("Gemini detectAction action={}", result.getAction());
            return result;
        } catch (Exception e) {
            log.warn("Gemini detectAction failed, falling back to rule-based parser: {}", e.getMessage());
            return fallbackDetectAction(userMessage, state, cachedResults, allAmenities);
        }
    }

    // =========================================================================
    // Gemini API call
    // =========================================================================

    private String callGemini(String prompt) {
        if (props.getApi().getKey() == null || props.getApi().getKey().isBlank()) {
            throw new RuntimeException("Gemini API key is missing");
        }

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                        "temperature", 0.1,
                        "maxOutputTokens", 1024,
                        "responseMimeType", "application/json"
                )
        );

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("key", props.getApi().getKey())
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            log.error("Gemini API call failed: {}", e.getMessage());
            throw new RuntimeException("Gemini API unreachable: " + e.getMessage(), e);
        }

        if (responseBody == null) {
            throw new RuntimeException("Gemini API returned null body");
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String text = root
                    .path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text").asText();

            text = text.trim();
            if (text.startsWith("```")) {
                text = text.replaceAll("(?s)^```[a-z]*\\n?", "").replaceAll("\\n?```$", "").trim();
            }
            return text;
        } catch (Exception e) {
            log.error("Failed to extract text from Gemini response: {}", responseBody, e);
            throw new RuntimeException("Unexpected Gemini response structure", e);
        }
    }

    // =========================================================================
    // Prompt builders
    // =========================================================================

    private String buildPhase1Prompt(String userMessage, ChatSessionState state,
                                      String categoriesJson, String amenitiesJson,
                                      String surroundingsJson, String targetTenantsJson,
                                      String provincesJson) {
        return """
                Bạn là chuyên viên tư vấn tìm phòng trọ tại Việt Nam.
                
                === DỮ LIỆU HỆ THỐNG ===
                LOẠI BĐS:   %s
                TIỆN ÍCH:   %s
                XUNG QUANH: %s
                ĐỐI TƯỢNG:  %s
                TỈNH/THÀNH: %s
                
                === THÔNG TIN ĐÃ BIẾT ===
                Ngân sách:      %s
                Tỉnh/Thành:     %s
                Khu vực cụ thể: %s
                Loại BĐS:       %s
                Tiện ích:       %s
                
                === HỘI THOẠI GẦN ĐÂY (4 turn) ===
                %s
                
                === TIN NHẮN MỚI ===
                User: %s
                
                Phân tích và trả về JSON hợp lệ duy nhất, không markdown, không giải thích:
                {
                  "intent": "find_room | off_topic | ambiguous",
                  "extracted": {
                    "budget": <số VNĐ hoặc null>,
                    "provinceCode": "<code từ danh sách TỈNH/THÀNH ở trên, hoặc null>",
                    "locationType": "ward | landmark | street | province_only | null",
                    "localKeyword": "<tên phường có dấu — chỉ khi locationType=ward, null nếu không>",
                    "placeName": "<tên địa điểm đầy đủ — chỉ khi locationType=landmark/street, null nếu không>",
                    "categoryId": <id hoặc null>,
                    "amenityIds": [<id>],
                    "surroundingIds": [<id>],
                    "targetTenantId": <id hoặc null>
                  },
                  "missingRequired": ["budget" | "location"],
                  "clarificationNeeded": "<câu hỏi nếu intent=ambiguous, null nếu không>"
                }
                
                Quy tắc locationType:
                - "ward": user nhắc đến tên phường/xã/khu vực hành chính → localKeyword = tên có dấu (không cần tiền tố "Phường"), placeName = null
                - "landmark": địa điểm cụ thể (trường ĐH, bệnh viện, TTTM, KCN) → placeName = tên đầy đủ để gọi Geocoding API, localKeyword = null
                - "street": tên đường hoặc địa chỉ cụ thể → placeName = tên đường (thêm khu vực nếu có), localKeyword = null
                - "province_only": user chỉ nói tỉnh/thành, không nhắc khu vực → cả hai null
                - provinceCode phải lấy từ danh sách TỈNH/THÀNH ở trên, không tự bịa
                - Việt Nam hiện chỉ có 2 cấp: Tỉnh/Thành phố → Phường/Xã (không còn quận/huyện)
                """.formatted(
                categoriesJson, amenitiesJson, surroundingsJson, targetTenantsJson, provincesJson,
                formatBudget(state.getBudget()),
                state.getProvinceCode() != null ? state.getProvinceCode() : "chưa biết",
                formatKnownLocation(state),
                state.getCategoryId() != null ? state.getCategoryId().toString() : "chưa biết",
                state.getAmenityIds() != null ? state.getAmenityIds().toString() : "chưa biết",
                formatHistory(state.getHistoryAsList()),
                userMessage
        );
    }

    private String buildPhase2Prompt(String userMessage, ChatSessionState state,
                                      List<PropertySummaryForChat> cachedResults,
                                      String provinceName, List<Amenity> allAmenities) {
        String amenityNames = resolveAmenityNames(state.getAmenityIds(), allAmenities);
        String location = formatKnownLocation(state);
        String resultsJson = buildCachedResultsJson(cachedResults);
        String amenityListJson = toIdNameJson(allAmenities, Amenity::getId, Amenity::getName);

        return """
                Bạn là chuyên viên tư vấn tìm phòng trọ.
                
                === THÔNG TIN TÌM KIẾM HIỆN TẠI ===
                Ngân sách: %s | Tỉnh/Thành: %s | Khu vực: %s | Tiện ích: %s
                
                === KẾT QUẢ HIỆN TẠI ===
                %s
                
                === DANH SÁCH TIỆN ÍCH HỢP LỆ (chỉ dùng id số nguyên từ danh sách này) ===
                %s
                
                === HỘI THOẠI GẦN ĐÂY ===
                %s
                
                User: %s
                
                Trả về JSON hợp lệ duy nhất, không markdown, không giải thích:
                {
                  "action": "refine | new_search | question | interest | exit",
                  "refinement": {
                    "newBudget": null,
                    "addAmenityIds": [],
                    "removeAmenityIds": [],
                    "newCategoryId": null
                  },
                  "targetPropertyIndex": <số 1-based nếu action=interest, null nếu không>,
                  "reply": "<câu trả lời tự nhiên tiếng Việt>"
                }
                
                Quy tắc:
                - refine: user điều chỉnh filter (budget/amenity/category) NHƯNG KHÔNG đổi tỉnh/thành/khu vực
                  Ví dụ refine: "thêm tiện ích", "tìm thêm tiện ích", "thêm máy lạnh", "bớt ngân sách", "tìm loại khác"
                - new_search: user đổi hẳn tỉnh/thành hoặc khu vực địa lý sang một nơi KHÁC hoàn toàn
                  Ví dụ new_search: "tìm ở Hà Nội đi", "đổi sang Đà Nẵng", "thử quận 1 xem"
                  KHÔNG phải new_search nếu user chỉ nói "tìm thêm tiện ích" hay "thêm yêu cầu" mà không đổi địa điểm
                - question: trả lời dựa hoàn toàn trên KẾT QUẢ HIỆN TẠI, không query DB thêm
                - interest: targetPropertyIndex = số thứ tự trong danh sách (1, 2, 3...)
                - exit: user không muốn tìm nữa
                - addAmenityIds / removeAmenityIds: PHẢI là mảng số nguyên (Long) lấy từ "id" trong DANH SÁCH TIỆN ÍCH. Tuyệt đối không dùng tên chuỗi.
                - newCategoryId: PHẢI là số nguyên (Long) hoặc null.
                """.formatted(
                formatBudget(state.getBudget()), provinceName, location, amenityNames,
                resultsJson,
                amenityListJson,
                formatHistory(state.getHistoryAsList()),
                userMessage
        );
    }

    // =========================================================================
    // Rule-based fallback
    // =========================================================================

    private GeminiExtractResult fallbackExtractParams(
            String userMessage,
            ChatSessionState state,
            List<Amenity> amenities,
            List<Category> categories,
            List<Province> provinces
    ) {
        String normalizedMessage = normalizeText(userMessage);

        GeminiExtractResult result = new GeminiExtractResult();
        GeminiExtractResult.Extracted extracted = new GeminiExtractResult.Extracted();
        result.setExtracted(extracted);

        Double budget = parseBudget(userMessage);
        if (budget != null) {
            extracted.setBudget(budget);
        }

        Long categoryId = detectCategoryId(userMessage, categories);
        if (categoryId != null) {
            extracted.setCategoryId(categoryId);
        }

        String provinceCode = detectProvinceCode(userMessage, provinces);
        if (provinceCode != null) {
            extracted.setProvinceCode(provinceCode);
            extracted.setLocationType("province_only");
        }

        List<Long> amenityIds = detectAmenityIds(userMessage, amenities);
        if (!amenityIds.isEmpty()) {
            extracted.setAmenityIds(amenityIds);
        }

        boolean hasBudget = state.getBudget() != null || extracted.getBudget() != null;
        boolean hasLocation = state.getProvinceCode() != null || extracted.getProvinceCode() != null;
        List<String> missing = new ArrayList<>();
        if (!hasBudget) {
            missing.add("budget");
        }
        if (!hasLocation) {
            missing.add("location");
        }
        result.setMissingRequired(missing);

        boolean hasSearchSignal = budget != null
                || categoryId != null
                || provinceCode != null
                || !amenityIds.isEmpty()
                || looksLikeSearchRequest(normalizedMessage);

        if (!hasSearchSignal) {
            result.setIntent("ambiguous");
            result.setClarificationNeeded("Bạn muốn tìm phòng ở khu vực nào và ngân sách khoảng bao nhiêu một tháng ạ?");
            return result;
        }

        result.setIntent("find_room");
        return result;
    }

    private GeminiActionResult fallbackDetectAction(
            String userMessage,
            ChatSessionState state,
            List<PropertySummaryForChat> cachedResults,
            List<Amenity> allAmenities
    ) {
        String normalizedMessage = normalizeText(userMessage);
        GeminiActionResult result = new GeminiActionResult();

        if (containsAny(normalizedMessage, EXIT_KEYWORDS)) {
            result.setAction("exit");
            result.setReply("Cảm ơn bạn đã sử dụng dịch vụ tư vấn tìm phòng. Khi nào cần tìm thêm, cứ nhắn mình nhé!");
            return result;
        }

        Integer interestIndex = detectInterestIndex(normalizedMessage, cachedResults.size());
        if (interestIndex != null) {
            result.setAction("interest");
            result.setTargetPropertyIndex(interestIndex);
            result.setReply("Mình đã đánh dấu đúng phòng bạn chọn rồi. Bạn bấm \"Xem chi tiết\" để xem thêm nhé!");
            return result;
        }

        if (containsAny(normalizedMessage, NEW_SEARCH_KEYWORDS)
                || containsAny(normalizedMessage, List.of(
                        "ha noi", "hn", "ho chi minh", "hcm", "sai gon", "da nang", "dn", "binh duong",
                        "cau giay", "dong da", "thanh xuan", "binh thanh", "go vap", "thu duc", "quan 1", "quan 7"
                ))) {
            result.setAction("new_search");
            result.setReply("Mình chuyển sang tìm phòng theo khu vực mới cho bạn nhé.");
            return result;
        }

        GeminiActionResult.Refinement refinement = buildFallbackRefinement(userMessage, state, allAmenities);
        if (hasRefinement(refinement)) {
            result.setAction("refine");
            result.setRefinement(refinement);
            result.setReply("Mình đang lọc lại danh sách theo yêu cầu mới của bạn nhé.");
            return result;
        }

        result.setAction("question");
        result.setReply(
                "Mình đang hiển thị danh sách phòng phù hợp. Nếu bạn muốn đổi khu vực (ví dụ: Hà Nội, Hồ Chí Minh), điều chỉnh ngân sách hoặc chọn số thứ tự phòng thì cứ nhắn mình nhé!"
        );
        return result;
    }

    private GeminiActionResult.Refinement buildFallbackRefinement(
            String userMessage,
            ChatSessionState state,
            List<Amenity> allAmenities
    ) {
        String normalizedMessage = normalizeText(userMessage);
        GeminiActionResult.Refinement refinement = new GeminiActionResult.Refinement();

        Double newBudget = parseBudget(userMessage);
        if (newBudget != null) {
            refinement.setNewBudget(newBudget);
        }

        List<Long> matchedAmenityIds = detectAmenityIds(userMessage, allAmenities);
        if (!matchedAmenityIds.isEmpty()) {
            if (containsAny(normalizedMessage, List.of("khong can", "bo", "loai bo", "xoa"))) {
                refinement.setRemoveAmenityIds(matchedAmenityIds);
            } else {
                refinement.setAddAmenityIds(matchedAmenityIds);
            }
        }

        if (refinement.getNewBudget() == null
                && matchedAmenityIds.isEmpty()
                && containsAny(normalizedMessage, List.of("re hon", "thap hon", "duoi ngan sach"))
                && state.getBudget() != null) {
            refinement.setNewBudget(state.getBudget() * 0.85);
        }

        return refinement;
    }

    private boolean hasRefinement(GeminiActionResult.Refinement refinement) {
        return refinement.getNewBudget() != null
                || refinement.getNewCategoryId() != null
                || (refinement.getAddAmenityIds() != null && !refinement.getAddAmenityIds().isEmpty())
                || (refinement.getRemoveAmenityIds() != null && !refinement.getRemoveAmenityIds().isEmpty());
    }

    private Double parseBudget(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String rawLower = Normalizer.normalize(message, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'd')
                .toLowerCase(Locale.ROOT)
                .replace('–', '-')
                .replace('—', '-')
                .replace("mot", "1");

        if (rawLower.contains("tren 5") || rawLower.contains("tren 6") || rawLower.contains("tren 7") || rawLower.contains("cao cap")) {
            return 35_000_000.0;
        }

        Matcher rangeMatcher = RANGE_BUDGET_PATTERN.matcher(rawLower);
        if (rangeMatcher.find()) {
            double upper = parseNumber(rangeMatcher.group(2));
            String unit = rangeMatcher.group(3);
            Double val = toCurrency(upper, unit != null ? unit : "trieu");
            if (val != null) return val;
        }

        Matcher singleMatcher = SINGLE_BUDGET_PATTERN.matcher(rawLower);
        if (singleMatcher.find()) {
            double amount = parseNumber(singleMatcher.group(1));
            String unit = singleMatcher.group(2);
            Double val = toCurrency(amount, unit);
            if (val != null) return val;
        }

        if (rawLower.contains("gia re") || rawLower.contains("o ghep") || rawLower.contains("sinh vien")) {
            return 2_500_000.0;
        }

        return null;
    }

    private double parseNumber(String raw) {
        return Double.parseDouble(raw.replace(",", "."));
    }

    private Double toCurrency(double amount, String unit) {
        if (unit == null || unit.isBlank()) {
            return null;
        }
        return switch (unit) {
            case "k", "nghin", "ngan" -> amount * 1_000;
            case "tr", "trieu", "m" -> amount * 1_000_000;
            case "vnd", "dong" -> amount;
            default -> null;
        };
    }

    private Long detectCategoryId(String message, List<Category> categories) {
        String normalizedMessage = normalizeText(message);

        for (Category category : categories) {
            String categoryName = normalizeText(category.getName());
            if (normalizedMessage.contains(categoryName)) {
                return category.getId();
            }
        }

        if (normalizedMessage.contains("can ho")) {
            return findCategoryIdByKeyword(categories, List.of("can ho"));
        }
        if (normalizedMessage.contains("ktx") || normalizedMessage.contains("ky tuc xa") || normalizedMessage.contains("o ghep")) {
            return findCategoryIdByKeyword(categories, List.of("ktx", "ky tuc xa", "o ghep"));
        }
        if (normalizedMessage.contains("nha tro") || normalizedMessage.contains("phong tro")) {
            return findCategoryIdByKeyword(categories, List.of("nha tro", "phong tro"));
        }
        if (normalizedMessage.contains("nha nguyen can")) {
            return findCategoryIdByKeyword(categories, List.of("nha nguyen can"));
        }

        return null;
    }

    private Long findCategoryIdByKeyword(List<Category> categories, List<String> keywords) {
        for (Category category : categories) {
            String categoryName = normalizeText(category.getName());
            if (keywords.stream().anyMatch(categoryName::contains)) {
                return category.getId();
            }
        }
        return null;
    }

    private String detectProvinceCode(String message, List<Province> provinces) {
        String normalizedMessage = normalizeText(message);

        for (Province province : provinces.stream()
                .sorted(Comparator.comparingInt((Province province) -> province.getName().length()).reversed())
                .toList()) {
            String normalizedProvinceName = normalizeText(province.getName());
            if (normalizedMessage.contains(normalizedProvinceName)
                    || normalizedMessage.contains(stripProvincePrefix(normalizedProvinceName))) {
                return province.getCode();
            }

            if (normalizedProvinceName.contains("ho chi minh")
                    && containsAny(normalizedMessage, List.of(
                            "hcm", "sai gon", "tphcm", "binh thanh", "go vap", "tan binh",
                            "phu nhuan", "thu duc", "quan 1", "quan 3", "quan 7", "quan 10"
                    ))) {
                return province.getCode();
            }
            if (normalizedProvinceName.contains("ha noi")
                    && containsAny(normalizedMessage, List.of(
                            "hn", "cau giay", "dong da", "thanh xuan", "ba dinh", "hoan kiem",
                            "hai ba trung", "hoang mai", "tu liem", "ha dong", "tay ho", "long bien", "gia lam"
                    ))) {
                return province.getCode();
            }
            if (normalizedProvinceName.contains("da nang")
                    && containsAny(normalizedMessage, List.of("dn", "hai chau", "son tra", "ngu hanh son", "lien chieu"))) {
                return province.getCode();
            }
            if (normalizedProvinceName.contains("binh duong")
                    && containsAny(normalizedMessage, List.of("di an", "thuan an", "thu dau mot"))) {
                return province.getCode();
            }
        }

        return null;
    }

    private String stripProvincePrefix(String normalizedProvinceName) {
        return normalizedProvinceName
                .replaceFirst("^thanh pho ", "")
                .replaceFirst("^tp ", "")
                .replaceFirst("^tinh ", "")
                .trim();
    }

    private List<Long> detectAmenityIds(String message, List<Amenity> amenities) {
        String normalizedMessage = normalizeText(message);
        return amenities.stream()
                .filter(amenity -> matchesAmenity(amenity, normalizedMessage))
                .map(Amenity::getId)
                .distinct()
                .toList();
    }

    private boolean matchesAmenity(Amenity amenity, String normalizedMessage) {
        String amenityName = normalizeText(amenity.getName());
        if (normalizedMessage.contains(amenityName)) {
            return true;
        }

        if (amenityName.contains("dieu hoa") && normalizedMessage.contains("may lanh")) {
            return true;
        }
        if (amenityName.contains("wifi") && normalizedMessage.contains("internet")) {
            return true;
        }
        if (amenityName.contains("ve sinh") && (normalizedMessage.contains("wc") || normalizedMessage.contains("toilet"))) {
            return true;
        }
        if (amenityName.contains("phong tam") && normalizedMessage.contains("nha tam")) {
            return true;
        }
        if (amenityName.contains("giuong") && normalizedMessage.contains("nem")) {
            return true;
        }

        return false;
    }

    private Integer detectInterestIndex(String normalizedMessage, int maxIndex) {
        Matcher matcher = Pattern.compile("\\b(\\d{1,2})\\b").matcher(normalizedMessage);
        while (matcher.find()) {
            int candidate = Integer.parseInt(matcher.group(1));
            if (candidate < 1 || candidate > maxIndex) {
                continue;
            }

            if (normalizedMessage.matches("^\\d{1,2}$")
                    || normalizedMessage.contains("so " + candidate)
                    || normalizedMessage.contains("phong " + candidate)
                    || normalizedMessage.contains("tin " + candidate)
                    || normalizedMessage.contains("can " + candidate)
                    || normalizedMessage.contains("chon " + candidate)
                    || normalizedMessage.contains("lay " + candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean looksLikeSearchRequest(String normalizedMessage) {
        return containsAny(
                normalizedMessage,
                List.of("tim", "thue", "phong", "tro", "can ho", "ktx", "nha tro", "nha nguyen can", "o ghep", "gia re")
        );
    }

    private boolean containsAny(String normalizedMessage, Collection<String> keywords) {
        return keywords.stream().anyMatch(normalizedMessage::contains);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'd')
                .toLowerCase(Locale.ROOT);

        return normalized.replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // =========================================================================
    // Serialization helpers
    // =========================================================================

    private <T> String toIdNameJson(List<T> items,
                                     Function<T, Long> idFn,
                                     Function<T, String> nameFn) {
        List<Map<String, Object>> list = items.stream()
                .map(item -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", idFn.apply(item));
                    map.put("name", nameFn.apply(item));
                    return map;
                })
                .toList();
        return writeJson(list);
    }

    private String toCodeNameJson(List<Province> provinces) {
        List<Map<String, Object>> list = provinces.stream()
                .map(p -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("code", p.getCode());
                    map.put("name", p.getName());
                    return map;
                })
                .toList();
        return writeJson(list);
    }

    private String buildCachedResultsJson(List<PropertySummaryForChat> results) {
        List<Map<String, Object>> list = results.stream()
                .map(r -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("no", r.displayIndex());
                    map.put("title", r.postTitle());
                    map.put("price", r.price());
                    map.put("area", r.area());
                    map.put("ward", r.wardName());
                    map.put("amenities", r.amenityNames());
                    return map;
                })
                .toList();
        return writeJson(list);
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("JSON serialization failed: {}", e.getMessage());
            return "[]";
        }
    }

    // =========================================================================
    // Prompt formatting helpers
    // =========================================================================

    private String formatBudget(Double budget) {
        if (budget == null) return "chưa biết";
        return String.format("%.0f VNĐ/tháng", budget);
    }

    private String formatKnownLocation(ChatSessionState state) {
        if (state.getPlaceName() != null) return state.getPlaceName();
        if (state.getLocalKeyword() != null) return state.getLocalKeyword();
        return "chưa biết";
    }

    private String resolveAmenityNames(List<Long> amenityIds, List<Amenity> allAmenities) {
        if (amenityIds == null || amenityIds.isEmpty()) return "không có";
        Set<Long> idSet = new HashSet<>(amenityIds);
        String names = allAmenities.stream()
                .filter(a -> idSet.contains(a.getId()))
                .map(Amenity::getName)
                .collect(Collectors.joining(", "));
        return names.isEmpty() ? "không có" : names;
    }

    private String formatHistory(List<ChatSessionState.ConversationTurn> history) {
        if (history.isEmpty()) return "(chưa có hội thoại)";
        StringBuilder sb = new StringBuilder();
        for (var turn : history) {
            String role = "model".equals(turn.role()) ? "Bot" : "User";
            sb.append(role).append(": ").append(turn.content()).append("\n");
        }
        return sb.toString().trim();
    }
}
