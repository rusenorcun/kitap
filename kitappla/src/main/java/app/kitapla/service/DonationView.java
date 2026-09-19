package app.kitapla.service;

import app.kitapla.domain.Book;
import app.kitapla.domain.Donation;
import app.kitapla.domain.DonationSource;
import app.kitapla.domain.TargetLevel;

import java.time.Instant;

/** Bağış + hesaplanmış alanlar (kalan adet, öncelik durumu). Şablonlar bunu kullanır. */
public record DonationView(Donation donation, long claimed, long remaining) {

    public Long getId() { return donation.getId(); }
    public Book getBook() { return donation.getBook(); }
    public String getDonorName() { return donation.getDonor().getName(); }
    public String getDescription() { return donation.getDescription(); }
    public int getQuantity() { return donation.getQuantity(); }
    public DonationSource getSource() { return donation.getSource(); }
    public TargetLevel getTargetLevel() { return donation.getTargetLevel(); }
    public Instant getCreatedAt() { return donation.getCreatedAt(); }

    public long getClaimed() { return claimed; }
    public long getRemaining() { return remaining; }
    public boolean isAvailable() { return remaining > 0; }

    public boolean isPriorityActive() { return donation.isPriorityActive(); }
    public Instant getPriorityUntil() { return donation.getPriorityUntil(); }

    /** Öncelik penceresinin bitimine kalan süre, "1g 4s" biçiminde. */
    public String getPriorityLeft() {
        long minutes = java.time.Duration.between(Instant.now(), getPriorityUntil()).toMinutes();
        if (minutes <= 0) return null;
        long days = minutes / (60 * 24);
        long hours = (minutes % (60 * 24)) / 60;
        long mins = minutes % 60;
        if (days > 0) return days + "g " + hours + "s";
        if (hours > 0) return hours + "s " + mins + "dk";
        return mins + "dk";
    }
}
