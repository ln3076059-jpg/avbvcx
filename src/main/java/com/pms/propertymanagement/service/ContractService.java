package com.pms.propertymanagement.service;

import com.pms.propertymanagement.dto.request.ContractRequest;
import com.pms.propertymanagement.entity.Contract;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.enums.ContractStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface ContractService {
    Contract createContract(ContractRequest contractRequest);
    Contract createContract(ContractRequest contractRequest, Long ownerId);
    Page<Contract> getContractsByOwner(Long ownerId, ContractStatus status, String keyword, Pageable pageable);
    Contract getContractById(Long id);
    void updateContractStatus(Long id, ContractStatus status);
    void checkoutContract(com.pms.propertymanagement.dto.request.ContractCheckoutRequest request, Long ownerId);
    void renewContract(Long contractId, LocalDate newEndDate, Double newRentPrice, User owner);
}
