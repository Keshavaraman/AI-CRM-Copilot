package com.keshava.crmai.crm.ticket.repository;

import com.keshava.crmai.crm.ticket.entity.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    Page<Ticket> findByStatus(Ticket.Status status, Pageable pageable);

    List<Ticket> findByAssigneeId(UUID assigneeId);

    List<Ticket> findByContactId(UUID contactId);
}
