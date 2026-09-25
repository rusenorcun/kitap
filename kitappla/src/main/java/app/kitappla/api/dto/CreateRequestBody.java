package app.kitappla.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param coverUrl isteğe bağlı kapak: yükleme ucundan dönen yerel yol ({@code /uploads/covers/...}) ya da dış adres
 *                 (sunucu indirir); bağışta olduğu gibi {@code BookService.findOrCreate} doğrular.
 */
public record CreateRequestBody(
        @NotBlank String title,
        String author,
        String purchaseLink,
        String description,
        String coverUrl
) {
    public CreateRequestBody(String title, String author, String purchaseLink, String description) {
        this(title, author, purchaseLink, description, null);
    }
}
