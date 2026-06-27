package com.keshava.crmai.crm.contact.controller;

import com.keshava.crmai.crm.contact.entity.Contact;
import com.keshava.crmai.crm.contact.service.ContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;

    @GetMapping
    public ResponseEntity<Page<Contact>> list(
            @RequestParam(required = false) String search,
            Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return ResponseEntity.ok(contactService.search(search, pageable));
        }
        return ResponseEntity.ok(contactService.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Contact> get(@PathVariable UUID id) {
        return ResponseEntity.ok(contactService.findById(id));
    }

    @PostMapping
    public ResponseEntity<Contact> create(@RequestBody Contact contact) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contactService.create(contact));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Contact> update(@PathVariable UUID id, @RequestBody Contact contact) {
        return ResponseEntity.ok(contactService.update(id, contact));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        contactService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
