package com.pms.propertymanagement.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceRequest {

    private Long id;

    @NotNull(message = "Vui lòng chọn phòng")
    private Long roomId;

    @NotNull(message = "Vui lòng chọn tháng")
    @Min(value = 1, message = "Tháng từ 1 đến 12")
    @Max(value = 12, message = "Tháng từ 1 đến 12")
    private Integer month;

    @NotNull(message = "Vui lòng nhập năm")
    @Min(value = 2000, message = "Năm không hợp lệ")
    private Integer year;

    private Double roomPrice;

    private Double otherAmount = 0.0;

    private String note;

    @NotNull(message = "Hạn thanh toán không được để trống")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dueDate;

    private Boolean isProrated = false;
    private Integer actualDays;
    private Integer totalDaysInMonth;

    public InvoiceRequest(Long id, Long roomId, Integer month, Integer year, Double roomPrice, Double otherAmount, String note, LocalDate dueDate) {
        this.id = id;
        this.roomId = roomId;
        this.month = month;
        this.year = year;
        this.roomPrice = roomPrice;
        this.otherAmount = otherAmount;
        this.note = note;
        this.dueDate = dueDate;
        this.isProrated = false;
    }
}
