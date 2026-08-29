package app.kitapla.repo;

import app.kitapla.domain.Claim;
import app.kitapla.domain.ClaimStatus;
import app.kitapla.domain.Donation;
import app.kitapla.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ClaimRepository extends JpaRepository<Claim, Long> {
    long countByDonation(Donation donation);

    /** Kalan adet hesabı: gelinmeyen buluşmalar kitabı havuza geri bırakır. */
    long countByDonationAndStatusNot(Donation donation, ClaimStatus status);
    boolean existsByDonationAndStudent(Donation donation, User student);
    @Query("""
           select c from Claim c
           join fetch c.donation d
           join fetch d.book
           join fetch d.donor
           left join fetch c.meeting.point
           where c.student = :student
           order by c.createdAt desc
           """)
    List<Claim> findByStudentWithDetails(@Param("student") User student);

    @Query("""
           select c from Claim c
           join fetch c.donation d
           join fetch d.book
           join fetch d.donor
           join fetch c.student
           left join fetch c.meeting.point
           where c.id = :id
           """)
    Optional<Claim> findByIdWithDetails(@Param("id") Long id);

    @Query("""
           select c from Claim c
           join fetch c.student
           left join fetch c.meeting.point
           where c.donation = :donation
           order by c.createdAt
           """)
    List<Claim> findByDonationWithStudent(@Param("donation") Donation donation);

    /** Yaklaşan ve henüz hatırlatılmamış buluşmalar. */
    @Query("""
           select c from Claim c
           join fetch c.donation d
           join fetch d.book
           join fetch d.donor
           join fetch c.student
           left join fetch c.meeting.point
           where c.status = :status
             and c.meeting.arrangedAt is not null
             and c.meeting.remindedAt is null
             and c.meeting.at between :simdi and :esik
           """)
    List<Claim> findYaklasanBulusmalar(@Param("status") ClaimStatus status,
                                       @Param("simdi") Instant simdi,
                                       @Param("esik") Instant esik);

    List<Claim> findByStudentOrderByCreatedAtDesc(User student);
    List<Claim> findByDonation(Donation donation);
    long countByStudentAndCreatedAtAfter(User student, Instant after);
    long countByStatus(ClaimStatus status);
    long countByStudent(User student);
}
