package com.pms.propertymanagement.service.scheduled;

import com.pms.propertymanagement.entity.Contract;
import com.pms.propertymanagement.entity.Room;
import com.pms.propertymanagement.entity.Subscription;
import com.pms.propertymanagement.entity.Post;
import com.pms.propertymanagement.enums.ContractStatus;
import com.pms.propertymanagement.enums.RoomStatus;
import com.pms.propertymanagement.enums.SubscriptionStatus;
import com.pms.propertymanagement.enums.SubscriptionType;
import com.pms.propertymanagement.repository.ContractRepository;
import com.pms.propertymanagement.repository.PostRepository;
import com.pms.propertymanagement.repository.RoomRepository;
import com.pms.propertymanagement.service.PropertyPolicyService;
import com.pms.propertymanagement.service.SubscriptionPolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled service for processing subscription expirations
 * Runs every 5 minutes to check for expired subscriptions
 * 
 * CRITICAL CONSTRAINTS:
 * - Must be idempotent (safe to run multiple times)
 * - Only affects ACTIVE properties (never touches already locked ones)
 * - Checks for other active management subscriptions before property locking
 * - Batch processing for performance
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(isolation = Isolation.READ_COMMITTED, timeout = 300) // 5 minute timeout
public class SubscriptionExpirationScheduler {

    private final SubscriptionPolicyService subscriptionPolicyService;
    private final PropertyPolicyService propertyPolicyService;
    private final PostRepository postRepository;  // Added for post expiration handling
    private final ContractRepository contractRepository;
    private final RoomRepository roomRepository;

    /**
     * Process expired subscriptions every 5 minutes
     * Handles both MANAGEMENT and POST subscription types
     * CRITICAL: Also updates Post status synchronization
     */
    @Scheduled(fixedRate = 300000) // 5 minutes = 300,000 milliseconds
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void processExpiredSubscriptions() {
        LocalDateTime startTime = LocalDateTime.now();
        log.info("Starting subscription expiration processing at {}", startTime);

        try {
            // STEP 1: Process subscription expiration
            processSubscriptionExpiration();
            
            // STEP 2: CRITICAL - Synchronize Post status with expiration
            synchronizePostExpiration();

            // STEP 3: Expire overdue contracts and release rooms
            processExpiredContracts();

            LocalDateTime endTime = LocalDateTime.now();
            log.info("Full expiration processing completed in {} ms",
                    java.time.Duration.between(startTime, endTime).toMillis());

        } catch (Exception e) {
            log.error("Critical error in expiration processing", e);
        }
    }

    private void processExpiredContracts() {
        try {
            LocalDate today = LocalDate.now();
            List<Contract> activeContracts = contractRepository.findByStatus(ContractStatus.ACTIVE);
            int expiredCount = 0;
            for (Contract contract : activeContracts) {
                if (contract.getEndDate() != null && contract.getEndDate().isBefore(today)) {
                    contract.setStatus(ContractStatus.EXPIRED);
                    contract.setUpdatedAt(LocalDateTime.now());
                    contractRepository.save(contract);

                    Room room = contract.getRoom();
                    if (room != null && room.getStatus() == RoomStatus.RENTED) {
                        room.setStatus(RoomStatus.AVAILABLE);
                        roomRepository.save(room);
                    }
                    expiredCount++;
                }
            }
            if (expiredCount > 0) {
                log.info("Expired {} overdue contracts and released rooms", expiredCount);
            }
        } catch (Exception e) {
            log.error("Error processing expired contracts", e);
        }
    }
    
    /**
     * Process subscription expiration (separated for clarity)
     */
    private void processSubscriptionExpiration() {
            LocalDateTime startTime = LocalDateTime.now();
            
            // Find all subscriptions that are marked ACTIVE but have expired
            List<Subscription> expiredSubscriptions = subscriptionPolicyService.findExpiredActiveSubscriptions();
            
            if (expiredSubscriptions.isEmpty()) {
                log.debug("No expired subscriptions found");
                return;
            }

            log.info("Found {} expired subscriptions to process", expiredSubscriptions.size());

            int managementProcessed = 0;
            int postProcessed = 0;
            int errors = 0;

            for (Subscription subscription : expiredSubscriptions) {
                try {
                    processExpiredSubscription(subscription);
                    
                    if (subscription.getType() == SubscriptionType.MANAGEMENT) {
                        managementProcessed++;
                    } else {
                        postProcessed++;
                    }
                } catch (Exception e) {
                    log.error("Error processing expired subscription ID {}: {}", 
                             subscription.getId(), e.getMessage(), e);
                    errors++;
                }
            }

            LocalDateTime endTime = LocalDateTime.now();
            log.info("Subscription expiration processing completed. " +
                    "Management: {}, Post: {}, Errors: {}, Duration: {} ms",
                    managementProcessed, postProcessed, errors,
                    java.time.Duration.between(startTime, endTime).toMillis());
    }
    
    /**
     * CRITICAL: Synchronize Post status with actual expiration time
     * This fixes the issue where posts remain ACTIVE even after expiration
     */
    private void synchronizePostExpiration() {
        try {
            LocalDateTime now = LocalDateTime.now();
            
            // Find all ACTIVE posts that have actually expired
            List<Post> expiredPosts = postRepository.findExpiredActivePosts(now);
            
            if (!expiredPosts.isEmpty()) {
                log.info("Found {} posts that need status synchronization", expiredPosts.size());
                
                int updated = 0;
                for (Post post : expiredPosts) {
                    if (post.autoExpireIfNeeded()) {
                        updated++;
                    }
                }
                
                if (updated > 0) {
                    postRepository.saveAll(expiredPosts);
                    log.info("Updated {} post statuses to EXPIRED", updated);
                }
            }
        } catch (Exception e) {
            log.error("Error synchronizing post expiration", e);
        }
    }

    /**
     * Process individual expired subscription
     * Handles MANAGEMENT and POST subscriptions differently
     */
    private void processExpiredSubscription(Subscription subscription) {
        log.debug("Processing expired subscription: ID={}, Type={}, User={}", 
                 subscription.getId(), subscription.getType(), subscription.getUserId());

        // Mark subscription as expired
        subscription.setStatus(SubscriptionStatus.EXPIRED);
        subscription.setUpdatedAt(LocalDateTime.now());

        if (subscription.getType() == SubscriptionType.MANAGEMENT) {
            processExpiredManagementSubscription(subscription);
        } else if (subscription.getType() == SubscriptionType.POST) {
            processExpiredPostSubscription(subscription);
        }
    }

    /**
     * Handle expired MANAGEMENT subscription
     * CRITICAL: Only lock properties if no other active management exists
     */
    private void processExpiredManagementSubscription(Subscription subscription) {
        Long userId = subscription.getUserId();
        
        // Check if user has other active MANAGEMENT subscription
        boolean hasOtherActiveManagement = subscriptionPolicyService.hasOtherActiveManagementSubscription(
            userId, subscription.getId()
        );

        if (hasOtherActiveManagement) {
            log.info("User {} has other active management subscription, skipping property locking", userId);
            return;
        }

        // No other active management subscription - lock all active properties
        log.info("Locking all active properties for user {} due to management subscription expiration", userId);
        
        try {
            propertyPolicyService.lockAllActiveProperties(userId);
            log.info("Successfully locked properties for user {}", userId);
        } catch (Exception e) {
            log.error("Failed to lock properties for user {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Handle expired POST subscription  
     * Posts will automatically not show in marketplace due to expiredAt checking
     * No immediate action needed - marketplace queries handle expiration
     */
    private void processExpiredPostSubscription(Subscription subscription) {
        log.debug("POST subscription expired for user {}, marketplace will handle post visibility", 
                 subscription.getUserId());
        
        // Post expiration is handled by marketplace queries checking postExpiredAt
        // No additional action needed here
    }

    /**
     * Health check method to verify scheduler is running
     * Logs every hour to confirm the scheduler is active
     */
    @Scheduled(fixedRate = 3600000) // 1 hour = 3,600,000 milliseconds
    public void healthCheck() {
        log.info("Subscription expiration scheduler health check - Running normally at {}", 
                LocalDateTime.now());
    }

    /**
     * Send reminder notifications for subscriptions expiring soon
     * Runs daily at 9:00 AM
     * FIXED: Now properly finds subscriptions expiring in future (not already expired)
     */
    @Scheduled(cron = "0 0 9 * * ?")
    @Transactional(readOnly = true)
    public void sendExpirationReminders() {
        try {
            // FIXED LOGIC: Find ACTIVE subscriptions expiring in next 7 days
            List<Subscription> expiringSoon = subscriptionPolicyService.findExpiringWithinDays(7);
            
            int managementExpiring = 0;
            int postExpiring = 0;
            
            for (Subscription sub : expiringSoon) {
                if (sub.isExpiringSoon(7)) {  // Double-check with entity method
                    if (sub.isManagement()) {
                        managementExpiring++;
                    } else {
                        postExpiring++;
                    }
                }
            }
            
            log.info("Found {} subscriptions expiring soon - Management: {}, Post: {}", 
                    expiringSoon.size(), managementExpiring, postExpiring);
            
            // TODO: Integrate with email/SMS notification service
            // TODO: Add user preference checking for notifications
        } catch (Exception e) {
            log.error("Error checking expiring subscriptions for reminders", e);
        }
    }
}
