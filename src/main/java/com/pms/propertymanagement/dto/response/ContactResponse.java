package com.pms.propertymanagement.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContactResponse {
    private Long id;
    private String name;
    private String phone;
    private String note;
    private String propertyTitle;
    private String propertySlug;
    private Boolean isChecked;
    private Long roomId;
    private String bookingType;
    private String bookingStatus;

    public ContactResponse(Long id, String name, String phone, String note,
                           String propertyTitle, String propertySlug, Boolean isChecked) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.note = note;
        this.propertyTitle = propertyTitle;
        this.propertySlug = propertySlug;
        this.isChecked = isChecked;
        this.bookingType = isRentRegistration() ? "RENT_ORDER" : "VISIT";
        this.bookingStatus = Boolean.TRUE.equals(isChecked) ? "APPROVED" : "PENDING";
    }

    public Boolean getStatus() {
        return (Boolean.TRUE.equals(isChecked) || "APPROVED".equalsIgnoreCase(bookingStatus))
                && !isCancelled();
    }

    public boolean isCancelled() {
        return "CANCELLED".equalsIgnoreCase(bookingStatus) || "REJECTED".equalsIgnoreCase(bookingStatus);
    }

    public boolean isRentRegistration() {
        if ("RENT_ORDER".equalsIgnoreCase(bookingType)) {
            return true;
        }
        return note != null && (note.contains("[ĐĂNG KÝ THUÊ") || note.contains("Yêu cầu thuê phòng"));
    }

    public Long getRequestedRoomId() {
        if (roomId != null) {
            return roomId;
        }
        if (note == null) return null;
        Matcher m = Pattern.compile("#(\\d+)").matcher(note);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        Matcher m2 = Pattern.compile("roomId=(\\d+)").matcher(note);
        if (m2.find()) {
            try {
                return Long.parseLong(m2.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
