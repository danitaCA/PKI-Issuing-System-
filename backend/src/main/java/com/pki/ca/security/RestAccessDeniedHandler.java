package com.pki.ca.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger logger = LoggerFactory.getLogger(RestAccessDeniedHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        String principalName = (request.getUserPrincipal() != null) ? request.getUserPrincipal().getName() : "anonymous";
        logger.warn("AUDIT [ACCESS_DENIED]: User '{}' denied access to protected endpoint '{}': {}",
                principalName, request.getRequestURI(), accessDeniedException.getMessage());

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "https://pki.internal.corp/errors/FORBIDDEN");
        problem.put("title", "Access Denied");
        problem.put("status", HttpServletResponse.SC_FORBIDDEN);
        problem.put("detail", "You do not have the necessary role or authority to access this resource.");
        problem.put("instance", request.getRequestURI());
        problem.put("timestamp", Instant.now().toString());
        problem.put("errorCode", "PKI-40300");

        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
