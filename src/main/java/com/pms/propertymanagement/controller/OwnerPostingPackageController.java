package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.entity.PostingOrder;
import com.pms.propertymanagement.entity.PostingPackage;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.service.PostingPackageService;
import com.pms.propertymanagement.service.PostingOrderService;
import com.pms.propertymanagement.service.WalletService;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Locale;

@Controller
@RequestMapping("/owner/posting-packages")
@RequiredArgsConstructor
public class OwnerPostingPackageController {

    private final PostingPackageService postingPackageService;
    private final PostingOrderService postingOrderService;
    private final WalletService walletService;

    @GetMapping
    public String listPackages(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        // Load all posting packages using service
        List<PostingPackage> packages = postingPackageService.findAll();
        model.addAttribute("postingPackages", packages);
        model.addAttribute("walletBalance", walletService.getOrCreateWallet(user).getBalance());

        model.addAttribute("content", "owner/package/list");
        model.addAttribute("activeMenu", "packages");
        return "layout/owner-layout";
    }

    @GetMapping("/buy/{packageId}")
    public String buyPackage(@PathVariable Long packageId, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        PostingOrder order = postingOrderService.createOrderForPackage(user.getId(), packageId);
        return "redirect:/owner/posting-packages/checkout/" + order.getId();
    }

    @GetMapping("/new")
    public String createOrderAndGoCheckout(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        PostingOrder order = postingOrderService.createNewOrderForDefaultPackage(user.getId());
        return "redirect:/owner/posting-packages/checkout/" + order.getId();
    }

    @GetMapping("/checkout/{orderId}")
    public String checkout(@PathVariable Long orderId, Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        PostingOrder order = postingOrderService.getOrderForOwner(orderId, user.getId());
        PostingPackage pkg = order.getPostingPackage();

        NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
        String priceText = nf.format(order.getAmount()) + " VND";

        model.addAttribute("pkg", pkg);
        model.addAttribute("buyerName", user.getFullName());
        model.addAttribute("priceText", priceText);
        model.addAttribute("orderId", order.getId());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("walletBalance", walletService.getOrCreateWallet(user).getBalance());

        model.addAttribute("content", "owner/package/checkout");
        return "layout/owner-layout";
    }

    @PostMapping("/checkout/{orderId}/pay")
    public String pay(@PathVariable Long orderId, HttpSession session, HttpServletRequest request) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login/owner";

        String ipAddress = request.getRemoteAddr();

        // baseUrl động theo domain hiện tại (localhost hoặc ngrok)
        String baseUrl = request.getScheme() + "://" + request.getServerName()
                + ((request.getServerPort() == 80 || request.getServerPort() == 443)
                ? "" : ":" + request.getServerPort());

        String returnUrl = baseUrl + "/payment/vnpay/return";

        String payUrl = postingOrderService.createVnpayPaymentUrl(orderId, user.getId(), ipAddress, returnUrl);
        return "redirect:" + payUrl;
    }
}
