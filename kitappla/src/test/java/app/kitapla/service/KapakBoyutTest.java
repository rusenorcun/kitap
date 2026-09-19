package app.kitapla.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sıkıştırma bombası: birkaç KB'lık PNG dev boyut bildirir. Görsel çözülmeden reddedilmeli;
 * çözülseydi bellek taşar ve süreç ExitOnOutOfMemoryError ile kapanırdı.
 */
class KapakBoyutTest {

    @TempDir Path dir;

    @Test
    void devBoyutBildirenPngReddedilir() throws IOException {
        Path bomba = dir.resolve("bomba.png");
        Files.write(bomba, png(50_000, 50_000));

        assertThat(CoverService.gercektenGorsel(bomba)).isTrue();
        assertThat(CoverService.boyutUygun(bomba)).isFalse();
    }

    @Test
    void normalKapakKabulEdilir() throws IOException {
        Path kapak = dir.resolve("kapak.png");
        ImageIO.write(new BufferedImage(800, 1200, BufferedImage.TYPE_INT_RGB), "png", kapak.toFile());

        assertThat(CoverService.boyutUygun(kapak)).isTrue();
    }

    /** Geçerli başlıklı, çok küçük gövdeli PNG (tek sıkıştırılmış satır yeter; başlık okunur). */
    private static byte[] png(int w, int h) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(ihdr);
        d.writeInt(w);
        d.writeInt(h);
        d.write(new byte[]{8, 0, 0, 0, 0});   // 8 bit, gri tonlama
        parca(out, "IHDR", ihdr.toByteArray());
        Deflater deflater = new Deflater();
        deflater.setInput(new byte[w + 1]);
        deflater.finish();
        byte[] buf = new byte[1024];
        int n = deflater.deflate(buf);
        byte[] idat = new byte[n];
        System.arraycopy(buf, 0, idat, 0, n);
        parca(out, "IDAT", idat);
        parca(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void parca(ByteArrayOutputStream out, String tur, byte[] veri) throws IOException {
        DataOutputStream d = new DataOutputStream(out);
        d.writeInt(veri.length);
        byte[] t = tur.getBytes(StandardCharsets.US_ASCII);
        d.write(t);
        d.write(veri);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(veri);
        d.writeInt((int) crc.getValue());
    }
}
