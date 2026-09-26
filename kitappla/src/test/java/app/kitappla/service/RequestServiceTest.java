package app.kitappla.service;

import app.kitappla.domain.*;
import app.kitappla.repo.BookRequestRepository;
import app.kitappla.repo.NotificationRepository;
import app.kitappla.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** İstek akışı: oluştur, karşıla, kargola, teslim al, teşekkür, kaldır. */
@SpringBootTest
@ActiveProfiles("test")
class RequestServiceTest {

    @Autowired RequestService requestService;
    @Autowired BookService bookService;
    @Autowired UserRepository users;
    @Autowired BookRequestRepository requests;
    @Autowired NotificationRepository notifications;
    @Autowired MessageService messages;
    @Autowired app.kitappla.repo.ConversationRepository conversations;

    private void reopen(BookRequest request, User requester, User fulfiller) {
        requestService.arrange(request.getId(), fulfiller, new MeetingRequest(
                null, "Kütüphane", java.time.Instant.now().plusSeconds(86400)));
        BookRequest arranged = requests.findByIdWithDetails(request.getId()).orElseThrow();
        arranged.getMeeting().setAt(java.time.Instant.now().minusSeconds(60));
        requests.save(arranged);
        requestService.noShow(request.getId(), requester);
    }

    @Test
    void reopenedRequestKeepsEachAttemptPrivateIncludingRepeatFulfiller() {
        User requester = user("attempt-owner", true, null);
        User first = user("attempt-first", false, null);
        User second = user("attempt-second", false, null);
        BookRequest r = requestService.create(requester, book(), null);
        requestService.fulfill(r.getId(), first, DonationSource.OWN);
        Conversation old = messages.open(ConversationKind.REQUEST, r.getId(), first);
        messages.send(old.getId(), first, "İlk görüşme");
        reopen(r, requester, first);
        assertThat(messages.require(old.getId(), first).isArchived()).isTrue();
        assertThat(messages.find(ConversationKind.REQUEST, r.getId())).isEmpty();
        assertThat(messages.conversationIds(ConversationKind.REQUEST, java.util.List.of(r.getId()))).isEmpty();
        assertThatThrownBy(() -> messages.open(ConversationKind.REQUEST, r.getId(), requester))
                .isInstanceOf(IllegalStateException.class);
        messages.markRead(old, requester);
        assertThat(messages.require(old.getId(), first).isArchived()).isTrue();
        assertThatThrownBy(() -> messages.send(old.getId(), first, "Yeni mesaj"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("arşivlendi");

        requestService.fulfill(r.getId(), second, DonationSource.OWN);
        Conversation next = messages.open(ConversationKind.REQUEST, r.getId(), second);
        assertThat(next.getId()).isNotEqualTo(old.getId());
        assertThat(messages.open(ConversationKind.REQUEST, r.getId(), requester).getId()).isEqualTo(next.getId());
        assertThat(messages.messagesOf(next)).isEmpty();
        assertThatThrownBy(() -> messages.require(old.getId(), second)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> messages.require(next.getId(), first)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> messages.subscribe(next.getId(), first)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> messages.open(ConversationKind.REQUEST, r.getId(), first))
                .isInstanceOf(IllegalStateException.class);
        messages.send(next.getId(), second, "İkinci görüşme");
        assertThat(messages.mine(first)).extracting(Conversation::getId).contains(old.getId()).doesNotContain(next.getId());
        assertThat(messages.mine(second)).extracting(Conversation::getId).contains(next.getId()).doesNotContain(old.getId());
        assertThat(messages.conversationIds(ConversationKind.REQUEST, java.util.List.of(r.getId())))
                .containsEntry(r.getId(), next.getId());

        reopen(r, requester, second);
        requestService.fulfill(r.getId(), first, DonationSource.OWN);
        Conversation repeated = messages.open(ConversationKind.REQUEST, r.getId(), first);
        assertThat(repeated.getId()).isNotIn(old.getId(), next.getId());
        assertThat(messages.messagesOf(repeated)).isEmpty();
        messages.send(repeated.getId(), first, "Üçüncü görüşme");
        assertThat(messages.messagesOf(messages.require(old.getId(), first)))
                .extracting(Message::getBody).containsExactly("İlk görüşme");
        assertThatThrownBy(() -> messages.require(repeated.getId(), second)).isInstanceOf(IllegalStateException.class);
        assertThat(messages.mine(requester)).extracting(Conversation::getId)
                .contains(old.getId(), next.getId(), repeated.getId());
        assertThat(messages.conversationIds(ConversationKind.REQUEST, java.util.List.of(r.getId())))
                .containsEntry(r.getId(), repeated.getId()).hasSize(1);
    }

    @Test
    void repeatedFulfillerImmediatelyAfterNoShowGetsNewConversation() {
        User requester = user("repeat-owner", true, null);
        User fulfiller = user("repeat-fulfiller", false, null);
        BookRequest r = requestService.create(requester, book(), null);
        requestService.fulfill(r.getId(), fulfiller, DonationSource.OWN);
        Conversation old = messages.open(ConversationKind.REQUEST, r.getId(), requester);
        reopen(r, requester, fulfiller);
        requestService.fulfill(r.getId(), fulfiller, DonationSource.OWN);
        Conversation next = messages.open(ConversationKind.REQUEST, r.getId(), requester);
        assertThat(next.getId()).isNotEqualTo(old.getId());
        assertThat(messages.require(old.getId(), fulfiller).isArchived()).isTrue();
        assertThat(messages.open(ConversationKind.REQUEST, r.getId(), fulfiller).getId()).isEqualTo(next.getId());
    }

    @Test
    void flywayUpgradePreservesHistoryAndArchivesStaleAttempts() {
        String url = "jdbc:h2:mem:attempt-migration-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        org.flywaydb.core.Flyway.configure().dataSource(url, "sa", "").target("2").load().migrate();
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(
                new org.springframework.jdbc.datasource.DriverManagerDataSource(url, "sa", ""));
        jdbc.update("INSERT INTO users (id, name, email, password_hash) VALUES (1, 'Owner', 'owner@test.local', 'x'), (2, 'First', 'first@test.local', 'x'), (3, 'Next', 'next@test.local', 'x')");
        jdbc.update("INSERT INTO books (id, title) VALUES (1, 'Book')");
        jdbc.update("""
                INSERT INTO requests (id, student_id, book_id, status, fulfilled_by_id, fulfilled_at) VALUES
                (1, 1, 1, 'OPEN', NULL, NULL),
                (2, 1, 1, 'FULFILLED', 3, TIMESTAMP '2026-09-02 00:00:00'),
                (3, 1, 1, 'FULFILLED', 2, TIMESTAMP '2026-09-02 00:00:00'),
                (4, 1, 1, 'FULFILLED', 2, TIMESTAMP '2026-08-01 00:00:00')
                """);
        for (long id = 1; id <= 4; id++) {
            jdbc.update("INSERT INTO conversations (id, kind, ref_id, user_a_id, user_b_id, created_at) VALUES (?, 'REQUEST', ?, 1, 2, TIMESTAMP '2026-09-01 00:00:00')", id, id);
            jdbc.update("INSERT INTO messages (conversation_id, sender_id, body) VALUES (?, 2, 'Private history')", id);
        }
        jdbc.update("INSERT INTO conversations (id, kind, ref_id, user_a_id, user_b_id) VALUES (5, 'CLAIM', 1, 1, 2)");
        org.flywaydb.core.Flyway.configure().dataSource(url, "sa", "").load().migrate();
        assertThat(jdbc.queryForList("SELECT active_key FROM conversations ORDER BY id", Long.class))
                .containsExactly(1L, 2L, 3L, 0L, 0L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE body = 'Private history'", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conversations WHERE user_a_id = 1 AND user_b_id = 2", Long.class)).isEqualTo(5);
        jdbc.update("INSERT INTO conversations (id, kind, ref_id, user_a_id, user_b_id) VALUES (6, 'REQUEST', 3, 1, 2)");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO conversations (id, kind, ref_id, user_a_id, user_b_id) VALUES (7, 'REQUEST', 3, 1, 2)"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.execute("SHUTDOWN");
    }

    private User user(String tag, boolean student, String address) {
        User u = new User();
        u.setName("İstek " + tag);
        u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash("x");
        u.setAddress(address);
        if (student) {
            u.setStudentStatus(StudentStatus.APPROVED);
            u.setSchoolLevel(SchoolLevel.LISE);
        }
        return users.save(u);
    }

    private Book book() {
        return bookService.findOrCreate("İstek Kitabı " + UUID.randomUUID(), "Yazar", null, null, null, null);
    }

    @Test
    void istekOlusturulurVeAcikListedeGorunur() {
        User isteyen = user("isteyen", true, "Ankara");
        BookRequest r = requestService.create(isteyen, book(), "Ödevim için lazım");

        assertThat(r.getStatus()).isEqualTo(RequestStatus.OPEN);
        assertThat(requestService.openRequests(null)).extracting(BookRequest::getId).contains(r.getId());
        assertThat(requestService.myRequests(isteyen)).hasSize(1);
    }

    @Test
    void kampusTeslimindeAdressizIstekOlusturulabilir() {
        // Yüz yüze teslimde adres gerekmez; kargo modunda yeniden istenir (KargoModuTest)
        User adressiz = user("adressiz", true, null);
        assertThat(requestService.create(adressiz, book(), null)).isNotNull();
    }

    @Test
    void ayniKitapIcinIkinciAcikIstekOlusturulamaz() {
        User isteyen = user("mukerrer", true, "Ankara");
        Book b = book();
        requestService.create(isteyen, b, null);
        assertThatThrownBy(() -> requestService.create(isteyen, b, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zaten açık bir isteğin");
    }

    @Test
    void acikIstekSayisiSinirlidir() {
        User isteyen = user("limit", true, "Ankara");
        for (int i = 0; i < RequestService.MAX_OPEN_REQUESTS; i++) {
            requestService.create(isteyen, book(), null);
        }
        assertThatThrownBy(() -> requestService.create(isteyen, book(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("en fazla");
    }

    @Test
    void istekKarsilanirVeIsteyeneBildirimGider() {
        User isteyen = user("isteyen", true, "Ankara");
        User karsilayan = user("karsilayan", false, "İzmir");
        BookRequest r = requestService.create(isteyen, book(), null);

        requestService.fulfill(r.getId(), karsilayan, DonationSource.PURCHASE);

        BookRequest updated = requests.findByIdWithDetails(r.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(RequestStatus.FULFILLED);
        assertThat(updated.getFulfilledBy().getId()).isEqualTo(karsilayan.getId());
        assertThat(updated.getFulfilledAt()).isNotNull();
        assertThat(notifications.findTop50ByUserOrderByCreatedAtDesc(isteyen))
                .extracting(Notification::getType).contains("request_fulfilled");
        assertThat(requestService.fulfilledByMe(karsilayan)).hasSize(1);
    }

    @Test
    void kendiIsteginiKarsilayamaz() {
        User isteyen = user("kendi", true, "Ankara");
        BookRequest r = requestService.create(isteyen, book(), null);
        assertThatThrownBy(() -> requestService.fulfill(r.getId(), isteyen, DonationSource.PURCHASE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Kendi isteğini");
    }

    @Test
    void ayniIstekIkiKezKarsilanamaz() {
        User isteyen = user("isteyen", true, "Ankara");
        User a = user("a", false, "İzmir");
        User b = user("b", false, "Bursa");
        BookRequest r = requestService.create(isteyen, book(), null);

        requestService.fulfill(r.getId(), a, DonationSource.OWN);
        assertThatThrownBy(() -> requestService.fulfill(r.getId(), b, DonationSource.OWN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("başkası tarafından karşılandı");
    }

    @Test
    void isteyeninKotasiDoluysaKarsilanamaz() {
        User uye = user("kotali", false, "Ankara");        // üye: haftada 1
        User karsilayan = user("karsilayan", false, "İzmir");

        BookRequest ilk = requestService.create(uye, book(), null);
        requestService.fulfill(ilk.getId(), karsilayan, DonationSource.PURCHASE);   // kota doldu

        BookRequest ikinci = requestService.create(uye, book(), null);
        assertThatThrownBy(() -> requestService.fulfill(ikinci.getId(), karsilayan, DonationSource.PURCHASE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kotası dolu");
    }

    @Test
    void teslimatAkisiBulusmaTeslimTesekkur() {
        User isteyen = user("isteyen", true, "Ankara");
        User karsilayan = user("karsilayan", false, "İzmir");
        BookRequest r = requestService.create(isteyen, book(), null);
        requestService.fulfill(r.getId(), karsilayan, DonationSource.OWN);

        // Kampüs teslimi: kargo adımı yok, önce buluşma ayarlanır
        requestService.arrange(r.getId(), karsilayan, new MeetingRequest(
                null, "Kütüphane girişi", java.time.Instant.now().plusSeconds(86400)));
        assertThat(requests.findById(r.getId()).orElseThrow().getStatus()).isEqualTo(RequestStatus.ARRANGED);

        requestService.deliver(r.getId(), isteyen);
        assertThat(requests.findById(r.getId()).orElseThrow().getStatus()).isEqualTo(RequestStatus.DELIVERED);

        requestService.thank(r.getId(), isteyen, "Sağ ol!");
        assertThat(notifications.findTop50ByUserOrderByCreatedAtDesc(karsilayan))
                .extracting(Notification::getType).contains("request_delivered", "thank_you");
    }

    @Test
    void baskasininIstegineMudahaleEdilemez() {
        User isteyen = user("isteyen", true, "Ankara");
        User karsilayan = user("karsilayan", false, "İzmir");
        User yabanci = user("yabanci", false, "Bursa");
        BookRequest r = requestService.create(isteyen, book(), null);
        requestService.fulfill(r.getId(), karsilayan, DonationSource.OWN);

        assertThatThrownBy(() -> requestService.ship(r.getId(), yabanci)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> requestService.ship(r.getId(), karsilayan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Kargo akışı kapalı");
        assertThatThrownBy(() -> requestService.deliver(r.getId(), yabanci)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> requestService.delete(r.getId(), yabanci)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acikIstekKaldirilirKarsilanmisKaldirilamaz() {
        User isteyen = user("isteyen", true, "Ankara");
        User karsilayan = user("karsilayan", false, "İzmir");

        BookRequest acik = requestService.create(isteyen, book(), null);
        requestService.delete(acik.getId(), isteyen);
        assertThat(requests.findById(acik.getId())).isEmpty();

        BookRequest dolu = requestService.create(isteyen, book(), null);
        requestService.fulfill(dolu.getId(), karsilayan, DonationSource.OWN);
        assertThatThrownBy(() -> requestService.delete(dolu.getId(), isteyen))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kaldırılamaz");
    }

    @Test
    void karsilananIstekKotadanDuser() {
        User uye = user("kota", false, "Ankara");
        User karsilayan = user("karsilayan", false, "İzmir");

        assertThat(requestService.openRequests(null)).isNotNull();
        BookRequest r = requestService.create(uye, book(), null);
        requestService.fulfill(r.getId(), karsilayan, DonationSource.PURCHASE);

        // Üye haftalık kotası 1 -> dolmuş olmalı
        assertThat(requestService.openRequests(null)).isNotNull();
        assertThat(requests.countByStudentAndStatusInAndFulfilledAtAfter(uye,
                java.util.List.of(RequestStatus.FULFILLED, RequestStatus.SHIPPED, RequestStatus.DELIVERED),
                java.time.Instant.now().minusSeconds(3600))).isEqualTo(1);
    }

    @Test
    void aramaBasligaGoreSuzer() {
        User isteyen = user("arama", true, "Ankara");
        Book b = bookService.findOrCreate("Arama Testi Kitabı", "Özel Yazar", null, null, null, null);
        requestService.create(isteyen, b, null);

        assertThat(requestService.openRequests("Arama Testi")).isNotEmpty();
        assertThat(requestService.openRequests("boyle-bir-kitap-yok")).isEmpty();
    }

    @Test
    void karsilananAmaBulusmasiAyarlanmayanIstekAcikListedeGorunur() {
        User isteyen = user("isteyen-acik", true, "Ankara");
        User karsilayan = user("karsilayan-acik", false, "İzmir");
        Book b = bookService.findOrCreate("Görünürlük Testi " + java.util.UUID.randomUUID(), "Yazar", null, null, null, null);
        BookRequest r = requestService.create(isteyen, b, null);

        // Henüz karşılanmadı -> açık listede
        assertThat(requestService.openRequests(b.getTitle())).hasSize(1);

        // Karşılandı ama buluşma ayarlanmadı -> hâlâ açık listede diğerlerine görünür
        requestService.fulfill(r.getId(), karsilayan, DonationSource.OWN);
        assertThat(requestService.openRequests(b.getTitle())).hasSize(1);

        // Buluşma ayarlandı -> açık listeden kalkar
        requestService.arrange(r.getId(), karsilayan, new MeetingRequest(null, "Kütüphane önü", java.time.Instant.now().plusSeconds(3600)));
        assertThat(requestService.openRequests(b.getTitle())).isEmpty();
    }

    @Test
    void bulusmaKaydedilmemisseKarsilamaIptalEdilebilir() {
        User isteyen = user("isteyen-iptal", true, "Ankara");
        User karsilayan = user("karsilayan-iptal", false, "İzmir");
        User baskaKarsilayan = user("baska-iptal", false, "Bursa");
        Book b = bookService.findOrCreate("İptal Testi " + java.util.UUID.randomUUID(), "Yazar", null, null, null, null);
        BookRequest r = requestService.create(isteyen, b, null);

        BookRequest fulfilled = requestService.fulfill(r.getId(), karsilayan, DonationSource.OWN);
        assertThat(fulfilled.getStatus()).isEqualTo(RequestStatus.FULFILLED);

        // Karşılayan iptal eder
        requestService.cancelFulfillment(r.getId(), karsilayan);

        BookRequest sifirlanan = requests.findByIdWithDetails(r.getId()).orElseThrow();
        assertThat(sifirlanan.getStatus()).isEqualTo(RequestStatus.OPEN);
        assertThat(sifirlanan.getFulfilledBy()).isNull();
        assertThat(sifirlanan.getFulfilledAt()).isNull();
        assertThat(sifirlanan.getSource()).isNull();

        // Şimdi başka biri karşılayabilir
        requestService.fulfill(r.getId(), baskaKarsilayan, DonationSource.PURCHASE);
        BookRequest yeniKarsilanan = requests.findByIdWithDetails(r.getId()).orElseThrow();
        assertThat(yeniKarsilanan.getFulfilledBy().getId()).isEqualTo(baskaKarsilayan.getId());

        // İsteyen kişi de buluşma öncesi karşılamayı iptal edebilir
        requestService.cancelFulfillment(r.getId(), isteyen);
        BookRequest isteyenIptalEtti = requests.findByIdWithDetails(r.getId()).orElseThrow();
        assertThat(isteyenIptalEtti.getStatus()).isEqualTo(RequestStatus.OPEN);
        assertThat(isteyenIptalEtti.getFulfilledBy()).isNull();
    }

    @Test
    void bulusmaKaydedildiktenSonraIptalEdilemez() {
        User isteyen = user("isteyen-kilit", true, "Ankara");
        User karsilayan = user("karsilayan-kilit", false, "İzmir");
        Book b = bookService.findOrCreate("Kilit Testi " + java.util.UUID.randomUUID(), "Yazar", null, null, null, null);
        BookRequest r = requestService.create(isteyen, b, null);

        requestService.fulfill(r.getId(), karsilayan, DonationSource.OWN);
        requestService.arrange(r.getId(), karsilayan, new MeetingRequest(null, "Giriş kapısı", java.time.Instant.now().plusSeconds(3600)));

        assertThatThrownBy(() -> requestService.cancelFulfillment(r.getId(), karsilayan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Buluşma kaydedildikten sonra");
    }
}
