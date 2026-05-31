-- V2: tabela Outbox (Transactional Outbox Pattern)
-- Zdarzenie do Kafki zapisujemy w TEJ SAMEJ transakcji co zamowienie (atomowo, jeden commit).
-- Osobny proces (OutboxRelay) odczytuje niewyslane wiersze i publikuje je do Kafki.
-- Dzieki temu nie ma dual-write: albo zapis zamowienia I jego eventu sie powiodl, albo oba sie cofnely.

CREATE TABLE outbox (
    id           VARCHAR(255)                NOT NULL,
    -- aggregate_id: id zamowienia, ktorego dotyczy zdarzenie (do logow i ewentualnego debugowania).
    aggregate_id VARCHAR(255)                NOT NULL,
    -- topic + message_key + payload: wszystko czego relay potrzebuje, zeby wyslac wiadomosc do Kafki
    -- bez znajomosci typu zdarzenia. Payload to gotowy JSON (zserializowany przy zapisie).
    topic        VARCHAR(255)                NOT NULL,
    message_key  VARCHAR(255)                NOT NULL,
    payload      TEXT                        NOT NULL,
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    -- published_at NULL = jeszcze nie wyslane. Po udanym send() relay ustawia czas wyslania.
    published_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_outbox PRIMARY KEY (id)
);

-- Indeks czesciowy (partial index): obejmuje TYLKO wiersze niewyslane (published_at IS NULL).
-- To jest dokladnie zapytanie relayu - skanuje maly "ogon" niewyslanych, nie cala tabele.
-- Po wyslaniu wiersz wypada z indeksu, wiec indeks nie rosnie wraz z historia zdarzen.
CREATE INDEX idx_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;
