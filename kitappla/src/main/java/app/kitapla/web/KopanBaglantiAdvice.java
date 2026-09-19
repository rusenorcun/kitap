package app.kitapla.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.util.DisconnectedClientHelper;

import java.io.IOException;

/**
 * İstemci bağlantıyı kapattığında (sayfa değişti, sekme kapandı, uygulama arka plana geçti)
 * oluşan hataları sessizce kapatır.
 * <p>
 * Canlı akışta (SSE) her sayfa geçişi böyle bir kopuş üretir. Yakalanmadığında her biri tam
 * yığın iziyle ERROR olarak günlüğe yazılıyordu (yük testinde 3 dakikada ~9.000 satır).
 * Kopuşlar yalnızca DEBUG düzeyinde kaydedilir; gerçek G/Ç hataları olduğu gibi yukarı iletilir.
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class KopanBaglantiAdvice {

    private static final DisconnectedClientHelper ISTEMCI =
            new DisconnectedClientHelper(KopanBaglantiAdvice.class.getName());

    @ExceptionHandler({AsyncRequestNotUsableException.class, IOException.class})
    public void kopanBaglanti(Exception ex) throws Exception {
        if (!ISTEMCI.checkAndLogClientDisconnectedException(ex)) {
            throw ex;
        }
    }
}
