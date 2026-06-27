package com.keshava.crmai.security.entity;

import com.keshava.crmai.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "profiles")
public class Profile extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String description;

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Permission> permissions = new ArrayList<>();

    public boolean hasPermission(String moduleName, Permission.Action action) {
        return permissions.stream().anyMatch(p ->
                p.getModuleName().equalsIgnoreCase(moduleName) &&
                (p.getAction() == action || p.getAction() == Permission.Action.ALL));
    }
}
