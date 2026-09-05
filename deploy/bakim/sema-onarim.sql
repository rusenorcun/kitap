-- Yarım kalmış şema yükseltmesinden arta kalan boş değerleri doldurur.
--
-- NORMALDE BU DOSYAYA GEREK YOK: uygulama açılışta aynı onarımı kendisi yapar
-- (app.kitapla.config.SemaOnarim). Bu betik yalnızca yeni sürümü hemen dağıtmadan
-- siteyi ayağa kaldırmak gerektiğinde işe yarar.
--
-- Neden gerekiyor: ddl-auto=update dolu bir tabloya varsayılanı olmayan NOT NULL
-- sütun ekleyemez. Sütun boş değer alabilir hâlde kalır, eski satırlar NULL ile
-- yaşamaya devam eder. Bu alanlar boolean/int gibi ilkel tiplere eşlendiği için
-- NULL okununca Hibernate satırı hiç yükleyemez ve o satıra dokunan her sorgu
-- 500 döndürür.
--
-- Çalıştırmak (uygulama ayakta kalabilir, AUTO_SERVER=TRUE eşzamanlı bağlantıya izin verir):
--   java -cp h2-*.jar org.h2.tools.Shell \
--     -url "jdbc:h2:file:/opt/kitapla/data/kitapla;AUTO_SERVER=TRUE" \
--     -user sa -password "" -sql "$(cat sema-onarim.sql)"
--
-- Betik etkisizdir (idempotent): doldurulacak satır yoksa hiçbir şey değişmez.

-- --- Üyeler: üretimde siteyi düşüren tablo buydu ---
update users set blocked        = false      where blocked        is null;
update users set no_show_count  = 0          where no_show_count  is null;
update users set admin          = false      where admin          is null;
update users set student_status = 'NONE'     where student_status is null;
update users set created_at     = current_timestamp where created_at is null;

-- --- Bağışlar ---
update donations set source       = 'PURCHASE' where source       is null;
update donations set target_level = 'HEPSI'    where target_level is null;
update donations set status       = 'OPEN'     where status       is null;
update donations set created_at   = current_timestamp where created_at is null;

-- --- Talepler ve istekler ---
update claims   set status     = 'MATCHED' where status     is null;
update claims   set created_at = current_timestamp where created_at is null;
update requests set status     = 'OPEN'    where status     is null;
update requests set created_at = current_timestamp where created_at is null;

-- --- Takas ---
update swap_books  set status     = 'OPEN'    where status     is null;
update swap_books  set created_at = current_timestamp where created_at is null;
update swap_offers set status     = 'PENDING' where status     is null;
update swap_offers set created_at = current_timestamp where created_at is null;

-- --- Mesajlaşma ve bildirimler ---
update conversations set created_at = current_timestamp where created_at is null;
update messages      set created_at = current_timestamp where created_at is null;
update notifications set read_flag  = false where read_flag  is null;
update notifications set created_at = current_timestamp where created_at is null;

-- --- Diğer ---
update books         set created_at = current_timestamp where created_at is null;
update auth_tokens   set created_at = current_timestamp where created_at is null;
update reports       set status     = 'OPEN' where status     is null;
update reports       set created_at = current_timestamp where created_at is null;
update pickup_points set active     = true   where active     is null;
update pickup_points set created_at = current_timestamp where created_at is null;
