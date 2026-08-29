package app.kitapla.repo;

import app.kitapla.domain.Report;
import app.kitapla.domain.ReportKind;
import app.kitapla.domain.ReportStatus;
import app.kitapla.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /** Yönetim kuyruğu; şablonlar kişileri gösterdiği için birlikte çekilir. */
    @Query("""
           select r from Report r
           join fetch r.reporter
           left join fetch r.reportedUser
           left join fetch r.reviewedBy
           where r.status = :status
           order by r.createdAt asc
           """)
    List<Report> findByStatusWithUsers(@Param("status") ReportStatus status);

    @Query("""
           select r from Report r
           join fetch r.reporter
           left join fetch r.reportedUser
           left join fetch r.reviewedBy
           order by r.createdAt desc
           """)
    List<Report> findAllWithUsers();

    @Query("""
           select r from Report r
           join fetch r.reporter
           left join fetch r.reportedUser
           left join fetch r.reviewedBy
           where r.id = :id
           """)
    java.util.Optional<Report> findByIdWithUsers(@Param("id") Long id);

    /** Aynı kişi aynı şeyi ikinci kez şikâyet etmesin. */
    boolean existsByReporterAndKindAndRefIdAndStatus(User reporter, ReportKind kind, Long refId,
                                                     ReportStatus status);

    /** Yöneticinin sohbeti okuyabilmesi bu kontrole bağlıdır. */
    boolean existsByKindAndRefIdAndStatus(ReportKind kind, Long refId, ReportStatus status);

    long countByStatus(ReportStatus status);

    void deleteByReporter(User reporter);
}
