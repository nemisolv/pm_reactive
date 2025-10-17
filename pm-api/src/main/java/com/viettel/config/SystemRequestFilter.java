package com.viettel.config;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class SystemRequestFilter extends OncePerRequestFilter {

    private static final String HEADER_SYSTEM = "X-System";
    private static final String HEADER_DATASOURCE = "X-DS"; // optional logical datasource key
    private final ConfigManager configManager;

    public SystemRequestFilter(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String systemHeader = request.getHeader(HEADER_SYSTEM);
            SystemType system = SystemType.fromString(systemHeader);

            boolean deployed = configManager.isDeployed(system);
            if(system == null || !deployed) {
                log.warn("Client request with invalid system header: {} or the designated system type is not deployed", systemHeader);
                 response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Either the system header is missing or invalid, or the designated system type is not operational.");
            return;
            }


            TenantContextHolder.setCurrentSystem(system);
            String dsHeader = request.getHeader(HEADER_DATASOURCE);
            String resolvedDs = configManager.resolveDatasourceKey(system, dsHeader);
            TenantContextHolder.setCurrentDatasourceKey(resolvedDs);
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }
}


