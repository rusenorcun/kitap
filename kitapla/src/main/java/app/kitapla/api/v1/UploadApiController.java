package app.kitapla.api.v1;

import app.kitapla.api.dto.UploadedFileDto;
import app.kitapla.service.CoverService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/uploads")
public class UploadApiController {

    private final CoverService coverService;

    public UploadApiController(CoverService coverService) {
        this.coverService = coverService;
    }

    @PostMapping
    public ResponseEntity<UploadedFileDto> uploadCover(@RequestParam("file") MultipartFile file) {
        String url = coverService.saveUpload(file);
        if (url == null) {
            throw new IllegalArgumentException("Dosya yüklenemedi.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(new UploadedFileDto(url));
    }
}
