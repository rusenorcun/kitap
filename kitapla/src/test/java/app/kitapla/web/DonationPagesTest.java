package app.kitapla.web;

import app.kitapla.domain.*;
import app.kitapla.repo.DonationRepository;
import app.kitapla.repo.UserRepository;
import app.kitapla.security.AppUserDetails;
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

/** Bağış oluşturma sayfası, bağışlarım ve teslimat aksiyonları. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DonationPagesTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired DonationRepository donations;
    @Autowired PasswordEncoder encoder;

    private User makeUser(String tag, String address) {
        User u = new User();
        u.setName("Web " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("sifre123"));
        u.setAddress(address);
        return users.save(u);
    }

    private AppUserDetails as(User u) {
        return new AppUserDetails(u);
    }

    @Test
    void bagisFormuAcilir() throws Exception {
        User donor = makeUser("form", "İzmir");
        mvc.perform(get("/bagis/yeni").with(user(as(donor))))
                .andExpect(status().isOk())
                .andExpect(view().name("bagis-yeni"))
                .andExpect(content().string(containsString("Bağış oluştur")));
    }

    @Test
    void bagisFormuGirisIster() throws Exception {
        mvc.perform(get("/bagis/yeni"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void bagisOlusturulurVeBagislarimdaGorunur() throws Exception {
        User donor = makeUser("olustur", "İzmir Bornova");

        mvc.perform(post("/bagis/yeni").with(user(as(donor))).with(csrf())
                        .param("title", "Web Test Kitabı")
                        .param("author", "Web Yazar")
                        .param("quantity", "2")
                        .param("targetLevel", "HEPSI")
                        .param("source", "OWN")
                        .param("description", "Temiz"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/bagislarim"));

        assertThat(donations.findByDonorWithDetails(donor)).hasSize(1);

        mvc.perform(get("/bagislarim").with(user(as(donor))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Web Test Kitabı")))
                .andExpect(content().string(containsString("alındı")));
    }

    @Test
    void baslikVeLinkYoksaHataGosterilir() throws Exception {
        User donor = makeUser("hatali", "İzmir");
        mvc.perform(post("/bagis/yeni").with(user(as(donor))).with(csrf())
                        .param("title", "")
                        .param("quantity", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("bagis-yeni"))
                .andExpect(model().attributeExists("hata"));
    }

    @Test
    void onizlemeParcasiYalnizcaFragmentDondurur() throws Exception {
        User donor = makeUser("onizleme", "İzmir");
        mvc.perform(post("/bagis/onizleme").with(user(as(donor))).with(csrf())
                        .param("purchaseLink", "ftp://gecersiz"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<html"))))
                .andExpect(content().string(containsString("elle yazabilirsin")));
    }

    @Test
    void bagisciAlicininAdresiniGorurVeKargolayabilir() throws Exception {
        User donor = makeUser("kargo-bagisci", "İzmir");
        User alici = makeUser("kargo-alici", "Ankara Çankaya 42");

        mvc.perform(post("/bagis/yeni").with(user(as(donor))).with(csrf())
                .param("title", "Kargo Kitabı " + UUID.randomUUID()).param("author", "Y")
                .param("quantity", "1").param("targetLevel", "HEPSI").param("source", "OWN"));

        Donation d = donations.findByDonorWithDetails(donor).get(0);
        // öncelik penceresini atlamak için öğrenci yapalım
        alici.setStudentStatus(StudentStatus.APPROVED);
        alici.setSchoolLevel(SchoolLevel.LISE);
        alici = users.save(alici);

        mvc.perform(post("/kitap/" + d.getId() + "/al").with(user(as(alici))).with(csrf()))
                .andExpect(redirectedUrl("/aldiklarim"));

        // Bağışçı adresi görür
        mvc.perform(get("/bagislarim").with(user(as(donor))))
                .andExpect(content().string(containsString("Ankara Çankaya 42")))
                .andExpect(content().string(containsString("Kargoya verdim")));
    }

    @Test
    void teslimatAksiyonlariSirayiTakipEder() throws Exception {
        User donor = makeUser("akis-bagisci", "İzmir");
        User alici = makeUser("akis-alici", "Ankara");
        alici.setStudentStatus(StudentStatus.APPROVED);
        alici.setSchoolLevel(SchoolLevel.LISE);
        alici = users.save(alici);

        mvc.perform(post("/bagis/yeni").with(user(as(donor))).with(csrf())
                .param("title", "Akış Kitabı " + UUID.randomUUID()).param("author", "Y")
                .param("quantity", "1").param("targetLevel", "HEPSI").param("source", "OWN"));
        Donation d = donations.findByDonorWithDetails(donor).get(0);

        mvc.perform(post("/kitap/" + d.getId() + "/al").with(user(as(alici))).with(csrf()));

        // aldıklarım sayfası "Teslim aldım" gösterir
        mvc.perform(get("/aldiklarim").with(user(as(alici))))
                .andExpect(content().string(containsString("Teslim aldım")))
                .andExpect(content().string(containsString("İptal et")));
    }
}
