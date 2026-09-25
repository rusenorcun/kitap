package app.kitappla.web;

import app.kitappla.domain.*;
import app.kitappla.repo.DonationRepository;
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
        "kitappla.features.address=true",
        "kitappla.features.shipping=true",
        "kitappla.features.purchase=true",
        "kitappla.features.handover=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KargoModuSayfaTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired DonationRepository donations;
    @Autowired app.kitappla.repo.ClaimRepository claims;
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

        // Kargolanınca alıcı "Teslim aldım" düğmesini görür (yalnızca ARRANGED'da çıkıyordu)
        Long claimId = claims.findByStudentWithDetails(alici).get(0).getId();
        mvc.perform(post("/teslimat/" + claimId + "/kargola").with(user(as(donor))).with(csrf()));
        mvc.perform(get("/aldiklarim").with(user(as(alici))))
                .andExpect(content().string(containsString("Kargoda")))
                .andExpect(content().string(containsString("/teslimat/" + claimId + "/teslim-aldim")));
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

        // İsteyen, kargo beklerken de teslim aldığını bildirebilir (servis izin veriyor)
        mvc.perform(get("/isteklerim").with(user(as(isteyen))))
                .andExpect(content().string(containsString("/istek/" + r.getId() + "/teslim-aldim")));

        // Üçüncü bir kişi adresi görmez
        User yabanci = mk("yabanci", "Bursa");
        mvc.perform(get("/karsiladiklarim").with(user(as(yabanci))))
                .andExpect(content().string(not(containsString("Ankara Çankaya 99"))));
    }

    @Test
    void profilFormuAdresiGosterirVeKaydeder() throws Exception {
        User u = mk("profil-adres", "Eski Sokak 1");
        mvc.perform(get("/profil").with(user(as(u))))
                .andExpect(content().string(containsString("name=\"address\"")))
                .andExpect(content().string(containsString("Eski Sokak 1")));

        mvc.perform(post("/profil").with(user(as(u))).with(csrf())
                .param("name", "Kargo Profil").param("address", "Yeni Sokak 2"));
        org.assertj.core.api.Assertions.assertThat(users.findById(u.getId()).orElseThrow().getAddress())
                .isEqualTo("Yeni Sokak 2");
    }

    @Test
    void kayitFormundaAdresAlaniVar() throws Exception {
        mvc.perform(get("/register"))
                .andExpect(content().string(containsString("name=\"address\"")));
    }

    @Test
    void satinAlmaSecenegiGeriGelir() throws Exception {
        User u = mk("formcu", "İzmir");
        mvc.perform(get("/bagis/yeni").with(user(as(u))))
                .andExpect(content().string(containsString("Satın alıp göndereceğim")));
    }
}
