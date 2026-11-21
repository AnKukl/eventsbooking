package com.example.eventsbooking.storage;

import com.example.eventsbooking.support.TestImages;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageTypeTest {

    @Test
    void theTypeComesFromTheSignatureOfTheContent() {
        assertThat(ImageType.detect(TestImages.jpeg())).contains(ImageType.JPEG);
        assertThat(ImageType.detect(TestImages.png())).contains(ImageType.PNG);
        assertThat(ImageType.detect(TestImages.webp())).contains(ImageType.WEBP);
    }

    @Test
    void everythingElseIsNotAnImage() {
        assertThat(ImageType.detect(TestImages.text())).isEmpty();
        assertThat(ImageType.detect(TestImages.exe())).isEmpty();
        assertThat(ImageType.detect(TestImages.gif())).isEmpty();
        assertThat(ImageType.detect(TestImages.wav())).as("RIFF alone is not WebP").isEmpty();
        assertThat(ImageType.detect(new byte[0])).isEmpty();
        assertThat(ImageType.detect(new byte[] {(byte) 0xFF, (byte) 0xD8})).as("too short for a JPEG signature").isEmpty();
        assertThat(ImageType.detect("%PDF-1.7".getBytes())).isEmpty();
        assertThat(ImageType.detect("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes())).isEmpty();
    }

    @Test
    void theClaimedTypeComesFromTheExtensionOnly() {
        assertThat(ImageType.fromFileName("photo.jpg")).contains(ImageType.JPEG);
        assertThat(ImageType.fromFileName("photo.JPEG")).contains(ImageType.JPEG);
        assertThat(ImageType.fromFileName("a.b.png")).contains(ImageType.PNG);
        assertThat(ImageType.fromFileName("cover.webp")).contains(ImageType.WEBP);
    }

    @Test
    void unknownExtensionsAndTrickyNamesAreNotClaimedAsImages() {
        assertThat(ImageType.fromFileName("anim.gif")).isEmpty();
        assertThat(ImageType.fromFileName("logo.svg")).isEmpty();
        assertThat(ImageType.fromFileName("noextension")).isEmpty();
        assertThat(ImageType.fromFileName("photo.png.exe")).isEmpty();
        assertThat(ImageType.fromFileName("photo.jpg ")).isEmpty();
        assertThat(ImageType.fromFileName(null)).isEmpty();
        assertThat(ImageType.fromFileName("")).isEmpty();
    }

    @Test
    void eachTypeKnowsItsContentTypeAndExtension() {
        assertThat(ImageType.JPEG.contentType()).isEqualTo("image/jpeg");
        assertThat(ImageType.JPEG.extension()).isEqualTo("jpg");
        assertThat(ImageType.PNG.contentType()).isEqualTo("image/png");
        assertThat(ImageType.WEBP.contentType()).isEqualTo("image/webp");
        assertThat(ImageType.fromStoredName("0d9f2f5e-7c63-4c1f-9a60-0a3f7d1c7c11.webp")).contains(ImageType.WEBP);
    }
}
