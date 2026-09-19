package app.kitapla.web;

import app.kitapla.domain.*;
import app.kitapla.repo.SwapBookRepository;
import app.kitapla.repo.SwapOfferRepository;
import app.kitapla.repo.UserRepository;
import app.kitapla.security.AppUserDetails;
import app.kitapla.service.BookService;
import app.kitapla.service.SwapService;
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

/** Takas sayfaları ve adres paylaşımının kabul şartına bağlı olması. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SwapPagesTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired SwapBookRepository swapBooks;
    @Autowired SwapOfferRepository offers;
    @Autowired SwapService swapService;
    @Autowired BookService bookService;
    @Autowired PasswordEncoder encoder;

    private User mk(String tag, String address) {
        User u = new User();
        u.setName("Swap " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("sifre123"));
        u.setAddress(address);
        return users.save(u);
    }

    private AppUserDetails as(User u) { return new AppUserDetails(u); }

    private SwapBook open(User u, String prefix) {
        return swapService.open(u,
                bookService.findOrCreate(prefix + " " + UUID.randomUUID(), "Y", null, null, null, null), "Klasik");
    }

    @Test
    void takasSayfasiGirisIster() throws Exception {
        mvc.perform(get("/takas"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void takasSayfasiBaskalarininKitaplariniListeler() throws Exception {
        User ali = mk("ali", "İzmir");
        User veli = mk("veli", "Ankara");
        SwapBook aliKitap = open(ali, "Listelenen");

        mvc.perform(get("/takas").with(user(as(veli))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(aliKitap.getBook().getTitle())))
                .andExpect(content().string(containsString("Takas teklif et")));
    }

    @Test
    void kitapTakasaAcilirVeListelenir() throws Exception {
        User ali = mk("ekleyen", "İzmir");
        mvc.perform(post("/takas/kitaplarim").with(user(as(ali))).with(csrf())
                        .param("title", "Web Takas Kitabı")
                        .param("author", "Y")
                        .param("note", "Distopya isterim"))
                .andExpect(redirectedUrl("/takas/kitaplarim"));

        assertThat(swapBooks.findByUserWithDetails(ali)).hasSize(1);
        mvc.perform(get("/takas/kitaplarim").with(user(as(ali))))
                .andExpect(content().string(containsString("Web Takas Kitabı")))
                .andExpect(content().string(containsString("Takasta")));
    }

    @Test
    void teklifFormuHedefVeKendiKitaplariniGosterir() throws Exception {
        User ali = mk("ali", "İzmir");
        User veli = mk("veli", "Ankara");
        SwapBook hedef = open(ali, "Hedef");
        SwapBook benim = open(veli, "Benim");

        mvc.perform(get("/takas/teklif/" + hedef.getId()).with(user(as(veli))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(hedef.getBook().getTitle())))
                .andExpect(content().string(containsString(benim.getBook().getTitle())));
    }

    @Test
    void teklifGonderilirVeTakaslarimdaGorunur() throws Exception {
        User ali = mk("ali", "İzmir");
        User veli = mk("veli", "Ankara");
        SwapBook hedef = open(ali, "Hedef");
        SwapBook benim = open(veli, "Benim");

        mvc.perform(post("/takas/teklif/" + hedef.getId()).with(user(as(veli))).with(csrf())
                        .param("offeredId", benim.getId().toString())
                        .param("message", "Olur mu?"))
                .andExpect(redirectedUrl("/takas/takaslarim"));

        mvc.perform(get("/takas/takaslarim").with(user(as(veli))))
                .andExpect(content().string(containsString("Yanıt bekliyor")))
                .andExpect(content().string(containsString("Geri çek")));

        mvc.perform(get("/takas/takaslarim").with(user(as(ali))))
                .andExpect(content().string(containsString("Kabul et")));
    }

    @Test
    void kampusTakasindaAdresHicGorunmez() throws Exception {
        User ali = mk("adresli-ali", "İzmir Gizli Cadde 5");
        User veli = mk("adresli-veli", "Ankara Saklı Sokak 9");
        SwapBook hedef = open(ali, "Hedef");
        SwapBook benim = open(veli, "Benim");
        SwapOffer o = swapService.offer(hedef.getId(), benim.getId(), veli, null);

        // Kabulden ÖNCE: iki taraf da karşı adresi görmemeli
        mvc.perform(get("/takas/takaslarim").with(user(as(veli))))
                .andExpect(content().string(not(containsString("Gizli Cadde 5"))));
        mvc.perform(get("/takas/takaslarim").with(user(as(ali))))
                .andExpect(content().string(not(containsString("Saklı Sokak 9"))));

        // Kabul
        mvc.perform(post("/takas/teklif/" + o.getId() + "/kabul").with(user(as(ali))).with(csrf()))
                .andExpect(redirectedUrl("/takas/takaslarim"));

        // Kabulden SONRA da adres paylaşılmaz; onun yerine buluşma ayarlanır.
        // Kargo modundaki karşılıklı adres paylaşımı: KargoModuSayfaTest
        mvc.perform(get("/takas/takaslarim").with(user(as(veli))))
                .andExpect(content().string(not(containsString("Gizli Cadde 5"))))
                .andExpect(content().string(not(containsString("Kargoya verdim"))))
                .andExpect(content().string(containsString("Buluşma ayarla")));
        mvc.perform(get("/takas/takaslarim").with(user(as(ali))))
                .andExpect(content().string(not(containsString("Saklı Sokak 9"))));
    }

    @Test
    void ciftTeslimSonrasiTamamlandiGorunur() throws Exception {
        User ali = mk("ali", "İzmir");
        User veli = mk("veli", "Ankara");
        SwapOffer o = swapService.offer(open(ali, "A").getId(), open(veli, "V").getId(), veli, null);
        swapService.accept(o.getId(), ali);

        // Yüz yüze takasta önce buluşma ayarlanır
        mvc.perform(post("/bulusma/takas/" + o.getId()).with(user(as(ali))).with(csrf())
                        .param("note", "Yemekhane önü")
                        .param("at", java.time.LocalDateTime.now().plusDays(1)
                                .truncatedTo(java.time.temporal.ChronoUnit.MINUTES).toString()))
                .andExpect(flash().attributeExists("basari"));

        mvc.perform(post("/takas/teklif/" + o.getId() + "/kargola").with(user(as(ali))).with(csrf()));
        mvc.perform(get("/takas/takaslarim").with(user(as(ali))))
                .andExpect(content().string(containsString("Teslimi onayladın")));

        mvc.perform(post("/takas/teklif/" + o.getId() + "/kargola").with(user(as(veli))).with(csrf()));
        assertThat(offers.findById(o.getId()).orElseThrow().getStatus()).isEqualTo(OfferStatus.COMPLETED);
        mvc.perform(get("/takas/takaslarim").with(user(as(veli))))
                .andExpect(content().string(containsString("Tamamlandı")));
    }

    @Test
    void kitabiOlmayanaOnceKitapEkleUyarisiGosterilir() throws Exception {
        User yeni = mk("kitapsiz", "Bursa");
        mvc.perform(get("/takas").with(user(as(yeni))))
                .andExpect(content().string(containsString("önce kendi kitabını aç")));
    }

    @Test
    void takasOnizlemeVeBagisaAktarmaCalisir() throws Exception {
        User ali = mk("onizle-ali", "İzmir");

        // HTMX önizleme testi
        mvc.perform(post("/takas/onizleme").with(user(as(ali))).with(csrf())
                        .param("purchaseLink", "https://example.com/kitap"))
                .andExpect(status().isOk());

        // Kitap oluştur
        SwapBook sb = open(ali, "Aktarılacak");

        // Sayfada "Bağışa aktar" butonunu gör
        mvc.perform(get("/takas/kitaplarim").with(user(as(ali))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/takas/kitaplarim/" + sb.getId() + "/bagisa-aktar")))
                .andExpect(content().string(containsString("Bağışa aktar")));

        // Bağışa aktar
        mvc.perform(post("/takas/kitaplarim/" + sb.getId() + "/bagisa-aktar").with(user(as(ali))).with(csrf()))
                .andExpect(redirectedUrl("/takas/kitaplarim"))
                .andExpect(flash().attributeExists("basari"));

        // Takas listesinden kalktığını doğrula
        assertThat(swapBooks.findById(sb.getId())).isEmpty();
    }

    @Test
    void teklifDetayKiyaslamaVeSohbetSikayetSayfasiCalisir() throws Exception {
        User ali = mk("detay-ali", "İzmir");
        User veli = mk("detay-veli", "Ankara");
        SwapBook hedef = open(ali, "Ali Roman");
        SwapBook benim = open(veli, "Veli Tarih");
        SwapOffer o = swapService.offer(hedef.getId(), benim.getId(), veli, "Takas edebilir miyiz?");

        // 1. Takaslarım sayfasında "Teklifi İncele & Kıyasla", sohbet ve şikâyet linkleri görünmeli
        mvc.perform(get("/takas/takaslarim").with(user(as(ali))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/takas/teklifler/" + o.getId())))
                .andExpect(content().string(containsString("Teklifi İncele & Kıyasla")))
                .andExpect(content().string(containsString("/mesajlar/ac/swap/" + o.getId())))
                .andExpect(content().string(containsString("/sikayet/swap_offer/" + o.getId())));

        // 2. Detaylı yan yana kıyaslama sayfası (/takas/teklifler/{id})
        mvc.perform(get("/takas/teklifler/" + o.getId()).with(user(as(ali))))
                .andExpect(status().isOk())
                .andExpect(view().name("takas-teklif-detay"))
                .andExpect(content().string(containsString("ALACAĞIN KİTAP")))
                .andExpect(content().string(containsString("VERECEĞİN KİTAP")))
                .andExpect(content().string(containsString(hedef.getBook().getTitle())))
                .andExpect(content().string(containsString(benim.getBook().getTitle())))
                .andExpect(content().string(containsString("Takas edebilir miyiz?")))
                .andExpect(content().string(containsString("/mesajlar/ac/swap/" + o.getId())))
                .andExpect(content().string(containsString("/sikayet/swap_offer/" + o.getId())));

        // Yabancı kullanıcı erişemez -> /takas/takaslarim sayfasına yönlenir
        User yabanci = mk("detay-yabanci", "Bursa");
        mvc.perform(get("/takas/teklifler/" + o.getId()).with(user(as(yabanci))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/takas/takaslarim"))
                .andExpect(flash().attributeExists("hata"));
    }
}
