package br.com.vagasflow.api.shared.config;

import br.com.vagasflow.api.auth.service.JwtAuthFilter;
import br.com.vagasflow.api.auth.service.OAuth2SuccessHandler;
import br.com.vagasflow.api.auth.service.OAuth2UserServiceImpl;
import br.com.vagasflow.api.auth.service.OidcUserServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.vagasflow.api.shared.exception.ApiErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final OAuth2UserServiceImpl oAuth2UserService;
    private final OidcUserServiceImpl oidcUserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final ObjectMapper objectMapper;
    private final String allowedOrigins;
    private final String frontendUrl;

    public SecurityConfig(
            JwtAuthFilter jwtAuthFilter,
            OAuth2UserServiceImpl oAuth2UserService,
            OidcUserServiceImpl oidcUserService,
            OAuth2SuccessHandler oAuth2SuccessHandler,
            ObjectMapper objectMapper,
            @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.oAuth2UserService = oAuth2UserService;
        this.oidcUserService = oidcUserService;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
        this.objectMapper = objectMapper;
        this.allowedOrigins = allowedOrigins;
        this.frontendUrl = allowedOrigins.split(",")[0].trim();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .headers(headers -> headers
                        .contentTypeOptions(c -> {
                        })
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)).permissionsPolicyHeader(p -> p.policy("geolocation=(), camera=(), microphone=()"))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/oauth2/**", "/login/**", "/auth/callback",
                                "/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(info -> info
                                .userService(oAuth2UserService)
                                .oidcUserService(oidcUserService)
                        )
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler((req, res, ex) -> {
                            String redirectUrl = frontendUrl + "/auth/callback?error=oauth_denied";
                            res.sendRedirect(redirectUrl);
                        })
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, authEx) -> {
                            res.setStatus(HttpStatus.UNAUTHORIZED.value());
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            var body = new ApiErrorResponse(
                                    HttpStatus.UNAUTHORIZED.value(),
                                    HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                                    "Authentication required",
                                    req.getRequestURI()
                            );
                            objectMapper.writeValue(res.getOutputStream(), body);
                        })
                        .accessDeniedHandler((req, res, accessEx) -> {
                            res.setStatus(HttpStatus.FORBIDDEN.value());
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            var body = new ApiErrorResponse(
                                    HttpStatus.FORBIDDEN.value(),
                                    HttpStatus.FORBIDDEN.getReasonPhrase(),
                                    "Access denied",
                                    req.getRequestURI()
                            );
                            objectMapper.writeValue(res.getOutputStream(), body);
                        })
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
