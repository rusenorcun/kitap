package app.kitappla.web;

import app.kitappla.domain.*;
import app.kitappla.repo.BookRepository;
import app.kitappla.repo.UserRepository;
import app.kitappla.security.AppUserDetails;
import app.kitappla.service.*;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Mesajlaşma sayfaları, canlı akış ucu ve erişim denetimi. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessagePagesTest {

    @Autowired MockMvc mvc;
    @Autowired MessageService messages;
    @Autowired DonationService donationService;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired PasswordEncoder encoder;

    private User mk(String tag, boolean ogrenci) {
        User u = new User();
        u.setName("Sayfa " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("sifre123"));
        u.setAddress("İzmir");
        if (ogrenci) {
            u.setStudentStatus(StudentStatus.APPROVED);
            u.setSchoolLevel(SchoolLevel.LISE);
        }
        return users.save(u);
    }

    private AppUserDetails as(User u) { return new AppUserDetails(u); }

    private Claim talep(User donor, User alici) {
        Book b = new Book();
        b.setTitle("Sohbet Kitabı " + UUID.randomUUID());
        books.save(b);
        Donation d = donationService.create(donor, b, 1, TargetLevel.HEPSI, DonationSource.OWN, null);
        return donationService.claim(d.getId(), alici);
    }

    @Test
    void mesajlarGirisIster() throws Exception {
        mvc.perform(get("/mesajlar"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void bosDurumGosterilir() throws Exception {
        User u = mk("bos", false);
        mvc.perform(get("/mesajlar").with(user(as(u))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Henüz sohbetin yok")));
    }

    @Test
    void alisverisUzerindenSohbetAcilirVeYazisilir() throws Exception {
        User donor = mk("acan-bagisci", false);
        User alici = mk("acan-alici", true);
        Claim c = talep(donor, alici);

        // Sohbet yoksa açılır ve içine yönlendirilir
        var sonuc = mvc.perform(get("/mesajlar/ac/claim/" + c.getId()).with(user(as(alici))))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String hedef = sonuc.getResponse().getRedirectedUrl();
        assertThat(hedef).startsWith("/mesajlar/");
        Long sohbetId = Long.valueOf(hedef.substring("/mesajlar/".length()));

        mvc.perform(post("/mesajlar/" + sohbetId).with(user(as(alici))).with(csrf())
                        .param("body", "Kütüphanede saat 14 olur mu?"))
                .andExpect(redirectedUrl("/mesajlar/" + sohbetId));

        // Karşı taraf mesajı görür
        mvc.perform(get("/mesajlar/" + sohbetId).with(user(as(donor))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Kütüphanede saat 14 olur mu?")));

        // Listede önizleme ve karşı tarafın adı
        mvc.perform(get("/mesajlar").with(user(as(donor))))
                .andExpect(content().string(containsString("Kütüphanede saat 14 olur mu?")))
                .andExpect(content().string(containsString(alici.getName())));
    }

    @Test
    void ucuncuKisiSohbetiGoremezVeAkisaAboneOlamaz() throws Exception {
        User donor = mk("gizli-bagisci", false);
        User alici = mk("gizli-alici", true);
        User yabanci = mk("gizli-yabanci", false);
        Claim c = talep(donor, alici);
        Conversation s = messages.open(ConversationKind.CLAIM, c.getId(), alici);
        messages.send(s.getId(), alici, "gizli mesaj");

        // Sohbet sayfası: hata ile mesajlara döner, içerik sızmaz
        mvc.perform(get("/mesajlar/" + s.getId()).with(user(as(yabanci))))
                .andExpect(redirectedUrl("/mesajlar"))
                .andExpect(flash().attributeExists("hata"));

        // Parça ucu da korunmalı — sunucu hatası değil, açıkça 403
        mvc.perform(get("/mesajlar/" + s.getId() + "/liste").with(user(as(yabanci))))
                .andExpect(status().isForbidden());

        // Kendi listesinde başkasının sohbeti görünmez
        mvc.perform(get("/mesajlar").with(user(as(yabanci))))
                .andExpect(content().string(not(containsString("gizli mesaj"))));
    }

    @Test
    void canliAkisUcuAcilirVeYalnizcaTaraflaraAcik() throws Exception {
        User donor = mk("akis-bagisci", false);
        User alici = mk("akis-alici", true);
        Conversation s = messages.open(ConversationKind.CLAIM, talep(donor, alici).getId(), alici);

        mvc.perform(get("/mesajlar/" + s.getId() + "/akis").with(user(as(alici))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/event-stream")));

        User yabanci = mk("akis-yabanci", false);
        mvc.perform(get("/mesajlar/" + s.getId() + "/akis").with(user(as(yabanci))))
                .andExpect(status().isForbidden());
    }

    @Test
    void navdaOkunmamisRozetiGorunur() throws Exception {
        User donor = mk("rozet-bagisci", false);
        User alici = mk("rozet-alici", true);
        Conversation s = messages.open(ConversationKind.CLAIM, talep(donor, alici).getId(), alici);
        messages.send(s.getId(), alici, "okunmamış");

        mvc.perform(get("/panom").with(user(as(donor))))
                .andExpect(content().string(containsString("Mesajlar")));

        // Sohbeti açınca rozet düşer
        mvc.perform(get("/mesajlar/" + s.getId()).with(user(as(donor))));
        assertThat(messages.unreadConversations(donor)).isZero();
    }

    @Test
    void akislardanSohbeteBaglantiVar() throws Exception {
        User donor = mk("baglanti-bagisci", false);
        User alici = mk("baglanti-alici", true);
        Claim c = talep(donor, alici);

        mvc.perform(get("/aldiklarim").with(user(as(alici))))
                .andExpect(content().string(containsString("/mesajlar/ac/claim/" + c.getId())));
        mvc.perform(get("/bagislarim").with(user(as(donor))))
                .andExpect(content().string(containsString("/mesajlar/ac/claim/" + c.getId())));
    }

    @Test
    void yoneticiyeMesajGonderilirVeListedeYonetimOlarakGorunur() throws Exception {
        User uye = mk("destek-sayfa", false);
        mvc.perform(get("/mesajlar").with(user(as(uye))))
                .andExpect(content().string(containsString("Yöneticiye mesaj gönder")));

        var sonuc = mvc.perform(get("/mesajlar/destek").with(user(as(uye))))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String hedef = sonuc.getResponse().getRedirectedUrl();
        assertThat(hedef).matches("/mesajlar/\\d+");

        mvc.perform(post(hedef).with(user(as(uye))).with(csrf()).param("body", "Şifremi yenileyebilir misiniz?"))
                .andExpect(redirectedUrl(hedef));
        mvc.perform(get(hedef).with(user(as(uye))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Şifremi yenileyebilir misiniz?")))
                .andExpect(content().string(containsString("Yönetimle yazışma")))
                .andExpect(content().string(not(containsString("Bu sohbeti şikâyet et"))));
        mvc.perform(get("/mesajlar").with(user(as(uye))))
                .andExpect(content().string(containsString("Şifremi yenileyebilir misiniz?")));
    }

    @Test
    void yoneticiUyelerSayfasindanUyeyeMesajGonderir() throws Exception {
        User admin = mk("uyeler-yonetici", false);
        admin.setAdmin(true);
        users.save(admin);
        User uye = mk("uyeler-hedef", false);

        mvc.perform(get("/admin/uyeler").param("q", uye.getEmail()).with(user(as(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/mesajlar/ac/support/" + uye.getId())));

        var sonuc = mvc.perform(get("/mesajlar/ac/support/" + uye.getId()).with(user(as(admin))))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String hedef = sonuc.getResponse().getRedirectedUrl();
        assertThat(hedef).matches("/mesajlar/\\d+");

        mvc.perform(post(hedef).with(user(as(admin))).with(csrf()).param("body", "İlanın hakkında bir sorumuz var."))
                .andExpect(redirectedUrl(hedef));
        // Üye mesajı yönetimden gelmiş olarak görür, yöneticinin adı görünmez
        mvc.perform(get(hedef).with(user(as(uye))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("İlanın hakkında bir sorumuz var.")))
                .andExpect(content().string(not(containsString(admin.getName()))));

        // Normal üye başkası adına bu sohbeti açamaz
        User baska = mk("uyeler-baska", false);
        mvc.perform(get("/mesajlar/ac/support/" + uye.getId()).with(user(as(baska))))
                .andExpect(redirectedUrl("/mesajlar"));
    }
}
