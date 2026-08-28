package app.kitapla.repo;

import app.kitapla.domain.PickupPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PickupPointRepository extends JpaRepository<PickupPoint, Long> {

    /** Üyelere gösterilecek noktalar. */
    List<PickupPoint> findByActiveTrueOrderByCampusAscNameAsc();

    List<PickupPoint> findAllByOrderByCampusAscNameAsc();

    Optional<PickupPoint> findByCampusIgnoreCaseAndNameIgnoreCase(String campus, String name);

    long countByActiveTrue();
}
