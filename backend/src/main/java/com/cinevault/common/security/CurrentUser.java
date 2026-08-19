package com.cinevault.common.security;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the authenticated {@link JwtPrincipal} into a controller method.
 *
 * <p>A meta-annotation over {@code @AuthenticationPrincipal} that also hides the
 * parameter from the generated OpenAPI document, since it is derived from the
 * Authorization header rather than supplied by the caller.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal
@Parameter(hidden = true, in = ParameterIn.HEADER)
public @interface CurrentUser {
}
