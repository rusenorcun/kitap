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
    List<User> findByStudentStatus(StudentStatus status);
    long countByStudentStatus(StudentStatus status);
    long countByAdminTrue();
    long countByBlockedTrue();
}
