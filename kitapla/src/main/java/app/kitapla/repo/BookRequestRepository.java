package app.kitapla.repo;

import app.kitapla.domain.BookRequest;
import app.kitapla.domain.RequestStatus;
import app.kitapla.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookRequestRepository extends JpaRepository<BookRequest, Long> {

    /** Şablonlar kitap ve isteyene eriştiği için ilişkiler birlikte çekilir (open-in-view kapalı). */
    @Query("""
           select r from BookRequest r
           join fetch r.book
           join fetch r.student
           left join fetch r.fulfilledBy
           left join fetch r.meeting.point
           where r.status = :status
           order by r.createdAt desc
           """)
    List<BookRequest> findByStatusWithDetails(@Param("status") RequestStatus status);

    @Query("""
           select r from BookRequest r
           join fetch r.book
           join fetch r.student
           left join fetch r.fulfilledBy
           left join fetch r.meeting.point
           where r.student = :student
           order by r.createdAt desc
           """)
    List<BookRequest> findByStudentWithDetails(@Param("student") User student);

    @Query("""
           select r from BookRequest r
           join fetch r.book
           join fetch r.student
           left join fetch r.fulfilledBy
           left join fetch r.meeting.point
           where r.fulfilledBy = :user
           order by r.fulfilledAt desc
           """)
    List<BookRequest> findByFulfilledByWithDetails(@Param("user") User user);

    @Query("""
           select r from BookRequest r
           join fetch r.book
           join fetch r.student
           left join fetch r.fulfilledBy
           left join fetch r.meeting.point
           where r.id = :id
           """)
    Optional<BookRequest> findByIdWithDetails(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           select r from BookRequest r
           join fetch r.book
           join fetch r.student
           left join fetch r.fulfilledBy
           left join fetch r.meeting.point
           where r.id = :id
           """)
    Optional<BookRequest> findByIdWithDetailsForUpdate(@Param("id") Long id);

    @Query("""
           select r from BookRequest r
           join fetch r.book
           join fetch r.student
           left join fetch r.fulfilledBy
           left join fetch r.meeting.point
           where r.status = :status
             and r.meeting.arrangedAt is not null
             and r.meeting.remindedAt is null
             and r.meeting.at between :simdi and :esik
           """)
    List<BookRequest> findYaklasanBulusmalar(@Param("status") RequestStatus status,
                                             @Param("simdi") java.time.Instant simdi,
                                             @Param("esik") java.time.Instant esik);

    List<BookRequest> findByStatusOrderByCreatedAtDesc(RequestStatus status);
    List<BookRequest> findByStudentOrderByCreatedAtDesc(User student);
    List<BookRequest> findByFulfilledByOrderByFulfilledAtDesc(User fulfilledBy);
    long countByStudentAndStatusInAndFulfilledAtAfter(User student, List<RequestStatus> statuses, Instant after);
    long countByStudent(User student);
    long countByStatus(RequestStatus status);
    long countByStudentAndStatus(User student, RequestStatus status);
}
