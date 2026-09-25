package app.kitappla.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TomcatMultipartTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void cokSayidaParcaIcerenMultipartFormHataVermez() {
        // Tomcat varsayılanı (10 parça) aşıldığında FileCountLimitExceededException ve 403/500
        // vermemeli; max-part-count=50 ayarı sayesinde istek normal karşılanmalı.
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        for (int i = 1; i <= 15; i++) {
            parts.add("field" + i, "value" + i);
        }
        parts.add("file", new ByteArrayResource(new byte[]{1, 2, 3}) {
            @Override
            public String getFilename() {
                return "test.bin";
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(parts, headers);

        // Anonim POST /bagis/yeni isteği CSRF veya yetki nedeniyle 302 ya da 403 dönebilir,
        // ancak Tomcat'in FileCountLimitExceededException (500 veya ham 403) hatasına düşmemeli.
        // Daha da önemlisi, multipart parse hatası olmadan Spring katmanına ulaşmalıdır.
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/bagis/yeni", request, String.class);

        // Tomcat multipart parse hatası verirse yanıt gövdesinde FileCountLimitExceededException
        // veya Apache Tomcat hata sayfası görülürdü.
        assertThat(response.getBody()).doesNotContain("FileCountLimitExceededException");
        assertThat(response.getBody()).doesNotContain("HTTP Status 403 – Forbidden");
    }
}
