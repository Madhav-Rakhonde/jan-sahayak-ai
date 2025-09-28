package com.JanSahayak.AI.config;

import org.springframework.beans.factory.annotation.Value; // CORRECT - For property injection
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:${user.home}/uploads/posts}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/posts/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }
}
