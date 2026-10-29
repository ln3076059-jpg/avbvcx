package com.pms.propertymanagement;

import com.pms.propertymanagement.dto.ApiStatisticDTO;
import com.pms.propertymanagement.dto.request.ClassifyIdRequest;
import com.pms.propertymanagement.dto.response.ClassifyIdResponse;
import com.pms.propertymanagement.entity.ApiLog;
import com.pms.propertymanagement.repository.ApiLogRepository;
import com.pms.propertymanagement.service.AiService;
import com.pms.propertymanagement.service.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@SpringBootTest
public class ApiStatisticsIntegrationTest {

    @Autowired
    private AiService aiService;

    @Autowired
    private StatisticsService statisticsService;

    @Autowired
    private ApiLogRepository apiLogRepository;

    @MockitoBean
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        apiLogRepository.deleteAll();
    }

    @Test
    void testApiStatistics() {
        // 1. Simulate a successful API call
        ClassifyIdResponse mockResponse = new ClassifyIdResponse();
        Mockito.when(restTemplate.postForEntity(
                ArgumentMatchers.contains("/ai/v1/classify/id"),
                any(),
                eq(ClassifyIdResponse.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        aiService.classifyId(new ClassifyIdRequest());

        // 2. Simulate a client error (400)
        Mockito.when(restTemplate.postForEntity(
                ArgumentMatchers.contains("/ai/v1/classify/id"),
                any(),
                eq(ClassifyIdResponse.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request"));

        try {
            aiService.classifyId(new ClassifyIdRequest());
        } catch (Exception e) {
            // expected
        }

        // 3. Simulate a system error (500) - generic exception
        Mockito.when(restTemplate.postForEntity(
                ArgumentMatchers.contains("/ai/v1/classify/id"),
                any(),
                eq(ClassifyIdResponse.class)
        )).thenThrow(new RuntimeException("System Error"));

        try {
            aiService.classifyId(new ClassifyIdRequest());
        } catch (Exception e) {
            // expected
        }

        // 4. Verify Logs
        List<ApiLog> logs = apiLogRepository.findAll();
        assertEquals(3, logs.size());

        // 5. Verify Statistics
        List<ApiStatisticDTO> stats = statisticsService.getApiStatistics();
        assertEquals(1, stats.size()); // All calls were to "Classify ID" (or whatever name I gave it)

        ApiStatisticDTO stat = stats.get(0);
        assertEquals("Classify ID", stat.getApiName());
        assertEquals("/ai/v1/classify/id", stat.getPath());
        assertEquals(3, stat.getTotal());
        assertEquals(1, stat.getSuccess());
        assertEquals(1, stat.getInputError()); // 400
        assertEquals(0, stat.getClientError()); // 4xx (excluding 400)
        assertEquals(1, stat.getSystemError()); // 500
    }
}
