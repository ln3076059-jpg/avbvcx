package com.pms.propertymanagement.repository;

import com.pms.propertymanagement.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {
    List<Tenant> findByOwner_Username(String username);
    List<Tenant> findByOwner_Id(Long ownerId);
    boolean existsByCitizenId(String citizenId);
    Optional<Tenant> findFirstByAccount_Id(Long accountId);
    Optional<Tenant> findFirstByOwner_IdAndPhone(Long ownerId, String phone);
    Optional<Tenant> findFirstByPhone(String phone);
    Optional<Tenant> findFirstByEmail(String email);
}
