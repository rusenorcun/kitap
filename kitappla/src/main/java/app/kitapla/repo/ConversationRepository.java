package app.kitapla.repo;

import java.util.Collection;
import app.kitapla.domain.Conversation;
import app.kitapla.domain.ConversationKind;
import app.kitapla.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("select c from Conversation c where c.kind = :kind and c.refId = :refId and c.activeKey = 0")
    Optional<Conversation> findByKindAndRefId(@Param("kind") ConversationKind kind, @Param("refId") Long refId);

    /** Şablonlar karşı tarafın adını gösterdiği için ikisi de çekilir (open-in-view kapalı). */
    @Query("""
           select c from Conversation c
           join fetch c.userA
           join fetch c.userB
           where c.id = :id
           """)
    Optional<Conversation> findByIdWithUsers(@Param("id") Long id);

    @Query("""
           select c from Conversation c
           join fetch c.userA
           join fetch c.userB
           where (c.kind <> app.kitapla.domain.ConversationKind.REPORT and (c.userA = :user or c.userB = :user))
               or (c.kind = app.kitapla.domain.ConversationKind.REPORT and exists (
                   select r.id from Report r where r.id = c.refId and (r.reporter = :user or exists (
                       select u.id from User u where u = :user and u.admin = true))))
           order by c.lastMessageAt desc nulls last, c.createdAt desc
           """)
    List<Conversation> findMine(@Param("user") User user);

    /**
     * Kişinin taraf olduğu ve karşı taraftan okunmamış mesajı bulunan sohbet sayısı.
     * Okunmamış: kendi okuma damgasından sonra gelen (damga yoksa hepsi) karşı taraf mesajı.
     */
    @Query("""
           select count(c) from Conversation c
           where c.kind <> app.kitapla.domain.ConversationKind.REPORT and ((c.userA = :me and exists (
                     select m.id from Message m where m.conversation = c and m.sender <> :me
                       and (c.lastReadA is null or m.createdAt > c.lastReadA)))
              or (c.userB = :me and exists (
                     select m.id from Message m where m.conversation = c and m.sender <> :me
                       and (c.lastReadB is null or m.createdAt > c.lastReadB))))
           """)
    long countUnreadAsParty(@Param("me") User me);

    /** Yöneticinin taraf olmadığı şikâyet sohbetleri: yönetici tarafının (userB) damgası kullanılır. */
    @Query("""
           select count(c) from Conversation c
           where c.kind = app.kitapla.domain.ConversationKind.REPORT
              and exists (select r.id from Report r where r.id = c.refId
                  and (r.reporter = :me or exists (select u.id from User u where u = :me and u.admin = true))
                  and exists (select m.id from Message m where m.conversation = c and m.sender <> :me
                      and ((r.reporter = :me and (c.lastReadA is null or m.createdAt > c.lastReadA))
                          or (r.reporter <> :me and (c.lastReadB is null or m.createdAt > c.lastReadB)))))
           """)
    long countUnreadReportsForAdmin(@Param("me") User me);

    /** Alışveriş → sohbet kimliği eşlemesi (projeksiyon). */
    interface SohbetKimligi {
        Long getRefId();
        Long getId();
    }

    /** Birden çok alışverişin sohbet kimlikleri tek sorguda; sohbeti açılmamış olanlar sonuçta yer almaz. */
    @Query("select c.refId as refId, c.id as id from Conversation c where c.kind = :kind and c.refId in :refIds and c.activeKey = 0")
    List<SohbetKimligi> findIdsByKindAndRefIds(@Param("kind") ConversationKind kind,
                                               @Param("refIds") Collection<Long> refIds);
}
