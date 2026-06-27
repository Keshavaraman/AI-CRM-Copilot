package com.keshava.crmai.module.controller;

import com.keshava.crmai.module.dto.FieldRequest;
import com.keshava.crmai.module.dto.ModuleRequest;
import com.keshava.crmai.module.dto.RecordPage;
import com.keshava.crmai.module.entity.DynamicField;
import com.keshava.crmai.module.entity.DynamicModule;
import com.keshava.crmai.module.service.DynamicFieldService;
import com.keshava.crmai.module.service.DynamicModuleService;
import com.keshava.crmai.module.service.DynamicRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/modules")
@RequiredArgsConstructor
public class DynamicModuleController {

    private final DynamicModuleService moduleService;
    private final DynamicFieldService fieldService;
    private final DynamicRecordService recordService;

    // ─── Module endpoints ──────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<DynamicModule> createModule(@Valid @RequestBody ModuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(moduleService.createModule(request));
    }

    @GetMapping
    public ResponseEntity<List<DynamicModule>> listModules() {
        return ResponseEntity.ok(moduleService.listModules());
    }

    @GetMapping("/{apiName}")
    public ResponseEntity<DynamicModule> getModule(@PathVariable String apiName) {
        return ResponseEntity.ok(moduleService.getModule(apiName));
    }

    @DeleteMapping("/{apiName}")
    public ResponseEntity<Void> deleteModule(@PathVariable String apiName) {
        moduleService.deleteModule(apiName);
        return ResponseEntity.noContent().build();
    }

    // ─── Field endpoints ───────────────────────────────────────────────────────

    @PostMapping("/{apiName}/fields")
    public ResponseEntity<DynamicField> addField(
            @PathVariable String apiName,
            @Valid @RequestBody FieldRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fieldService.addField(apiName, request));
    }

    @GetMapping("/{apiName}/fields")
    public ResponseEntity<List<DynamicField>> listFields(@PathVariable String apiName) {
        return ResponseEntity.ok(fieldService.listFields(apiName));
    }

    @DeleteMapping("/{apiName}/fields/{fieldApiName}")
    public ResponseEntity<Void> deleteField(
            @PathVariable String apiName,
            @PathVariable String fieldApiName) {
        fieldService.deleteField(apiName, fieldApiName);
        return ResponseEntity.noContent().build();
    }

    // ─── Record endpoints ──────────────────────────────────────────────────────

    @GetMapping("/{apiName}/records")
    public ResponseEntity<RecordPage> listRecords(
            @PathVariable String apiName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(recordService.listRecords(apiName, page, size));
    }

    @GetMapping("/{apiName}/records/{id}")
    public ResponseEntity<Map<String, Object>> getRecord(
            @PathVariable String apiName,
            @PathVariable UUID id) {
        return ResponseEntity.ok(recordService.getRecord(apiName, id));
    }

    @PostMapping("/{apiName}/records")
    public ResponseEntity<Map<String, Object>> createRecord(
            @PathVariable String apiName,
            @RequestBody Map<String, Object> data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recordService.createRecord(apiName, data));
    }

    @PutMapping("/{apiName}/records/{id}")
    public ResponseEntity<Map<String, Object>> updateRecord(
            @PathVariable String apiName,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> data) {
        return ResponseEntity.ok(recordService.updateRecord(apiName, id, data));
    }

    @DeleteMapping("/{apiName}/records/{id}")
    public ResponseEntity<Void> deleteRecord(
            @PathVariable String apiName,
            @PathVariable UUID id) {
        recordService.deleteRecord(apiName, id);
        return ResponseEntity.noContent().build();
    }
}
