package app.kitapla.service;

import app.kitapla.domain.PickupPoint;
import app.kitapla.repo.PickupPointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Kampüs teslim noktalarının yönetimi. */
@Service
public class PickupPointService {

    private final PickupPointRepository points;

    public PickupPointService(PickupPointRepository points) {
        this.points = points;
    }

    /** Üyelerin seçebileceği noktalar. */
    public List<PickupPoint> active() {
        return points.findByActiveTrueOrderByCampusAscNameAsc();
    }

    /** Yönetim listesi (pasifler dahil). */
    public List<PickupPoint> all() {
        return points.findAllByOrderByCampusAscNameAsc();
    }

    public Optional<PickupPoint> find(Long id) {
        return id == null ? Optional.empty() : points.findById(id);
    }

    /** Seçim sırasında yalnızca aktif nokta kabul edilir. */
    public Optional<PickupPoint> findSelectable(Long id) {
        return find(id).filter(PickupPoint::isActive);
    }

    @Transactional
    public PickupPoint create(String campus, String name, String description) {
        String k = clean(campus, 120);
        String a = clean(name, 160);
        if (k == null) throw new IllegalStateException("Kampüs adı zorunlu.");
        if (a == null) throw new IllegalStateException("Nokta adı zorunlu.");
        if (points.findByCampusIgnoreCaseAndNameIgnoreCase(k, a).isPresent())
            throw new IllegalStateException("Bu kampüste aynı adlı bir nokta zaten var.");

        PickupPoint p = new PickupPoint();
        p.setCampus(k);
        p.setName(a);
        p.setDescription(clean(description, 400));
        return points.save(p);
    }

    @Transactional
    public PickupPoint update(Long id, String campus, String name, String description) {
        PickupPoint p = points.findById(id)
                .orElseThrow(() -> new IllegalStateException("Nokta bulunamadı."));
        String k = clean(campus, 120);
        String a = clean(name, 160);
        if (k == null) throw new IllegalStateException("Kampüs adı zorunlu.");
        if (a == null) throw new IllegalStateException("Nokta adı zorunlu.");

        // Başka bir kayıt aynı ada sahipse çakışma
        points.findByCampusIgnoreCaseAndNameIgnoreCase(k, a)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new IllegalStateException("Bu kampüste aynı adlı bir nokta zaten var.");
                });

        p.setCampus(k);
        p.setName(a);
        p.setDescription(clean(description, 400));
        return points.save(p);
    }

    /**
     * Noktayı pasifleştirir ya da geri açar. Silme yerine pasifleştirme:
     * geçmiş buluşma kayıtları bu noktaya bağlı olduğu için kayıt korunur.
     */
    @Transactional
    public PickupPoint setActive(Long id, boolean active) {
        PickupPoint p = points.findById(id)
                .orElseThrow(() -> new IllegalStateException("Nokta bulunamadı."));
        if (p.isActive() == active)
            throw new IllegalStateException(active ? "Nokta zaten aktif." : "Nokta zaten pasif.");
        p.setActive(active);
        return points.save(p);
    }

    private static String clean(String s, int max) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
