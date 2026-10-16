package com.gbconnected.kpivalidation.application;

/**
 * Raised when an authenticated actor is not authorized for KPI Validation.
 * Authentication failures remain a runtime concern; this exception represents
 * an authenticated actor that lacks the dedicated role, KPI plant assignment,
 * or generic plant access required by the functional specifications.
 */
public final class AuthorizationException extends RuntimeException {
    public AuthorizationException(String message) {
        super(message);
    }
}
