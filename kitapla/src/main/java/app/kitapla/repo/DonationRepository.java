package app.kitapla.repo;

import app.kitapla.domain.Donation;
import app.kitapla.domain.DonationStatus;
import app.kitapla.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DonationRepository extends JpaRepository<Donation, Long> {

    /**
     * Şablonlar kitap ve bağışçıya eriştiği için ilişkiler birlikte çekilir
     * (open-in-view kapalı; ayrıca N+1 sorgusunu da önler).
     */
    @Query("""
           select d from Donation d
           join fetch d.book
           join fetch d.donor
           left join fetch d.preferredPoint
           where d.status = :status
           order by d.createdAt desc
           """)
    List<Donation> findOpenWithDetails(@Param("status") DonationStatus status);

    @Query("""
           select d from Donation d
           join fetch d.book
           join fetch d.donor
           left join fetch d.preferredPoint
           where d.id = :id
           """)
    Optional<Donation> findByIdWithDetails(@Param("id") Long id);

    @Query("""
           select d from Donation d
           join fetch d.book
           join fetch d.donor
           left join fetch d.preferredPoint
           where d.donor = :donor
           order by d.createdAt desc
           """)
    List<Donation> findByDonorWithDetails(@Param("donor") User donor);

    long countByStatus(DonationStatus status);
    long countByDonor(User donor);
    List<Donation> findByStatusOrderByCreatedAtDesc(DonationStatus status);
    List<Donation> findByDonorOrderByCreatedAtDesc(User donor);
}
