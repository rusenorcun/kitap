# --- 1. aşama: derleme ---
# Kaynak koddan jar üretilir. Bu aşama son imaja dahil edilmez;
# böylece Maven, JDK ve kaynak kod sunucuya hiç gitmez.
FROM maven:3.9-eclipse-temurin-21 AS derleme
WORKDIR /derleme

# Önce yalnızca pom.xml kopyalanır: kaynak kod değişse de bağımlılıklar
# yeniden indirilmez, katman önbelleğinden gelir.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# --- 2. aşama: çalıştırma ---
# Yalnızca JRE: imaj küçük kalır ve derleme araçları saldırı yüzeyine girmez.
FROM eclipse-temurin:21-jre-alpine

# Sağlık kontrolü ve Türkçe karakterler için
RUN apk add --no-cache curl tzdata

# Uygulama root olarak çalışmaz
RUN addgroup -S kitapla && adduser -S -G kitapla -h /uygulama kitapla
WORKDIR /uygulama

COPY --from=derleme /derleme/target/kitapla-*.jar uygulama.jar

# Veritabanı ve yüklenen dosyalar; compose bunlara kalıcı birim bağlar.
RUN mkdir -p /uygulama/data /uygulama/uploads && chown -R kitapla:kitapla /uygulama
USER kitapla

ENV LANG=C.UTF-8 \
    TZ=Europe/Istanbul \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 8080

HEALTHCHECK --interval=20s --timeout=4s --start-period=60s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8080/saglik || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar uygulama.jar"]
