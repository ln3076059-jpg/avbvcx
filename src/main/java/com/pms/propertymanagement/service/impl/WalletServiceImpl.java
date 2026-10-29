package com.pms.propertymanagement.service.impl;

import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.entity.UserWallet;
import com.pms.propertymanagement.entity.WalletTransaction;
import com.pms.propertymanagement.enums.TransactionStatus;
import com.pms.propertymanagement.enums.TransactionType;
import com.pms.propertymanagement.repository.UserWalletRepository;
import com.pms.propertymanagement.repository.WalletTransactionRepository;
import com.pms.propertymanagement.service.VnPayService;
import com.pms.propertymanagement.service.WalletService;
import com.pms.propertymanagement.utils.VnPayUtil;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class WalletServiceImpl implements WalletService {
    
    @Value("${vnpay.hashSecret}")
    private String hashSecret;
    
    private final UserWalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final VnPayService vnPayService;
    
    @Override
    @Transactional
    public UserWallet getOrCreateWallet(User user) {
        return walletRepository.findByUser(user)
                .orElseGet(() -> {
                    UserWallet wallet = new UserWallet();
                    wallet.setUser(user);
                    wallet.setBalance(BigDecimal.ZERO);
                    wallet.setTotalDeposited(BigDecimal.ZERO);
                    wallet.setTotalSpent(BigDecimal.ZERO);
                    return walletRepository.save(wallet);
                });
    }
    
    @Override
    public UserWallet getWalletByUser(User user) {
        return walletRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found for user: " + user.getId()));
    }
    
    @Override
    public BigDecimal getCurrentBalance(User user) {
        return walletRepository.findByUser(user)
                .map(UserWallet::getBalance)
                .orElse(BigDecimal.ZERO);
    }
    
    @Override
    @Transactional
    public WalletTransaction deposit(User user, BigDecimal amount, String description, String vnpayTxnRef) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
        
        UserWallet wallet = getOrCreateWallet(user);
        wallet.addBalance(amount);
        walletRepository.save(wallet);
        
        WalletTransaction transaction = new WalletTransaction();
        transaction.setWallet(wallet);
        transaction.setType(TransactionType.DEPOSIT);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(wallet.getBalance());
        transaction.setDescription(description != null ? description : "Nạp tiền vào ví");
        transaction.setVnpayTxnRef(vnpayTxnRef);
        transaction.setStatus(TransactionStatus.COMPLETED);
        
        return transactionRepository.save(transaction);
    }
    
    @Override
    @Transactional
    public WalletTransaction deduct(User user, BigDecimal amount, TransactionType type, String description, String referenceId) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deduct amount must be positive");
        }
        
        UserWallet wallet = getWalletByUser(user);
        if (!wallet.hasEnoughBalance(amount)) {
            throw new IllegalArgumentException("Insufficient wallet balance");
        }
        
        wallet.deductBalance(amount);
        walletRepository.save(wallet);
        
        WalletTransaction transaction = new WalletTransaction();
        transaction.setWallet(wallet);
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(wallet.getBalance());
        transaction.setDescription(description != null ? description : type.getDisplayName());
        transaction.setReferenceId(referenceId);
        transaction.setStatus(TransactionStatus.COMPLETED);
        
        return transactionRepository.save(transaction);
    }
    
    @Override
    @Transactional
    public WalletTransaction refund(User user, BigDecimal amount, String description, String referenceId) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be positive");
        }
        
        UserWallet wallet = getOrCreateWallet(user);
        wallet.refundBalance(amount);
        walletRepository.save(wallet);
        
        WalletTransaction transaction = new WalletTransaction();
        transaction.setWallet(wallet);
        transaction.setType(TransactionType.REFUND);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(wallet.getBalance());
        transaction.setDescription(description != null ? description : "Hoàn tiền");
        transaction.setReferenceId(referenceId);
        transaction.setStatus(TransactionStatus.COMPLETED);
        
        return transactionRepository.save(transaction);
    }
    
    @Override
    public Page<WalletTransaction> getTransactionHistory(User user, Pageable pageable) {
        UserWallet wallet = getWalletByUser(user);
        return transactionRepository.findByWalletOrderByCreatedAtDesc(wallet, pageable);
    }
    
    @Override
    public List<WalletTransaction> getRecentTransactions(User user, int limit) {
        UserWallet wallet = getWalletByUser(user);
        Pageable pageable = PageRequest.of(0, limit);
        return transactionRepository.findByWalletOrderByCreatedAtDesc(wallet, pageable).getContent();
    }
    
    @Override
    public BigDecimal getTotalDeposited(User user) {
        return walletRepository.findByUser(user)
                .map(UserWallet::getTotalDeposited)
                .orElse(BigDecimal.ZERO);
    }
    
    @Override
    public BigDecimal getTotalSpent(User user) {
        return walletRepository.findByUser(user)
                .map(UserWallet::getTotalSpent)
                .orElse(BigDecimal.ZERO);
    }
    
    @Override
    public BigDecimal getMonthlySpending(User user) {
        UserWallet wallet = walletRepository.findByUser(user).orElse(null);
        if (wallet == null) return BigDecimal.ZERO;
        
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        BigDecimal purchase = transactionRepository.sumAmountByWalletAndTypeAndDateAfter(wallet, TransactionType.PURCHASE, startOfMonth);
        BigDecimal extension = transactionRepository.sumAmountByWalletAndTypeAndDateAfter(wallet, TransactionType.EXTENSION, startOfMonth);
        return (purchase != null ? purchase : BigDecimal.ZERO).add(extension != null ? extension : BigDecimal.ZERO);
    }
    
    @Override
    public boolean hasEnoughBalance(User user, BigDecimal amount) {
        return getCurrentBalance(user).compareTo(amount) >= 0;
    }
    
    @Override
    @Transactional
    public String createDepositUrl(User user, BigDecimal amount, String returnUrl, String ipAddress) {
        // Create pending transaction first
        UserWallet wallet = getOrCreateWallet(user);
        String txnRef = "WALLET_" + System.currentTimeMillis() + "_" + user.getId();
        
        WalletTransaction transaction = new WalletTransaction();
        transaction.setWallet(wallet);
        transaction.setType(TransactionType.DEPOSIT);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(wallet.getBalance()); // Will be updated on success
        transaction.setDescription("Nạp tiền vào ví - " + amount + " VNĐ");
        transaction.setVnpayTxnRef(txnRef);
        transaction.setStatus(TransactionStatus.PENDING);
        
        transactionRepository.save(transaction);
        
        // Create VNPay URL using existing service
        return vnPayService.createWalletDepositUrl(amount.longValue(), txnRef, returnUrl, ipAddress);
    }
    
    @Override
    @Transactional
    public boolean processVnpayCallback(Map<String, String> returnParams, String rawQuery) {
        try {
            // Verify signature first
            boolean validSig = VnPayUtil.verifySecureHash(returnParams, hashSecret);
            if (!validSig) {
                log.warn("Invalid VNPay signature for wallet deposit");
                return false;
            }
            
            String vnpayTxnRef = returnParams.get("vnp_TxnRef");
            String responseCode = returnParams.get("vnp_ResponseCode");
            String transactionNo = returnParams.get("vnp_TransactionNo");
            String bankCode = returnParams.get("vnp_BankCode");
            String payDate = returnParams.get("vnp_PayDate");
            
            if (vnpayTxnRef == null || vnpayTxnRef.isBlank()) {
                log.warn("Missing vnp_TxnRef in wallet deposit callback");
                return false;
            }
            
            // Find pending transaction by VNPay transaction reference
            Optional<WalletTransaction> transactionOpt = transactionRepository
                    .findByVnpayTxnRefAndStatus(vnpayTxnRef, TransactionStatus.PENDING);
            
            if (transactionOpt.isEmpty()) {
                log.warn("No pending wallet transaction found for VNPay ref: {}", vnpayTxnRef);
                return false;
            }
            
            WalletTransaction transaction = transactionOpt.get();
            
            // Update transaction with VNPay response data
            transaction.setVnpayResponseCode(responseCode);
            transaction.setVnpayTransactionNo(transactionNo);
            transaction.setVnpayBankCode(bankCode);
            transaction.setVnpayPayDate(payDate);
            if (rawQuery != null && rawQuery.length() <= 2000) {
                transaction.setVnpayRawQuery(rawQuery);
            }
            
            // Validate amount
            String vnpAmountStr = returnParams.get("vnp_Amount");
            if (vnpAmountStr != null) {
                try {
                    long vnpAmount = Long.parseLong(vnpAmountStr);
                    long expectedAmount = transaction.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
                    
                    if (vnpAmount != expectedAmount) {
                        log.warn("Amount mismatch in wallet deposit. Expected: {}, Received: {}", expectedAmount, vnpAmount);
                        transaction.setStatus(TransactionStatus.FAILED);
                        transactionRepository.save(transaction);
                        return false;
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid vnp_Amount format: {}", vnpAmountStr);
                }
            }
            
            // Check if payment was successful (VNPay response code "00" means success)
            if ("00".equals(responseCode)) {
                // Update transaction status
                transaction.setStatus(TransactionStatus.COMPLETED);
                transaction.setCompletedAt(LocalDateTime.now());
                
                // Add money to wallet
                UserWallet wallet = transaction.getWallet();
                wallet.addBalance(transaction.getAmount());
                
                // Update remaining balance in transaction record
                transaction.setBalanceAfter(wallet.getBalance());
                transaction.setRemainingBalance(wallet.getBalance());
                
                // Save updates
                walletRepository.save(wallet);
                transactionRepository.save(transaction);
                
                log.info("Wallet deposit completed successfully. Amount: {}, User: {}, TxnRef: {}", 
                        transaction.getAmount(), wallet.getUser().getId(), vnpayTxnRef);
                return true;
                
            } else {
                // Payment failed
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setCompletedAt(LocalDateTime.now());
                
                transactionRepository.save(transaction);
                
                log.warn("Wallet deposit failed. Response code: {}, TxnRef: {}", responseCode, vnpayTxnRef);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error processing VNPay wallet callback", e);
            return false;
        }
    }
}
