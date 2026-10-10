package com.invoicespot.config;

import com.invoicespot.business.ActiveBusinessResolver;
import com.invoicespot.upload.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String uploadDir;
    private final ActiveBusinessResolver activeBusinessResolver;

    public WebConfig(AppProperties properties, ActiveBusinessResolver activeBusinessResolver) {
        this.uploadDir = properties.upload().dir();
        this.activeBusinessResolver = activeBusinessResolver;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(uploadDir).toAbsolutePath().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        // Only images are served, and in a sandbox should a browser ever read
        // one as a page. Each upload gets a name of its own and is never
        // written again, so browsers may keep it.
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .resourceChain(false)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        return UploadService.servable(resourcePath) ? super.getResource(resourcePath, location) : null;
                    }
                });
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
                    @Override
                    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                        response.setHeader("Content-Security-Policy", "sandbox");
                        return true;
                    }
                })
                .addPathPatterns("/uploads/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(activeBusinessResolver);
    }
}
