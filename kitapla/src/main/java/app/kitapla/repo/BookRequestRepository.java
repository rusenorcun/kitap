package app.kitapla.repo;

import app.kitapla.domain.BookRequest;
import app.kitapla.domain.RequestStatus;
import app.kitapla.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface BookRequestRepository extends JpaRepository<BookRequest, Long> {
    List<BookRequest> findByStatusOrderByCreatedAtDesc(RequestStatus status);
    List<BookRequest> findByStudentOrderByCreatedAtDesc(User student);
    List<BookRequest> findByFulfilledByOrderByFulfilledAtDesc(User fulfilledBy);
    long countByStudentAndStatusInAndFulfilledAtAfter(User student, List<RequestStatus> statuses, Instant after);
    long countByStatus(RequestStatus status);
}
