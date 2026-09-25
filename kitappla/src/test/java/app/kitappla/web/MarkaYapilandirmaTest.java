package app.kitappla.web;

import app.kitappla.domain.*;
import app.kitappla.mail.MailService;
import app.kitappla.repo.UserRepository;
import app.kitappla.security.AppUserDetails;
import app.kitappla.service.MessageService;
import app.kitappla.service.PasswordResetService;
import app.kitappla.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Marka adı tek yapılandırmadan gelir: özel bir ad verildiğinde web sayfalarında,
 * e-postalarda, destek sohbetinde ve mobil API'de eski ad hiçbir yerde kalmamalı.
 */
@SpringBootTest(properties = {
        "kitappla.name=Kitap Köprüsü",
        "kitappla.domain=kopru.example.org",
        "kitappla.contact.email="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarkaYapilandirmaTest {

    private static final String AD = "Kitap Köprüsü";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired PasswordResetService passwordReset;
    @Autowired MailService mail;
    @Autowired ReportService reports;
    @Autowired MessageService messages;

    private User mk(String tag, boolean admin) {
        User u = new User();
        u.setName("Marka " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("sifre123"));
        u.setAdmin(admin);
        return users.save(u);
    }

    @Test
    void herkeseAcikSayfalardaYalnizcaYapilandirilanAdGorunur() throws Exception {
        for (String yol : new String[]{"/", "/login", "/register", "/kesfet", "/iletisim", "/gizlilik", "/sss", "/kurallar"}) {
            mvc.perform(get(yol))
                    .andExpect(content().string(containsString(AD)))
                    .andExpect(content().string(not(containsString("KİTAPLA"))));
        }
        // İletişim adresi boşsa marka alan adından türetilir; sayfa boş mailto göstermemeli
        mvc.perform(get("/iletisim"))
                .andExpect(content().string(containsString("info@kopru.example.org")));
    }

    @Test
    void oturumluSayfalardaUstMenuVeAltBilgiMarkayiKullanir() throws Exception {
        User u = mk("oturumlu", false);
        mvc.perform(get("/panom").with(user(new AppUserDetails(u))))
                .andExpect(content().string(containsString("<title>" + AD + " — Panom</title>")))
                .andExpect(content().string(not(containsString("${marka"))))
                .andExpect(content().string(not(containsString("KİTAPLA"))));
    }

    @Test
    void epostaKonusuVeGovdesiMarkayiKullanir() {
        User u = mk("posta", false);
        passwordReset.request(u.getEmail());
        var son = mail.outbox().get(mail.outbox().size() - 1);
        assertThat(son.subject()).startsWith(AD).doesNotContain("KİTAPLA");
        assertThat(son.html()).contains(AD).doesNotContain("KİTAPLA");
    }

    @Test
    void destekSohbetiMarkaAdiylaGosterilir() throws Exception {
        User admin = mk("destek-yonetici", true);
        User sikayetci = mk("destek-uye", false);
        Report r = reports.create(sikayetci, ReportKind.USER, admin.getId(), ReportReason.TACIZ, "Not");
        Conversation c = messages.open(ConversationKind.REPORT, r.getId(), admin);
        messages.send(c.getId(), admin, "Merhaba");

        mvc.perform(get("/mesajlar/" + c.getId()).with(user(new AppUserDetails(sikayetci))))
                .andExpect(content().string(containsString(AD + " Destek")))
                .andExpect(content().string(not(containsString("Kitapla Destek"))));
        mvc.perform(get("/mesajlar").with(user(new AppUserDetails(sikayetci))))
                .andExpect(content().string(containsString(AD + " Destek")));

        mvc.perform(get("/api/v1/conversations").with(user(new AppUserDetails(sikayetci))))
                .andExpect(jsonPath("$[0].counterpartName").value(AD + " Destek"));
    }
}
