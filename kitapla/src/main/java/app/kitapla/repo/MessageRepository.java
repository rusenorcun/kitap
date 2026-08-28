package app.kitapla.repo;

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

    void deleteByConversation(Conversation conversation);
}
