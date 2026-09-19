package app.kitapla.repo;

import app.kitapla.domain.Book;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {

    @org.springframework.data.jpa.repository.Query("""
           select b from Book b
           where lower(b.title) = lower(:title)
             and ((:author is null and (b.author is null or b.author = ''))
                  or (:author is not null and lower(b.author) = lower(:author)))
           """)
    Optional<Book> findFirstByTitleAndAuthorNormalized(@org.springframework.data.repository.query.Param("title") String title,
                                                      @org.springframework.data.repository.query.Param("author") String author);

    /** Arama metni yokken gösterilen ilk 50 kitap (tüm tabloyu okumadan). */
    List<Book> findTop50ByOrderByTitleAsc();
    List<Book> findTop50ByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCaseOrderByTitleAsc(String t, String a);
}
