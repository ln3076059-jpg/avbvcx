package com.pms.propertymanagement.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MeterReadingRequest {

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

    @NotNull(message = "Chỉ số điện cũ không được để trống")
    @Min(value = 0, message = "Chỉ số điện cũ phải >= 0")
    private Double oldElectricityIndex;

    @NotNull(message = "Chỉ số điện mới không được để trống")
    @Min(value = 0, message = "Chỉ số điện mới phải >= 0")
    private Double newElectricityIndex;

    @NotNull(message = "Chỉ số nước cũ không được để trống")
    @Min(value = 0, message = "Chỉ số nước cũ phải >= 0")
    private Double oldWaterIndex;

    @NotNull(message = "Chỉ số nước mới không được để trống")
    @Min(value = 0, message = "Chỉ số nước mới phải >= 0")
    private Double newWaterIndex;

    private Double electricityPrice;
    private Double waterPrice;
    private Boolean isReset = false;

    public MeterReadingRequest(Long id, Long roomId, Integer month, Integer year,
                               Double oldElectricityIndex, Double newElectricityIndex,
                               Double oldWaterIndex, Double newWaterIndex,
                               Double electricityPrice, Double waterPrice) {
        this.id = id;
        this.roomId = roomId;
        this.month = month;
        this.year = year;
        this.oldElectricityIndex = oldElectricityIndex;
        this.newElectricityIndex = newElectricityIndex;
        this.oldWaterIndex = oldWaterIndex;
        this.newWaterIndex = newWaterIndex;
        this.electricityPrice = electricityPrice;
        this.waterPrice = waterPrice;
        this.isReset = false;
    }
}
