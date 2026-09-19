package app.kitapla.service;

/** Alışveriş linkinden çekilen kitap üst verisi. Alanlar bulunamazsa null olur. */
public record BookMetadata(String title, String author, String imageUrl, String description) {

    public boolean isEmpty() {
        return title == null && author == null && imageUrl == null;
    }
}
