package app.kitapla.repo;

import app.kitapla.domain.OfferStatus;
import app.kitapla.domain.SwapBook;
import app.kitapla.domain.SwapOffer;
import app.kitapla.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SwapOfferRepository extends JpaRepository<SwapOffer, Long> {

    String DETAILS = """
           select o from SwapOffer o
           join fetch o.fromUser
           join fetch o.toUser
           join fetch o.offeredSwapBook osb join fetch osb.book
           join fetch o.targetSwapBook tsb join fetch tsb.book
           """;

    @Query(DETAILS + " where o.toUser = :user order by o.createdAt desc")
    List<SwapOffer> findIncoming(@Param("user") User user);

    @Query(DETAILS + " where o.fromUser = :user order by o.createdAt desc")
    List<SwapOffer> findOutgoing(@Param("user") User user);

    @Query(DETAILS + " where o.id = :id")
    Optional<SwapOffer> findByIdWithDetails(@Param("id") Long id);

    /** Aynı hedefe aynı kişinin bekleyen teklifi var mı? */
    boolean existsByFromUserAndTargetSwapBookAndStatus(User fromUser, SwapBook target, OfferStatus status);

    /** Kabul edilince rakip teklifleri reddetmek için. */
    @Query("""
           select o from SwapOffer o
           where o.status = :status and o.id <> :exceptId
             and (o.targetSwapBook in :books or o.offeredSwapBook in :books)
           """)
    List<SwapOffer> findCompeting(@Param("status") OfferStatus status,
                                  @Param("exceptId") Long exceptId,
                                  @Param("books") List<SwapBook> books);

    /** Takas kitabına bağlı, sonuçlanmamış teklif var mı? */
    @Query("""
           select count(o) from SwapOffer o
           where (o.targetSwapBook = :book or o.offeredSwapBook = :book)
             and o.status in :statuses
           """)
    long countByBookAndStatuses(@Param("book") SwapBook book, @Param("statuses") List<OfferStatus> statuses);

    List<SwapOffer> findByToUserOrderByCreatedAtDesc(User toUser);
    List<SwapOffer> findByFromUserOrderByCreatedAtDesc(User fromUser);
    long countByStatus(OfferStatus status);
}
