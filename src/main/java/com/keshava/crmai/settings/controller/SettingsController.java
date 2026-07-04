package com.keshava.crmai.settings.controller;

import com.keshava.crmai.settings.dto.SettingAction;
import com.keshava.crmai.settings.dto.SettingsPage;
import com.keshava.crmai.settings.dto.SettingsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private static final List<String> FIELD_TYPES =
            List.of("TEXT", "TEXT_AREA", "PHONE_NO", "EMAIL", "DATE",
                    "NUMBER", "CHECKBOX", "CURRENCY", "URL", "AUTO_NUMBER");

    private static final SettingsPage MODULES_PAGE = new SettingsPage(
            "modules",
            "Module Manager",
            "Create and configure custom modules with their fields",
            List.of(
                    new SettingAction("create_module", "Create Module",   "POST",   "/api/modules"),
                    new SettingAction("edit_module",   "Edit Module",     "PUT",    "/api/modules/{apiName}"),
                    new SettingAction("add_field",     "Add Field",       "POST",   "/api/modules/{apiName}/fields"),
                    new SettingAction("delete_field",  "Delete Field",    "DELETE", "/api/modules/{apiName}/fields/{fieldApiName}")
            ),
            FIELD_TYPES
    );

    private static final List<SettingsPage> ALL_PAGES = List.of(MODULES_PAGE);

    @GetMapping
    public ResponseEntity<SettingsResponse> getSettings() {
        return ResponseEntity.ok(new SettingsResponse(ALL_PAGES.size(), ALL_PAGES));
    }
}
