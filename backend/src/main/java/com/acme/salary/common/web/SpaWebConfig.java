package com.acme.salary.common.web;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built Angular app from the same server as the API (architecture.md section 10: one origin,
 * so the session cookie and CSRF work without CORS). The Docker build copies Angular's output into
 * {@code classpath:/static/}.
 *
 * <p>The app does its own routing in the browser, so an address like {@code /employees/42} has no file
 * behind it. Any such path gets {@code index.html}, and Angular shows the right screen; that is what makes
 * refreshing a page or opening a shared link work. Real files (scripts, styles, fonts) are served as they
 * are, and {@code /api/...} is never answered with the app: an unknown API path stays a 404.
 *
 * <p>In development the Angular dev server serves the app instead, and this folder is empty.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    private static final String STATIC = "classpath:/static/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/")) {
                            return null;
                        }
                        Resource index = new ClassPathResource("static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
