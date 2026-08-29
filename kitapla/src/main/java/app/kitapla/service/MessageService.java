package app.kitapla.service;

import app.kitapla.domain.*;
import app.kitapla.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Eşleşme üzerinden yürüyen sohbetler.
 * <p>
 * Sohbet kendiliğinden açılmaz: ilgili alışveriş (bağış talebi, karşılanan
 * istek, kabul edilmiş takas) varsa ve isteyen kişi onun taraflarından biriyse
 * açılır. Erişim denetimi her zaman alışverişin kendisinden doğrulanır.
 */
@Service
public class MessageService {

    private static final int MAX_UZUNLUK = 2000;

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ClaimRepository claims;
    private final BookRequestRepository requests;
    private final SwapOfferRepository offers;
    private final NotificationService notifications;
    private final SseHub sse;

    public MessageService(ConversationRepository conversations, MessageRepository messages,
                          ClaimRepository claims, BookRequestRepository requests,
                          SwapOfferRepository offers, NotificationService notifications, SseHub sse) {
        this.conversations = conversations;
        this.messages = messages;
        this.claims = claims;
        this.requests = requests;
        this.offers = offers;
        this.notifications = notifications;
        this.sse = sse;
    }

    // ---------- Açma ve erişim ----------

    /** Alışverişin iki tarafını çözer; kişi taraflardan biri değilse hata verir. */
    private User[] taraflar(ConversationKind kind, Long refId, User me) {
        switch (kind) {
            case CLAIM -> {
                Claim c = claims.findByIdWithDetails(refId)
                        .orElseThrow(() -> new IllegalStateException("Kayıt bulunamadı."));
                return dogrula(c.getDonation().getDonor(), c.getStudent(), me);
            }
            case REQUEST -> {
                BookRequest r = requests.findByIdWithDetails(refId)
                        .orElseThrow(() -> new IllegalStateException("İstek bulunamadı."));
                if (r.getFulfilledBy() == null)
                    throw new IllegalStateException("Bu isteği henüz kimse karşılamadı.");
                return dogrula(r.getStudent(), r.getFulfilledBy(), me);
            }
            case SWAP -> {
                SwapOffer o = offers.findByIdWithDetails(refId)
                        .orElseThrow(() -> new IllegalStateException("Teklif bulunamadı."));
                if (o.getStatus() != OfferStatus.ACCEPTED && o.getStatus() != OfferStatus.COMPLETED)
                    throw new IllegalStateException("Takas kabul edilmeden mesajlaşma açılmaz.");
                return dogrula(o.getFromUser(), o.getToUser(), me);
            }
            default -> throw new IllegalStateException("Bilinmeyen sohbet türü.");
        }
    }

    private User[] dogrula(User a, User b, User me) {
        if (!a.getId().equals(me.getId()) && !b.getId().equals(me.getId()))
            throw new IllegalStateException("Bu sohbet sana ait değil.");
        return new User[]{a, b};
    }

    /** Sohbeti bulur, yoksa açar. */
    @Transactional
    public Conversation open(ConversationKind kind, Long refId, User me) {
        User[] t = taraflar(kind, refId, me);
        return conversations.findByKindAndRefId(kind, refId).orElseGet(() -> {
            Conversation c = new Conversation();
            c.setKind(kind);
            c.setRefId(refId);
            c.setUserA(t[0]);
            c.setUserB(t[1]);
            return conversations.save(c);
        });
    }

    /** Kimlikten sohbeti getirir; yalnızca tarafları erişebilir. */
    public Conversation require(Long conversationId, User me) {
        Conversation c = conversations.findByIdWithUsers(conversationId)
                .orElseThrow(() -> new IllegalStateException("Sohbet bulunamadı."));
        if (!c.has(me)) throw new IllegalStateException("Bu sohbet sana ait değil.");
        return c;
    }

    // ---------- Okuma ----------

    public List<Conversation> mine(User me) {
        return conversations.findMine(me);
    }

    public List<Message> messagesOf(Conversation c) {
        return messages.findByConversation(c);
    }

    public long unread(Conversation c, User me) {
        Instant son = c.lastReadOf(me);
        return son == null
                ? messages.countByConversationAndSenderNot(c, me)
                : messages.countByConversationAndSenderNotAndCreatedAtAfter(c, me, son);
    }

    /** Nav'daki rozet için toplam okunmamış sohbet sayısı. */
    public long unreadConversations(User me) {
        return conversations.findMine(me).stream().filter(c -> unread(c, me) > 0).count();
    }

    @Transactional
    public void markRead(Conversation c, User me) {
        c.markRead(me, Instant.now());
        conversations.save(c);
    }

    // ---------- Yazma ----------

    @Transactional
    public Message send(Long conversationId, User me, String body) {
        Conversation c = require(conversationId, me);

        String metin = body == null ? null : body.trim();
        if (metin == null || metin.isEmpty())
            throw new IllegalStateException("Mesaj boş olamaz.");
        if (metin.length() > MAX_UZUNLUK)
            metin = metin.substring(0, MAX_UZUNLUK);

        Message m = new Message();
        m.setConversation(c);
        m.setSender(me);
        m.setBody(metin);
        messages.save(m);

        c.setLastMessage(metin.length() > 200 ? metin.substring(0, 200) : metin);
        c.setLastMessageAt(m.getCreatedAt());
        c.markRead(me, m.getCreatedAt());   // kendi mesajın okunmuş sayılır
        conversations.save(c);

        // Karşı tarafa hem canlı akış hem bildirim
        sse.publish(c.getId());
        notifications.notify(c.other(me), "mesaj",
                me.getName() + " sana mesaj gönderdi: \"" + kisalt(metin) + "\"");
        return m;
    }

    private static String kisalt(String s) {
        return s.length() <= 80 ? s : s.substring(0, 80) + "…";
    }

    /**
     * Moderasyon için sohbeti açar. Yönetici bile <b>yalnızca açık şikâyeti
     * olan</b> bir sohbeti okuyabilir; şikâyetsiz sohbetler yönetime de kapalıdır.
     * Kural burada zorlanır ki controller'da unutulması mümkün olmasın.
     */
    public Conversation requireForModeration(Long conversationId, ReportService reports) {
        Conversation c = conversations.findByIdWithUsers(conversationId)
                .orElseThrow(() -> new IllegalStateException("Sohbet bulunamadı."));
        if (!reports.hasOpenReport(ReportKind.CONVERSATION, conversationId))
            throw new IllegalStateException(
                    "Bu sohbetin açık şikâyeti yok; mesajlar yönetime kapalıdır.");
        return c;
    }

    // ---------- Yardımcılar ----------

    /** Belirli bir alışverişin sohbeti (varsa). */
    public Optional<Conversation> find(ConversationKind kind, Long refId) {
        return conversations.findByKindAndRefId(kind, refId);
    }
}
