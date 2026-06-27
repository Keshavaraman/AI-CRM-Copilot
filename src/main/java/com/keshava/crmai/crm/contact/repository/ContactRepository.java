package com.keshava.crmai.crm.contact.repository;

import com.keshava.crmai.crm.contact.entity.Contact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContactRepository extends JpaRepository<Contact, UUID> {

    Optional<Contact> findByEmail(String email);

    List<Contact> findByOwnerId(UUID ownerId);

    @Query("SELECT c FROM Contact c WHERE " +
           "LOWER(c.firstName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(c.lastName)  LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(c.email)     LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Contact> search(@Param("q") String query, Pageable pageable);
}
