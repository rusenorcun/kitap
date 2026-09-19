-- KİTAPLA — V2: sık sorgulanan alanlar için indeksler
--
-- PostgreSQL yabancı anahtarlara kendiliğinden indeks koymaz. Aşağıdakiler kapasite
-- taramasında tam tablo taraması yaptığı ölçülen ya da liste sayfalarında filtre/sıralama
-- için kullanılan alanlardır. Göç testleri H2 üzerinde de çalıştığı için yalnızca iki
-- veritabanının ortak söz dizimi kullanıldı (kısmi indeks yok).

-- Her sayfada okunmamış bildirim sayısı ve son 50 bildirim
CREATE INDEX ix_notif_user_read    ON notifications (user_id, read_flag);
CREATE INDEX ix_notif_user_created ON notifications (user_id, created_at);

-- Kota hesabı (öğrencinin son 7/30 gündeki talepleri) ve "aldıklarım"
CREATE INDEX ix_claims_student_created ON claims (student_id, created_at);

-- İstekler: kişinin istekleri, açık istekler listesi, karşıladıklarım
CREATE INDEX ix_requests_student     ON requests (student_id, created_at);
CREATE INDEX ix_requests_status      ON requests (status, created_at);
CREATE INDEX ix_requests_fulfilled_by ON requests (fulfilled_by_id);

-- Bağışlar: açık bağışlar listesi ve bağışlarım
CREATE INDEX ix_donations_status ON donations (status, created_at);
CREATE INDEX ix_donations_donor  ON donations (donor_id);

-- Takas: gelen/giden teklifler ve açık takas kitapları
CREATE INDEX ix_swap_offers_to   ON swap_offers (to_user_id, created_at);
CREATE INDEX ix_swap_offers_from ON swap_offers (from_user_id, created_at);
CREATE INDEX ix_swap_books_status ON swap_books (status, created_at);
