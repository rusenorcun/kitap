package app.kitapla.service;

import app.kitapla.domain.*;
import app.kitapla.repo.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Eşleşme üzerinden mesajlaşma ve erişim denetimi. */
@SpringBootTest
@ActiveProfiles("test")
class MessageServiceTest {

    @Autowired MessageService messages;
    @Autowired DonationService donationService;
    @Autowired RequestService requestService;
    @Autowired SwapService swapService;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired ClaimRepository claims;
    @Autowired SwapBookRepository swapBooks;
    @Autowired NotificationService notifications;
    @Autowired PasswordEncoder encoder;

    private User mk(String tag, boolean ogrenci) {
        User u = new User();
        u.setName("Mesaj " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode("sifre123"));
        u.setAddress("İzmir");
        if (ogrenci) {
            u.setStudentStatus(StudentStatus.APPROVED);
            u.setSchoolLevel(SchoolLevel.LISE);
        }
        return users.save(u);
    }

    private Book book(String t) {
        Book b = new Book();
        b.setTitle(t + " " + UUID.randomUUID());
        return books.save(b);
    }

    /** Bağış talebi kurar ve claim id döndürür. */
    private Claim talep(User donor, User alici) {
        Donation d = donationService.create(donor, book("Kitap"), 1, TargetLevel.HEPSI,
                DonationSource.OWN, null);
        return donationService.claim(d.getId(), alici);
    }

    @Test
    void esiklesenTaraflarYazisabilir() {
        User donor = mk("bagisci", false);
        User alici = mk("alici", true);
        Claim c = talep(donor, alici);

        Conversation s = messages.open(ConversationKind.CLAIM, c.getId(), alici);
        assertThat(s.has(donor)).isTrue();
        assertThat(s.has(alici)).isTrue();

        messages.send(s.getId(), alici, "Merhaba, saat 14'te kütüphanede olur mu?");
        messages.send(s.getId(), donor, "Olur, görüşürüz.");

        assertThat(messages.messagesOf(s)).hasSize(2)
                .extracting(Message::getBody)
                .containsExactly("Merhaba, saat 14'te kütüphanede olur mu?", "Olur, görüşürüz.");
    }

    @Test
    void ucuncuKisiSohbeteErisemez() {
        User donor = mk("sahip", false);
        User alici = mk("alan", true);
        User yabanci = mk("yabanci", false);
        Claim c = talep(donor, alici);
        Conversation s = messages.open(ConversationKind.CLAIM, c.getId(), alici);

        assertThatThrownBy(() -> messages.require(s.getId(), yabanci))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sana ait değil");
        assertThatThrownBy(() -> messages.send(s.getId(), yabanci, "araya girdim"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> messages.open(ConversationKind.CLAIM, c.getId(), yabanci))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ayniAlisverisIcinTekSohbetAcilir() {
        User donor = mk("tek", false);
        User alici = mk("tek2", true);
        Claim c = talep(donor, alici);

        Conversation a = messages.open(ConversationKind.CLAIM, c.getId(), alici);
        Conversation b = messages.open(ConversationKind.CLAIM, c.getId(), donor);
        assertThat(a.getId()).isEqualTo(b.getId());
    }

    @Test
    void okunmamisSayisiDogruHesaplanir() {
        User donor = mk("okunmamis1", false);
        User alici = mk("okunmamis2", true);
        Claim c = talep(donor, alici);
        Conversation s = messages.open(ConversationKind.CLAIM, c.getId(), alici);

        messages.send(s.getId(), alici, "ilk");
        messages.send(s.getId(), alici, "ikinci");

        // Gönderen için okunmamış yok, karşı taraf için iki tane
        assertThat(messages.unread(s, alici)).isZero();
        assertThat(messages.unread(s, donor)).isEqualTo(2);
        assertThat(messages.unreadConversations(donor)).isEqualTo(1);

        messages.markRead(s, donor);
        assertThat(messages.unread(s, donor)).isZero();
        assertThat(messages.unreadConversations(donor)).isZero();
    }

    @Test
    void bosMesajReddedilir() {
        User donor = mk("bos1", false);
        User alici = mk("bos2", true);
        Conversation s = messages.open(ConversationKind.CLAIM, talep(donor, alici).getId(), alici);

        assertThatThrownBy(() -> messages.send(s.getId(), alici, "   "))
                .hasMessageContaining("boş olamaz");
        assertThatThrownBy(() -> messages.send(s.getId(), alici, null))
                .hasMessageContaining("boş olamaz");
    }

    @Test
    void mesajGelinceKarsiTarafaBildirimGider() {
        User donor = mk("bildirim1", false);
        User alici = mk("bildirim2", true);
        Conversation s = messages.open(ConversationKind.CLAIM, talep(donor, alici).getId(), alici);

        messages.send(s.getId(), alici, "buluşalım mı?");

        assertThat(notifications.latest(donor))
                .anyMatch(n -> n.getType().equals("mesaj") && n.getMessage().contains("buluşalım mı?"));
    }

    @Test
    void karsilanmamisIstekIcinSohbetAcilmaz() {
        User isteyen = mk("acik-istek", true);
        BookRequest r = requestService.create(isteyen, book("İstenen"), null);

        assertThatThrownBy(() -> messages.open(ConversationKind.REQUEST, r.getId(), isteyen))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("henüz kimse karşılamadı");
    }

    @Test
    void bekleyenVeKabulEdilmisTakasIcinSohbetAcilirIptaldeAcilmaz() {
        User ali = mk("takas1", false);
        User veli = mk("takas2", false);
        SwapBook a = swapService.open(ali, book("A"), null);
        SwapBook v = swapService.open(veli, book("V"), null);
        SwapOffer o = swapService.offer(a.getId(), v.getId(), veli, null);

        // Bekleyen teklif için sohbet açılabilir
        Conversation s = messages.open(ConversationKind.SWAP, o.getId(), veli);
        assertThat(s).isNotNull();
        messages.send(s.getId(), veli, "Kitabın baskı durumu nasıl?");
        assertThat(messages.messagesOf(s)).hasSize(1);

        // İptal edildikten sonra yeni sohbet açılması engellenir
        swapService.cancel(o.getId(), veli);
        assertThatThrownBy(() -> messages.open(ConversationKind.SWAP, o.getId(), veli))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("İptal edilmiş veya reddedilmiş");
    }

    @Test
    void cokUzunMesajKirpilir() {
        User donor = mk("uzun1", false);
        User alici = mk("uzun2", true);
        Conversation s = messages.open(ConversationKind.CLAIM, talep(donor, alici).getId(), alici);

        Message m = messages.send(s.getId(), alici, "x".repeat(3000));
        assertThat(m.getBody()).hasSize(2000);
    }

    @Autowired ReportService reports;

    @Test
    void sikayetUzerindenYoneticiIleDestekSohbetiAcilirVeMesajlasilir() {
        User admin = mk("yonetici-destek", false);
        admin.setAdmin(true);
        users.save(admin);

        User member = mk("uye-sikayetci", true);
        User reported = mk("uye-sikayet-edilen", false);

        Report r = reports.create(member, ReportKind.USER, reported.getId(), ReportReason.TACIZ, "Rahatsız edici davranış");

        // Üye şikâyet üzerinden destek sohbeti açar
        Conversation c = messages.open(ConversationKind.REPORT, r.getId(), member);
        assertThat(c.getKind()).isEqualTo(ConversationKind.REPORT);
        assertThat(c.getUserA().getId()).isEqualTo(member.getId());

        // Üye mesaj gönderir
        messages.send(c.getId(), member, "Merhaba, şikâyetime ek ekran görüntüsü eklemek istiyorum.");

        // Yönetici mesajı görür ve yanıtlar
        Conversation adminConv = messages.require(c.getId(), admin);
        assertThat(messages.messagesOf(adminConv)).hasSize(1);

        messages.send(c.getId(), admin, "Merhaba, konuyu inceliyoruz. Ek bilgileri buradan paylaşabilirsiniz.");

        assertThat(messages.messagesOf(c)).hasSize(2)
                .extracting(Message::getBody)
                .containsExactly(
                        "Merhaba, şikâyetime ek ekran görüntüsü eklemek istiyorum.",
                        "Merhaba, konuyu inceliyoruz. Ek bilgileri buradan paylaşabilirsiniz."
                );

        // Yabancı üye erişemez
        User yabanci = mk("yabanci-destek", false);
        assertThatThrownBy(() -> messages.require(c.getId(), yabanci))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sana ait değil");
    }
}
