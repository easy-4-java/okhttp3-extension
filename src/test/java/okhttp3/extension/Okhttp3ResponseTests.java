package okhttp3.extension;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Okhttp3ResponseTests {

    @Test
    void shouldIdentifySuccessfulStatus() {
        Okhttp3Response response = new Okhttp3Response();
        response.setCode(200);
        assertTrue(response.isSuccess());
        response.setCode(500);
        assertFalse(response.isSuccess());
    }
}
