package com.pms.propertymanagement.dto.request;

import com.pms.propertymanagement.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    @NotNull(message = "Vui lòng chọn hóa đơn cần thanh toán")
    private Long invoiceId;

    @NotNull(message = "Số tiền thanh toán không được để trống")
    @DecimalMin(value = "1000.0", message = "Số tiền thanh toán tối thiểu là 1.000 đ")
    private Double amount;

    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    private String transactionCode;

    private String note;
}
