package com.keshava.crmai.crm.contact.entity;

import com.keshava.crmai.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "contacts")
public class Contact extends BaseEntity {

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(unique = true)
    private String email;

    private String phone;

    private String company;

    private String jobTitle;

    @Column(columnDefinition = "TEXT")
    private String description;

    private UUID ownerId;
}
