package com.blog.backend.config;

import com.blog.backend.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/comments/admin").hasAnyRole("ADMIN", "AUTHOR")
                .requestMatchers(HttpMethod.POST, "/api/articles/*/interactions").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/articles/*/interactions").authenticated()
                // 前台阅读接口保持公开，写操作交给下面的角色规则控制。
                .requestMatchers(HttpMethod.GET, "/api/articles/**", "/api/categories/**", "/api/tags/**", "/api/comments/**").permitAll()
                // 管理后台统一放在 /api/admin 下，便于后续继续扩展权限边界。
                .requestMatchers("/api/admin/**", "/api/logs/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/articles/**", "/api/categories/**", "/api/tags/**").hasAnyRole("ADMIN", "AUTHOR")
                .requestMatchers(HttpMethod.GET, "/api/media/**").hasAnyRole("ADMIN", "AUTHOR")
                .requestMatchers(HttpMethod.PUT, "/api/comments/**").hasAnyRole("ADMIN", "AUTHOR")
                .requestMatchers(HttpMethod.DELETE, "/api/articles/**", "/api/categories/**", "/api/tags/**").hasAnyRole("ADMIN", "AUTHOR")
                .requestMatchers("/doc.html", "/webjars/**", "/v3/api-docs/**", "/swagger-resources/**").permitAll()
                .anyRequest().authenticated()
            )
            // JWT 是无状态认证，每次请求都从 Authorization 头恢复用户身份。
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
