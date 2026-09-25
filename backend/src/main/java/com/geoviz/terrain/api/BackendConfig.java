package com.geoviz.terrain.api;

import com.geoviz.terrain.erosion.ErosionSimulator;
import com.geoviz.terrain.noise.NoiseGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 装配各算法模块为 Spring Bean，并放开 /api 的跨域（便于本地开发直连）。
 */
@Configuration
public class BackendConfig {

    @Bean
    public NoiseGenerator noiseGenerator() {
        return new NoiseGenerator();
    }

    @Bean
    public ErosionSimulator erosionSimulator() {
        return new ErosionSimulator();
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("*")
                        .allowedMethods("GET", "POST");
            }
        };
    }
}
