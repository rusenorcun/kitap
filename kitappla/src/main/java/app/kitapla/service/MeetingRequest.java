package app.kitapla.service;

import java.time.Instant;

/**
 * Buluşma ayarlama girdisi: listeden seçilen nokta, serbest metin ve zaman.
 * En az biri (nokta ya da metin) dolu olmalıdır.
 */
public record MeetingRequest(Long pointId, String note, Instant at) {}
