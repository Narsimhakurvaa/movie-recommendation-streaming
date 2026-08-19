package com.cinevault.common.config;

import com.cinevault.common.security.JwtAuthenticationFilter;
import com.cinevault.common.security.RestAccessDeniedHandler;
import com.cinevault.common.security.RestAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Central security policy.
 *
 * <h2>Stateless sessions and CSRF</h2>
 * <p>CSRF protection is disabled deliberately, and only because the API is
 * genuinely stateless: it authenticates from an {@code Authorization} header,
 * never from a cookie. CSRF works by abusing the browser's automatic sending of
 * cookies, and a header the attacker's page cannot set is not vulnerable to it.
 * If refresh tokens were ever moved into cookies, CSRF protection would have to
 * be reinstated for the endpoints that read them.
 *
 * <h2>Authorisation</h2>
 * <p>Rules are declared here as a coarse first pass, and refined per-handler
 * with {@code @PreAuthorize}. Anything not explicitly permitted is denied, so
 * adding an endpoint without considering its access level fails closed.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    /** BCrypt cost. 12 keeps verification near ~250ms on modern hardware. */
    private static final int BCRYPT_STRENGTH = 12;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final String allowedOrigins;
    private final boolean swaggerEnabled;

    public SecurityConfiguration(JwtAuthenticationFilter jwtAuthenticationFilter,
                                 RestAuthenticationEntryPoint authenticationEntryPoint,
                                 RestAccessDeniedHandler accessDeniedHandler,
                                 @Value("${cinevault.cors.allowed-origins}") String allowedOrigins,
                                 @Value("${cinevault.openapi.enabled:true}") boolean swaggerEnabled) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.allowedOrigins = allowedOrigins;
        this.swaggerEnabled = swaggerEnabled;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .xssProtection(xss -> xss
                                .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // HSTS is only meaningful over TLS; the reverse proxy
                        // terminates HTTPS in every deployed environment.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; frame-ancestors 'none'; object-src 'none'")))
                .authorizeHttpRequests(auth -> {
                    // --- Preflight ---
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

                    // --- Authentication ---
                    auth.requestMatchers("/api/auth/register", "/api/auth/login",
                            "/api/auth/refresh", "/api/auth/logout",
                            "/api/auth/password-reset/**",
                            "/api/auth/verify-email/**").permitAll();

                    // --- Public catalogue browsing ---
                    // Discovery must work for signed-out visitors; anything
                    // that mutates state is excluded by matching GET only.
                    auth.requestMatchers(HttpMethod.GET,
                            "/api/movies", "/api/movies/*", "/api/movies/*/reviews",
                            "/api/movies/search", "/api/movies/suggest",
                            "/api/genres", "/api/genres/**",
                            "/api/recommendations/trending",
                            "/api/recommendations/popular",
                            "/api/recommendations/top-rated",
                            "/api/recommendations/new-releases",
                            "/api/recommendations/similar/*").permitAll();

                    // --- Operations ---
                    auth.requestMatchers("/actuator/health", "/actuator/health/**",
                            "/actuator/info").permitAll();
                    auth.requestMatchers("/actuator/**").hasRole("ADMIN");

                    // --- API documentation ---
                    if (swaggerEnabled) {
                        auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**").permitAll();
                    }

                    // --- Administration ---
                    auth.requestMatchers("/api/admin/**").hasRole("ADMIN");

                    // --- Everything else requires a valid token ---
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt with an explicit cost factor.
     *
     * <p>The algorithm is deliberately slow, which is what makes offline
     * cracking of a leaked digest impractical. The per-hash salt is generated
     * and embedded automatically, so identical passwords never share a digest.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * CORS for the single-page frontend.
     *
     * <p>Origins come from configuration and are never wildcarded, because
     * {@code allowCredentials} plus {@code *} is both forbidden by the spec and
     * a genuine security hole.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT, "X-Requested-With"));
        configuration.setExposedHeaders(List.of(HttpHeaders.RETRY_AFTER, "X-Total-Count"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
