package app.kitapla.repo;

import app.kitapla.domain.Book;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {
    Optional<Book> findFirstByTitleIgnoreCaseAndAuthorIgnoreCase(String title, String author);
    List<Book> findTop50ByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCaseOrderByTitleAsc(String t, String a);
}
