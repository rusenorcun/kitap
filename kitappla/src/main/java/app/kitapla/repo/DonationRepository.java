package app.kitapla.repo;

import app.kitapla.domain.TargetLevel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import java.util.Collection;
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

    /**
     * Keşfet listesi. Seviye, arama ve "yalnızca kalan adedi olanlar" filtreleri ile sayfalama
     * veritabanında uygulanır. {@code desen} küçük harfli bir LIKE desenidir; "%" filtre yok demektir.
     * Seviye seçilmemişse ({@code tumSeviyeler}) seviyesi boş kalmış eski kayıtlar da listelenir.
     */
    @Query(value = """
           select d from Donation d
           join fetch d.book b
           join fetch d.donor dn
           left join fetch d.preferredPoint
           where d.status = app.kitapla.domain.DonationStatus.OPEN and dn.blocked = false
             and (:tumSeviyeler = true or d.targetLevel in :seviyeler)
             and (lower(b.title) like :desen or lower(b.author) like :desen)
             and (:yalnizcaKalan = false or d.quantity > (
                    select count(c) from Claim c
                    where c.donation = d
                      and c.status not in (app.kitapla.domain.ClaimStatus.NO_SHOW, app.kitapla.domain.ClaimStatus.CANCELLED)))
           order by d.createdAt desc, d.id desc
           """,
           countQuery = """
           select count(d) from Donation d join d.book b
           where d.status = app.kitapla.domain.DonationStatus.OPEN and d.donor.blocked = false
             and (:tumSeviyeler = true or d.targetLevel in :seviyeler)
             and (lower(b.title) like :desen or lower(b.author) like :desen)
             and (:yalnizcaKalan = false or d.quantity > (
                    select count(c) from Claim c
                    where c.donation = d
                      and c.status not in (app.kitapla.domain.ClaimStatus.NO_SHOW, app.kitapla.domain.ClaimStatus.CANCELLED)))
           """)
    Page<Donation> findOpenPage(@Param("tumSeviyeler") boolean tumSeviyeler,
                                @Param("seviyeler") Collection<TargetLevel> seviyeler,
                                @Param("desen") String desen,
                                @Param("yalnizcaKalan") boolean yalnizcaKalan,
                                Pageable pageable);

    @Query("""
           select d from Donation d
           join fetch d.book
           join fetch d.donor
           left join fetch d.preferredPoint
           where d.id = :id
           """)
    Optional<Donation> findByIdWithDetails(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           select d from Donation d
           join fetch d.book
           join fetch d.donor
           left join fetch d.preferredPoint
           where d.id = :id
           """)
    Optional<Donation> findByIdWithDetailsForUpdate(@Param("id") Long id);

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
}
