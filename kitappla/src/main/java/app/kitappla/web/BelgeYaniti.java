package app.kitappla.web;

import app.kitappla.service.DocumentService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Öğrenci belgesinin yöneticiye gösterilen yanıtı; web ({@code /admin/belge/{id}}) ve mobil API
 * ({@code /api/v1/admin/docs/{userId}/file}) aynı başlıkları kullanır. Belge indirilmez, satır içi gösterilir;
 * içerik türü tahmin edilmez, sayfa olarak çalıştırılamaz (sandbox) ve hiçbir ara bellekte tutulmaz.
 */
public final class BelgeYaniti {

    private BelgeYaniti() {
    }

    public static ResponseEntity<FileSystemResource> satirIci(Long userId, Path file) throws IOException {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, DocumentService.resolveContentType(file))
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox; default-src 'none'; style-src 'unsafe-inline'")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-cache, no-store, must-revalidate")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"belge-" + userId + uzanti(file) + "\"")
                .contentLength(Files.size(file))
                .body(new FileSystemResource(file));
    }

    private static String uzanti(Path file) {
        String n = file.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot < 0 ? "" : n.substring(dot);
    }
}
