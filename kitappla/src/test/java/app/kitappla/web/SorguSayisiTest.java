package app.kitappla.web;

import app.kitappla.domain.*;
import app.kitappla.repo.*;
import app.kitappla.security.AppUserDetails;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sık açılan uçların sorgu sayısı kayıt sayısıyla büyümemeli (N+1 koruması).
 * Kapasite taramasında /kesfet 1.501, oturumlu /api/v1/donations 5.263 SQL çalıştırıyordu.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SorguSayisiTest {

    /** Bir liste sayfasından (24) fazla: sayfalama ve toplu sorgular birlikte sınanır. */
    private static final int KAYIT_SAYISI = 30;

    private static Donation ornekBagis;
    private static User talepEden;

    @Autowired MockMvc mvc;
    @Autowired EntityManagerFactory emf;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired DonationRepository donations;
    @Autowired ClaimRepository claims;
    @Autowired ConversationRepository conversations;
    @Autowired MessageRepository messages;

    /** Tek bağışçıdan 30 bağış; hepsini aynı kişi talep etmiş ve her talebin bir sohbeti var. */
    @BeforeEach
    void veriHazirla() {
        if (ornekBagis != null) return;
        User bagisci = uye("sorgu-bagisci");
        talepEden = uye("sorgu-talep");
        for (int i = 0; i < KAYIT_SAYISI; i++) {
            Book b = new Book();
            b.setTitle("Sorgu Sayısı Kitabı " + i);
            Donation d = new Donation();
            d.setDonor(bagisci);
            d.setBook(books.save(b));
            d.setQuantity(2);
            d.setTargetLevel(TargetLevel.HEPSI);
            d = donations.save(d);

            Claim c = new Claim();
            c.setDonation(d);
            c.setStudent(talepEden);
            c = claims.save(c);

            Conversation s = new Conversation();
            s.setKind(ConversationKind.CLAIM);
            s.setRefId(c.getId());
            s.setUserA(bagisci);
            s.setUserB(talepEden);
            s = conversations.save(s);

            Message m = new Message();
            m.setConversation(s);
            m.setSender(bagisci);
            m.setBody("Merhaba " + i);
            messages.save(m);
            ornekBagis = d;
        }
    }

    private User uye(String etiket) {
        User u = new User();
        u.setName("Sorgu " + etiket);
        u.setEmail(etiket + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash("x");
        u.setStudentStatus(StudentStatus.APPROVED);
        u.setSchoolLevel(SchoolLevel.LISE);
        return users.save(u);
    }

    /** İsteği çalıştırır ve Hibernate'in hazırladığı SQL ifadesi sayısını döndürür. */
    private long sorguSayisi(RequestBuilder istek) throws Exception {
        Statistics istatistik = emf.unwrap(SessionFactory.class).getStatistics();
        istatistik.clear();
        mvc.perform(istek).andExpect(status().isOk());
        return istatistik.getPrepareStatementCount();
    }

    @Test
    void kesfetSayfasiBagisBasinaSorguAtmaz() throws Exception {
        assertThat(sorguSayisi(get("/kesfet"))).isLessThanOrEqualTo(4);
        assertThat(sorguSayisi(get("/kesfet/liste").param("sayfa", "1"))).isLessThanOrEqualTo(4);
    }

    @Test
    void kitapDetayiBenzerBolumuIcinTumListeyiOkumaz() throws Exception {
        assertThat(sorguSayisi(get("/kitap/" + ornekBagis.getId()))).isLessThanOrEqualTo(6);
    }

    @Test
    void oturumluApiBagisListesiUygunlugunuSabitSorguylaHesaplar() throws Exception {
        User ogrenci = uye("sorgu-api");
        long sorgu = sorguSayisi(get("/api/v1/donations").with(user(new AppUserDetails(ogrenci))));
        // kullanıcı tazeleme 1 + liste 1 + kalan adetler 1 + talep edilenler 1 + kota 4
        assertThat(sorgu).isLessThanOrEqualTo(10);
    }

    @Test
    void apiIstegiNavRozetleriniHesaplamaz() throws Exception {
        User u = uye("sorgu-rozet");
        // Yalnızca FreshPrincipalFilter'ın kullanıcı tazelemesi kalmalı
        assertThat(sorguSayisi(get("/api/v1/features").with(user(new AppUserDetails(u))))).isLessThanOrEqualTo(1);
    }

    @Test
    void sohbetListesiOkunmamisSayilariniSohbetBasinaSorgulamaz() throws Exception {
        assertThat(sorguSayisi(get("/mesajlar").with(user(new AppUserDetails(talepEden))))).isLessThanOrEqualTo(6);
        assertThat(sorguSayisi(get("/api/v1/conversations").with(user(new AppUserDetails(talepEden))))).isLessThanOrEqualTo(4);
    }

    @Test
    void apiTalepListesiSohbetKimliginiTalepBasinaAramaz() throws Exception {
        assertThat(sorguSayisi(get("/api/v1/my/claims").with(user(new AppUserDetails(talepEden))))).isLessThanOrEqualTo(4);
    }
}
