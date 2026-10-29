package com.pms.propertymanagement.repository;

import com.pms.propertymanagement.entity.Contact;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {

    @EntityGraph(attributePaths = {"property", "owner", "room", "customer"})
    List<Contact> findByOwnerId(Long ownerId);

    @EntityGraph(attributePaths = {"property", "owner", "room", "customer"})
    List<Contact> findByOwnerIdOrderByIdDesc(Long ownerId);

    @EntityGraph(attributePaths = {"property", "owner", "room", "customer"})
    List<Contact> findByProperty_Owner_IdOrderByIdDesc(Long ownerId);

    @EntityGraph(attributePaths = {"property", "owner", "room", "customer"})
    List<Contact> findByPhoneOrderByIdDesc(String phone);

    @EntityGraph(attributePaths = {"property", "owner", "room", "customer"})
    List<Contact> findByCustomer_IdOrderByIdDesc(Long customerId);

    @EntityGraph(attributePaths = {"property", "owner", "room", "customer"})
    List<Contact> findByProperty_IdAndPhoneOrderByIdDesc(Long propertyId, String phone);

    @EntityGraph(attributePaths = {"property", "owner", "room", "customer"})
    List<Contact> findAllByOrderByIdDesc();

    long countByProperty_Id(Long propertyId);
}
