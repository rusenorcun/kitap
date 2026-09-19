package app.kitapla.api.v1;

import app.kitapla.api.dto.ApiDtoMapper;
import app.kitapla.api.dto.BookDto;
import app.kitapla.api.dto.BookMetadataDto;
import app.kitapla.api.dto.PreviewBody;
import app.kitapla.domain.Book;
import app.kitapla.service.BookMetadata;
import app.kitapla.service.BookService;
import app.kitapla.service.OpenGraphService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/books")
public class BookApiController {

    private final BookService bookService;
    private final OpenGraphService openGraphService;

    public BookApiController(BookService bookService, OpenGraphService openGraphService) {
        this.bookService = bookService;
        this.openGraphService = openGraphService;
    }

    @GetMapping
    public ResponseEntity<List<BookDto>> searchBooks(@RequestParam(required = false) String q) {
        List<Book> books = bookService.search(q);
        List<BookDto> dtos = books.stream().map(ApiDtoMapper::toBookDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/preview")
    public ResponseEntity<BookMetadataDto> previewBook(@Valid @RequestBody PreviewBody body) {
        BookMetadata meta = openGraphService.fetch(body.purchaseLink());
        boolean found = meta != null && (meta.title() != null || meta.imageUrl() != null);
        return ResponseEntity.ok(ApiDtoMapper.toBookMetadataDto(meta, found));
    }
}
