package app.kitappla.domain;

/**
 * Bağlantı jetonu türleri. {@code EMAIL_VERIFY} okul (.edu.tr) adresini, {@code ACCOUNT_VERIFY}
 * hesabın giriş e-postasını doğrular.
 */
public enum TokenType { PASSWORD_RESET, EMAIL_VERIFY, ACCOUNT_VERIFY }
