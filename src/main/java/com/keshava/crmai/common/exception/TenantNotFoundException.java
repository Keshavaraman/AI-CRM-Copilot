package com.keshava.crmai.common.exception;

import org.springframework.http.HttpStatus;

public class TenantNotFoundException extends AppException {

    public TenantNotFoundException(String tenantId) {
        super("Tenant not found: " + tenantId, HttpStatus.BAD_REQUEST);
    }
}
