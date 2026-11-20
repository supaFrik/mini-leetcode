package jsd.toiyeujava.mini_leetcode.ownership;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class OwnershipWebConfig implements WebMvcConfigurer {

    private final OwnershipInterceptor interceptor;

    public OwnershipWebConfig(OwnershipInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns(
                "/api/v1/problems/*", "/api/v1/problems/*/**", "/api/v1/admin/problems/**", "/api/v1/admin/contests/**");
    }
}
