package com.MyProject.DigitalBankingSystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.MyProject.DigitalBankingSystem.auth.jwt.JwtAuthFilter;
import com.MyProject.DigitalBankingSystem.auth.jwt.JwtService;
import com.MyProject.DigitalBankingSystem.auth.jwt.TokenBlacklistService;
import com.MyProject.DigitalBankingSystem.rate_limiting.filter.RateLimitingFilter;
import com.MyProject.DigitalBankingSystem.rate_limiting.service.RateLimitingService;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final RateLimitingService rateLimitingService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Authentication
                        .requestMatchers(
                                "/auth/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html").permitAll()
                        // User access
                        .requestMatchers(HttpMethod.GET, "/users/my-user").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET,"/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/users/**").hasAnyRole("USER", "ADMIN")
                        // Account access
                        .requestMatchers(HttpMethod.GET, "/accounts/my-accounts").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/accounts/my-account/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/accounts").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/accounts/user/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/accounts/account-number/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/accounts/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/accounts/**").hasRole("ADMIN")
                        // Transaction access
                        .requestMatchers(HttpMethod.GET, "/transactions/my-transaction/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/transactions/my-transactions/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/transactions/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/transactions/reference/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/transactions/all-transactions/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/transactions/transfer").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/transactions/withdraw").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/transactions/deposit").hasAnyRole("USER", "ADMIN")
                        // FraudLog access
                        .requestMatchers(HttpMethod.GET, "/fraud-logs/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/fraud-logs/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(rateLimitingFilter(), UsernamePasswordAuthenticationFilter.class) // Accepts 2 parameters at a time
                .addFilterBefore(jwtAuthFilter(), RateLimitingFilter.class)
                .build();
    }
    @Bean
    public JwtAuthFilter jwtAuthFilter() {
        return new JwtAuthFilter(jwtService, tokenBlacklistService);
    }
    
    @Bean
    public RateLimitingFilter rateLimitingFilter() { 
        return new RateLimitingFilter(rateLimitingService); 
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider daoAuthenticationProvider = new DaoAuthenticationProvider(userDetailsService);
        daoAuthenticationProvider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(daoAuthenticationProvider);
    }
}
