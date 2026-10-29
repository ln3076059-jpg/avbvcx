package com.pms.propertymanagement.config.init;

import com.pms.propertymanagement.entity.*;
import com.pms.propertymanagement.enums.*;
import com.pms.propertymanagement.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class LargeScaleDataInitializer {

    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final ContractRepository contractRepository;
    private final TenantRepository tenantRepository;
    private final PaymentRepository paymentRepository;
    private final PostingOrderRepository postingOrderRepository;
    private final PostingPackageRepository postingPackageRepository;
    private final CategoryRepository categoryRepository;
    private final WardRepository wardRepository;
    private final AmenityRepository amenityRepository;

    @Transactional
    public void init() {
        log.info("Starting Large Scale Data Initialization...");
        
        // 1. Ensure we have owners to work with
        List<User> owners = userRepository.findByRoles_Name("OWNER");
        if (owners.isEmpty()) return;

        User owner1 = userRepository.findByUsername("owner1").orElse(null);
        
        // 2. Generate Properties and Contracts for ALL owners (including owner1)
        for (User owner : owners) {
            // Check if owner already has properties
            if (propertyRepository.countByOwnerId(owner.getId()) == 0) {
                generatePropertiesForOwner(owner);
            }
        }

        // 3. Generate Historical Income for Owner 1 (Payments linked to contracts)
        if (owner1 != null) {
            generateHistoricalPayments(owner1);
        }

        // 4. Generate Historical Revenue for Admin (Posting Orders)
        generateHistoricalPostingOrders(owners);
        
        log.info("Large Scale Data Initialization Completed.");
    }

    private void generateHistoricalPayments(User owner) {
        if (paymentRepository.count() > 0) return; // Skip if data exists

        // Get contracts for this owner
        List<Contract> contracts = contractRepository.findByRoom_Property_Owner_Id(owner.getId());
        if (contracts.isEmpty()) return;

        Random random = new Random();
        List<Payment> payments = new ArrayList<>();
        
        // Generate for past 6 months
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 6; i++) {
            LocalDate monthDate = today.minusMonths(i);
            int year = monthDate.getYear();
            int month = monthDate.getMonthValue();
            
            // Random number of payments per month (5-15)
            int numPayments = 5 + random.nextInt(11);
            
            for (int j = 0; j < numPayments; j++) {
                Payment payment = new Payment();
                payment.setOwner(owner);
                
                // Pick a random contract
                Contract contract = contracts.get(random.nextInt(contracts.size()));
                payment.setContract(contract);
                
                // Random amount based on contract rent price
                double amount = contract.getRentPrice();
                payment.setAmount(amount);
                
                // Random day in month
                int day = 1 + random.nextInt(monthDate.lengthOfMonth());
                payment.setPaymentDate(LocalDateTime.of(year, month, day, 8 + random.nextInt(12), random.nextInt(60)));
                
                payment.setStatus(PaymentStatus.PAID);
                payments.add(payment);
            }
        }
        paymentRepository.saveAll(payments);
        log.info("Generated {} historical payments for owner1.", payments.size());
    }

    private void generateHistoricalPostingOrders(List<User> owners) {
        if (postingOrderRepository.count() > 0) return;

        List<PostingPackage> packages = postingPackageRepository.findAll();
        if (packages.isEmpty()) return;

        Random random = new Random();
        List<PostingOrder> orders = new ArrayList<>();

        // Generate for past 6 months
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 6; i++) {
            LocalDate monthDate = today.minusMonths(i);
            
            // Random number of orders per month (10-30) across all owners
            int numOrders = 10 + random.nextInt(21);
            
            for (int j = 0; j < numOrders; j++) {
                User randomOwner = owners.get(random.nextInt(owners.size()));
                PostingPackage randomPackage = packages.get(random.nextInt(packages.size()));
                
                PostingOrder order = new PostingOrder();
                order.setOwner(randomOwner);
                order.setPostingPackage(randomPackage);
                order.setAmount(randomPackage.getPrice());
                order.setStatus(PaymentStatus.PAID);
                order.setRemainingUses(randomPackage.getUsageLimit());
                
                // Random day in month
                int day = 1 + random.nextInt(monthDate.lengthOfMonth());
                LocalDateTime orderDate = LocalDateTime.of(monthDate.getYear(), monthDate.getMonthValue(), day, random.nextInt(23), random.nextInt(59));
                order.setCreatedAt(orderDate);
                order.setPaidAt(orderDate);
                
                // Fake VNPay fields
                order.setVnpTxnRef(UUID.randomUUID().toString());
                order.setVnpTransactionNo(String.valueOf(System.currentTimeMillis() + random.nextInt(100000)));
                order.setVnpResponseCode("00");
                order.setVnpBankCode("NCB");
                
                orders.add(order);
            }
        }
        postingOrderRepository.saveAll(orders);
        log.info("Generated {} historical posting orders.", orders.size());
    }

    private void generatePropertiesForOwner(User owner) {
        List<Category> categories = categoryRepository.findAll();
        List<Ward> wards = wardRepository.findAll();
        List<Amenity> amenities = amenityRepository.findAll();
        
        if (categories.isEmpty() || wards.isEmpty()) return;
        
        Random random = new Random();
        List<Property> properties = new ArrayList<>();
        
        // Create 3-5 properties for each owner
        int numProps = 3 + random.nextInt(3);
        
        for (int i = 0; i < numProps; i++) {
            Property p = new Property();
            Category cat = categories.get(random.nextInt(categories.size()));
            Ward ward = wards.get(random.nextInt(wards.size()));
            
            p.setOwner(owner);
            p.setCategory(cat);
            p.setWard(ward);
            p.setName(cat.getName() + " " + owner.getUsername() + " " + (i+1));
            p.setAddressNumber((10 + random.nextInt(100)) + " Đường số " + (1 + random.nextInt(20)));
            p.setAcreage(20 + random.nextDouble() * 50);
            p.setPrice(1500000 + random.nextInt(50) * 100000);
            p.setNumberOfRooms(5 + random.nextInt(10));
            p.setCreatedAt(LocalDateTime.now().minusDays(random.nextInt(60)));
            p.setUpdatedAt(LocalDateTime.now());
            
            // Add amenities
            if (!amenities.isEmpty()) {
                Collections.shuffle(amenities);
                p.setAmenities(new HashSet<>(amenities.subList(0, Math.min(amenities.size(), 2 + random.nextInt(3)))));
            }

            String[] sampleImages = {
                "/images/rooms/room-1.jpg", "/images/rooms/room-2.jpg", "/images/rooms/room-3.jpg", "/images/rooms/room-4.jpg",
                "/images/rooms/room-5.jpg", "/images/rooms/room-6.jpg", "/images/rooms/room-7.jpg", "/images/rooms/room-8.jpg",
                "/images/rooms/room-9.jpg", "/images/rooms/room-10.jpg", "/images/rooms/room-11.jpg", "/images/rooms/room-12.jpg"
            };
            List<PropertyImage> propImages = new ArrayList<>();
            int baseIdx = random.nextInt(sampleImages.length);
            for (int imgIdx = 0; imgIdx < 4; imgIdx++) {
                PropertyImage img = new PropertyImage();
                img.setImageUrl(sampleImages[(baseIdx + imgIdx) % sampleImages.length]);
                img.setIsPrimary(imgIdx == 0);
                img.setProperty(p);
                propImages.add(img);
            }
            p.setImages(propImages);
            
            properties.add(p);
        }
        
        propertyRepository.saveAll(properties);
        
        List<Room> rooms = new ArrayList<>();
        for (Property p : properties) {
            for (int r = 1; r <= p.getNumberOfRooms(); r++) {
                Room room = new Room();
                room.setName("Phòng " + r);
                room.setProperty(p);
                room.setPrice((double) p.getPrice());
                room.setArea(p.getAcreage()); // Assuming acreage is per room or total? logic in PropertyInitializer says acreage is total. Let's assume per room for simplicity or divide.
                room.setMaxOccupancy(2);
                room.setBedCount(1);
                room.setDeposit(p.getPrice() * 1.0);
                room.setPaymentCycle(1);
                
                // Random status
                boolean isRented = random.nextDouble() > 0.3;
                if (isRented) {
                    room.setStatus(RoomStatus.RENTED);
                } else {
                    room.setStatus(RoomStatus.AVAILABLE);
                }
                
                room.setCreatedAt(LocalDateTime.now());
                rooms.add(room);
            }
        }
        roomRepository.saveAll(rooms);
        
        // Create contracts for rented rooms
        List<Contract> contracts = new ArrayList<>();
        for (Room room : rooms) {
            if (room.getStatus() == RoomStatus.RENTED) {
                 contracts.add(createContractEntityForRoom(room, owner));
            }
        }
        contractRepository.saveAll(contracts);
        
        log.info("Generated properties and rooms for {}", owner.getUsername());
    }

    private Contract createContractEntityForRoom(Room room, User owner) {
        Tenant t = new Tenant();
        t.setOwner(owner);
        t.setFullName("Tenant " + UUID.randomUUID().toString().substring(0, 5));
        t.setPhone("09" + (10000000 + new Random().nextInt(89999999)));
        t.setCitizenId("ID" + UUID.randomUUID().toString().substring(0, 10));
        tenantRepository.save(t);
        
        Contract c = new Contract();
        c.setCode("HD-" + UUID.randomUUID());
        c.setRoom(room);
        c.setStartDate(LocalDate.now().minusMonths(1));
        c.setEndDate(LocalDate.now().plusMonths(5));
        c.setRentPrice(room.getPrice());
        c.setDeposit(room.getDeposit());
        c.setPaymentCycle(1);
        c.setStatus(ContractStatus.ACTIVE);
        c.setRepresentative(t);
        c.setTenants(new HashSet<>(Collections.singletonList(t)));
        return c;
    }
}
