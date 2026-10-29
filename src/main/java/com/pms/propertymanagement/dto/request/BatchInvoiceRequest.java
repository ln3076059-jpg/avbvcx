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
public class BatchInvoiceRequest {

    private Long propertyId; // null = tất cả nhà trọ của owner

    @NotNull(message = "Vui lòng chọn tháng")
    @Min(value = 1, message = "Tháng từ 1 đến 12")
    @Max(value = 12, message = "Tháng từ 1 đến 12")
    private Integer month;

    @NotNull(message = "Vui lòng chọn năm")
    @Min(value = 2000, message = "Năm không hợp lệ")
    private Integer year;

    @NotNull(message = "Hạn thanh toán không được để trống")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dueDate;
}
