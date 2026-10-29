package com.pms.propertymanagement.service;

import com.pms.propertymanagement.dto.request.ContactRequest;
import com.pms.propertymanagement.dto.response.ContactResponse;
import com.pms.propertymanagement.entity.Contract;
import com.pms.propertymanagement.entity.Contact;
import com.pms.propertymanagement.entity.User;

import java.time.LocalDate;
import java.util.List;

public interface ContactService {
    List<ContactResponse> getContactsByOwner(Long ownerId);

    void createContact(String propertySlug, ContactRequest contactRequest);

    Contact createRentBooking(String propertySlug,
                              Long roomId,
                              User customer,
                              String name,
                              String phone,
                              String citizenId,
                              LocalDate moveInDate,
                              Integer durationMonths,
                              Integer occupants,
                              String formattedNote);

    Contract approveAndCreateContractAutomatically(Long contactId, Long actorOwnerId, boolean isAdmin);

    void updateBookingStatus(Long contactId, String newStatus, Long actorOwnerId, boolean isAdmin);

    void changeStatus(Long contactId);

    void changeStatus(Long contactId, Long ownerId);

    void markAsContacted(Long contactId, Long ownerId);
}
