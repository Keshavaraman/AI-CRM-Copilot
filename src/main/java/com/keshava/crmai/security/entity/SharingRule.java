package com.keshava.crmai.security.entity;

import com.keshava.crmai.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "sharing_rules")
public class SharingRule extends BaseEntity {

    @Column(nullable = false)
    private String moduleName;

    @Column(nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private UUID sharedWithId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccessLevel accessLevel;

    public enum AccessLevel {
        READ, WRITE
    }
}
