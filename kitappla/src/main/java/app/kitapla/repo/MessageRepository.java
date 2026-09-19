package app.kitapla.repo;

import java.util.Collection;
import app.kitapla.domain.Conversation;
import app.kitapla.domain.Message;
import app.kitapla.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("""
           select m from Message m
           join fetch m.sender
           where m.conversation = :conversation
           order by m.createdAt asc
           """)
    List<Message> findByConversation(@Param("conversation") Conversation conversation);

    /** Karşı taraftan gelen ve okuma damgasından sonraki mesajlar. */
    long countByConversationAndSenderNotAndCreatedAtAfter(Conversation c, User me, Instant after);

    long countByConversationAndSenderNot(Conversation c, User me);

    /** Sohbet başına okunmamış adet (projeksiyon). */
    interface OkunmamisAdet {
        Long getConversationId();
        Long getAdet();
    }

    /**
     * Birden çok sohbetin okunmamış sayıları tek sorguda. Kural MessageService.unread ile aynıdır:
     * kişi userA ise lastReadA, değilse (userB ya da taraf olmayan yönetici) lastReadB damgası kullanılır.
     */
    @Query("""
           select c.id as conversationId, count(m) as adet from Message m join m.conversation c
           where c.id in :conversationIds and m.sender <> :me
              and ((c.kind not in (app.kitapla.domain.ConversationKind.REPORT, app.kitapla.domain.ConversationKind.SUPPORT)
                    and ((c.userA = :me and (c.lastReadA is null or m.createdAt > c.lastReadA))
                      or (c.userB = :me and (c.lastReadB is null or m.createdAt > c.lastReadB))))
                or (c.kind = app.kitapla.domain.ConversationKind.REPORT and exists (
                    select r.id from Report r where r.id = c.refId
                      and ((r.reporter = :me and (c.lastReadA is null or m.createdAt > c.lastReadA))
                        or (r.reporter <> :me and (c.lastReadB is null or m.createdAt > c.lastReadB)
                          and exists (select u.id from User u where u = :me and u.admin = true)))))
                or (c.kind = app.kitapla.domain.ConversationKind.SUPPORT
                    and ((c.userA = :me and (c.lastReadA is null or m.createdAt > c.lastReadA))
                      or (c.userA <> :me and (c.lastReadB is null or m.createdAt > c.lastReadB)
                          and exists (select u.id from User u where u = :me and u.admin = true)))))
           group by c.id
           """)
    List<OkunmamisAdet> countUnreadByConversation(@Param("conversationIds") Collection<Long> conversationIds,
                                                  @Param("me") User me);

    void deleteByConversation(Conversation conversation);
}
