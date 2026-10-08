package com.fptufa26.ecomotion.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Serves the seeded vehicle photos straight from the repo's {@code images/} folder.
 *
 * <p>Layout on disk (already prepared): {@code images/{providerUserId}/{vehicleId}/{file}}
 * e.g. {@code images/4/5/BMW-320i-M-Sport-830x415.webp}. The DB stores the matching URL path
 * {@code /images/4/5/BMW-320i-M-Sport-830x415.webp}, so this handler just strips {@code /images/}
 * and reads the rest under {@code app.images.root}.
 *
 * <p>{@code app.images.root} is relative to the backend module working directory
 * ({@code Backend/EcoMotion/}), so {@code ../../images} points at the folder next to the repo
 * root. It stays a relative path on purpose: a clone on any machine resolves the same way, with no
 * absolute path baked into the config.
 */
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
