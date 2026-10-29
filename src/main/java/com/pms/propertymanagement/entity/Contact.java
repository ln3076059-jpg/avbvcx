package com.pms.propertymanagement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "contact", indexes = {
        @Index(name = "idx_contact_owner_id", columnList = "owner_id"),
        @Index(name = "idx_contact_phone", columnList = "phone"),
        @Index(name = "idx_contact_booking_status", columnList = "booking_status")
})
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(length = 1000)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id")
    private Property property;

    // Khóa ngoại trực tiếp tới phòng trọ được đặt thuê
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    // Khóa ngoại trực tiếp tới tài khoản khách hàng (users.id)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private User customer;

    // Loại yêu cầu: RENT_ORDER (Đơn đặt thuê phòng) hoặc VISIT (Lịch hẹn xem phòng)
    @Column(name = "booking_type", length = 30)
    private String bookingType = "VISIT";

    // Trạng thái đơn: PENDING (Chờ duyệt), APPROVED (Đã duyệt), REJECTED (Từ chối), CANCELLED (Khách hủy)
    @Column(name = "booking_status", length = 30)
    private String bookingStatus = "PENDING";

    @Column(name = "citizen_id", length = 25)
    private String citizenId;

    @Column(name = "move_in_date")
    private LocalDate moveInDate;

    @Column(name = "duration_months")
    private Integer durationMonths;

    @Column(name = "occupants")
    private Integer occupants;

    @Column(name = "deposit_amount")
    private Double depositAmount;

    @Column(nullable = false)
    private Boolean isChecked = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Contact(String name, String phone, String note, User owner, Property property) {
        this.name = name;
        this.phone = phone;
        this.note = note;
        this.owner = owner;
        this.property = property;
        this.isChecked = false;
        this.bookingType = (note != null && note.startsWith("[ĐĂNG KÝ THUÊ PHÒNG")) ? "RENT_ORDER" : "VISIT";
        this.bookingStatus = "PENDING";
        this.createdAt = LocalDateTime.now();
    }
}
