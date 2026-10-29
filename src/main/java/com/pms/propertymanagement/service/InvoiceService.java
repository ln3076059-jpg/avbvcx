package com.pms.propertymanagement.service;

import com.pms.propertymanagement.dto.request.BatchInvoiceRequest;
import com.pms.propertymanagement.dto.request.InvoiceRequest;
import com.pms.propertymanagement.entity.Invoice;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.enums.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface InvoiceService {

    Page<Invoice> searchInvoices(Long ownerId, Long propertyId, Long roomId, Long tenantId, Integer month, Integer year, InvoiceStatus status, Pageable pageable);

    Page<Invoice> searchInvoicesForOwner(Long ownerId, Long propertyId, InvoiceStatus status, Integer month, Integer year, String keyword, Pageable pageable);

    Invoice getInvoiceById(Long id, Long ownerId);

    Invoice getInvoiceByIdForTenant(Long id, User tenantUser);

    Invoice createInvoice(InvoiceRequest request, Long ownerId);

    Invoice updateInvoice(Long id, InvoiceRequest request, Long ownerId);

    int generateBatchInvoices(BatchInvoiceRequest request, Long ownerId);

    void deleteInvoice(Long id, Long ownerId);

    List<Invoice> getInvoicesForTenant(User tenantUser);

    void notifyTransfer(Long invoiceId, User tenantUser, String transferNote);

    Optional<Invoice> getInvoiceByRoomAndMonthAndYear(Long roomId, Integer month, Integer year);
}
