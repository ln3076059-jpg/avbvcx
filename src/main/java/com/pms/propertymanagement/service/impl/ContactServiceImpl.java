package com.pms.propertymanagement.service.impl;

import com.pms.propertymanagement.dto.request.ContactRequest;
import com.pms.propertymanagement.dto.response.ContactResponse;
import com.pms.propertymanagement.entity.*;
import com.pms.propertymanagement.enums.ContractStatus;
import com.pms.propertymanagement.enums.RoomStatus;
import com.pms.propertymanagement.exception.ForbiddenException;
import com.pms.propertymanagement.exception.ResourceNotFoundException;
import com.pms.propertymanagement.repository.*;
import com.pms.propertymanagement.service.ContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class ContactServiceImpl implements ContactService {

    private final ContactRepository contactRepository;
    private final PostRepository postRepository;
    private final RoomRepository roomRepository;
    private final TenantRepository tenantRepository;
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ContactResponse> getContactsByOwner(Long ownerId) {
        List<Contact> contacts = contactRepository.findByOwnerIdOrderByIdDesc(ownerId);
        if (contacts.isEmpty()) {
            return List.of();
        }

        // Batch-fetch all Posts by property_id in 1 single query (fixes N+1 query issue)
        Set<Long> propertyIds = contacts.stream()
                .map(Contact::getProperty)
                .filter(Objects::nonNull)
                .map(Property::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Post> postByPropId = propertyIds.isEmpty()
                ? Map.of()
                : postRepository.findByProperty_IdIn(propertyIds).stream()
                        .filter(p -> p.getProperty() != null && p.getProperty().getId() != null)
                        .collect(Collectors.toMap(
                                p -> p.getProperty().getId(),
                                p -> p,
                                (a, b) -> a
                        ));

        return contacts.stream().map(c -> {
            String title = "N/A";
            String slug = "#";
            if (c.getProperty() != null) {
                title = c.getProperty().getName() != null ? c.getProperty().getName() : "N/A";
                Post post = postByPropId.get(c.getProperty().getId());
                if (post != null) {
                    title = post.getTitle();
                    slug = post.getSlug();
                }
            }
            ContactResponse resp = new ContactResponse(
                    c.getId(),
                    c.getName(),
                    c.getPhone(),
                    c.getNote(),
                    title,
                    slug,
                    c.getIsChecked()
            );
            if (c.getRoom() != null) {
                resp.setRoomId(c.getRoom().getId());
            }
            if (c.getBookingType() != null && !c.getBookingType().isBlank()) {
                resp.setBookingType(c.getBookingType());
            }
            if (c.getBookingStatus() != null && !c.getBookingStatus().isBlank()) {
                resp.setBookingStatus(c.getBookingStatus());
            }
            return resp;
        }).toList();
    }

    @Override
    @Transactional
    public void createContact(String propertySlug, ContactRequest contactRequest) {
        Post post = postRepository.findBySlug(propertySlug)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        var property = post.getProperty();
        if (property == null) {
            throw new ResourceNotFoundException("Property not found for this post");
        }

        Contact contact = new Contact(
                contactRequest.getName(),
                contactRequest.getPhone(),
                contactRequest.getNote(),
                property.getOwner(),
                property
        );
        contactRepository.save(contact);
    }

    @Override
    @Transactional
    public Contact createRentBooking(String propertySlug,
                                  Long roomId,
                                  User customer,
                                  String name,
                                  String phone,
                                  String citizenId,
                                  LocalDate moveInDate,
                                  Integer durationMonths,
                                  Integer occupants,
                                  String formattedNote) {
        Post post = postRepository.findBySlug(propertySlug)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        Property property = post.getProperty();
        if (property == null) {
            throw new ResourceNotFoundException("Property not found for this post");
        }

        if (roomId == null) {
            throw new IllegalArgumentException("Vui lòng chọn phòng muốn thuê");
        }
        Room selectedRoom = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng đã chọn"));
        if (selectedRoom.getProperty() == null || !property.getId().equals(selectedRoom.getProperty().getId())) {
            throw new IllegalArgumentException("Phòng đã chọn không thuộc khu trọ này");
        }
        if (selectedRoom.getStatus() != RoomStatus.AVAILABLE) {
            throw new IllegalStateException("Phòng " + selectedRoom.getName() + " không còn trống. Vui lòng chọn phòng khác.");
        }
        Contact contact = new Contact(
                name,
                phone,
                formattedNote,
                property.getOwner(),
                property
        );
        contact.setRoom(selectedRoom);
        contact.setCustomer(customer);
        contact.setBookingType("RENT_ORDER");
        contact.setBookingStatus("PENDING");
        contact.setCitizenId(citizenId);
        contact.setMoveInDate(moveInDate != null ? moveInDate : LocalDate.now().plusDays(3));
        contact.setDurationMonths(durationMonths != null ? durationMonths : 12);
        contact.setOccupants(occupants != null ? occupants : 1);
        contact.setDepositAmount(selectedRoom != null && selectedRoom.getDeposit() != null
                ? selectedRoom.getDeposit()
                : (selectedRoom != null && selectedRoom.getPrice() != null ? selectedRoom.getPrice() : (double) property.getPrice()));
        return contactRepository.save(contact);
    }

    @Override
    @Transactional
    public Contract approveAndCreateContractAutomatically(Long contactId, Long actorOwnerId, boolean isAdmin) {
        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn đặt thuê #" + contactId));

        if (!"RENT_ORDER".equalsIgnoreCase(contact.getBookingType())) {
            throw new IllegalStateException("Yêu cầu này không phải đơn đặt thuê phòng");
        }
        if (!"PENDING".equalsIgnoreCase(contact.getBookingStatus())) {
            throw new IllegalStateException("Chỉ có thể duyệt đơn đang chờ xử lý");
        }

        Property property = contact.getProperty();
        User owner = contact.getOwner() != null ? contact.getOwner()
                : (property != null ? property.getOwner() : null);

        if (!isAdmin && actorOwnerId != null && owner != null && !actorOwnerId.equals(owner.getId())) {
            throw new ForbiddenException("Bạn không có quyền duyệt đơn đặt thuê này!");
        }

        // 1. Xác định và khóa phòng trong transaction để chống hai người duyệt cùng lúc.
        Room targetRoom = contact.getRoom();
        if (targetRoom == null && contact.getNote() != null) {
            Matcher m = Pattern.compile("#(\\d+)").matcher(contact.getNote());
            if (m.find()) {
                try {
                    Long rId = Long.parseLong(m.group(1));
                    targetRoom = roomRepository.findById(rId).orElse(null);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (targetRoom == null) {
            throw new IllegalStateException("Đơn đặt thuê chưa chỉ định phòng cụ thể hoặc phòng không tồn tại. Không thể duyệt đơn khi chưa rõ phòng.");
        }
        targetRoom = roomRepository.findByIdForUpdate(targetRoom.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng đã đặt"));
        if (owner == null || targetRoom.getProperty() == null
                || targetRoom.getProperty().getOwner() == null
                || !owner.getId().equals(targetRoom.getProperty().getOwner().getId())) {
            throw new ForbiddenException("Phòng không thuộc quyền quản lý của chủ trọ này");
        }
        if (targetRoom.getStatus() != RoomStatus.AVAILABLE
                || contractRepository.findFirstByRoom_IdAndStatus(targetRoom.getId(), ContractStatus.ACTIVE).isPresent()) {
            throw new IllegalStateException("Phòng " + targetRoom.getName() + " hiện không còn trống (đã có người thuê hoặc hợp đồng đang hoạt động). Không thể duyệt đơn trùng phòng.");
        }

        // 3. Tìm hoặc tạo mới hồ sơ Khách thuê (Tenant) và gắn khóa ngoại account_id
        if (contact.getPhone() == null || contact.getPhone().trim().isBlank()) {
            throw new IllegalArgumentException("Hồ sơ đơn đặt phòng thiếu số điện thoại người thuê hợp lệ!");
        }
        String cleanPhone = contact.getPhone().trim();
        Tenant tenant = tenantRepository.findFirstByOwner_IdAndPhone(owner.getId(), cleanPhone)
                .orElseGet(() -> {
                    Tenant t = new Tenant();
                    t.setOwner(owner);
                    t.setFullName(contact.getName() != null && !contact.getName().isBlank() ? contact.getName().trim() : "Khách thuê");
                    t.setPhone(cleanPhone);
                    String cccd = contact.getCitizenId();
                    if (cccd == null || cccd.isBlank()) cccd = "PENDING-" + contact.getId();
                    t.setCitizenId(cccd);
                    t.setPermanentAddress("Cập nhật từ đơn đặt thuê trực tuyến #" + contact.getId());
                    return t;
                });

        User customerAccount = contact.getCustomer();
        if (customerAccount == null) {
            customerAccount = userRepository.findAll().stream()
                    .filter(u -> u.getPhone() != null && u.getPhone().trim().equals(cleanPhone))
                    .findFirst()
                    .orElse(null);
        }
        if (customerAccount != null && tenant.getAccount() == null) {
            tenant.setAccount(customerAccount);
        }
        tenant = tenantRepository.save(tenant);

        // 4. Tạo hợp đồng thuê phòng hiệu lực (ACTIVE) mới.
        LocalDate start = contact.getMoveInDate() != null ? contact.getMoveInDate() : LocalDate.now();
        int months = contact.getDurationMonths() != null && contact.getDurationMonths() > 0 ? contact.getDurationMonths() : 12;

        Contract contract = new Contract();
        contract.setCode("HD-" + targetRoom.getId() + "-" + (System.currentTimeMillis() % 100000));
        contract.setRoom(targetRoom);
        contract.setStartDate(start);
        contract.setEndDate(start.plusMonths(months));
        Double rentPrice = targetRoom.getPrice() != null && targetRoom.getPrice() > 0
                ? Double.valueOf(Math.round(targetRoom.getPrice()))
                : (property != null && property.getPrice() > 0 ? Double.valueOf(property.getPrice()) : 2000000.0);
        Double deposit = targetRoom.getDeposit() != null && targetRoom.getDeposit() > 0
                ? Double.valueOf(Math.round(targetRoom.getDeposit()))
                : rentPrice;
        contract.setRentPrice(rentPrice);
        contract.setDeposit(deposit);
        contract.setPaymentCycle(1);
        contract.setIsElectricityWaterIncluded(false);
        contract.setBedCount(contact.getOccupants() != null ? contact.getOccupants() : 2);
        contract.setNote("Tự động tạo khi duyệt Đơn đặt thuê trực tuyến #ĐƠN-" + contact.getId());
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setRepresentative(tenant);
        Set<Tenant> tenantSet = new HashSet<>();
        tenantSet.add(tenant);
        contract.setTenants(tenantSet);
        if (targetRoom.getServices() != null && !targetRoom.getServices().isEmpty()) {
            contract.setServices(new HashSet<>(targetRoom.getServices()));
        }
        contract = contractRepository.save(contract);

        // 5. Khóa phòng và chỉ sau đó mới đánh dấu đơn đã duyệt.
        targetRoom.setStatus(RoomStatus.RENTED);
        roomRepository.save(targetRoom);
        contact.setRoom(targetRoom);
        contact.setIsChecked(true);
        contact.setBookingStatus("APPROVED");
        contactRepository.save(contact);

        return contract;
    }

    @Override
    @Transactional
    public void updateBookingStatus(Long contactId, String newStatus, Long actorOwnerId, boolean isAdmin) {
        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact not found"));
        if (!isAdmin && actorOwnerId != null) {
            Long contactOwnerId = contact.getOwner() != null ? contact.getOwner().getId()
                    : (contact.getProperty() != null && contact.getProperty().getOwner() != null ? contact.getProperty().getOwner().getId() : null);
            if (!actorOwnerId.equals(contactOwnerId)) {
                throw new ForbiddenException("Bạn không có quyền thao tác trên đơn này!");
            }
        }
        contact.setBookingStatus(newStatus);
        contact.setIsChecked("APPROVED".equalsIgnoreCase(newStatus));
        contactRepository.save(contact);
    }

    @Override
    @Transactional
    public void changeStatus(Long contactId) {
        changeStatus(contactId, null);
    }

    @Override
    @Transactional
    public void changeStatus(Long contactId, Long ownerId) {
        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact not found"));
        if (ownerId != null) {
            Long contactOwnerId = contact.getOwner() != null ? contact.getOwner().getId()
                    : (contact.getProperty() != null && contact.getProperty().getOwner() != null ? contact.getProperty().getOwner().getId() : null);
            if (!ownerId.equals(contactOwnerId)) {
                throw new ForbiddenException("Bạn không có quyền thao tác trên liên hệ này!");
            }
        }
        boolean nextChecked = !Boolean.TRUE.equals(contact.getIsChecked());
        contact.setIsChecked(nextChecked);
        contact.setBookingStatus(nextChecked ? "APPROVED" : "PENDING");
        if (nextChecked && contact.getRoom() != null && "RENT_ORDER".equalsIgnoreCase(contact.getBookingType())) {
            Room r = contact.getRoom();
            r.setStatus(RoomStatus.RENTED);
            roomRepository.save(r);
        }
        contactRepository.save(contact);
    }

    @Override
    @Transactional
    public void markAsContacted(Long contactId, Long ownerId) {
        if (contactId == null) return;
        contactRepository.findById(contactId).ifPresent(contact -> {
            if (ownerId != null) {
                Long contactOwnerId = contact.getOwner() != null ? contact.getOwner().getId()
                        : (contact.getProperty() != null && contact.getProperty().getOwner() != null ? contact.getProperty().getOwner().getId() : null);
                if (!ownerId.equals(contactOwnerId)) {
                    return;
                }
            }
            if (!Boolean.TRUE.equals(contact.getIsChecked())) {
                contact.setIsChecked(true);
                contact.setBookingStatus("APPROVED");
                contactRepository.save(contact);
            }
        });
    }

    /**
     * Tự động quét và hủy các đơn đặt phòng quá hạn giữ chỗ 24h chưa được xác nhận (SLA 24h).
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 3600000L, initialDelay = 600000L)
    @Transactional
    public void expireStalePendingBookings() {
        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusHours(24);
        List<Contact> allPending = contactRepository.findAllByOrderByIdDesc().stream()
                .filter(c -> !Boolean.TRUE.equals(c.getIsChecked())
                        && "PENDING".equalsIgnoreCase(c.getBookingStatus())
                        && c.getCreatedAt() != null
                        && c.getCreatedAt().isBefore(cutoff))
                .toList();
        for (Contact stale : allPending) {
            stale.setBookingStatus("CANCELLED");
            contactRepository.save(stale);
        }
    }
}
