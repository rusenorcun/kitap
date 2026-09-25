package app.kitappla.mail;

import app.kitappla.config.Marka;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * E-posta gönderimi. Sağlayıcıdan bağımsızdır: standart {@code spring.mail.*}
 * ayarlarını kullanır, dolayısıyla Resend, Brevo, Gmail, Yandex ya da kendi
 * Postfix sunucun fark etmeksizin aynı kodla çalışır.
 * <p>
 * Gönderen adresi, görünen ad ve bağlantı kökü {@link Marka}dan gelir;
 * {@code kitappla.mail.enabled=false} iken (yerel geliştirmenin varsayılanı)
 * hiçbir şey gönderilmez; ileti loglanır ve son 50 tanesi bellekte tutulur.
 * Böylece SMTP kurmadan tüm akışlar denenebilir.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final int OUTBOX_LIMIT = 50;

    private final JavaMailSender sender;
    private final TemplateEngine templates;
    private final boolean enabled;
    private final Marka marka;
    private final String from;
    private final String fromName;
    private final String baseUrl;
    private final TaskExecutor yurutucu;
    private final TransactionTemplate ayriIslem;

    /** Gönderim kapalıyken son iletiler burada tutulur (yerel deneme ve testler için). */
    private final Deque<MailMessage> outbox = new ConcurrentLinkedDeque<>();

    public MailService(JavaMailSender sender, TemplateEngine templates,
                       @Value("${kitappla.mail.enabled:false}") boolean enabled,
                       Marka marka,
                       @Qualifier(PostaYurutucuConfig.BEAN) TaskExecutor yurutucu,
                       PlatformTransactionManager islemYoneticisi) {
        this.yurutucu = yurutucu;
        this.ayriIslem = new TransactionTemplate(islemYoneticisi);
        this.ayriIslem.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.sender = sender;
        this.templates = templates;
        this.enabled = enabled;
        this.marka = marka;
        this.from = marka.gonderenAdres();
        this.fromName = marka.gonderenAd();
        this.baseUrl = marka.siteAdresi();
    }

    public String baseUrl() {
        return baseUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Bkz. {@link #send(String, String, String, Map, Runnable)}; teslim hatasında ek işlem yapılmaz. */
    public boolean send(String to, String konu, String template, Map<String, Object> model) {
        return send(to, konu, template, model, null);
    }

    /**
     * Şablonu hemen işler; SMTP teslimini veritabanı işlemi tamamlandıktan sonra arka planda yapar.
     * <p>
     * Teslim sağlayıcıya göre birkaç saniye sürer (Natro'da yalnızca bağlantı ~1,2 sn); istek içinde
     * yapıldığında şifre sıfırlama gibi sayfalar o süre boyunca bekliyordu. İşlemden sonra başlatılması,
     * geri alınan bir işlemin (ör. hiç kaydedilmemiş bir jetonun) bağlantısının postalanmasını da önler.
     *
     * @param teslimEdilemezse SMTP teslimi başarısız olursa arka planda çağrılır (ör. jetonu geçersiz kıl)
     * @return ileti teslim sırasına alındıysa true; posta kapalıysa ya da şablon işlenemediyse false
     */
    public boolean send(String to, String konu, String template, Map<String, Object> model,
                        Runnable teslimEdilemezse) {
        // Konu yalın gelir; marka öneki burada eklenir ki hiçbir çağıran marka adını sabit yazmasın.
        String subject = marka.epostaKonusu(konu);
        String html;
        try {
            Context ctx = new Context();
            ctx.setVariables(model);
            ctx.setVariable("baseUrl", baseUrl);
            ctx.setVariable("marka", marka);
            html = templates.process("mail/" + template, ctx);
        } catch (Exception ex) {
            log.error("E-posta şablonu işlenemedi ({}): {}", template, ex.getMessage());
            return false;
        }

        remember(new MailMessage(to, subject, html));

        if (!enabled) {
            log.info("[POSTA KAPALI] Gönderilecekti -> {} | {}", to, subject);
            return false;
        }

        Runnable teslim = () -> teslimEt(to, subject, html, teslimEdilemezse);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    yurutucu.execute(teslim);
                }
            });
        } else {
            yurutucu.execute(teslim);
        }
        return true;
    }

    private void teslimEt(String to, String subject, String html, Runnable teslimEdilemezse) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
            log.info("E-posta gönderildi -> {} | {}", to, subject);
        } catch (Exception ex) {
            log.error("E-posta gönderilemedi -> {} | {} : {}", to, subject, ex.getMessage());
            if (teslimEdilemezse != null) {
                try {
                    // Her zaman kendi işleminde: commit sonrası aşamada aynı iş parçacığında çalışırsa
                    // (ör. testlerde) kapanmış işleme katılıp yazdıkları kaydedilmezdi.
                    ayriIslem.executeWithoutResult(durum -> teslimEdilemezse.run());
                } catch (Exception geriAlmaHatasi) {
                    log.error("Teslim hatası sonrası işlem başarısız ({}): {}", to, geriAlmaHatasi.getMessage());
                }
            }
        }
    }

    private void remember(MailMessage m) {
        outbox.addLast(m);
        while (outbox.size() > OUTBOX_LIMIT) outbox.pollFirst();
    }

    /** Son iletiler (en yeni sonda). Testler ve yerel doğrulama için. */
    public List<MailMessage> outbox() {
        return List.copyOf(outbox);
    }

    public void clearOutbox() {
        outbox.clear();
    }
}
