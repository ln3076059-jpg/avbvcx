package com.pms.propertymanagement.service.impl;

import com.pms.propertymanagement.dto.response.VNPTUploadResponse;
import com.pms.propertymanagement.entity.UploadFile;
import com.pms.propertymanagement.repository.UploadFileRepository;
import com.pms.propertymanagement.service.FileUploadService;
import com.pms.propertymanagement.utils.ImageUtil;
import com.pms.propertymanagement.utils.VnptErrorUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FileUploadServiceImpl implements FileUploadService {

    @Value("${vnpt.access-token}")
    private String accessToken;

    @Value("${vnpt.token-id}")
    private String tokenId;

    @Value("${vnpt.token-key}")
    private String tokenKey;
    
    @Value("${vnpt.base-url}")
    private String baseUrl;

    private final UploadFileRepository uploadFileRepository;
    private final RestTemplate restTemplate;

    @Override
    public String uploadToVNPT(MultipartFile file, String title) throws IOException {
        File resizedFile = null;
        try {

            resizedFile = ImageUtil.resizeImage(file);

            HttpHeaders headers = new HttpHeaders();
            String raw = accessToken == null ? "" : accessToken.trim();
            String token = raw.toLowerCase().startsWith("bearer ") ? raw.substring(7).trim() : raw;
            headers.setBearerAuth(token);
            headers.set("Token-id", tokenId);
            headers.set("Token-key", tokenKey);
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body =
                    new LinkedMultiValueMap<>();

            body.add("file",
                    new FileSystemResource(resizedFile));

            body.add("title", title);
            body.add("description", "upload from springboot");

            HttpEntity<MultiValueMap<String, Object>> request =
                    new HttpEntity<>(body, headers);

            ResponseEntity<VNPTUploadResponse> response =
                    restTemplate.postForEntity(
                            baseUrl + "/file-service/v1/addFile",
                            request,
                            VNPTUploadResponse.class
                    );

            String hash =
                    response.getBody()
                            .getObject()
                            .getHash();

            UploadFile entity = new UploadFile();
            entity.setFileName(file.getOriginalFilename());
            entity.setHash(hash);
            entity.setUploadTime(LocalDateTime.now().toString());

            uploadFileRepository.save(entity);

            return hash;

        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Lỗi upload ảnh lên VNPT: " + VnptErrorUtil.extractErrorMessage(e));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (resizedFile != null && resizedFile.exists()) {
                resizedFile.delete();
            }
        }
    }
}
