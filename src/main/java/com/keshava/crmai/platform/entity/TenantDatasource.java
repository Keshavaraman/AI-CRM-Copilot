package com.keshava.crmai.platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "tenant_datasources")
public class TenantDatasource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private String dbUrl;

    @Column(nullable = false)
    private String dbName;

    @Column(nullable = false)
    private String dbUsername;

    @Column(nullable = false)
    private String dbPassword;

    @Column(nullable = false)
    private boolean active = true;
}
