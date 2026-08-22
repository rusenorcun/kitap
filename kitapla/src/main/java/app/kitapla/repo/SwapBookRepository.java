package app.kitapla.repo;

import app.kitapla.domain.SwapBook;
import app.kitapla.domain.SwapBookStatus;
import app.kitapla.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SwapBookRepository extends JpaRepository<SwapBook, Long> {

    /** Başkalarının takasa açtığı kitaplar (keşif). */
    @Query("""
           select s from SwapBook s
           join fetch s.book
           join fetch s.user
           where s.status = :status and s.user <> :me
           order by s.createdAt desc
           """)
    List<SwapBook> findOpenOfOthers(@Param("status") SwapBookStatus status, @Param("me") User me);

    @Query("""
           select s from SwapBook s
           join fetch s.book
           join fetch s.user
           where s.user = :user
           order by s.createdAt desc
           """)
    List<SwapBook> findByUserWithDetails(@Param("user") User user);

    @Query("""
           select s from SwapBook s
           join fetch s.book
           join fetch s.user
           where s.user = :user and s.status = :status
           order by s.createdAt desc
           """)
    List<SwapBook> findByUserAndStatusWithDetails(@Param("user") User user, @Param("status") SwapBookStatus status);

    @Query("""
           select s from SwapBook s
           join fetch s.book
           join fetch s.user
           where s.id = :id
           """)
    Optional<SwapBook> findByIdWithDetails(@Param("id") Long id);

    List<SwapBook> findByStatusAndUserNotOrderByCreatedAtDesc(SwapBookStatus status, User notUser);
    List<SwapBook> findByUserOrderByCreatedAtDesc(User user);
    Optional<SwapBook> findByUserAndBook_Id(User user, Long bookId);
}
