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

    @Value("${app.social.upload.dir:${user.home}/uploads/social-posts}")
    private String socialUploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Issue/Report posts upload handler
        String absolutePath = Paths.get(uploadDir).toAbsolutePath().toString();
        registry.addResourceHandler("/uploads/posts/**")
                .addResourceLocations("file:" + absolutePath + "/");

        System.out.println("Static resource handler configured for posts:");
        System.out.println("URL Pattern: /uploads/posts/**");
        System.out.println("File Location: file:" + absolutePath + "/");

        // Social posts upload handler
        String socialAbsolutePath = Paths.get(socialUploadDir).toAbsolutePath().toString();
        registry.addResourceHandler("/uploads/social-posts/**")
                .addResourceLocations("file:" + socialAbsolutePath + "/");

        System.out.println("Static resource handler configured for social posts:");
        System.out.println("URL Pattern: /uploads/social-posts/**");
        System.out.println("File Location: file:" + socialAbsolutePath + "/");
    }
}