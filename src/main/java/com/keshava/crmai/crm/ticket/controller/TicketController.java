package com.keshava.crmai.crm.ticket.controller;

import com.keshava.crmai.crm.ticket.entity.Ticket;
import com.keshava.crmai.crm.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping
    public ResponseEntity<Page<Ticket>> list(
            @RequestParam(required = false) Ticket.Status status,
            Pageable pageable) {
        if (status != null) {
            return ResponseEntity.ok(ticketService.findByStatus(status, pageable));
        }
        return ResponseEntity.ok(ticketService.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Ticket> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ticketService.findById(id));
    }

    @PostMapping
    public ResponseEntity<Ticket> create(@RequestBody Ticket ticket) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.create(ticket));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Ticket> update(@PathVariable UUID id, @RequestBody Ticket ticket) {
        return ResponseEntity.ok(ticketService.update(id, ticket));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        ticketService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
