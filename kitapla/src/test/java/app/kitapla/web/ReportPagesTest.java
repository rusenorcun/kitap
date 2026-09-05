package app.kitapla.web;

import app.kitapla.domain.*;
import app.kitapla.repo.BookRepository;
import app.kitapla.repo.UserRepository;
import app.kitapla.security.AppUserDetails;
import app.kitapla.service.*;
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

/** Şikâyet sayfaları ve yönetim kuyruğu. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportPagesTest {

    @Autowired MockMvc mvc;
    @Autowired ReportService reports;
    @Autowired MessageService messages;
    @Autowired DonationService donationService;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired PasswordEncoder encoder;

    private User mk(String tag, boolean admin, boolean ogrenci) {
        User u = new User();
        u.setName("Rapor " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("sifre123"));
        u.setAddress("İzmir");
        u.setAdmin(admin);
        if (ogrenci) {
            u.setStudentStatus(StudentStatus.APPROVED);
            u.setSchoolLevel(SchoolLevel.LISE);
        }
        return users.save(u);
    }

    private AppUserDetails as(User u) { return new AppUserDetails(u); }

    private Conversation sohbet(User donor, User alici) {
        Book b = new Book();
        b.setTitle("Rapor Kitabı " + UUID.randomUUID());
        books.save(b);
        Donation d = donationService.create(donor, b, 1, TargetLevel.HEPSI, DonationSource.OWN, null);
        Claim c = donationService.claim(d.getId(), alici);
        return messages.open(ConversationKind.CLAIM, c.getId(), alici);
    }

    @Test
    void sikayetGirisIster() throws Exception {
        mvc.perform(get("/sikayet/conversation/1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void formVeGonderimCalisir() throws Exception {
        User donor = mk("form-bagisci", false, false);
        User alici = mk("form-alici", false, true);
        Conversation s = sohbet(donor, alici);

        mvc.perform(get("/sikayet/conversation/" + s.getId()).with(user(as(alici)))
                        .param("geri", "/mesajlar/" + s.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Taciz, hakaret ya da tehdit")))
                .andExpect(content().string(containsString("Buluşmaya gelmedi")));

        mvc.perform(post("/sikayet/conversation/" + s.getId()).with(user(as(alici))).with(csrf())
                        .param("reason", "TACIZ").param("note", "kaba sözler")
                        .param("geri", "/mesajlar/" + s.getId()))
                .andExpect(redirectedUrl("/mesajlar/" + s.getId()))
                .andExpect(flash().attributeExists("basari"));

        assertThat(reports.open()).anyMatch(r -> "kaba sözler".equals(r.getNote()));
    }

    @Test
    void sohbetSayfasindaSikayetBaglantisiVar() throws Exception {
        User donor = mk("bag-bagisci", false, false);
        User alici = mk("bag-alici", false, true);
        Conversation s = sohbet(donor, alici);

        mvc.perform(get("/mesajlar/" + s.getId()).with(user(as(alici))))
                .andExpect(content().string(containsString("/sikayet/conversation/" + s.getId())));
    }

    @Test
    void siradanUyeSikayetKuyrugunuGoremez() throws Exception {
        User u = mk("uye", false, false);
        mvc.perform(get("/admin/sikayetler").with(user(as(u)))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/sikayetler/1").with(user(as(u)))).andExpect(status().isForbidden());
    }

    @Test
    void yoneticiKuyruguGorurVeSonuclandirir() throws Exception {
        User yonetici = mk("mod", true, false);
        User donor = mk("q-bagisci", false, false);
        User alici = mk("q-alici", false, true);
        Conversation s = sohbet(donor, alici);
        messages.send(s.getId(), donor, "incelenecek ileti");
        Report r = reports.create(alici, ReportKind.CONVERSATION, s.getId(), ReportReason.TACIZ, "not");

        mvc.perform(get("/admin/sikayetler").with(user(as(yonetici))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Taciz, hakaret ya da tehdit")))
                .andExpect(content().string(containsString(alici.getName())));

        // Açık şikâyette mesajlar görünür
        mvc.perform(get("/admin/sikayetler/" + r.getId()).with(user(as(yonetici))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("incelenecek ileti")));

        mvc.perform(post("/admin/sikayetler/" + r.getId() + "/sonuclandir")
                        .with(user(as(yonetici))).with(csrf())
                        .param("actioned", "true").param("adminNote", "uyarı verildi"))
                .andExpect(redirectedUrl("/admin/sikayetler"))
                .andExpect(flash().attributeExists("basari"));

        assertThat(reports.find(r.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.ACTIONED);
    }

    @Test
    void kapatilanSikayetteMesajlarARTIK_GORUNMEZ() throws Exception {
        User yonetici = mk("kapatan", true, false);
        User donor = mk("kp-bagisci", false, false);
        User alici = mk("kp-alici", false, true);
        Conversation s = sohbet(donor, alici);
        messages.send(s.getId(), donor, "gizli kalacak ileti");
        Report r = reports.create(alici, ReportKind.CONVERSATION, s.getId(), ReportReason.SPAM, null);
        reports.resolve(r.getId(), yonetici, false, null);

        // Şikâyet kapandıktan sonra yönetici de mesajları göremez
        mvc.perform(get("/admin/sikayetler/" + r.getId()).with(user(as(yonetici))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("gizli kalacak ileti"))))
                .andExpect(content().string(containsString("yönetime kapalıdır")));
    }

    @Test
    void claimTeslimatSikayetFormuVeGonderim() throws Exception {
        User donor = mk("ctest-donor", false, false);
        User alici = mk("ctest-alici", false, true);
        Book b = new Book();
        b.setTitle("Teslimat Kitabı " + UUID.randomUUID());
        books.save(b);
        Donation d = donationService.create(donor, b, 1, TargetLevel.HEPSI, DonationSource.OWN, null);
        Claim c = donationService.claim(d.getId(), alici);

        // Teslimat şikâyet formunu aç
        mvc.perform(get("/sikayet/claim/" + c.getId()).with(user(as(alici)))
                        .param("geri", "/aldiklarim"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Kitap hasarlı, eksik veya ilandakinden farklı")))
                .andExpect(content().string(containsString("Teslimat gerçekleşmedi veya teslimat sorunu")));

        // Teslimat şikâyeti gönder
        mvc.perform(post("/sikayet/claim/" + c.getId()).with(user(as(alici))).with(csrf())
                        .param("reason", "HASARLI").param("note", "Kapak yırtık ve sayfalar eksik")
                        .param("geri", "/aldiklarim"))
                .andExpect(redirectedUrl("/aldiklarim"))
                .andExpect(flash().attributeExists("basari"))
                .andExpect(flash().attributeExists("sikayetId"));
    }

    @Test
    void aldiklarimSayfasindaSikayetBaglantisiVar() throws Exception {
        User donor = mk("aldik-donor", false, false);
        User alici = mk("aldik-alici", false, true);
        Book b = new Book();
        b.setTitle("Aldığım Kitap " + UUID.randomUUID());
        books.save(b);
        Donation d = donationService.create(donor, b, 1, TargetLevel.HEPSI, DonationSource.OWN, null);
        Claim c = donationService.claim(d.getId(), alici);

        mvc.perform(get("/aldiklarim").with(user(as(alici))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/sikayet/claim/" + c.getId())));
    }

    @Test
    void adminSikayetDetayindaDestekMesajiGonderilebilir() throws Exception {
        User yonetici = mk("admin-chat", true, false);
        User member = mk("member-chat", false, true);
        User reported = mk("reported-user", false, false);
        Report r = reports.create(member, ReportKind.USER, reported.getId(), ReportReason.TACIZ, "Şikâyet açıklaması");

        mvc.perform(post("/admin/sikayetler/" + r.getId() + "/mesaj")
                        .with(user(as(yonetici))).with(csrf())
                        .param("body", "Merhaba, durumla ilgileniyoruz."))
                .andExpect(redirectedUrl("/admin/sikayetler/" + r.getId()))
                .andExpect(flash().attributeExists("basari"));

        mvc.perform(get("/admin/sikayetler/" + r.getId()).with(user(as(yonetici))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Merhaba, durumla ilgileniyoruz.")));
    }
}
