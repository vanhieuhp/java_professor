package dev.hieunv.totp_bankos.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Place on any controller method to require a specific permission code in the
 * caller's wallet-scoped JWT.
 *
 * Example:
 *   {@literal @}RequiresPermission("TRANSFER:CREATE_REQUEST")
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
    String value();
}