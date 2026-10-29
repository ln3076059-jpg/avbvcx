package com.pms.propertymanagement.service;

import com.pms.propertymanagement.entity.PostingOrder;

import java.util.Map;

public interface VnPayService {

    String createPaymentUrl(PostingOrder order, String ipAddress, String returnUrlOverride);
    
    String createWalletDepositUrl(Long amount, String txnRef, String returnUrl, String ipAddress);
    
    String createCustomPaymentUrl(Long amount, String txnRef, String orderInfo, String returnUrl, String ipAddress);

    VnPayReturnResult handleReturn(Map<String, String> returnParams, String rawQuery);

    class VnPayReturnResult {
        public final boolean validSignature;
        public final boolean success;
        public final String message;
        public final Long orderId;

        public VnPayReturnResult(boolean validSignature, boolean success, String message, Long orderId) {
            this.validSignature = validSignature;
            this.success = success;
            this.message = message;
            this.orderId = orderId;
        }
    }
}