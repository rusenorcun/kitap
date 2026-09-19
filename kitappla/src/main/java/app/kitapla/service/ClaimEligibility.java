package app.kitapla.service;

/**
 * Bir kullanıcının belirli bir bağıştan kitap alıp alamayacağı.
 * code: makine tarafı ayrım için (LOGIN_REQUIRED, ADDRESS_REQUIRED, PRIORITY_WINDOW, ...)
 */
public record ClaimEligibility(boolean allowed, String code, String reason) {

    public static ClaimEligibility ok() {
        return new ClaimEligibility(true, "OK", null);
    }

    public static ClaimEligibility deny(String code, String reason) {
        return new ClaimEligibility(false, code, reason);
    }

    public boolean isAllowed() { return allowed; }
    public String getCode() { return code; }
    public String getReason() { return reason; }
}
