package app.kitapla.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Herkese açık sayfaları gezen anonim ziyaretçi sunucuda oturum oluşturmamalı: prod'da oturumlar
 * 7 gün bellekte kalır ve arama motoru botları binlercesini biriktirebilir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnonimOturumTest {

    @Autowired MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"/", "/kesfet", "/kesfet/liste", "/istekler", "/sss"})
    void herkeseAcikSayfaOturumOlusturmaz(String yol) throws Exception {
        MvcResult sonuc = mvc.perform(get(yol)).andExpect(status().isOk()).andReturn();
        assertThat(sonuc.getRequest().getSession(false)).as(yol).isNull();
    }

    @Test
    void girisFormuCsrfJetonunuGizliAlandaTasir() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }
}
