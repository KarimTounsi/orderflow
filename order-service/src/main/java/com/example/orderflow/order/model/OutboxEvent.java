package com.example.orderflow.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    private String id;

    // id agregatu (zamowienia) - do logow i diagnostyki. Nie jest kluczem Kafki sam w sobie.
    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String topic;

    // Klucz partycji Kafki - tu id zamowienia, zeby zdarzenia jednego zamowienia szly w kolejnosci.
    @Column(nullable = false)
    private String messageKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // NULL => jeszcze nie wyslane. Relay ustawia czas po udanym wyslaniu do Kafki.
    private Instant publishedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
