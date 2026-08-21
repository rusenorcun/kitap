package app.kitapla.repo;

import app.kitapla.domain.SwapOffer;
import app.kitapla.domain.OfferStatus;
import app.kitapla.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SwapOfferRepository extends JpaRepository<SwapOffer, Long> {
    List<SwapOffer> findByToUserOrderByCreatedAtDesc(User toUser);
    List<SwapOffer> findByFromUserOrderByCreatedAtDesc(User fromUser);
    long countByStatus(OfferStatus status);
}
