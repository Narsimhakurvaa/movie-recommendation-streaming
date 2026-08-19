package com.cinevault.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI document definition.
 *
 * <p>Declares the bearer scheme once so every secured operation can reference
 * it, and Swagger UI offers an "Authorize" box that actually works.
 */
@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI cineVaultOpenApi(@Value("${server.port:8080}") String port) {
        return new OpenAPI()
                .info(new Info()
                        .title("CineVault API")
                        .version("1.0.0")
                        .description("""
                                Personalised movie discovery and recommendation platform.

                                ## Authentication
                                Obtain a token pair from `POST /api/auth/login`, then send
                                `Authorization: Bearer <accessToken>` on subsequent requests.
                                Access tokens are short-lived; use `POST /api/auth/refresh`
                                to rotate them. A refresh token is single-use - presenting
                                one twice revokes every session for that account.

                                ## Pagination
                                Paginated endpoints accept `page` (zero-based) and `size`,
                                and return a `PageResponse` envelope with `content`,
                                `totalElements`, `totalPages`, `first` and `last`.

                                ## Errors
                                Failures share one `ApiError` shape carrying `status`, a
                                stable `code`, a safe `message`, the `path`, a
                                `correlationId` for support, and `validationErrors` for
                                field-level problems.
                                """)
                        .contact(new Contact().name("CineVault"))
                        .license(new License().name("MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:" + port).description("Local development")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token issued by /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
