package app.kitappla.api.dto;

/** Hesap e-postası onayının sonucu: {@code DOGRULANDI}, {@code ZATEN_DOGRULANMIS} ya da {@code GECERSIZ}. */
public record EmailVerificationDto(
        String status
) {}
