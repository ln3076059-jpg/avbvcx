package com.pms.propertymanagement.service;

import com.pms.propertymanagement.dto.request.PaymentRequest;
import com.pms.propertymanagement.entity.Payment;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.enums.PaymentMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PaymentService {

    Payment createPayment(PaymentRequest request, Long ownerId);

    default Payment recordManualPayment(Long invoiceId, Double amount, Long ownerId, String note) {
        PaymentRequest req = new PaymentRequest();
        req.setInvoiceId(invoiceId);
        req.setAmount(amount);
        req.setPaymentMethod(PaymentMethod.CASH);
        req.setNote(note);
        return createPayment(req, ownerId);
    }

    Page<Payment> searchPayments(Long ownerId, Long propertyId, Long roomId, Long tenantId, Pageable pageable);

    List<Payment> getPaymentsByInvoiceId(Long invoiceId);

    List<Payment> getPaymentsForTenant(User tenantUser);
    
    String createInvoiceVnpayUrl(Long invoiceId, User tenantUser, String returnUrl, String ipAddress);

    boolean processVnpayInvoiceCallback(java.util.Map<String, String> returnParams, String rawQuery);
}
