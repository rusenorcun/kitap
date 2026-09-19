package app.kitapla.web;

import app.kitapla.domain.SwapBook;
import app.kitapla.domain.User;
import app.kitapla.repo.UserRepository;
import app.kitapla.security.AppUserDetails;
import app.kitapla.service.BookService;
import app.kitapla.service.SwapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pano: bir adım bekleyen süreçler sayılıp ilgili sayfaya bağlanır. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PanoSayfasiTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired SwapService swapService;
    @Autowired BookService bookService;

    private User mk(String tag) {
        User u = new User();
        u.setName("Pano " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash("x");
        return users.save(u);
    }

    private SwapBook kitap(User u) {
        return swapService.open(u, bookService.findOrCreate("Pano Kitabı " + UUID.randomUUID(), "Y", null, null, null, null), null);
    }

    @Test
    void gelenTakasTeklifiBekleyenIslerdeSayilir() throws Exception {
        User alan = mk("alan");
        User veren = mk("veren");
        swapService.offer(kitap(alan).getId(), kitap(veren).getId(), veren, "Takas?");

        mvc.perform(get("/panom").with(user(new AppUserDetails(alan))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Yanıt bekleyen takas teklifi")))
                .andExpect(content().string(containsString("href=\"/takas/takaslarim\"")))
                .andExpect(content().string(not(containsString("Şu an bekleyen bir işin yok"))));
        // Teklifi gönderen için yanıt bekleyen bir iş yok
        mvc.perform(get("/panom").with(user(new AppUserDetails(veren))))
                .andExpect(content().string(not(containsString("Yanıt bekleyen takas teklifi"))));
    }

    @Test
    void isiOlmayanUyeBosDurumGorur() throws Exception {
        mvc.perform(get("/panom").with(user(new AppUserDetails(mk("bos")))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Şu an bekleyen bir işin yok")))
                .andExpect(content().string(containsString("Merhaba, <span>Pano</span>")));
    }
}
