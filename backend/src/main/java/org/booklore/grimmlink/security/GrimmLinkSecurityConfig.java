package org.booklore.grimmlink.security;

import org.booklore.config.security.filter.AuthenticationCheckFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import lombok.AllArgsConstructor;

@Configuration
@AllArgsConstructor
public class GrimmLinkSecurityConfig {

    private final GrimmlinkAuthFilter grimmlinkAuthFilter;
    private final AuthenticationCheckFilter authenticationCheckFilter;

    @Bean
    @Order(0)
    public SecurityFilterChain grimmlinkSecurityChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/grimmlink/v1/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .addFilterBefore(grimmlinkAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(authenticationCheckFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
