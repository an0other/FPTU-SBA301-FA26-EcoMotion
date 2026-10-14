package com.fptufa26.ecomotion.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private final Path imagesRoot;

    public StaticResourceConfig(@Value("${app.images.root:../../images}") String imagesRoot) {
        this.imagesRoot = Paths.get(imagesRoot).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = imagesRoot.toUri().toString();
        registry.addResourceHandler("/images/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }
}
