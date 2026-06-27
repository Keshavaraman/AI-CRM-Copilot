package com.keshava.crmai.module.entity;

import com.keshava.crmai.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "dynamic_modules")
public class DynamicModule extends BaseEntity {

    @Column(unique = true, nullable = false)
    private String apiName;

    @Column(nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModuleType type = ModuleType.CUSTOM;

    @Column(unique = true, nullable = false)
    private String tableName;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "module", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DynamicField> fields = new ArrayList<>();

    public enum ModuleType {
        SYSTEM, CUSTOM
    }
}
