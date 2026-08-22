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
    void ADRES_kabulden_once_GORUNMEZ_kabulden_sonra_gorunur() throws Exception {
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

        // Kabulden SONRA: karşılıklı adresler görünür
        mvc.perform(get("/takas/takaslarim").with(user(as(veli))))
                .andExpect(content().string(containsString("Gizli Cadde 5")))
                .andExpect(content().string(containsString("Kargoya verdim")));
        mvc.perform(get("/takas/takaslarim").with(user(as(ali))))
                .andExpect(content().string(containsString("Saklı Sokak 9")));
    }

    @Test
    void ciftKargoSonrasiTamamlandiGorunur() throws Exception {
        User ali = mk("ali", "İzmir");
        User veli = mk("veli", "Ankara");
        SwapOffer o = swapService.offer(open(ali, "A").getId(), open(veli, "V").getId(), veli, null);
        swapService.accept(o.getId(), ali);

        mvc.perform(post("/takas/teklif/" + o.getId() + "/kargola").with(user(as(ali))).with(csrf()));
        mvc.perform(get("/takas/takaslarim").with(user(as(ali))))
                .andExpect(content().string(containsString("Kargoya verdin")));

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
}
