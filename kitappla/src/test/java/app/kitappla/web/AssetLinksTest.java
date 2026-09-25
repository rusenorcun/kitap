package app.kitappla.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Android App Links: doğrulama dosyası kimliksiz, yönlendirmesiz ve doğru paket/sertifikayla sunulmalı. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssetLinksTest {

    @Autowired MockMvc mvc;

    @Test
    void kimliksiz_ve_yonlendirmesiz_sunulur() throws Exception {
        mvc.perform(get("/.well-known/assetlinks.json"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().string("Cache-Control", containsString("max-age=3600")))
                .andExpect(jsonPath("$[0].relation[0]").value("delegate_permission/common.handle_all_urls"))
                .andExpect(jsonPath("$[0].target.namespace").value("android_app"))
                .andExpect(jsonPath("$[0].target.package_name").value("com.kitappla.app"))
                .andExpect(jsonPath("$[0].target.sha256_cert_fingerprints[0]").value(
                        "46:89:02:F9:02:24:90:66:AD:FB:73:02:BC:4D:A0:6F:CA:7D:6A:9B:B9:E0:7F:EB:7B:6B:06:D1:97:11:74:AF"));
    }

    @Test
    void gecersiz_parmak_izi_uygulamayi_baslatmaz() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new AssetLinksController("com.kitappla.app", "46:89:ZZ"))
                .isInstanceOf(IllegalStateException.class);
    }
}
