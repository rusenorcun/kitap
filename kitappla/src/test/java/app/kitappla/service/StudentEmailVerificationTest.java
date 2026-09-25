package app.kitappla.service;

import app.kitappla.domain.School;
import app.kitappla.domain.SchoolLevel;
import app.kitappla.domain.StudentStatus;
import app.kitappla.domain.TokenType;
import app.kitappla.domain.User;
import app.kitappla.mail.MailService;
import app.kitappla.repo.AuthTokenRepository;
import app.kitappla.repo.UserRepository;
import app.kitappla.security.AppUserDetails;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "kitappla.mail.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:student-verification;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudentEmailVerificationTest {

    @Autowired UserService service;
    @Autowired UserRepository users;
    @Autowired AuthTokenRepository proofs;
    @Autowired TokenService tokens;
    @Autowired MockMvc mvc;
    @MockBean JavaMailSender sender;
    @SpyBean MailService mail;
    @Autowired app.kitappla.repo.NotificationRepository notifications;

    @BeforeEach
    void prepareMail() {
        mail.clearOutbox();
        when(sender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
    }

    private String address() {
        return UUID.randomUUID() + "@school.edu.tr";
    }

    /** Hesap e-postasını doğrulamış üye; kayıt postası kutudan temizlenir. */
    private User member() {
        User u = service.register("Üye", UUID.randomUUID() + "@test.local", "sifre123", null, null,
                School.ATATURK_UNIVERSITESI, false, null, null, null);
        u.setEmailVerified(true);
        mail.clearOutbox();
        return users.save(u);
    }

    private String lastToken(String address) {
        var message = mail.outbox().getLast();
        assertThat(message.to()).isEqualTo(address);
        var matcher = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(message.html());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private void rejected(User user, String token) {
        assertThatThrownBy(() -> service.confirmStudentEmail(user, token)).isInstanceOf(IllegalStateException.class);
        assertThat(users.findById(user.getId()).orElseThrow().isStudent()).isFalse();
    }

    @Test
    void registrationWithSchoolAddressIsApprovedByAccountLinkOnly() {
        String address = address();
        User registered = service.register("Öğrenci", address, "sifre123", null, null,
                School.ATATURK_UNIVERSITESI, false, null, null, null);
        assertThat(registered.getStudentStatus()).isEqualTo(StudentStatus.PENDING);
        assertThat(registered.isStudent()).isFalse();
        assertThat(registered.isEmailVerified()).isFalse();
        assertThat(registered.isStudentVerificationSent()).isTrue();
        // Aynı adrese tek posta gider: hesap doğrulama bağlantısı
        assertThat(mail.outbox()).hasSize(1);
        String token = lastToken(address);
        assertThat(tokens.verify(token, TokenType.EMAIL_VERIFY)).isEmpty();
        var proof = tokens.verify(token, TokenType.ACCOUNT_VERIFY).orElseThrow();
        assertThat(proof.getTokenHash()).isNotEqualTo(token);
        assertThat(proof.getVerificationEmail()).isEqualTo(address);
        assertThat(proof.getExpiresAt()).isAfter(Instant.now()).isBefore(Instant.now().plusSeconds(49 * 3600));
        rejected(registered, token);   // öğrenci onay ucu bu jetonu kabul etmez

        assertThat(service.confirmAccountEmail(token)).isEqualTo(UserService.HesapDogrulama.DOGRULANDI);
        User approved = users.findById(registered.getId()).orElseThrow();
        assertThat(approved.isEmailVerified()).isTrue();
        assertThat(approved.isStudent()).isTrue();
        assertThat(service.confirmAccountEmail(token)).isEqualTo(UserService.HesapDogrulama.ZATEN_DOGRULANMIS);
        verify(sender).send(any(MimeMessage.class));
    }

    @Test
    void profileSendsOnlyToSchoolMailboxAndRejectsWrongUser() {
        User member = member();
        String school = address();
        User pending = service.verifyStudentEmail(member, school);
        assertThat(pending.getStudentStatus()).isEqualTo(StudentStatus.PENDING);
        assertThat(pending.getEmail()).isEqualTo(member.getEmail());
        String token = lastToken(school);
        rejected(member(), token);
        assertThat(service.confirmStudentEmail(member, token).isStudent()).isTrue();
    }

    @Test
    void expiredAndWrongTypeTokensCannotApprove() {
        User member = member();
        String school = address();
        User pending = service.verifyStudentEmail(member, school);
        String token = lastToken(school);
        var proof = tokens.verify(token, TokenType.EMAIL_VERIFY).orElseThrow();
        proof.setExpiresAt(Instant.now().minusSeconds(1));
        proofs.save(proof);
        rejected(member, token);
        rejected(member, tokens.issue(pending, TokenType.PASSWORD_RESET));
    }

    @Test
    void changedAddressAndResendInvalidateOldProof() {
        User member = member();
        String oldAddress = address();
        String newAddress = address();
        service.verifyStudentEmail(member, oldAddress);
        String oldToken = lastToken(oldAddress);
        service.verifyStudentEmail(member, newAddress);
        String replacedToken = lastToken(newAddress);
        rejected(member, oldToken);
        service.verifyStudentEmail(member, newAddress);
        String currentToken = lastToken(newAddress);
        rejected(member, replacedToken);
        assertThat(service.confirmStudentEmail(member, currentToken).isStudent()).isTrue();
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void exhaustedQuotaPreservesCurrentAddressAndUsableProof() {
        User member = member();
        String school = address();
        User pending = member;
        for (int i = 0; i < 5; i++) pending = service.verifyStudentEmail(pending, school);
        assertThat(tokens.limitDoldu(pending, TokenType.EMAIL_VERIFY)).isTrue();
        assertThat(pending.isStudentVerificationSent()).isTrue();
        String token = lastToken(school);
        var proof = tokens.verify(token, TokenType.EMAIL_VERIFY).orElseThrow();
        Instant expiry = proof.getExpiresAt();
        for (String requested : new String[]{school, address()}) {
            User result = service.verifyStudentEmail(pending, requested);
            assertThat(result.isStudentVerificationSent()).isFalse();
            assertThat(result.getStudentEmail()).isEqualTo(school);
            assertThat(result.getStudentStatus()).isEqualTo(StudentStatus.PENDING);
            assertThat(tokens.verify(token, TokenType.EMAIL_VERIFY)).isPresent();
            assertThat(proof.getUsedAt()).isNull();
            assertThat(proof.getExpiresAt()).isEqualTo(expiry);
        }
        assertThat(mail.outbox()).hasSize(5);
        assertThat(service.confirmStudentEmail(member, token).isStudent()).isTrue();
    }

    @Test
    void disabledMailResendPreservesAddressAndProof() {
        User member = member();
        String school = address();
        User pending = service.verifyStudentEmail(member, school);
        String token = lastToken(school);
        doReturn(false).when(mail).isEnabled();
        User result = service.verifyStudentEmail(pending, address());
        assertThat(result.isStudentVerificationSent()).isFalse();
        assertThat(result.getStudentEmail()).isEqualTo(school);
        assertThat(tokens.verify(token, TokenType.EMAIL_VERIFY)).isPresent();
        assertThat(users.findById(member.getId()).orElseThrow().getStudentEmail()).isEqualTo(school);
        assertThat(mail.outbox()).hasSize(1);
        doReturn(true).when(mail).isEnabled();
        assertThat(service.confirmStudentEmail(member, token).isStudent()).isTrue();
    }

    @Test
    void studentApiRetainsFlatResponseAndReportsDeliveryHeader() throws Exception {
        User member = member();
        String school = address();
        mvc.perform(post("/api/v1/me/student").with(user(new AppUserDetails(member))).with(csrf())
                        .contentType("application/json").content("{\"email\":\"" + school + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Student-Verification-Sent", "true"))
                .andExpect(jsonPath("$.id").value(member.getId()))
                .andExpect(jsonPath("$.email").value(member.getEmail()))
                .andExpect(jsonPath("$.studentStatus").value("PENDING"))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.verificationSent").doesNotExist());
        for (int i = 0; i < 4; i++) service.verifyStudentEmail(member, school);
        String token = lastToken(school);
        mvc.perform(post("/api/v1/me/student").with(user(new AppUserDetails(member))).with(csrf())
                        .contentType("application/json").content("{\"email\":\"" + address() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Student-Verification-Sent", "false"))
                .andExpect(jsonPath("$.id").value(member.getId()))
                .andExpect(jsonPath("$.studentStatus").value("PENDING"))
                .andExpect(jsonPath("$.user").doesNotExist());
        assertThat(users.findById(member.getId()).orElseThrow().getStudentEmail()).isEqualTo(school);
        assertThat(service.confirmStudentEmail(member, token).isStudent()).isTrue();
    }

    @Test
    void proofMustMatchCurrentAddressEvenWithoutResend() {
        User member = member();
        String school = address();
        User pending = service.verifyStudentEmail(member, school);
        String token = lastToken(school);
        pending.setStudentEmail(address());
        users.save(pending);
        rejected(member, token);
    }

    @Test
    void mailDisabledCreatesNoProofAndDoesNotBlockRegistration() {
        doReturn(false).when(mail).isEnabled();
        String school = address();
        User registered = service.register("Öğrenci", school, "sifre123", null, null,
                null, false, null, null, null);
        assertThat(registered.getStudentStatus()).isEqualTo(StudentStatus.PENDING);
        assertThat(registered.isStudentVerificationSent()).isFalse();
        assertThat(proofs.findByUserAndTypeAndUsedAtIsNull(registered, TokenType.EMAIL_VERIFY)).isEmpty();
        String requested = address();
        User pending = service.verifyStudentEmail(member(), requested);
        assertThat(pending.isStudent()).isFalse();
        assertThat(pending.getStudentStatus()).isEqualTo(StudentStatus.PENDING);
        assertThat(pending.getStudentEmail()).isEqualTo(requested);
        assertThat(pending.isStudentVerificationSent()).isFalse();
        assertThat(member().getStudentStatus()).isEqualTo(StudentStatus.NONE);
        assertThat(mail.outbox()).isEmpty();
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void disablingMailPreventsConfirmationOfOutstandingProof() {
        User member = member();
        String school = address();
        service.verifyStudentEmail(member, school);
        String token = lastToken(school);
        doReturn(false).when(mail).isEnabled();
        rejected(member, token);
    }

    @Test
    void failedDeliveryCannotGrantPrivilegesOrBreakRegistration() {
        doThrow(new MailSendException("SMTP unavailable")).when(sender).send(any(MimeMessage.class));
        String school = address();
        User registered = service.register("Öğrenci", school, "sifre123", null, null,
                null, false, null, null, null);
        // Teslim arka planda yapılır: istek anında ileti sıraya alınmıştır ("gönderiliyor")...
        assertThat(registered.isStudentVerificationSent()).isTrue();
        assertThat(registered.getStudentStatus()).isEqualTo(StudentStatus.PENDING);
        // ...teslim başarısız olunca bağlantı geçersiz kılınır ve üye site içinden bilgilendirilir
        String token = lastToken(school);
        rejected(registered, token);
        assertThat(service.confirmAccountEmail(token)).isEqualTo(UserService.HesapDogrulama.GECERSIZ);
        assertThat(users.findById(registered.getId()).orElseThrow().isEmailVerified()).isFalse();
        assertThat(notifications.findTop50ByUserOrderByCreatedAtDesc(registered))
                .anyMatch(n -> n.getMessage().contains("gönderilemedi"));
    }

    @Test
    void legacyApprovalsRemainAndDocumentApplicationStillWorks() {
        User legacy = member();
        legacy.setStudentStatus(StudentStatus.APPROVED);
        legacy.setStudentEmail(address());
        users.save(legacy);
        assertThat(service.updateProfile(legacy, "Yeni ad", null, null, null).isStudent()).isTrue();
        assertThatThrownBy(() -> service.verifyStudentEmail(legacy, address())).isInstanceOf(IllegalStateException.class);
        assertThat(users.findById(legacy.getId()).orElseThrow().isStudent()).isTrue();
        User pending = service.verifyStudentEmail(member(), address());
        User document = service.applyForStudent(pending, SchoolLevel.LISE, UUID.randomUUID().toString(), "document.pdf");
        assertThat(document.getDocumentPath()).isEqualTo("document.pdf");
        assertThat(document.getStudentStatus()).isEqualTo(StudentStatus.PENDING);
    }

    @Test
    void confirmationLandingDoesNotConsumeAndPostRequiresCsrf() throws Exception {
        User member = member();
        String school = address();
        service.verifyStudentEmail(member, school);
        String token = lastToken(school);
        var principal = new AppUserDetails(member);
        mvc.perform(get("/profil/ogrenci/eposta/onay").with(user(principal)).param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
        assertThat(users.findById(member.getId()).orElseThrow().isStudent()).isFalse();
        assertThat(tokens.verify(token, TokenType.EMAIL_VERIFY)).isPresent();
        mvc.perform(post("/profil/ogrenci/eposta/onay").with(user(principal)).param("token", token))
                .andExpect(status().isForbidden());
        assertThat(tokens.verify(token, TokenType.EMAIL_VERIFY)).isPresent();
        mvc.perform(post("/profil/ogrenci/eposta/onay").with(user(principal)).with(csrf()).param("token", token))
                .andExpect(redirectedUrl("/profil"))
                .andExpect(flash().attributeExists("basari"));
        assertThat(users.findById(member.getId()).orElseThrow().isStudent()).isTrue();
    }

    @Test
    void dogrulanmamisOkulAdresiGercekSahibiniEngellemez() {
        // Kötü niyetli üye başkasının okul adresini profiline yazar ama bağlantıyı onaylayamaz
        User isgalci = member();
        String kurbanAdresi = address();
        service.verifyStudentEmail(isgalci, kurbanAdresi);
        String isgalciJetonu = lastToken(kurbanAdresi);

        // Adresin sahibi o adresle kayıt olabilir ve kendi bağlantısıyla doğrular
        User sahibi = service.register("Gerçek Sahip", kurbanAdresi, "sifre123", null, null,
                School.ATATURK_UNIVERSITESI, false, null, null, null);
        assertThat(service.confirmAccountEmail(lastToken(kurbanAdresi))).isEqualTo(UserService.HesapDogrulama.DOGRULANDI);
        assertThat(users.findById(sahibi.getId()).orElseThrow().isStudent()).isTrue();

        User eski = users.findById(isgalci.getId()).orElseThrow();
        assertThat(eski.getStudentEmail()).isNull();
        assertThat(eski.getStudentStatus()).isEqualTo(StudentStatus.NONE);
        rejected(isgalci, isgalciJetonu);
    }

    @Test
    void onayliOkulAdresiBaskaHesabaGecmez() {
        User sahibi = member();
        String adres = address();
        service.verifyStudentEmail(sahibi, adres);
        service.confirmStudentEmail(sahibi, lastToken(adres));

        assertThatThrownBy(() -> service.verifyStudentEmail(member(), adres)).isInstanceOf(IllegalStateException.class);
        assertThat(users.findById(sahibi.getId()).orElseThrow().getStudentEmail()).isEqualTo(adres);
    }
}
