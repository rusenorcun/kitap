package app.kitapla.api;

import app.kitapla.api.dto.CreateOfferBody;
import app.kitapla.api.dto.CreateRequestBody;
import app.kitapla.api.dto.CreateSwapBookBody;
import app.kitapla.api.dto.FulfillBody;
import app.kitapla.domain.Book;
import app.kitapla.domain.SchoolLevel;
import app.kitapla.domain.StudentStatus;
import app.kitapla.domain.User;
import app.kitapla.repo.BookRepository;
import app.kitapla.repo.UserRepository;
import app.kitapla.security.AppUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestAndSwapApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired PasswordEncoder encoder;

    private User userA;
    private User userB;

    @BeforeEach
    void setup() {
        userA = new User();
        userA.setName("Kullanıcı A");
        userA.setEmail("usera-" + UUID.randomUUID() + "@ogr.atauni.edu.tr");
        userA.setStudentEmail(userA.getEmail());
        userA.setStudentStatus(StudentStatus.APPROVED);
        userA.setSchoolLevel(SchoolLevel.UNIVERSITE);
        userA.setPasswordHash(encoder.encode("password"));
        userA.setAddress("Kampüs");
        userA = users.save(userA);

        userB = new User();
        userB.setName("Kullanıcı B");
        userB.setEmail("userb-" + UUID.randomUUID() + "@ogr.atauni.edu.tr");
        userB.setStudentEmail(userB.getEmail());
        userB.setStudentStatus(StudentStatus.APPROVED);
        userB.setSchoolLevel(SchoolLevel.UNIVERSITE);
        userB.setPasswordHash(encoder.encode("password"));
        userB.setAddress("Kampüs");
        userB = users.save(userB);
    }

    private AppUserDetails as(User u) {
        return new AppUserDetails(u);
    }

    @Test
    void request_flow_create_and_fulfill() throws Exception {
        CreateRequestBody reqBody = new CreateRequestBody("İhtiyaç Kitabı", "Yazar X", null, "Ders için gerekli");

        MvcResult createResult = mvc.perform(post("/api/v1/requests")
                        .with(user(as(userA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(reqBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();

        long reqId = mapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(get("/api/v1/requests/open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));

        // User B fulfills request
        mvc.perform(post("/api/v1/requests/" + reqId + "/fulfill")
                        .with(user(as(userB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new FulfillBody("OWN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FULFILLED"));

        // Arrange meeting for request
        String reqMeetingTime = java.time.Instant.now().plus(2, java.time.temporal.ChronoUnit.DAYS).toString();
        app.kitapla.api.dto.ArrangeMeetingBody reqMeeting = new app.kitapla.api.dto.ArrangeMeetingBody(null, "Saat 15:00", reqMeetingTime);
        mvc.perform(post("/api/v1/requests/" + reqId + "/meeting")
                        .with(user(as(userB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(reqMeeting)))
                .andExpect(status().isNoContent());

        // User A marks delivered
        mvc.perform(post("/api/v1/requests/" + reqId + "/deliver")
                        .with(user(as(userA))))
                .andExpect(status().isNoContent());
    }

    @Test
    void swap_flow_create_offer_and_accept() throws Exception {
        // User A lists a swap book
        CreateSwapBookBody bookA = new CreateSwapBookBody("Takas Kitap A", "Yazar A", "Temiz", null, null);
        MvcResult resA = mvc.perform(post("/api/v1/swap/books")
                        .with(user(as(userA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(bookA)))
                .andExpect(status().isCreated())
                .andReturn();
        long swapBookAId = mapper.readTree(resA.getResponse().getContentAsString()).get("id").asLong();

        // User B lists a swap book
        CreateSwapBookBody bookB = new CreateSwapBookBody("Takas Kitap B", "Yazar B", "Yeni gibi", null, null);
        MvcResult resB = mvc.perform(post("/api/v1/swap/books")
                        .with(user(as(userB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(bookB)))
                .andExpect(status().isCreated())
                .andReturn();
        long swapBookBId = mapper.readTree(resB.getResponse().getContentAsString()).get("id").asLong();

        // User A discovers User B's book
        mvc.perform(get("/api/v1/swap/discover").with(user(as(userA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));

        // User A makes offer to User B's book using book A
        CreateOfferBody offerBody = new CreateOfferBody(swapBookBId, swapBookAId, "Takas edelim mi?");
        MvcResult offerRes = mvc.perform(post("/api/v1/swaps")
                        .with(user(as(userA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(offerBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        long offerId = mapper.readTree(offerRes.getResponse().getContentAsString()).get("id").asLong();

        // User B sees incoming offers and accepts
        mvc.perform(get("/api/v1/swaps/incoming").with(user(as(userB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));

        mvc.perform(post("/api/v1/swaps/" + offerId + "/accept")
                        .with(user(as(userB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // Arrange meeting for swap
        String swapMeetingTime = java.time.Instant.now().plus(2, java.time.temporal.ChronoUnit.DAYS).toString();
        app.kitapla.api.dto.ArrangeMeetingBody swapMeeting = new app.kitapla.api.dto.ArrangeMeetingBody(null, "Kütüphane önü", swapMeetingTime);
        mvc.perform(post("/api/v1/swaps/" + offerId + "/meeting")
                        .with(user(as(userA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(swapMeeting)))
                .andExpect(status().isNoContent());

        // Both handover
        mvc.perform(post("/api/v1/swaps/" + offerId + "/handover").with(user(as(userA))))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/swaps/" + offerId + "/handover").with(user(as(userB))))
                .andExpect(status().isNoContent());
    }

    @Test
    void swap_donation_transfer_flow() throws Exception {
        // 1. User A lists a swap book with purchaseLink
        CreateSwapBookBody swapBody = new CreateSwapBookBody("Geçiş Kitabı", "Yazar G", "Not", "https://example.com/kitap", null);
        MvcResult swapRes = mvc.perform(post("/api/v1/swap/books")
                        .with(user(as(userA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(swapBody)))
                .andExpect(status().isCreated())
                .andReturn();
        long swapBookId = mapper.readTree(swapRes.getResponse().getContentAsString()).get("id").asLong();

        // 2. Takastaki kitabı bağışa aktar
        MvcResult toDonRes = mvc.perform(post("/api/v1/swap/books/" + swapBookId + "/to-donation")
                        .with(user(as(userA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();
        long donationId = mapper.readTree(toDonRes.getResponse().getContentAsString()).get("id").asLong();

        // 3. Bağıştaki kitabı takasa geri aktar
        CreateSwapBookBody transferBody = new CreateSwapBookBody("Geçiş Kitabı", "Yazar G", "Takasa geri döndü", null, null);
        mvc.perform(post("/api/v1/donations/" + donationId + "/to-swap")
                        .with(user(as(userA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(transferBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }
}
