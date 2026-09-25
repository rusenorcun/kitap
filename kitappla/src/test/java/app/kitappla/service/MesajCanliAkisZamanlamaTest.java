package app.kitappla.service;

import app.kitappla.domain.*;
import app.kitappla.repo.BookRepository;
import app.kitappla.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Sohbet canlı akışı mesaj commit edildikten sonra tetiklenmeli. Olay işlem sürerken
 * giderse tarayıcı listeyi hemen çeker, mesajı henüz göremez ve ekranda eksik kalır.
 */
@SpringBootTest
@ActiveProfiles("test")
class MesajCanliAkisZamanlamaTest {

    @SpyBean SseHub sse;
    @Autowired MessageService messages;
    @Autowired DonationService donationService;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired TransactionTemplate tx;

    private User mk(String tag) {
        User u = new User();
        u.setName("Akış " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash("x");
        u.setAddress("İzmir");
        return users.save(u);
    }

    @Test
    void canliAkisOlayiCommitSonrasiGider() {
        User donor = mk("bagisci");
        User alici = mk("alici");
        // Yeni bağışın ilk 48 saati öğrencilere öncelikli
        alici.setStudentStatus(StudentStatus.APPROVED);
        alici.setSchoolLevel(SchoolLevel.LISE);
        alici = users.save(alici);
        Book b = new Book();
        b.setTitle("Akış Kitabı " + UUID.randomUUID());
        Donation d = donationService.create(donor, books.save(b), 1, TargetLevel.HEPSI, DonationSource.OWN, null);
        Claim c = donationService.claim(d.getId(), alici);
        User alan = alici;
        Conversation sohbet = messages.open(ConversationKind.CLAIM, c.getId(), alan);
        clearInvocations(sse);

        tx.executeWithoutResult(durum -> {
            messages.send(sohbet.getId(), alan, "Merhaba");
            verify(sse, never()).publish(anyLong());
        });

        verify(sse, times(1)).publish(sohbet.getId());
    }
}
