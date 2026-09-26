package app.kitappla.web;

import app.kitappla.domain.*;
import app.kitappla.repo.BookRequestRepository;
import app.kitappla.repo.UserRepository;
import app.kitappla.security.AppUserDetails;
import app.kitappla.service.BookService;
import app.kitappla.service.RequestService;
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

/** İstek sayfaları ve adres gizliliği. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestPagesTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired BookRequestRepository requests;
    @Autowired BookService bookService;
    @Autowired RequestService requestService;
    @Autowired PasswordEncoder encoder;

    private User mk(String tag, String address) {
        User u = new User();
        u.setName("Web " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("sifre123"));
        u.setAddress(address);
        return users.save(u);
    }

    private AppUserDetails as(User u) { return new AppUserDetails(u); }

    @Test
    void acikIsteklerSayfasiAnonimeAcilir() throws Exception {
        User isteyen = mk("acik", "Ankara Gizli Sokak 7");
        requestService.create(isteyen, bookService.findOrCreate("Anonim Test Kitabı", "Y", null, null, null, null), null);

        mvc.perform(get("/istekler"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Anonim Test Kitabı")))
                .andExpect(content().string(containsString("Karşılamak için giriş yap")));
    }

    @Test
    void acikIsteklerdeTeslimatAdresiGORUNMEZ() throws Exception {
        User isteyen = mk("gizli", "Ankara Gizli Sokak 7");
        requestService.create(isteyen, bookService.findOrCreate("Gizlilik Kitabı", "Y", null, null, null, null), null);

        mvc.perform(get("/istekler"))
                .andExpect(content().string(not(containsString("Gizli Sokak 7"))));
    }

    @Test
    void istekOlusturulurVeIsteklerimdeGorunur() throws Exception {
        User isteyen = mk("olustur", "İzmir");

        mvc.perform(post("/istek/yeni").with(user(as(isteyen))).with(csrf())
                        .param("title", "Form Testi Kitabı")
                        .param("author", "Form Yazar")
                        .param("description", "Ödev için"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/isteklerim"));

        assertThat(requests.findByStudentWithDetails(isteyen)).hasSize(1);
        mvc.perform(get("/isteklerim").with(user(as(isteyen))))
                .andExpect(content().string(containsString("Form Testi Kitabı")))
                .andExpect(content().string(containsString("Açık · bağışçı bekleniyor")));
    }

    @Test
    void baslikYoksaHataGosterilir() throws Exception {
        User isteyen = mk("hatali", "İzmir");
        mvc.perform(post("/istek/yeni").with(user(as(isteyen))).with(csrf()).param("title", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("istek-yeni"))
                .andExpect(model().attributeExists("hata"));
    }

    @Test
    void kampusTeslimindeIsteyenAdresiGosterilmez() throws Exception {
        User isteyen = mk("adresli", "Ankara Çankaya 99");
        User karsilayan = mk("karsilayan", "İzmir");
        BookRequest r = requestService.create(isteyen,
                bookService.findOrCreate("Adres Testi " + UUID.randomUUID(), "Y", null, null, null, null), null);

        mvc.perform(post("/istek/" + r.getId() + "/karsila").with(user(as(karsilayan))).with(csrf())
                        .param("source", "PURCHASE"))
                .andExpect(redirectedUrl("/karsiladiklarim"));

        // Yüz yüze teslimde adres paylaşılmaz (adres akışı: KargoModuSayfaTest)
        mvc.perform(get("/karsiladiklarim").with(user(as(karsilayan))))
                .andExpect(content().string(not(containsString("Ankara Çankaya 99"))))
                .andExpect(content().string(not(containsString("Kargoya verdim"))))
                .andExpect(content().string(containsString("Buluşma ayarla")));

        // Üçüncü bir kişi görmez
        User yabanci = mk("yabanci", "Bursa");
        mvc.perform(get("/karsiladiklarim").with(user(as(yabanci))))
                .andExpect(content().string(not(containsString("Ankara Çankaya 99"))));
    }

    @Test
    void isteklerimdeTeslimAldimGorunur() throws Exception {
        User isteyen = mk("teslim", "Ankara");
        User karsilayan = mk("karsilayan2", "İzmir");
        BookRequest r = requestService.create(isteyen,
                bookService.findOrCreate("Teslim Testi " + UUID.randomUUID(), "Y", null, null, null, null), null);
        requestService.fulfill(r.getId(), karsilayan, DonationSource.OWN);

        // Yüz yüze teslimde önce buluşma ayarlanır
        mvc.perform(get("/isteklerim").with(user(as(isteyen))))
                .andExpect(content().string(containsString("Buluşma ayarla")));

        mvc.perform(post("/bulusma/istek/" + r.getId()).with(user(as(isteyen))).with(csrf())
                        .param("note", "Yemekhane önü")
                        .param("at", java.time.LocalDateTime.now().plusDays(1)
                                .truncatedTo(java.time.temporal.ChronoUnit.MINUTES).toString()))
                .andExpect(flash().attributeExists("basari"));

        mvc.perform(get("/isteklerim").with(user(as(isteyen))))
                .andExpect(content().string(containsString("Teslim aldım")))
                .andExpect(content().string(containsString("Yemekhane önü")));
    }

    @Test
    void istekOlusturmaGirisIster() throws Exception {
        mvc.perform(get("/istek/yeni"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void karsilananAmaBulusmasiOlmayanIstekIsteklerdeGorunur() throws Exception {
        User isteyen = mk("isteyen-sayfa", "Ankara");
        User karsilayan = mk("karsilayan-sayfa", "İzmir");
        User baska = mk("baska-sayfa", "İstanbul");
        String title = "Sayfa Testi " + UUID.randomUUID();
        BookRequest r = requestService.create(isteyen,
                bookService.findOrCreate(title, "Yazar", null, null, null, null), null);

        // Karşıla ama buluşma kaydetme
        requestService.fulfill(r.getId(), karsilayan, DonationSource.OWN);

        // Başka bir kullanıcı istekler sayfasında bu isteği görür
        mvc.perform(get("/istekler").with(user(as(baska))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(title)))
                .andExpect(content().string(containsString("Buluşma henüz kaydedilmedi")))
                .andExpect(content().string(containsString("Buluşma bekleniyor")));

        // Anonim kullanıcı da istekler sayfasında görür
        mvc.perform(get("/istekler"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(title)))
                .andExpect(content().string(containsString("Buluşma bekleniyor")));
    }

    @Test
    void karsilamayiIptalEtmeIstegiYenidenAcikYapar() throws Exception {
        User isteyen = mk("isteyen-iptal-web", "Ankara");
        User karsilayan = mk("karsilayan-iptal-web", "İzmir");
        String title = "İptal Web " + UUID.randomUUID();
        BookRequest r = requestService.create(isteyen,
                bookService.findOrCreate(title, "Yazar", null, null, null, null), null);

        requestService.fulfill(r.getId(), karsilayan, DonationSource.OWN);

        // Karşılayan iptal eder
        mvc.perform(post("/istek/" + r.getId() + "/iptal")
                        .with(user(as(karsilayan)))
                        .with(csrf())
                        .param("geri", "/karsiladiklarim"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/karsiladiklarim"));

        BookRequest son = requests.findByIdWithDetails(r.getId()).orElseThrow();
        assertThat(son.getStatus()).isEqualTo(RequestStatus.OPEN);
        assertThat(son.getFulfilledBy()).isNull();

        // İstekler sayfasında tekrar "Bu isteği karşıla" görünür
        User baska = mk("baska-iptal-web", "Bursa");
        mvc.perform(get("/istekler").with(user(as(baska))))
                .andExpect(content().string(containsString(title)))
                .andExpect(content().string(containsString("Bu isteği karşıla")));
    }

    @Test
    void karsilamaSayfasindaBulusmaKaydedilirseArrangedOlur() throws Exception {
        User isteyen = mk("isteyen-arrange", "Ankara");
        User karsilayan = mk("karsilayan-arrange", "İzmir");
        String title = "Buluşmalı Karşılama " + UUID.randomUUID();
        BookRequest r = requestService.create(isteyen,
                bookService.findOrCreate(title, "Yazar", null, null, null, null), null);

        mvc.perform(post("/istek/" + r.getId() + "/karsila")
                        .with(user(as(karsilayan)))
                        .with(csrf())
                        .param("source", "OWN")
                        .param("note", "Kütüphane önü")
                        .param("at", java.time.LocalDateTime.now().plusDays(2).truncatedTo(java.time.temporal.ChronoUnit.MINUTES).toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/karsiladiklarim"));

        BookRequest son = requests.findByIdWithDetails(r.getId()).orElseThrow();
        assertThat(son.getStatus()).isEqualTo(RequestStatus.ARRANGED);
        assertThat(son.getMeeting().isArranged()).isTrue();

        // Artık istekler sayfasında görünmez
        mvc.perform(get("/istekler"))
                .andExpect(content().string(not(containsString(title))));
    }
}
