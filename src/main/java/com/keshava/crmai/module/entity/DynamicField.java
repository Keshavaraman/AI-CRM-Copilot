package com.keshava.crmai.module.entity;

import com.keshava.crmai.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "dynamic_fields")
public class DynamicField extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false)
    private DynamicModule module;

    @Column(nullable = false)
    private String apiName;

    @Column(nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FieldType fieldType;

    @Column(nullable = false)
    private boolean required = false;

    @Column(nullable = false)
    private boolean active = true;

    private String defaultValue;

    public enum FieldType {
        TEXT,
        TEXT_AREA,
        PHONE_NO,
        EMAIL,
        DATE,
        NUMBER,
        CHECKBOX,
        CURRENCY,
        URL,
        AUTO_NUMBER
    }
}
