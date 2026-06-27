package com.keshava.crmai.module.service;

import com.keshava.crmai.common.exception.AppException;
import com.keshava.crmai.module.dto.RecordPage;
import com.keshava.crmai.module.entity.DynamicField;
import com.keshava.crmai.module.entity.DynamicModule;
import com.keshava.crmai.module.repository.DynamicFieldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DynamicRecordService {

    private final DynamicModuleService moduleService;
    private final DynamicFieldRepository fieldRepository;

    @Qualifier("tenantJdbcTemplate")
    private final JdbcTemplate tenantJdbcTemplate;

    @Qualifier("tenantNamedJdbcTemplate")
    private final NamedParameterJdbcTemplate tenantNamedJdbcTemplate;

    private static final Set<String> SYSTEM_COLUMNS =
            Set.of("id", "created_at", "updated_at", "created_by", "updated_by");

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public RecordPage listRecords(String moduleApiName, int page, int size) {
        DynamicModule module = moduleService.getModule(moduleApiName);
        String table = module.getTableName();

        long total = countRecords(table);
        int offset = page * size;

        String sql = "SELECT * FROM " + table + " ORDER BY created_at DESC LIMIT " + size + " OFFSET " + offset;
        List<Map<String, Object>> records = tenantJdbcTemplate.queryForList(sql);

        int totalPages = (int) Math.ceil((double) total / size);
        return new RecordPage(records, total, page, size, totalPages);
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Map<String, Object> getRecord(String moduleApiName, UUID id) {
        DynamicModule module = moduleService.getModule(moduleApiName);
        String sql = "SELECT * FROM " + module.getTableName() + " WHERE id = ?";
        List<Map<String, Object>> rows = tenantJdbcTemplate.queryForList(sql, id);
        if (rows.isEmpty()) {
            throw new AppException("Record '" + id + "' not found in module '" + moduleApiName + "'", HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public Map<String, Object> createRecord(String moduleApiName, Map<String, Object> data) {
        DynamicModule module = moduleService.getModule(moduleApiName);
        List<DynamicField> activeFields = fieldRepository.findByModuleIdAndActiveTrue(module.getId());
        Set<String> validColumns = activeFields.stream()
                .map(DynamicField::getApiName)
                .collect(Collectors.toSet());

        String currentUser = currentUsername();
        Map<String, Object> params = new LinkedHashMap<>();

        // Only include values for known active columns; system columns are always excluded
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String col = entry.getKey();
            if (validColumns.contains(col) && !SYSTEM_COLUMNS.contains(col)) {
                params.put(col, entry.getValue());
            }
        }
        params.put("created_by", currentUser);
        params.put("updated_by", currentUser);

        String cols = String.join(", ", params.keySet());
        String vals = params.keySet().stream().map(k -> ":" + k).collect(Collectors.joining(", "));
        String sql = "INSERT INTO " + module.getTableName() +
                " (" + cols + ", created_at, updated_at) " +
                "VALUES (" + vals + ", now(), now()) RETURNING *";

        return tenantNamedJdbcTemplate.queryForMap(sql, params);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public Map<String, Object> updateRecord(String moduleApiName, UUID id, Map<String, Object> data) {
        DynamicModule module = moduleService.getModule(moduleApiName);

        // Verify record exists
        getRecord(moduleApiName, id);

        List<DynamicField> activeFields = fieldRepository.findByModuleIdAndActiveTrue(module.getId());
        Set<String> validColumns = activeFields.stream()
                .map(DynamicField::getApiName)
                .collect(Collectors.toSet());

        String currentUser = currentUsername();
        Map<String, Object> params = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String col = entry.getKey();
            if (validColumns.contains(col) && !SYSTEM_COLUMNS.contains(col)) {
                params.put(col, entry.getValue());
            }
        }

        if (params.isEmpty()) {
            throw new AppException("No updatable fields provided", HttpStatus.BAD_REQUEST);
        }

        params.put("updated_by", currentUser);
        params.put("record_id", id);

        String setClauses = params.keySet().stream()
                .filter(k -> !k.equals("record_id"))
                .map(k -> k + " = :" + k)
                .collect(Collectors.joining(", "));

        String sql = "UPDATE " + module.getTableName() +
                " SET " + setClauses + ", updated_at = now() " +
                "WHERE id = :record_id RETURNING *";

        return tenantNamedJdbcTemplate.queryForMap(sql, params);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteRecord(String moduleApiName, UUID id) {
        DynamicModule module = moduleService.getModule(moduleApiName);
        int affected = tenantJdbcTemplate.update(
                "DELETE FROM " + module.getTableName() + " WHERE id = ?", id);
        if (affected == 0) {
            throw new AppException("Record '" + id + "' not found in module '" + moduleApiName + "'", HttpStatus.NOT_FOUND);
        }
    }

    private long countRecords(String tableName) {
        Long count = tenantJdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
        return count != null ? count : 0L;
    }

    private String currentUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null) ? auth.getName() : "system";
    }
}
