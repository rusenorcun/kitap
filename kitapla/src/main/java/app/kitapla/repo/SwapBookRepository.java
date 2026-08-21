package app.kitapla.repo;

import app.kitapla.domain.SwapBook;
import app.kitapla.domain.SwapBookStatus;
import app.kitapla.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SwapBookRepository extends JpaRepository<SwapBook, Long> {
    List<SwapBook> findByStatusAndUserNotOrderByCreatedAtDesc(SwapBookStatus status, User notUser);
    List<SwapBook> findByUserOrderByCreatedAtDesc(User user);
    Optional<SwapBook> findByUserAndBook_Id(User user, Long bookId);
}
