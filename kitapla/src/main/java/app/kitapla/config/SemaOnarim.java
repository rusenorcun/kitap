package app.kitapla.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.ColumnDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Yarım kalmış şema yükseltmelerinden arta kalan boş değerleri doldurur.
 *
 * <p><b>Neden gerekli.</b> {@code ddl-auto=update}, dolu bir tabloya varsayılanı olmayan
 * NOT NULL sütun ekleyemez. Böyle bir sürümde sütun boş değer alabilir hâlde kalır ve
 * mevcut satırlar NULL ile yaşamaya devam eder. Sonradan alana {@code @ColumnDefault}
 * eklenmesi bunu düzeltmez: Hibernate {@code update} var olan bir sütunun tanımına
 * dokunmaz, dolayısıyla eski satırlar NULL kalır.</p>
 *
 * <p><b>Etkisi.</b> Bu alanların çoğu {@code boolean} / {@code int} gibi ilkel tiplere
 * eşlenir. NULL okununca Hibernate satırı hiç yükleyemez ({@code PropertyAccessException})
 * ve o satıra dokunan her sorgu patlar. Bağışçısı bozuk olan tek bir bağış, keşfet
 * sayfasının tamamını 500'e düşürür — üretimde tam olarak bu oldu.</p>
 *
 * <p><b>Nasıl çalışır.</b> Alan listesi elle tutulmaz; {@code domain} paketindeki
 * {@code @ColumnDefault} taşıyan her alan taranır ve her biri için tek bir
 * {@code update ... set ... where ... is null} çalıştırılır. Böylece yeni bir alan
 * eklendiğinde burası kendiliğinden kapsar. İşlem etkisizdir (idempotent): doldurulacak
 * satır yoksa hiçbir şey yazılmaz, temiz kurulumlarda sessizce geçer.</p>
 *
 * <p>{@link DataSeeder}'dan <b>önce</b> çalışır: tohumlama da üye tablosunu okuduğu için
 * onarım önce bitmiş olmalıdır.</p>
 */
@Component
@Order(0)
public class SemaOnarim implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SemaOnarim.class);

    /** Varlıkların bulunduğu paket; sınıflar buradan taranır. */
    private static final String DOMAIN_PAKETI = "app.kitapla.domain";

    private final JdbcTemplate jdbc;

    public SemaOnarim(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Doldurulacak tek bir sütun. (Testin ad türetmesini doğrulayabilmesi için paket görünür.) */
    record Sutun(String tablo, String ad, String varsayilan) {
        String guncelleme() {
            return "update " + tablo + " set " + ad + " = " + varsayilan + " where " + ad + " is null";
        }
    }

    @Override
    public void run(String... args) {
        int toplam = 0;
        for (Sutun s : sutunlar()) {
            try {
                int n = jdbc.update(s.guncelleme());
                if (n > 0) {
                    log.warn("Şema onarımı: {}.{} sütununda {} satır varsayılana ({}) çekildi.",
                            s.tablo(), s.ad(), n, s.varsayilan());
                    toplam += n;
                }
            } catch (Exception ex) {
                // Sütun ya da tablo henüz yoksa (ilk kurulum) onarılacak bir şey de yoktur.
                log.debug("Şema onarımı atlandı ({}.{}): {}", s.tablo(), s.ad(), ex.getMessage());
            }
        }
        if (toplam > 0) {
            log.warn("Şema onarımı tamamlandı: toplam {} satır düzeltildi. "
                    + "Bu satırlar yarım kalmış bir sürüm yükseltmesinden kalmıştı.", toplam);
        }
    }

    /** {@code @ColumnDefault} taşıyan her alanı tablo/sütun/varsayılan üçlüsüne çevirir. */
    List<Sutun> sutunlar() {
        List<Sutun> bulunan = new ArrayList<>();
        for (Class<?> varlik : varliklar()) {
            String tablo = tabloAdi(varlik);
            if (tablo == null) continue;
            for (Field f : varlik.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (f.isAnnotationPresent(Transient.class)) continue;
                ColumnDefault d = f.getAnnotation(ColumnDefault.class);
                if (d == null) continue;
                bulunan.add(new Sutun(tablo, sutunAdi(f), d.value()));
            }
        }
        return bulunan;
    }

    /** Domain paketindeki {@code @Entity} sınıfları. */
    private static List<Class<?>> varliklar() {
        var tarayici = new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(false);
        tarayici.addIncludeFilter(new org.springframework.core.type.filter.AnnotationTypeFilter(Entity.class));

        List<Class<?>> siniflar = new ArrayList<>();
        for (var tanim : tarayici.findCandidateComponents(DOMAIN_PAKETI)) {
            try {
                siniflar.add(Class.forName(tanim.getBeanClassName()));
            } catch (ClassNotFoundException ex) {
                log.debug("Varlık sınıfı yüklenemedi: {}", tanim.getBeanClassName());
            }
        }
        return siniflar;
    }

    /** {@code @Table(name=...)} varsa onu, yoksa sınıf adının yılan_harf hâlini verir. */
    private static String tabloAdi(Class<?> varlik) {
        Table t = varlik.getAnnotation(Table.class);
        if (t != null && !t.name().isBlank()) return t.name();
        return yilanHarf(varlik.getSimpleName());
    }

    /** {@code @Column(name=...)} varsa onu, yoksa alan adının yılan_harf hâlini verir. */
    private static String sutunAdi(Field f) {
        Column c = f.getAnnotation(Column.class);
        if (c != null && !c.name().isBlank()) return c.name();
        return yilanHarf(f.getName());
    }

    /**
     * Spring Boot'un varsayılan adlandırma stratejisi (CamelCaseToUnderscoresNamingStrategy)
     * ile aynı dönüşüm: {@code noShowCount} → {@code no_show_count}.
     */
    private static String yilanHarf(String ad) {
        StringBuilder sb = new StringBuilder(ad.length() + 4);
        for (int i = 0; i < ad.length(); i++) {
            char ch = ad.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) sb.append('_');
            sb.append(Character.toLowerCase(ch));
        }
        return sb.toString();
    }
}
