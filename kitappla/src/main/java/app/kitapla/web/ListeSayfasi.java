package app.kitapla.web;

import org.springframework.data.domain.PageRequest;

/** HTML liste sayfalarında (keşfet, takas, istekler) bir seferde gösterilen kayıt sayısı. */
final class ListeSayfasi {

    static final int BOYUT = 24;

    private ListeSayfasi() {
    }

    static PageRequest of(int sayfa) {
        return PageRequest.of(Math.max(sayfa, 0), BOYUT);
    }
}
