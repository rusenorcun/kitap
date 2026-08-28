package app.kitapla.web;

import app.kitapla.domain.*;
import app.kitapla.repo.DonationRepository;
import app.kitapla.repo.UserRepository;
import app.kitapla.security.AppUserDetails;
import app.kitapla.service.BookService;
import app.kitapla.service.RequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/**
 * Kargo akışı yeniden açıldığında sayfaların eski davranışa döndüğünü doğrular:
 * adres yalnızca eşleşilen karşı tarafa gösterilir ve kargo düğmeleri çıkar.
 * Adres gizliliği kuralı bu modda hâlâ geçerlidir.
 */
@SpringBootTest(properties = {
        "kitapla.features.address=true",
        "kitapla.features.shipping=true",
        "kitapla.features.purchase=true",
        "kitapla.features.handover=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KargoModuSayfaTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired DonationRepository donations;
    @Autowired BookService bookService;
    @Autowired RequestService requestService;
    @Autowired PasswordEncoder encoder;

    private User mk(String tag, String address) {
        User u = new User();
        u.setName("Kargo " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("sifre123"));
        u.setAddress(address);
        return users.save(u);
    }

    private AppUserDetails as(User u) { return new AppUserDetails(u); }

    @Test
    void bagisciAlicininAdresiniGorurVeKargolayabilir() throws Exception {
        User donor = mk("bagisci", "İzmir");
        User alici = mk("alici", "Ankara Çankaya 42");
        alici.setStudentStatus(StudentStatus.APPROVED);
        alici.setSchoolLevel(SchoolLevel.LISE);
        alici = users.save(alici);

        mvc.perform(post("/bagis/yeni").with(user(as(donor))).with(csrf())
                .param("title", "Kargo Kitabı " + UUID.randomUUID()).param("author", "Y")
                .param("quantity", "1").param("targetLevel", "HEPSI").param("source", "OWN"));
        Donation d = donations.findByDonorWithDetails(donor).get(0);

        mvc.perform(post("/kitap/" + d.getId() + "/al").with(user(as(alici))).with(csrf()));

        mvc.perform(get("/bagislarim").with(user(as(donor))))
                .andExpect(content().string(containsString("Ankara Çankaya 42")))
                .andExpect(content().string(containsString("Kargoya verdim")));
    }

    @Test
    void karsilayanAdresiGorurUcuncuKisiGormez() throws Exception {
        User isteyen = mk("isteyen", "Ankara Çankaya 99");
        User karsilayan = mk("karsilayan", "İzmir");
        BookRequest r = requestService.create(isteyen,
                bookService.findOrCreate("Adres Testi " + UUID.randomUUID(), "Y", null, null, null, null), null);

        mvc.perform(post("/istek/" + r.getId() + "/karsila").with(user(as(karsilayan))).with(csrf())
                .param("source", "PURCHASE"));

        mvc.perform(get("/karsiladiklarim").with(user(as(karsilayan))))
                .andExpect(content().string(containsString("Ankara Çankaya 99")))
                .andExpect(content().string(containsString("Kargoya verdim")));

        // Üçüncü bir kişi adresi görmez
        User yabanci = mk("yabanci", "Bursa");
        mvc.perform(get("/karsiladiklarim").with(user(as(yabanci))))
                .andExpect(content().string(not(containsString("Ankara Çankaya 99"))));
    }

    @Test
    void satinAlmaSecenegiGeriGelir() throws Exception {
        User u = mk("formcu", "İzmir");
        mvc.perform(get("/bagis/yeni").with(user(as(u))))
                .andExpect(content().string(containsString("Satın alıp göndereceğim")));
    }
}
