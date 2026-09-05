package app.kitapla.web;

import app.kitapla.domain.*;
import app.kitapla.repo.*;
import app.kitapla.security.AppUserDetails;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * JVM varsayılan yerel ayarı Türkçe (tr_TR) olduğunda:
 * - "claim".toUpperCase() -> "CLAİM"
 * - "conversation".toUpperCase() -> "CONVERSATİON"
 * - "lise".toUpperCase() -> "LİSE"
 * - "universite".toUpperCase() -> "UNİVERSİTE"
 * üretilmesine rağmen Locale.ROOT sayesinde hiçbir akışın bozulmadığını doğrular.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TurkceYerelAyarDayaniklilikTest {

    private static Locale defaultLocaleOncesi;

    @BeforeAll
    static void turkceYerelAyarAyarla() {
        defaultLocaleOncesi = Locale.getDefault();
        Locale.setDefault(new Locale("tr", "TR"));
    }

    @AfterAll
    static void yerelAyariGeriYukle() {
        if (defaultLocaleOncesi != null) {
            Locale.setDefault(defaultLocaleOncesi);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired DonationRepository donations;
    @Autowired ClaimRepository claims;
    @Autowired ReportRepository reports;

    @Autowired ConversationRepository conversations;

    private User mkUser(String name, boolean student) {
        User u = new User();
        u.setName(name);
        u.setEmail("tr-locale-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash("x");
        if (student) {
            u.setStudentStatus(StudentStatus.APPROVED);
            u.setSchoolLevel(SchoolLevel.UNIVERSITE);
        }
        return users.save(u);
    }

    @Test
    void alisverisUzerindenSohbetAcmaTurkceLocaleIleCalisir() throws Exception {
        User donor = mkUser("Bağışçı Ali", false);
        User student = mkUser("Öğrenci Ayşe", true);

        Book b = new Book();
        b.setTitle("Türkçe Test Kitabı " + UUID.randomUUID());
        b = books.save(b);

        Donation d = new Donation();
        d.setDonor(donor);
        d.setBook(b);
        d.setQuantity(1);
        d = donations.save(d);

        Claim c = new Claim();
        c.setDonation(d);
        c.setStudent(student);
        c = claims.save(c);

        // /mesajlar/ac/claim/{id} isteği 'claim' küçük harfiyle gelir
        var result = mvc.perform(get("/mesajlar/ac/claim/" + c.getId())
                        .with(user(new AppUserDetails(student))))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String target = result.getResponse().getRedirectedUrl();
        assertThat(target).as("Sohbete yönlendirmeli").startsWith("/mesajlar/");
    }

    @Test
    void sohbetSikayetiTurkceLocaleIleCalisir() throws Exception {
        User u1 = mkUser("Şikayetçi Üye", false);
        User u2 = mkUser("Şikayet Edilen", false);

        Conversation conv = new Conversation();
        conv.setKind(ConversationKind.CLAIM);
        conv.setRefId(12345L);
        conv.setUserA(u1);
        conv.setUserB(u2);
        conv = conversations.save(conv);

        mvc.perform(post("/sikayet/conversation/" + conv.getId()).with(csrf())
                        .with(user(new AppUserDetails(u1)))
                        .param("reason", "TACIZ")
                        .param("note", "uygunsuz içerik")
                        .param("geri", "/panom"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("basari", containsString("Şikâyetin")));
    }

    @Test
    void kesfetSeviyeFiltresiTurkceLocaleIleCalisir() throws Exception {
        User donor = mkUser("Bağışçı Lise", false);

        Book b1 = new Book();
        b1.setTitle("Lise Seviye Kitap " + UUID.randomUUID());
        b1 = books.save(b1);

        Donation d1 = new Donation();
        d1.setDonor(donor);
        d1.setBook(b1);
        d1.setQuantity(1);
        d1.setTargetLevel(TargetLevel.LISE);
        donations.save(d1);

        Book b2 = new Book();
        b2.setTitle("Üniversite Seviye Kitap " + UUID.randomUUID());
        b2 = books.save(b2);

        Donation d2 = new Donation();
        d2.setDonor(donor);
        d2.setBook(b2);
        d2.setQuantity(1);
        d2.setTargetLevel(TargetLevel.UNIVERSITE);
        donations.save(d2);

        // level=lise (küçük harfle) filtreleme
        mvc.perform(get("/kesfet/liste").param("level", "lise"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(b1.getTitle())));

        // level=universite (küçük harfle) filtreleme
        mvc.perform(get("/kesfet/liste").param("level", "universite"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(b2.getTitle())));
    }
}
