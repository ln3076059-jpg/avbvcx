package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.dto.request.ContactRequest;
import com.pms.propertymanagement.dto.response.CategoryResponse;
import com.pms.propertymanagement.dto.response.PostPublicResponse;
import com.pms.propertymanagement.entity.*;
import com.pms.propertymanagement.enums.RoomStatus;
import com.pms.propertymanagement.exception.ResourceNotFoundException;
import com.pms.propertymanagement.repository.ContactRepository;
import com.pms.propertymanagement.repository.RoomRepository;
import com.pms.propertymanagement.service.CategoryService;
import com.pms.propertymanagement.service.ContactService;
import com.pms.propertymanagement.service.PostService;
import com.pms.propertymanagement.service.PropertyService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class PublicController {

    private final CategoryService categoryService;
    private final PropertyService propertyService;
    private final PostService postService;
    private final ContactService contactService;
    private final RoomRepository roomRepository;
    private final ContactRepository contactRepository;

    @GetMapping("/")
    public String index(Model model) {
        List<PostPublicResponse> properties = postService.getAllMarketplacePosts()
                .stream().map(PostPublicResponse::from).toList();

        addCommonAttributes(model);
        model.addAttribute("pageTitle", "Tìm phòng trọ, căn hộ mini và nhà nguyên căn");
        model.addAttribute("content", "public/home");
        model.addAttribute("properties", properties);
        model.addAttribute("featuredProperty", properties.isEmpty() ? null : properties.get(0));
        model.addAttribute("latestProperties", properties.stream().limit(6).toList());
        model.addAttribute("postCount", properties.size());
        model.addAttribute(
                "provinceCount",
                properties.stream()
                        .map(PostPublicResponse::getProvinceName)
                        .filter(name -> name != null && !name.isBlank())
                        .distinct()
                        .count()
        );
        model.addAttribute(
                "startingPrice",
                properties.stream()
                        .mapToInt(PostPublicResponse::getPrice)
                        .filter(price -> price > 0)
                        .min()
                        .orElse(0)
        );
        model.addAttribute(
                "featuredLocations",
                properties.stream()
                        .map(p -> p.getWardName() + ", " + p.getProvinceName())
                        .filter(location -> !location.startsWith(","))
                        .distinct()
                        .limit(4)
                        .collect(Collectors.toList())
        );

        return "layout/public-main";
    }

    @GetMapping({"/properties", "/public/properties"})
    public String listAllProperties(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            Model model) {
        addCommonAttributes(model);

        List<PostPublicResponse> properties = postService.getAllMarketplacePosts()
                .stream().map(PostPublicResponse::from).toList();

        if (categoryId != null) {
            properties = properties.stream().filter(p -> categoryId.equals(p.getCategoryId())).toList();
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.toLowerCase().trim();
            properties = properties.stream().filter(p ->
                    (p.getTitle() != null && p.getTitle().toLowerCase().contains(kw)) ||
                    (p.getAddressNumber() != null && p.getAddressNumber().toLowerCase().contains(kw)) ||
                    (p.getWardName() != null && p.getWardName().toLowerCase().contains(kw)) ||
                    (p.getProvinceName() != null && p.getProvinceName().toLowerCase().contains(kw))
            ).toList();
        }

        model.addAttribute("properties", properties);
        model.addAttribute("keyword", keyword);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("allAmenities", propertyService.getAllAmenities());
        model.addAttribute("pageTitle", "Danh sách bất động sản");
        model.addAttribute("content", "public/property-list");
        return "layout/public-main";
    }

    @GetMapping({"/category/{id}", "/public/category/{id}"})
    public String listByCategory(@PathVariable("id") Long categoryId, Model model) {
        List<PostPublicResponse> properties = postService.getMarketplacePostsByCategory(categoryId)
                .stream().map(PostPublicResponse::from).toList();

        model.addAttribute("properties", properties);
        model.addAttribute("currentCategoryId", categoryId);
        model.addAttribute("allAmenities", propertyService.getAllAmenities());
        model.addAttribute("categoryId", categoryId);
        addCommonAttributes(model);
        model.addAttribute("pageTitle", "Danh sách phòng trọ và nhà cho thuê");
        model.addAttribute("content", "public/property-list");
        return "layout/public-main";
    }

    @GetMapping({"/public/property/{slug}", "/properties/{slug}"})
    public String propertyDetail(@PathVariable("slug") String slug, HttpSession session, Model model) {
        Post post = postService.getMarketplacePostBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tin đăng bất động sản phù hợp."));

        postService.incrementView(slug);

        PostPublicResponse p = PostPublicResponse.from(post);
        model.addAttribute("p", p);
        model.addAttribute("post", post);

        Long propertyId = (post.getProperty() != null) ? post.getProperty().getId() : null;
        List<Room> propertyRooms = propertyId != null
                ? roomRepository.findByProperty_Id(propertyId).stream().sorted(Comparator.comparing(Room::getId)).toList()
                : List.of();
        List<Room> availableRooms = propertyRooms.stream()
                .filter(r -> r.getStatus() == RoomStatus.AVAILABLE)
                .toList();

        model.addAttribute("propertyRooms", propertyRooms);
        model.addAttribute("availableRooms", availableRooms);

        User user = (User) session.getAttribute("user");
        List<Contact> myPropertyRequests = List.of();
        if (user != null && propertyId != null) {
            myPropertyRequests = contactRepository.findByCustomer_IdOrderByIdDesc(user.getId()).stream()
                    .filter(c -> c.getProperty() != null && c.getProperty().getId().equals(propertyId))
                    .toList();
        }

        List<PostPublicResponse> similarProperties = postService.getAllMarketplacePosts().stream()
                .filter(other -> !other.getId().equals(post.getId()))
                .limit(3)
                .map(PostPublicResponse::from)
                .toList();

        model.addAttribute("myPropertyRequests", myPropertyRequests);
        model.addAttribute("similarProperties", similarProperties);
        addCommonAttributes(model);
        model.addAttribute("pageTitle", p.getTitle());
        model.addAttribute("content", "public/property-detail");
        model.addAttribute("contact", new ContactRequest());

        return "layout/public-main";
    }

    @PostMapping("/properties/{slug}/register-rent")
    public String registerRent(@PathVariable("slug") String slug,
                               @RequestParam(required = false) Long roomId,
                               @RequestParam(required = false) String name,
                               @RequestParam(required = false) String phone,
                               @RequestParam(required = false) String citizenId,
                               @RequestParam(required = false) String moveInDate,
                               @RequestParam(defaultValue = "12") Integer durationMonths,
                               @RequestParam(defaultValue = "1") Integer occupants,
                               @RequestParam(required = false) String note,
                               @RequestParam(required = false) String redirectAction,
                               HttpSession session,
                               RedirectAttributes ra) {
        User currentUser = (User) session.getAttribute("user");
        if (currentUser == null) {
            ra.addFlashAttribute("errorMessage", "Vui lòng đăng nhập tài khoản để đặt thuê phòng hoặc thêm vào giỏ phòng.");
            return "redirect:/login";
        }

        // 1. Chuẩn hóa & Kiểm tra dữ liệu đầu vào (Server-side Validation)
        String cleanName = name != null ? name.trim() : "";
        if (cleanName.isBlank() && currentUser != null) {
            cleanName = currentUser.getFullName() != null && !currentUser.getFullName().isBlank()
                    ? currentUser.getFullName().trim() : currentUser.getUsername();
        }
        if (cleanName.length() < 2 || cleanName.length() > 100) {
            ra.addFlashAttribute("registerRentError", "Vui lòng nhập họ tên từ 2 đến 100 ký tự.");
            return "redirect:/properties/" + slug;
        }

        String cleanPhone = phone != null ? phone.replaceAll("[^0-9+]", "").trim() : "";
        if (cleanPhone.isBlank() && currentUser != null && currentUser.getPhone() != null) {
            cleanPhone = currentUser.getPhone().replaceAll("[^0-9+]", "").trim();
        }
        if (!cleanPhone.matches("^(?:\\+84|0)[0-9]{9,10}$")) {
            ra.addFlashAttribute("registerRentError", "Số điện thoại không hợp lệ. Hãy nhập số Việt Nam bắt đầu bằng 0 hoặc +84.");
            return "redirect:/properties/" + slug;
        }

        int validDuration = (durationMonths != null) ? Math.max(1, Math.min(60, durationMonths)) : 12;
        int validOccupants = (occupants != null) ? Math.max(1, Math.min(10, occupants)) : 1;

        Room selectedRoom = roomId != null ? roomRepository.findById(roomId).orElse(null) : null;
        if (selectedRoom == null || selectedRoom.getStatus() != RoomStatus.AVAILABLE) {
            ra.addFlashAttribute("registerRentError", "Phòng đã chọn không còn trống. Vui lòng tải lại trang và chọn phòng khác.");
            return "redirect:/properties/" + slug;
        }
        if (selectedRoom != null && selectedRoom.getMaxOccupancy() != null && selectedRoom.getMaxOccupancy() > 0) {
            validOccupants = Math.min(validOccupants, selectedRoom.getMaxOccupancy());
        }

        LocalDate parsedMoveIn = LocalDate.now().plusDays(3);
        if (moveInDate != null && !moveInDate.isBlank()) {
            try {
                parsedMoveIn = LocalDate.parse(moveInDate.trim());
                if (parsedMoveIn.isBefore(LocalDate.now())) {
                    ra.addFlashAttribute("registerRentError", "Ngày nhận phòng không được nằm trong quá khứ.");
                    return "redirect:/properties/" + slug;
                }
            } catch (DateTimeParseException ex) {
                ra.addFlashAttribute("registerRentError", "Ngày nhận phòng không đúng định dạng.");
                return "redirect:/properties/" + slug;
            }
        }

        String roomTag = selectedRoom != null ? ("#" + selectedRoom.getId() + " - " + selectedRoom.getName()) : "Phòng tiêu chuẩn";
        StringBuilder sb = new StringBuilder();
        sb.append("[ĐĂNG KÝ THUÊ PHÒNG ").append(roomTag).append("] ");
        sb.append("Nhận phòng: ").append(parsedMoveIn).append(" | ");
        sb.append("Thời hạn: ").append(validDuration).append(" tháng | ");
        sb.append("Số người: ").append(validOccupants);
        if (citizenId != null && !citizenId.isBlank()) {
            sb.append(" | CCCD: ").append(citizenId.trim());
        }
        if (note != null && !note.isBlank()) {
            sb.append(" | Ghi chú: ").append(note.trim());
        }
        String finalNote = sb.toString();
        if (finalNote.length() > 950) {
            finalNote = finalNote.substring(0, 950);
        }

        Contact created;
        try {
            created = contactService.createRentBooking(
                    slug,
                    selectedRoom.getId(),
                    currentUser,
                    cleanName,
                    cleanPhone,
                    citizenId != null ? citizenId.trim() : null,
                    parsedMoveIn,
                    validDuration,
                    validOccupants,
                    finalNote
            );
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("registerRentError", ex.getMessage());
            return "redirect:/properties/" + slug;
        }

        rememberSessionBooking(session, created.getId());
        session.setAttribute("lastBookedCustomerName", cleanName);

        ra.addFlashAttribute("registerRentSuccess", true);
        ra.addFlashAttribute("registeredRoomName", selectedRoom != null ? selectedRoom.getName() : "Phòng tại khu trọ");
        ra.addFlashAttribute("registeredPhone", cleanPhone);
        ra.addFlashAttribute("registeredMoveIn", parsedMoveIn.toString());
        ra.addFlashAttribute("registeredDuration", validDuration);

        return "redirect:/my-bookings";
    }

    @GetMapping("/my-bookings")
    public String myBookings(@RequestParam(required = false) String status,
                             HttpSession session,
                             Model model) {
        User currentUser = (User) session.getAttribute("user");
        if (currentUser == null) {
            model.addAttribute("requireLoginNotice", true);
        }

        LinkedHashMap<Long, Contact> merged = new LinkedHashMap<>();
        if (currentUser != null && currentUser.getId() != null) {
            for (Contact c : contactRepository.findByCustomer_IdOrderByIdDesc(currentUser.getId())) {
                merged.put(c.getId(), c);
            }
        }
        for (Contact c : contactRepository.findAllById(getSessionBookingIds(session))) {
            merged.putIfAbsent(c.getId(), c);
        }

        List<Contact> allMyBookings = merged.values().stream()
                .sorted(Comparator.comparing(Contact::getId).reversed())
                .toList();

        long pendingCount = allMyBookings.stream()
                .filter(c -> !Boolean.TRUE.equals(c.getIsChecked()) && !"CANCELLED".equalsIgnoreCase(c.getBookingStatus()) && !"REJECTED".equalsIgnoreCase(c.getBookingStatus()))
                .count();
        long approvedCount = allMyBookings.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsChecked()) && !"CANCELLED".equalsIgnoreCase(c.getBookingStatus()) && !"REJECTED".equalsIgnoreCase(c.getBookingStatus()))
                .count();
        long cancelledCount = allMyBookings.stream()
                .filter(c -> "CANCELLED".equalsIgnoreCase(c.getBookingStatus()) || "REJECTED".equalsIgnoreCase(c.getBookingStatus()))
                .count();

        String activeTab = status != null && !status.isBlank() ? status.trim().toUpperCase() : "ALL";
        List<Contact> filteredBookings = switch (activeTab) {
            case "PENDING" -> allMyBookings.stream().filter(c -> !Boolean.TRUE.equals(c.getIsChecked()) && !"CANCELLED".equalsIgnoreCase(c.getBookingStatus()) && !"REJECTED".equalsIgnoreCase(c.getBookingStatus())).toList();
            case "APPROVED", "RENTING" -> allMyBookings.stream().filter(c -> Boolean.TRUE.equals(c.getIsChecked()) && !"CANCELLED".equalsIgnoreCase(c.getBookingStatus()) && !"REJECTED".equalsIgnoreCase(c.getBookingStatus())).toList();
            case "CANCELLED" -> allMyBookings.stream().filter(c -> "CANCELLED".equalsIgnoreCase(c.getBookingStatus()) || "REJECTED".equalsIgnoreCase(c.getBookingStatus())).toList();
            default -> allMyBookings;
        };

        String sessionCustName = (String) session.getAttribute("lastBookedCustomerName");
        String displayCustName = currentUser != null && currentUser.getFullName() != null
                ? currentUser.getFullName()
                : (!allMyBookings.isEmpty() && allMyBookings.get(0).getName() != null
                ? allMyBookings.get(0).getName()
                : (sessionCustName != null ? sessionCustName : "Khách thuê"));
        String displayPhone = currentUser != null && currentUser.getPhone() != null
                ? currentUser.getPhone()
                : (!allMyBookings.isEmpty() ? allMyBookings.get(0).getPhone() : "");

        addCommonAttributes(model);
        model.addAttribute("pageTitle", "Đơn đặt thuê phòng của tôi");
        model.addAttribute("content", "public/my-bookings");
        model.addAttribute("myBookings", filteredBookings);
        model.addAttribute("totalBookingsCount", allMyBookings.size());
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("approvedCount", approvedCount);
        model.addAttribute("cancelledCount", cancelledCount);
        model.addAttribute("activeTab", activeTab);
        model.addAttribute("hasApprovedBooking", approvedCount > 0);
        model.addAttribute("customerName", displayCustName);
        model.addAttribute("customerPhone", displayPhone);

        return "layout/public-main";
    }

    @PostMapping("/my-bookings/lookup")
    public String lookupBooking(@RequestParam(required = false) Long bookingId,
                                @RequestParam(required = false) String phone,
                                HttpSession session,
                                RedirectAttributes ra) {
        if (bookingId == null || phone == null || phone.isBlank()) {
            ra.addFlashAttribute("lookupError", "Vui lòng nhập đầy đủ cả Mã đơn đặt phòng và Số điện thoại liên hệ để tra cứu bảo mật.");
            return "redirect:/my-bookings";
        }
        String cleanPhone = phone.replaceAll("[^0-9]", "").trim();
        Contact target = contactRepository.findById(bookingId).orElse(null);
        if (target == null || target.getPhone() == null) {
            ra.addFlashAttribute("lookupError", "Không tìm thấy hồ sơ đơn đặt phòng #" + bookingId + ". Vui lòng kiểm tra lại thông tin.");
            return "redirect:/my-bookings";
        }
        String targetPhoneClean = target.getPhone().replaceAll("[^0-9]", "").trim();
        if (!targetPhoneClean.equals(cleanPhone) && !targetPhoneClean.endsWith(cleanPhone) && !cleanPhone.endsWith(targetPhoneClean)) {
            ra.addFlashAttribute("lookupError", "Số điện thoại không trùng khớp với hồ sơ đơn #" + bookingId + ". Vì lý do bảo mật, bạn không thể truy cập đơn này.");
            return "redirect:/my-bookings";
        }

        rememberSessionBooking(session, target.getId());
        session.setAttribute("lastBookedCustomerName", target.getName());
        ra.addFlashAttribute("lookupSuccess", "Đã xác thực và tải thông tin đơn đặt phòng #ĐƠN-" + target.getId() + " thành công!");
        return "redirect:/my-bookings";
    }

    @PostMapping("/my-bookings/{id}/pay-deposit")
    public String payDeposit(@PathVariable Long id,
                             @RequestParam(defaultValue = "BANK_TRANSFER") String paymentMethod,
                             @RequestParam(required = false) String transactionCode,
                             @RequestParam(required = false) String note,
                             HttpSession session,
                             RedirectAttributes ra) {
        User currentUser = (User) session.getAttribute("user");
        if (currentUser == null) {
            ra.addFlashAttribute("errorMessage", "Vui lòng đăng nhập tài khoản để thực hiện thanh toán tiền cọc.");
            return "redirect:/login";
        }
        Contact target = contactRepository.findById(id).orElse(null);
        if (target == null) {
            ra.addFlashAttribute("cancelBookingError", "Không tìm thấy đơn đặt phòng #" + id);
            return "redirect:/my-bookings";
        }
        boolean authorized = false;
        if (target.getCustomer() != null && currentUser.getId().equals(target.getCustomer().getId())) {
            authorized = true;
        } else if (currentUser.getRoles() != null && currentUser.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getName()))) {
            authorized = true;
        }
        if (!authorized && getSessionBookingIds(session).contains(id)) {
            authorized = true;
        }
        if (!authorized) {
            ra.addFlashAttribute("cancelBookingError", "Bạn không có quyền thanh toán cho đơn của khách hàng khác!");
            return "redirect:/my-bookings";
        }

        String paymentDesc = "BANK_TRANSFER".equalsIgnoreCase(paymentMethod) || "VIETQR".equalsIgnoreCase(paymentMethod)
                ? "Chuyển khoản VietQR Ngân hàng"
                : ("VNPAY".equalsIgnoreCase(paymentMethod) ? "Cổng thanh toán điện tử VNPay" : "Thanh toán trực tiếp khi nhận bàn giao phòng (Tiền mặt)");

        String updatedNote = (target.getNote() != null ? target.getNote() : "") +
                "\n[XÁC NHẬN THANH TOÁN TIỀN CỌC: " + paymentDesc + (transactionCode != null && !transactionCode.isBlank() ? " | Mã GD: " + transactionCode.trim() : "") + "]";
        target.setNote(updatedNote);
        target.setBookingStatus("APPROVED");
        target.setIsChecked(true);
        contactRepository.save(target);

        ra.addFlashAttribute("paymentSuccess", "Xác nhận thanh toán tiền cọc thành công qua " + paymentDesc + "! Hồ sơ giữ phòng đã được chuyển sang trạng thái ĐÃ DUYỆT & CHỜ KÝ HỢP ĐỒNG.");
        return "redirect:/my-bookings";
    }

    @PostMapping("/my-bookings/{id}/cancel")
    public String cancelMyBooking(@PathVariable("id") Long id,
                                  HttpSession session,
                                  RedirectAttributes ra) {
        User currentUser = (User) session.getAttribute("user");
        try {
            Contact target = contactRepository.findById(id).orElse(null);
            if (target == null) {
                ra.addFlashAttribute("cancelBookingError", "Không tìm thấy đơn đặt phòng #" + id);
                return "redirect:/my-bookings";
            }
            boolean authorized = false;
            if (currentUser != null) {
                if (target.getCustomer() != null && currentUser.getId().equals(target.getCustomer().getId())) {
                    authorized = true;
                } else if (currentUser.getRoles() != null && currentUser.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getName()))) {
                    authorized = true;
                }
            }
            if (!authorized && getSessionBookingIds(session).contains(id)) authorized = true;
            if (!authorized) {
                ra.addFlashAttribute("cancelBookingError", "Bạn không có quyền hủy đơn đặt phòng của khách hàng khác!");
                return "redirect:/my-bookings";
            }
            contactService.updateBookingStatus(id, "CANCELLED", null, true);
            ra.addFlashAttribute("cancelBookingSuccess", "Đã hủy đơn đặt thuê phòng #ĐƠN-" + id + " thành công.");
        } catch (Exception ex) {
            ra.addFlashAttribute("cancelBookingError", ex.getMessage());
        }
        return "redirect:/my-bookings";
    }

    @GetMapping("/video-review")
    public String videoReview(Model model) {
        List<PostPublicResponse> properties = postService.getAllMarketplacePosts()
                .stream().map(PostPublicResponse::from).toList();

        addCommonAttributes(model);
        model.addAttribute("pageTitle", "Video review phòng trọ và căn hộ mini");
        model.addAttribute("content", "public/video-review");
        model.addAttribute("videoHighlights", properties.stream().limit(3).toList());
        model.addAttribute("featuredProperty", properties.isEmpty() ? null : properties.get(0));

        return "layout/public-main";
    }

    private void addCommonAttributes(Model model) {
        model.addAttribute("categories", categoryService.findAll());
    }

    private Set<Long> getSessionBookingIds(HttpSession session) {
        Object value = session.getAttribute("ownedBookingIds");
        if (value instanceof Set) {
            Set<?> raw = (Set<?>) value;
            LinkedHashSet<Long> ids = new LinkedHashSet<>();
            raw.stream().filter(Long.class::isInstance).map(Long.class::cast).forEach(ids::add);
            return ids;
        }
        return new LinkedHashSet<>();
    }

    private void rememberSessionBooking(HttpSession session, Long bookingId) {
        Set<Long> ids = getSessionBookingIds(session);
        ids.add(bookingId);
        session.setAttribute("ownedBookingIds", ids);
    }
}
