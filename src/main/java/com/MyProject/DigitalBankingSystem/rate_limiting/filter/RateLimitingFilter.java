package com.MyProject.DigitalBankingSystem.rate_limiting.filter;

import com.MyProject.DigitalBankingSystem.common.ErrorResponse;
import com.MyProject.DigitalBankingSystem.rate_limiting.service.RateLimitingService;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitingService rateLimitingService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.equals("/api/v1/auth/login")) {
            filterChain.doFilter(request, response);
            return;
        }
        String ip = request.getRemoteAddr();
        Bucket bucket = rateLimitingService.resolveBucket(ip);
        ErrorResponse errorResponse = ErrorResponse.builder()
                .message("Too many requests")
                .status(429)
                .timestamp(LocalDateTime.now())
                .build();
        if (!bucket.tryConsume(1)) {
            response.setStatus(429);
            response.getWriter().write("Too many login attempts");
        }
        filterChain.doFilter(request, response);
    }
}
