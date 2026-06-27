package com.keshava.crmai.config.cache;

import com.keshava.crmai.multitenancy.TenantContext;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleKeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Component("tenantAwareKeyGenerator")
public class TenantAwareKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        String tenantId = TenantContext.getCurrentTenant();
        String prefix = (tenantId != null) ? tenantId : "shared";
        Object baseKey = SimpleKeyGenerator.generateKey(params);
        return prefix + ":" + baseKey;
    }
}

