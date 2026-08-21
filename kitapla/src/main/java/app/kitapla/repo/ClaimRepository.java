package app.kitapla.repo;

import app.kitapla.domain.Claim;
import app.kitapla.domain.ClaimStatus;
import app.kitapla.domain.Donation;
import app.kitapla.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ClaimRepository extends JpaRepository<Claim, Long> {
    long countByDonation(Donation donation);
    boolean existsByDonationAndStudent(Donation donation, User student);
    List<Claim> findByStudentOrderByCreatedAtDesc(User student);
    List<Claim> findByDonation(Donation donation);
    long countByStudentAndCreatedAtAfter(User student, Instant after);
    long countByStatus(ClaimStatus status);
}
