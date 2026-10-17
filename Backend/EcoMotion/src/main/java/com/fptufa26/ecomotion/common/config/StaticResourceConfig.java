package com.fptufa26.ecomotion.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private final Path imagesRoot;

    public StaticResourceConfig(@Value("${app.images.root:../../images}") String imagesRoot) {
        Path resolved = Paths.get(imagesRoot).toAbsolutePath().normalize();
        if (!Files.exists(resolved)) {
            Path fromRoot = Paths.get("images").toAbsolutePath().normalize();
            Path fromParent = Paths.get("../images").toAbsolutePath().normalize();
            if (Files.exists(fromRoot)) {
                resolved = fromRoot;
            } else if (Files.exists(fromParent)) {
                resolved = fromParent;
            }
        }
        this.imagesRoot = resolved;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = imagesRoot.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/images/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }
}

