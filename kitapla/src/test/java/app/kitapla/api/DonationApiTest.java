package app.kitapla.api;

import app.kitapla.api.dto.ArrangeMeetingBody;
import app.kitapla.api.dto.CreateDonationBody;
import app.kitapla.domain.*;
import app.kitapla.repo.BookRepository;
import app.kitapla.repo.DonationRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DonationApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired DonationRepository donations;
    @Autowired PasswordEncoder encoder;

    private User donor;
    private User student;
    private Donation donation;

    @BeforeEach
    void setup() {
        donor = new User();
        donor.setName("API Bağışçı");
        donor.setEmail("donor-" + UUID.randomUUID() + "@test.local");
        donor.setPasswordHash(encoder.encode("password"));
        donor.setAddress("Kampüs içi");
        donor = users.save(donor);

        student = new User();
        student.setName("API Öğrenci");
        student.setEmail("student-" + UUID.randomUUID() + "@ogr.atauni.edu.tr");
        student.setStudentEmail(student.getEmail());
        student.setStudentStatus(StudentStatus.APPROVED);
        student.setSchoolLevel(SchoolLevel.UNIVERSITE);
        student.setPasswordHash(encoder.encode("password"));
        student.setAddress("Yurt");
        student = users.save(student);

        Book b = new Book();
        b.setTitle("API Test Kitabı " + UUID.randomUUID());
        b.setAuthor("Test Yazar");
        b = books.save(b);

        donation = new Donation();
        donation.setDonor(donor);
        donation.setBook(b);
        donation.setQuantity(2);
        donation.setTargetLevel(TargetLevel.HEPSI);
        donation = donations.save(donation);
    }

    private AppUserDetails as(User u) {
        return new AppUserDetails(u);
    }

    @Test
    void open_donations_public_and_detail() throws Exception {
        mvc.perform(get("/api/v1/donations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[0].book.title").exists());

        mvc.perform(get("/api/v1/donations/" + donation.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(donation.getId()))
                .andExpect(jsonPath("$.book.title").value(donation.getBook().getTitle()))
                .andExpect(jsonPath("$.donorName").value("API Bağışçı"));
    }

    @Test
    void create_donation_and_claim_flow() throws Exception {
        CreateDonationBody createBody = new CreateDonationBody(
                "Yeni Bağışlanan Kitap",
                "Yazar Adı",
                null,
                1,
                "UNIVERSITE",
                "OWN",
                "İyi durumda",
                null,
                null,
                "Kütüphane önü"
        );

        MvcResult createResult = mvc.perform(post("/api/v1/donations")
                        .with(user(as(donor)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(createBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();

        long donationId = mapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        // Student claims donation
        MvcResult claimResult = mvc.perform(post("/api/v1/donations/" + donationId + "/claim")
                        .with(user(as(student))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andReturn();

        long claimId = mapper.readTree(claimResult.getResponse().getContentAsString()).get("id").asLong();

        // Arrange meeting
        String meetingTime = Instant.now().plus(2, ChronoUnit.DAYS).toString();
        ArrangeMeetingBody meetingBody = new ArrangeMeetingBody(null, "Saat 14:00'te", meetingTime);

        mvc.perform(post("/api/v1/claims/" + claimId + "/meeting")
                        .with(user(as(donor)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(meetingBody)))
                .andExpect(status().isNoContent());

        // Deliver claim
        mvc.perform(post("/api/v1/claims/" + claimId + "/deliver")
                        .with(user(as(student))))
                .andExpect(status().isNoContent());

        // My donations and My claims
        mvc.perform(get("/api/v1/my/donations").with(user(as(donor))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));

        mvc.perform(get("/api/v1/my/claims").with(user(as(student))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[0].status").value("DELIVERED"));
    }
}
