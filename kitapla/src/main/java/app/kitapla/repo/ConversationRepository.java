package app.kitapla.repo;

import app.kitapla.domain.Conversation;
import app.kitapla.domain.ConversationKind;
import app.kitapla.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByKindAndRefId(ConversationKind kind, Long refId);

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
           where c.userA = :user or c.userB = :user or (:isAdmin = true and c.kind = app.kitapla.domain.ConversationKind.REPORT)
           order by c.lastMessageAt desc nulls last, c.createdAt desc
           """)
    List<Conversation> findMineCustom(@Param("user") User user, @Param("isAdmin") boolean isAdmin);

    default List<Conversation> findMine(User user) {
        return findMineCustom(user, user != null && user.isAdmin());
    }

    void deleteByUserAOrUserB(User a, User b);
}
