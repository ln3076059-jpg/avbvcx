package com.pms.propertymanagement.service;

import com.pms.propertymanagement.dto.response.CategoryResponse;

import java.util.List;


public interface CategoryService {
    List<CategoryResponse> findAll();
}
