package app.kitapla.repo;

import app.kitapla.domain.Donation;
import app.kitapla.domain.DonationStatus;
import app.kitapla.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DonationRepository extends JpaRepository<Donation, Long> {
    List<Donation> findByStatusOrderByCreatedAtDesc(DonationStatus status);
    List<Donation> findByDonorOrderByCreatedAtDesc(User donor);
}
