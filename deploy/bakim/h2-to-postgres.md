# H2 Dosya Veritabanından PostgreSQL'e Veri Aktarım Rehberi

KİTAPLA üretim ortamında H2 dosya veritabanından PostgreSQL'e geçiş adımları aşağıda özetlenmiştir.

---

## 1. Hazırlık ve Uygulamayı Durdurma

Veri tutarlılığını sağlamak için eski H2 dosyasını kullanan uygulamayı durdurun:

```bash
# Docker kullanıyorsanız:
docker compose -f docker-compose.prod.yml stop kitapla

# veya systemd kullanıyorsanız:
sudo systemctl stop kitapla
```

---

## 2. H2 Verisini Dışa Aktarma (Export)

H2 araçları ile verileri salt INSERT ifadeleri olarak dışa aktarın:

```bash
java -cp h2-*.jar org.h2.tools.Script \
  -url "jdbc:h2:file:/opt/kitapla/data/kitapla;AUTO_SERVER=TRUE" \
  -user sa -password "" \
  -script /tmp/h2-export.sql \
  -options NODATA=FALSE DROP=FALSE
```

*(Yalnızca INSERT ifadelerini içeren temiz bir SQL dosyası elde etmek için tablo bazlı `TABLE users, books, ...` seçilebilir veya DBeaver / pgloader kullanılabilir.)*

---

## 3. PostgreSQL Şemasını Başlatma

Yeni yapılandırmada Flyway başlangıç şemasını (`V1__init_schema.sql`) otomatik uygular.
PostgreSQL konteynerini veya servisini başlatın:

```bash
# Docker Compose ile:
docker compose -f docker-compose.prod.yml up -d postgres
```

Uygulamayı bir kez başlatarak Flyway'in tabloları ve kısıtları oluşturmasını sağlayın:

```bash
docker compose -f docker-compose.prod.yml up -d kitapla
```

---

## 4. Verileri PostgreSQL'e Aktarma (Import)

Bağımlılık sırasına göre verileri PostgreSQL'e aktarın:
1. `users`
2. `books`
3. `pickup_points`
4. `donations`
5. `claims`
6. `requests`
7. `swap_books`
8. `swap_offers`
9. `conversations`
10. `messages`
11. `notifications`
12. `reports`
13. `auth_tokens`

```bash
docker compose -f docker-compose.prod.yml exec -T postgres psql -U kitapla -d kitapla < /tmp/aktarilacak_veriler.sql
```

---

## 5. Sıra (Sequence / Identity) Sayaçlarını Güncelleme

Manuel ID içeren satırlar aktarıldıktan sonra PostgreSQL identity sayaçlarını mevcut en büyük ID değerine çekin:

```sql
SELECT setval(pg_get_serial_sequence('users', 'id'), COALESCE(MAX(id), 1)) FROM users;
SELECT setval(pg_get_serial_sequence('books', 'id'), COALESCE(MAX(id), 1)) FROM books;
SELECT setval(pg_get_serial_sequence('pickup_points', 'id'), COALESCE(MAX(id), 1)) FROM pickup_points;
SELECT setval(pg_get_serial_sequence('donations', 'id'), COALESCE(MAX(id), 1)) FROM donations;
SELECT setval(pg_get_serial_sequence('claims', 'id'), COALESCE(MAX(id), 1)) FROM claims;
SELECT setval(pg_get_serial_sequence('requests', 'id'), COALESCE(MAX(id), 1)) FROM requests;
SELECT setval(pg_get_serial_sequence('swap_books', 'id'), COALESCE(MAX(id), 1)) FROM swap_books;
SELECT setval(pg_get_serial_sequence('swap_offers', 'id'), COALESCE(MAX(id), 1)) FROM swap_offers;
SELECT setval(pg_get_serial_sequence('conversations', 'id'), COALESCE(MAX(id), 1)) FROM conversations;
SELECT setval(pg_get_serial_sequence('messages', 'id'), COALESCE(MAX(id), 1)) FROM messages;
SELECT setval(pg_get_serial_sequence('notifications', 'id'), COALESCE(MAX(id), 1)) FROM notifications;
SELECT setval(pg_get_serial_sequence('reports', 'id'), COALESCE(MAX(id), 1)) FROM reports;
SELECT setval(pg_get_serial_sequence('auth_tokens', 'id'), COALESCE(MAX(id), 1)) FROM auth_tokens;
```

---

## 6. Doğrulama ve Canlıya Alma

Uygulamanın sağlık kontrolünü ve web arayüzünü test edin:

```bash
docker compose -f docker-compose.prod.yml logs -f kitapla
curl -I https://alanadin.com/saglik
```
