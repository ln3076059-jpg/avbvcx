package com.pms.propertymanagement.service.impl;

import com.pms.propertymanagement.dto.response.CategoryResponse;
import com.pms.propertymanagement.repository.CategoryRepository;
import com.pms.propertymanagement.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll()
                .stream()
                .map( c -> new CategoryResponse(c.getId(),c.getName()))
                .toList();
    }
}
