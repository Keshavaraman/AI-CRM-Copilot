package com.keshava.crmai.crm.contact.service;

import com.keshava.crmai.common.exception.AppException;
import com.keshava.crmai.crm.contact.entity.Contact;
import com.keshava.crmai.crm.contact.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "tenantTransactionManager")
public class ContactService {

    private final ContactRepository contactRepository;

    public Page<Contact> findAll(Pageable pageable) {
        return contactRepository.findAll(pageable);
    }

    public Page<Contact> search(String query, Pageable pageable) {
        return contactRepository.search(query, pageable);
    }

    public Contact findById(UUID id) {
        return contactRepository.findById(id)
                .orElseThrow(() -> new AppException("Contact not found: " + id, HttpStatus.NOT_FOUND));
    }

    public Contact create(Contact contact) {
        return contactRepository.save(contact);
    }

    public Contact update(UUID id, Contact updates) {
        Contact existing = findById(id);
        existing.setFirstName(updates.getFirstName());
        existing.setLastName(updates.getLastName());
        existing.setEmail(updates.getEmail());
        existing.setPhone(updates.getPhone());
        existing.setCompany(updates.getCompany());
        existing.setJobTitle(updates.getJobTitle());
        existing.setDescription(updates.getDescription());
        return contactRepository.save(existing);
    }

    public void delete(UUID id) {
        contactRepository.delete(findById(id));
    }
}
