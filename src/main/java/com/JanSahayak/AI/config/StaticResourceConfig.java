package com.JanSahayak.AI.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:${user.home}/uploads/posts}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Convert to absolute path
        String absolutePath = Paths.get(uploadDir).toAbsolutePath().toString();

        registry.addResourceHandler("/uploads/posts/**")
                .addResourceLocations("file:" + absolutePath + "/");

        System.out.println("Static resource handler configured:");
        System.out.println("URL Pattern: /uploads/posts/**");
        System.out.println("File Location: file:" + absolutePath + "/");
    }
}