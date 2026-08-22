package app.kitapla.service;

import app.kitapla.domain.Book;
import app.kitapla.repo.BookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Merkezi kitap veri tabanı. Aynı ad + yazar ikinci kez oluşturulmaz (find-or-create);
 * alışveriş linki verilirse eksik alanlar OpenGraph'tan doldurulur.
 */
@Service
public class BookService {

    private final BookRepository books;
    private final OpenGraphService openGraph;

    public BookService(BookRepository books, OpenGraphService openGraph) {
        this.books = books;
        this.openGraph = openGraph;
    }

    private static String clean(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }

    /** Ad + yazar eşleşmesiyle mevcut kitabı bulur (büyük/küçük harf duyarsız). */
    public Optional<Book> find(String title, String author) {
        String t = clean(title, 300);
        if (t == null) return Optional.empty();
        return books.findFirstByTitleIgnoreCaseAndAuthorIgnoreCase(t, author == null ? "" : author.trim());
    }

    public Optional<Book> byId(Long id) {
        return id == null ? Optional.empty() : books.findById(id);
    }

    public List<Book> search(String query) {
        String q = clean(query, 100);
        if (q == null) return books.findAll().stream().limit(50).toList();
        return books.findTop50ByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCaseOrderByTitleAsc(q, q);
    }

    /**
     * Kitabı bulur, yoksa oluşturur. Link verilmişse ve başlık/kapak eksikse
     * OpenGraph'tan tamamlanır. Başlık hiçbir şekilde çıkarılamazsa hata verir.
     */
    @Transactional
    public Book findOrCreate(String title, String author, String purchaseLink, String coverUrl,
                             String description, Long createdBy) {
        String t = clean(title, 300);
        String a = clean(author, 200);
        String link = clean(purchaseLink, 1000);
        String cover = clean(coverUrl, 1000);
        String desc = clean(description, 1000);

        // Link varsa eksikleri OpenGraph ile tamamla
        if (link != null && (t == null || cover == null || a == null)) {
            BookMetadata meta = openGraph.fetch(link);
            if (t == null) t = clean(meta.title(), 300);
            if (cover == null) cover = clean(meta.imageUrl(), 1000);
            if (a == null) a = clean(meta.author(), 200);
            if (desc == null) desc = clean(meta.description(), 1000);
        }

        if (t == null) {
            throw new IllegalArgumentException(
                    "Kitap adı gerekli. Geçerli bir alışveriş linki verin ya da adı elle girin.");
        }

        Optional<Book> existing = find(t, a);
        if (existing.isPresent()) return existing.get();

        Book b = new Book();
        b.setTitle(t);
        b.setAuthor(a);
        b.setPurchaseLink(link);
        b.setCoverUrl(cover);
        b.setDescription(desc);
        b.setCreatedBy(createdBy);
        return books.save(b);
    }

    /** Linkten üst veri önizlemesi (kaydetmeden). */
    public BookMetadata preview(String url) {
        return openGraph.fetch(url);
    }
}
