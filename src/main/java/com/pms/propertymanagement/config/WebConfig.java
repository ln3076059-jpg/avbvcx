package com.pms.propertymanagement.config;

import com.pms.propertymanagement.interceptor.AuthInterceptor;
import com.pms.propertymanagement.interceptor.EkycInterceptor;
import com.pms.propertymanagement.interceptor.WalletInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;
    
    @Autowired
    private WalletInterceptor walletInterceptor;
    
    @Autowired
    private EkycInterceptor ekycInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/owner/**", "/admin/**", "/staff/**", "/tenant/**", "/moderator/**",
                        "/ai/v1/**", "/face-service/**", "/upload", "/api/images/upload")
                .excludePathPatterns("/", "/home");
                
        registry.addInterceptor(walletInterceptor)
                .addPathPatterns("/owner/**");

        registry.addInterceptor(ekycInterceptor)
                .addPathPatterns("/owner/**", "/tenant/**");
    }
}
