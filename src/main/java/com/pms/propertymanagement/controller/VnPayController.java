package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.entity.UserWallet;
import com.pms.propertymanagement.service.VnPayService;
import com.pms.propertymanagement.service.WalletService;
import com.pms.propertymanagement.utils.VnPayUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/payment/vnpay")
@RequiredArgsConstructor
public class VnPayController {

    private final VnPayService vnPayService;
    private final WalletService walletService;
    private final com.pms.propertymanagement.service.PaymentService paymentService;

    @Value("${vnpay.hashSecret}")
    private String hashSecret;

    @GetMapping("/return")
    public String vnpayReturn(HttpServletRequest request, HttpSession session, RedirectAttributes ra) {
        Map<String, String> params = VnPayUtil.extractRequestParams(request.getParameterMap());
        String rawQuery = request.getQueryString();
        String txnRef = params.get("vnp_TxnRef");
        
        // 1. Xử lý thanh toán hóa đơn khách thuê
        if (txnRef != null && txnRef.startsWith("INV_")) {
            boolean success = paymentService.processVnpayInvoiceCallback(params, rawQuery);
            String[] parts = txnRef.split("_");
            String invoiceId = parts.length > 1 ? parts[1] : "";
            if (success) {
                ra.addFlashAttribute("successMessage", "Thanh toán hóa đơn qua VNPay thành công!");
            } else {
                ra.addFlashAttribute("errorMessage", "Thanh toán hóa đơn qua VNPay không thành công hoặc đã bị hủy.");
            }
            return "redirect:/tenant/invoices/" + invoiceId;
        }

        // 2. Xử lý nạp tiền ví chủ trọ
        if (txnRef != null && txnRef.startsWith("WALLET_")) {
            boolean success = walletService.processVnpayCallback(params, rawQuery);
            
            if (success) {
                User user = (User) session.getAttribute("user");
                if (user != null) {
                    UserWallet updatedWallet = walletService.getOrCreateWallet(user);
                    session.setAttribute("userWallet", updatedWallet);
                    session.setAttribute("lastWalletUpdate", System.currentTimeMillis());
                }
                
                ra.addFlashAttribute("successMessage", "Nạp tiền vào ví thành công!");
            } else {
                ra.addFlashAttribute("errorMessage", "Nạp tiền thất bại. Vui lòng thử lại.");
            }
            
            return "redirect:/owner/wallet";
        } else {
            // 3. Xử lý thanh toán gói tin kiểu cũ
            VnPayService.VnPayReturnResult result = vnPayService.handleReturn(params, rawQuery);

            if (!result.validSignature) {
                ra.addFlashAttribute("errorMessage", result.message);
                return "redirect:/owner/properties";
            }

            if (result.success) {
                ra.addFlashAttribute("successMessage", "Thanh Toán Thành Công");
            } else {
                ra.addFlashAttribute("errorMessage", result.message);
            }

            return "redirect:/owner/properties";
        }
    }

    /**
     * IPN Webhook Server-to-Server để VNPay đồng bộ tự động trạng thái ngầm
     */
    @GetMapping("/ipn")
    @org.springframework.web.bind.annotation.ResponseBody
    public Map<String, String> vnpayIpn(HttpServletRequest request) {
        Map<String, String> params = VnPayUtil.extractRequestParams(request.getParameterMap());
        String rawQuery = request.getQueryString();
        String txnRef = params.get("vnp_TxnRef");

        if (!VnPayUtil.verifySecureHash(params, hashSecret)) {
            return Map.of("RspCode", "97", "Message", "Invalid Checksum");
        }

        if (txnRef == null) {
            return Map.of("RspCode", "99", "Message", "Unknown error");
        }

        if (txnRef.startsWith("INV_")) {
            boolean success = paymentService.processVnpayInvoiceCallback(params, rawQuery);
            return success ? Map.of("RspCode", "00", "Message", "Confirm Success") 
                           : Map.of("RspCode", "97", "Message", "Invalid Checksum or Transaction Failed");
        } else if (txnRef.startsWith("WALLET_")) {
            boolean success = walletService.processVnpayCallback(params, rawQuery);
            return success ? Map.of("RspCode", "00", "Message", "Confirm Success") 
                           : Map.of("RspCode", "97", "Message", "Invalid Checksum or Transaction Failed");
        } else {
            VnPayService.VnPayReturnResult result = vnPayService.handleReturn(params, rawQuery);
            if (!result.validSignature) {
                return Map.of("RspCode", "97", "Message", "Invalid Checksum");
            }
            return result.success ? Map.of("RspCode", "00", "Message", "Confirm Success")
                                  : Map.of("RspCode", "01", "Message", "Order not found or payment failed");
        }
    }
}
