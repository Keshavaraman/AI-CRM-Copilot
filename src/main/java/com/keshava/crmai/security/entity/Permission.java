package com.keshava.crmai.security.entity;

import com.keshava.crmai.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "permissions")
public class Permission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(nullable = false)
    private String moduleName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Action action;

    public enum Action {
        READ, WRITE, DELETE, ALL
    }
}
