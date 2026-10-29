package com.pms.propertymanagement.service.impl;

import com.pms.propertymanagement.dto.request.PaymentRequest;
import com.pms.propertymanagement.entity.Invoice;
import com.pms.propertymanagement.entity.Payment;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.enums.PaymentMethod;
import com.pms.propertymanagement.enums.PaymentStatus;
import com.pms.propertymanagement.repository.InvoiceRepository;
import com.pms.propertymanagement.repository.PaymentRepository;
import com.pms.propertymanagement.repository.UserRepository;
import com.pms.propertymanagement.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public Payment createPayment(PaymentRequest request, Long ownerId) {
        Invoice invoice = invoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hóa đơn có ID: " + request.getInvoiceId()));

        if (!invoice.getRoom().getProperty().getOwner().getId().equals(ownerId)) {
            throw new IllegalArgumentException("Bạn không có quyền thao tác trên hóa đơn này");
        }

        if (invoice.getStatus() == com.pms.propertymanagement.enums.InvoiceStatus.CANCELLED) {
            throw new IllegalArgumentException("Hóa đơn đã bị hủy, không thể ghi nhận thanh toán!");
        }

        if (invoice.getStatus() == com.pms.propertymanagement.enums.InvoiceStatus.PAID || invoice.getRemainingAmount() <= 0) {
            throw new IllegalArgumentException("Hóa đơn này đã được thanh toán hoàn tất, không còn công nợ!");
        }

        if (request.getAmount() == null || request.getAmount() <= 0) {
            throw new IllegalArgumentException("Số tiền thanh toán phải lớn hơn 0");
        }

        double roundedAmount = (double) Math.round(request.getAmount());
        double roundedRemaining = (double) Math.round(invoice.getRemainingAmount());

        // Không cho thanh toán vượt quá số nợ còn lại
        if (roundedAmount > roundedRemaining) {
            throw new IllegalArgumentException("Số tiền thanh toán (" + String.format("%,.0f", roundedAmount) +
                    " đ) không được lớn hơn số tiền còn nợ (" + String.format("%,.0f", roundedRemaining) + " đ)");
        }

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chủ trọ"));

        Payment payment = new Payment();
        payment.setOwner(owner);
        payment.setInvoice(invoice);
        payment.setContract(invoice.getContract());
        payment.setAmount(roundedAmount);
        payment.setPaymentDate(LocalDateTime.now());
        payment.setPaymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : PaymentMethod.CASH);
        payment.setTransactionCode(request.getTransactionCode());
        payment.setNote(request.getNote());
        payment.setStatus(PaymentStatus.PAID);
        payment.setCreatedAt(LocalDateTime.now());

        // Cập nhật công nợ hóa đơn (làm tròn nguyên đồng VNĐ tránh sai số IEEE 754)
        double newPaid = (double) Math.round((invoice.getPaidAmount() != null ? invoice.getPaidAmount() : 0.0) + roundedAmount);
        invoice.setPaidAmount(newPaid);
        invoice.setRemainingAmount((double) Math.round(Math.max(0.0, invoice.getTotalAmount() - newPaid)));
        invoice.updateStatus();
        invoice.setUpdatedAt(LocalDateTime.now());

        invoiceRepository.save(invoice);
        return paymentRepository.save(payment);
    }

    @Override
    public Page<Payment> searchPayments(Long ownerId, Long propertyId, Long roomId, Long tenantId, Pageable pageable) {
        return paymentRepository.searchPayments(ownerId, propertyId, roomId, tenantId, pageable);
    }

    @Override
    public List<Payment> getPaymentsByInvoiceId(Long invoiceId) {
        return paymentRepository.findByInvoice_IdOrderByPaymentDateDesc(invoiceId);
    }

    @Override
    public List<Payment> getPaymentsForTenant(User tenantUser) {
        if (tenantUser == null || tenantUser.getPhone() == null || tenantUser.getPhone().isBlank()) return List.of();
        return paymentRepository.findByTenantIdOrPhone(tenantUser.getId(), tenantUser.getPhone().trim());
    }

    @org.springframework.beans.factory.annotation.Value("${vnpay.hashSecret}")
    private String hashSecret;

    private final com.pms.propertymanagement.service.VnPayService vnPayService;

    @Override
    public String createInvoiceVnpayUrl(Long invoiceId, User tenantUser, String returnUrl, String ipAddress) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hóa đơn ID: " + invoiceId));

        boolean isAllowed = false;
        String phoneNorm = tenantUser != null ? InvoiceServiceImpl.normalizePhone(tenantUser.getPhone()) : "";
        if (!phoneNorm.isEmpty()) {
            if (invoice.getTenant() != null && phoneNorm.equals(InvoiceServiceImpl.normalizePhone(invoice.getTenant().getPhone()))) {
                isAllowed = true;
            }
            if (!isAllowed && invoice.getContract() != null && invoice.getContract().getTenants() != null) {
                isAllowed = invoice.getContract().getTenants().stream()
                        .anyMatch(t -> phoneNorm.equals(InvoiceServiceImpl.normalizePhone(t.getPhone())));
            }
        }
        if (!isAllowed) {
            throw new IllegalArgumentException("Bạn không có quyền thanh toán hóa đơn này!");
        }

        if (invoice.getRemainingAmount() == null || invoice.getRemainingAmount() <= 0) {
            throw new IllegalStateException("Hóa đơn đã được thanh toán đầy đủ!");
        }

        long amountToPay = Math.round(invoice.getRemainingAmount());
        String txnRef = "INV_" + invoice.getId() + "_" + System.currentTimeMillis();
        String orderInfo = "Thanh toan hoa don " + invoice.getInvoiceCode();

        return vnPayService.createCustomPaymentUrl(amountToPay, txnRef, orderInfo, returnUrl, ipAddress);
    }

    @Override
    @Transactional
    public boolean processVnpayInvoiceCallback(java.util.Map<String, String> returnParams, String rawQuery) {
        try {
            boolean validSig = com.pms.propertymanagement.utils.VnPayUtil.verifySecureHash(returnParams, hashSecret);
            if (!validSig) {
                return false;
            }

            String vnpayTxnRef = returnParams.get("vnp_TxnRef");
            String responseCode = returnParams.get("vnp_ResponseCode");
            String transactionNo = returnParams.get("vnp_TransactionNo");

            if (vnpayTxnRef == null || !vnpayTxnRef.startsWith("INV_")) {
                return false;
            }

            if (!"00".equals(responseCode)) {
                return false;
            }

            if (transactionNo != null && !transactionNo.isBlank()) {
                if (paymentRepository.findByTransactionCode(transactionNo).isPresent()) {
                    return true;
                }
            }

            String[] parts = vnpayTxnRef.split("_");
            Long invoiceId = Long.parseLong(parts[1]);

            Invoice invoice = invoiceRepository.findById(invoiceId).orElse(null);
            if (invoice == null || invoice.getStatus() == com.pms.propertymanagement.enums.InvoiceStatus.CANCELLED) {
                return false;
            }

            if (invoice.getStatus() == com.pms.propertymanagement.enums.InvoiceStatus.PAID) {
                return true;
            }

            String vnpAmountStr = returnParams.get("vnp_Amount");
            double paidAmount = (double) Math.round((vnpAmountStr != null ? Double.parseDouble(vnpAmountStr) : 0.0) / 100.0);
            double expectedRemaining = (double) Math.round(invoice.getRemainingAmount() != null ? invoice.getRemainingAmount() : 0.0);
            if (paidAmount <= 0 || paidAmount > expectedRemaining + 1.0) {
                return false;
            }

            Payment payment = new Payment();
            payment.setInvoice(invoice);
            payment.setContract(invoice.getContract());
            payment.setOwner(invoice.getRoom().getProperty().getOwner());
            payment.setAmount(paidAmount);
            payment.setPaymentDate(LocalDateTime.now());
            payment.setPaymentMethod(PaymentMethod.VNPAY);
            payment.setTransactionCode(transactionNo);
            payment.setNote("Thanh toán trực tuyến qua cổng VNPay");
            payment.setStatus(PaymentStatus.PAID);
            payment.setCreatedAt(LocalDateTime.now());
            paymentRepository.save(payment);

            double newPaid = (double) Math.round((invoice.getPaidAmount() != null ? invoice.getPaidAmount() : 0.0) + paidAmount);
            invoice.setPaidAmount(newPaid);
            invoice.setRemainingAmount((double) Math.round(Math.max(0.0, invoice.getTotalAmount() - newPaid)));
            invoice.updateStatus();
            invoice.setUpdatedAt(LocalDateTime.now());
            invoiceRepository.save(invoice);

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
