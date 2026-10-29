package com.pms.propertymanagement.config.init;

import com.pms.propertymanagement.entity.*;
import com.pms.propertymanagement.enums.ContractStatus;
import com.pms.propertymanagement.enums.RoomStatus;
import com.pms.propertymanagement.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContractInitializer {

    private final ContractRepository contractRepository;
    private final RoomRepository roomRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    @Transactional
    public void init() {
        User owner = userRepository.findByUsername("owner1").orElse(null);
        if (owner == null) return;

        long currentContracts = contractRepository.count();
        if (currentContracts == 0) {
            List<Tenant> tenants = tenantRepository.findByOwner_Id(owner.getId());
            if (tenants.isEmpty()) return;

            List<Room> allRooms = roomRepository.findByProperty_Owner_Id(owner.getId());
            List<Room> rentedRooms = allRooms.stream()
                    .filter(r -> r.getStatus() == RoomStatus.RENTED)
                    .collect(Collectors.toList());

            if (rentedRooms.isEmpty()) {
                for (int i = 0; i < Math.min(5, allRooms.size()); i++) {
                    Room r = allRooms.get(i);
                    r.setStatus(RoomStatus.RENTED);
                    rentedRooms.add(r);
                }
                roomRepository.saveAll(rentedRooms);
            }

            Random random = new Random();
            boolean assignedFirstTenant = false;

            for (Room room : rentedRooms) {
                Contract contract = new Contract();

                Tenant representative;
                if (!assignedFirstTenant) {
                    representative = tenants.get(0);
                    assignedFirstTenant = true;
                } else if (tenants.size() > 1) {
                    representative = tenants.get(1 + random.nextInt(tenants.size() - 1));
                } else {
                    representative = tenants.get(0);
                }
                contract.setRepresentative(representative);

                Set<Tenant> contractTenants = new HashSet<>();
                contractTenants.add(representative);

                if (room.getMaxOccupancy() > 1 && tenants.size() > 2 && random.nextBoolean()) {
                    Tenant another = tenants.get(1 + random.nextInt(tenants.size() - 1));
                    if (!another.getId().equals(representative.getId())) {
                        contractTenants.add(another);
                    }
                }
                contract.setTenants(contractTenants);

                contract.setRoom(room);

                LocalDate startDate = LocalDate.now().minusMonths(random.nextInt(6) + 1);
                LocalDate endDate = startDate.plusMonths(6 + random.nextInt(7));

                contract.setStartDate(startDate);
                contract.setEndDate(endDate);

                contract.setRentPrice(room.getPrice());
                contract.setDeposit(room.getDeposit() != null ? room.getDeposit() : room.getPrice());
                contract.setPaymentCycle(room.getPaymentCycle() != null ? room.getPaymentCycle() : 1);
                contract.setIsElectricityWaterIncluded(room.getIsElectricityWaterIncluded());
                contract.setBedCount(room.getBedCount());
                contract.setCode("HD-" + room.getId() + "-" + System.currentTimeMillis() % 100000);

                if (room.getServices() != null) {
                    contract.setServices(new HashSet<>(room.getServices()));
                }

                if (endDate.isBefore(LocalDate.now())) {
                    contract.setStatus(ContractStatus.EXPIRED);
                } else if (endDate.isBefore(LocalDate.now().plusDays(30))) {
                    contract.setStatus(ContractStatus.EXPIRING_SOON);
                } else {
                    contract.setStatus(ContractStatus.ACTIVE);
                }

                contract.setNote("Hợp đồng tạo mẫu tự động.");

                contractRepository.save(contract);
            }

            log.info("Generated {} contracts for owner1.", rentedRooms.size());
        }

        ensureTenant1HasActiveContract(owner);
    }

    private void ensureTenant1HasActiveContract(User owner) {
        User tenantUser = userRepository.findByUsername("tenant1").orElse(null);
        if (tenantUser == null) return;

        Optional<Tenant> tenantOpt = Optional.empty();
        if (tenantUser.getPhone() != null && !tenantUser.getPhone().trim().isEmpty()) {
            tenantOpt = tenantRepository.findFirstByPhone(tenantUser.getPhone().trim());
        }
        if (tenantOpt.isEmpty() && tenantUser.getEmail() != null && !tenantUser.getEmail().trim().isEmpty()) {
            tenantOpt = tenantRepository.findFirstByEmail(tenantUser.getEmail().trim());
        }
        if (tenantOpt.isEmpty()) {
            Tenant t = new Tenant();
            t.setOwner(owner);
            t.setFullName(tenantUser.getFullName() != null ? tenantUser.getFullName() : tenantUser.getUsername());
            t.setPhone(tenantUser.getPhone() != null ? tenantUser.getPhone() : "0900000000");
            t.setEmail(tenantUser.getEmail());
            t.setCitizenId("SEED" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            tenantOpt = Optional.of(tenantRepository.save(t));
        }

        Tenant tenantProfile = tenantOpt.get();
        if (contractRepository.countActiveContractsByTenantId(tenantProfile.getId()) > 0) return;

        List<Room> ownerRooms = roomRepository.findByProperty_Owner_Id(owner.getId());
        Room selected = ownerRooms.stream()
                .filter(r -> r.getStatus() == RoomStatus.AVAILABLE)
                .filter(r -> contractRepository.countActiveContractsByRoomId(r.getId()) == 0)
                .findFirst()
                .orElseGet(() -> ownerRooms.stream()
                        .filter(r -> contractRepository.countActiveContractsByRoomId(r.getId()) == 0)
                        .findFirst()
                        .orElse(null));

        if (selected == null) return;

        selected.setStatus(RoomStatus.RENTED);
        roomRepository.save(selected);

        Contract contract = new Contract();
        contract.setCode("HD-TENANT1-" + UUID.randomUUID());
        contract.setRoom(selected);
        contract.setStartDate(LocalDate.now().minusMonths(1));
        contract.setEndDate(LocalDate.now().plusMonths(11));
        contract.setRentPrice(selected.getPrice());
        contract.setDeposit(selected.getDeposit() != null ? selected.getDeposit() : selected.getPrice());
        contract.setPaymentCycle(selected.getPaymentCycle() != null ? selected.getPaymentCycle() : 1);
        contract.setIsElectricityWaterIncluded(selected.getIsElectricityWaterIncluded());
        contract.setBedCount(selected.getBedCount());
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setRepresentative(tenantProfile);
        contract.setTenants(new HashSet<>(List.of(tenantProfile)));
        if (selected.getServices() != null) {
            contract.setServices(new HashSet<>(selected.getServices()));
        }
        contract.setNote("Seed: tạo hợp đồng ACTIVE cho tenant1.");
        contractRepository.save(contract);
    }
}
