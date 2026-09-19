package app.kitapla.web;

import app.kitapla.service.CoverService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Kapaklar herkese açık ve uzun süre önbelleğe alınabilir biçimde sunulur. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KapakSunumTest {

    @Autowired MockMvc mvc;
    @Autowired CoverService covers;

    @Test
    void kapakUzunSureliOnbellekBasligiylaSunulur() throws Exception {
        java.io.ByteArrayOutputStream cikti = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(
                new java.awt.image.BufferedImage(10, 10, java.awt.image.BufferedImage.TYPE_INT_ARGB),
                "png", cikti);
        String adres = covers.saveUpload(
                new MockMultipartFile("coverFile", "kapak.png", "image/png", cikti.toByteArray()));

        mvc.perform(get(adres))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control",
                        allOf(containsString("max-age=31536000"), containsString("public"), containsString("immutable"))));
    }
}
