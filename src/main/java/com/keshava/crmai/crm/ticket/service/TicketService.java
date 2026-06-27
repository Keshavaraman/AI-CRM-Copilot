package com.keshava.crmai.crm.ticket.service;

import com.keshava.crmai.common.exception.AppException;
import com.keshava.crmai.crm.ticket.entity.Ticket;
import com.keshava.crmai.crm.ticket.repository.TicketRepository;
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
public class TicketService {

    private final TicketRepository ticketRepository;

    public Page<Ticket> findAll(Pageable pageable) {
        return ticketRepository.findAll(pageable);
    }

    public Page<Ticket> findByStatus(Ticket.Status status, Pageable pageable) {
        return ticketRepository.findByStatus(status, pageable);
    }

    public Ticket findById(UUID id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new AppException("Ticket not found: " + id, HttpStatus.NOT_FOUND));
    }

    public Ticket create(Ticket ticket) {
        return ticketRepository.save(ticket);
    }

    public Ticket update(UUID id, Ticket updates) {
        Ticket existing = findById(id);
        existing.setSubject(updates.getSubject());
        existing.setDescription(updates.getDescription());
        existing.setStatus(updates.getStatus());
        existing.setPriority(updates.getPriority());
        existing.setAssigneeId(updates.getAssigneeId());
        existing.setContactId(updates.getContactId());
        return ticketRepository.save(existing);
    }

    public void delete(UUID id) {
        ticketRepository.delete(findById(id));
    }
}
