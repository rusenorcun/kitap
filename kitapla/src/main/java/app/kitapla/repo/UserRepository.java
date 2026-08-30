package app.kitapla.repo;

import app.kitapla.domain.StudentStatus;
import app.kitapla.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByDocumentNo(String documentNo);
    boolean existsByStudentEmail(String studentEmail);
    List<User> findByStudentStatus(StudentStatus status);
    long countByStudentStatus(StudentStatus status);
    List<User> findByStudentStatusOrderByCreatedAtDesc(StudentStatus status);
    List<User> findTop200ByOrderByCreatedAtDesc();
    List<User> findTop200ByNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByCreatedAtDesc(String name, String email);
    long countByAdminTrue();
    long countByBlockedTrue();
}
