package okhttp3.extension.ssl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.Principal;
import java.security.cert.Certificate;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SslUtilitiesTests {

    @Test
    void shouldValidateArgumentsAndPrivateKeyDetails() {
        Args.check(true, "ok");
        Args.check(true, "%s", "ok");
        Args.check(true, "%s", new Object[]{"ok"});
        assertThrows(IllegalArgumentException.class, () -> Args.check(false, "bad"));
        assertThrows(IllegalArgumentException.class, () -> Args.check(false, "bad %s", "value"));
        assertThrows(IllegalArgumentException.class, () -> Args.notNull(null, "value"));
        assertSame("x", Args.notNull("x", "value"));
        assertEquals(Collections.singletonList("x"), Args.notEmpty(Collections.singletonList("x"), "items"));
        assertThrows(IllegalArgumentException.class, () -> Args.notEmpty(null, "items"));
        assertThrows(IllegalArgumentException.class, () -> Args.notEmpty(Collections.emptyList(), "items"));
        assertEquals(1, Args.positive(1, "n"));
        assertEquals(1L, Args.positive(1L, "n"));
        assertThrows(IllegalArgumentException.class, () -> Args.positive(0, "n"));
        assertThrows(IllegalArgumentException.class, () -> Args.positive(0L, "n"));
        assertEquals(0, Args.notNegative(0, "n"));
        assertEquals(0L, Args.notNegative(0L, "n"));
        assertThrows(IllegalArgumentException.class, () -> Args.notNegative(-1, "n"));
        assertThrows(IllegalArgumentException.class, () -> Args.notNegative(-1L, "n"));

        X509Certificate certificate = mock(X509Certificate.class);
        PrivateKeyDetails details = new PrivateKeyDetails("RSA", new X509Certificate[]{certificate});
        assertEquals("RSA", details.getType());
        assertArrayEquals(new X509Certificate[]{certificate}, details.getCertChain());
        assertTrue(details.toString().startsWith("RSA:"));
    }

    @Test
    void shouldBuildContextsFromStoresFilesAndUrls(@TempDir Path tempDir) throws Exception {
        char[] password = "changeit".toCharArray();
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, password);
        File file = tempDir.resolve("empty.p12").toFile();
        try (FileOutputStream output = new FileOutputStream(file)) {
            store.store(output, password);
        }

        SSLContextBuilder builder = SSLContextBuilder.create()
                .useProtocol("TLS")
                .setProtocol("TLS")
                .setSecureRandom(new SecureRandom())
                .setProvider(SSLContext.getDefault().getProvider())
                .setProvider(SSLContext.getDefault().getProvider().getName())
                .setKeyStoreType(KeyStore.getDefaultType())
                .setKeyManagerFactoryAlgorithm(null)
                .setTrustManagerFactoryAlgorithm(null)
                .loadTrustMaterial(store, (chain, authType) -> true)
                .loadTrustMaterial((TrustStrategy) null)
                .loadTrustMaterial(file, password)
                .loadTrustMaterial(file)
                .loadTrustMaterial(file.toURI().toURL(), password)
                .loadKeyMaterial(store, password)
                .loadKeyMaterial(store, password, (aliases, socket) -> aliases.keySet().stream().findFirst().orElse(null))
                .loadKeyMaterial(file, password, password)
                .loadKeyMaterial(file, password, password, (aliases, socket) -> null)
                .loadKeyMaterial(file.toURI().toURL(), password, password)
                .loadKeyMaterial(file.toURI().toURL(), password, password, (aliases, socket) -> null);

        SSLContext context = builder.build();
        assertNotNull(context.getSocketFactory());
        assertTrue(builder.toString().contains("protocol=TLS"));
        assertNotNull(SSLContexts.createDefault());
        assertNotNull(SSLContexts.createSystemDefault());
        assertNotNull(SSLContexts.createSSLContext("TLS", (javax.net.ssl.KeyManager) null,
                (javax.net.ssl.TrustManager) null));
        assertNotNull(SSLContexts.createSSLContext("TLS", null, null, new SecureRandom()));
        assertNotNull(SSLContexts.createSSLContext(store, (chain, authType) -> true));
        assertNotNull(SSLContexts.createSSLContext("TLS", file, "changeit", (chain, authType) -> true));
        assertNotNull(SSLContexts.custom());
        assertThrows(IOException.class, () -> SSLContexts.createSSLContext("not-a-protocol",
                (javax.net.ssl.KeyManager) null, (javax.net.ssl.TrustManager) null));
        assertThrows(IOException.class, () -> SSLContexts.createSSLContext("not-a-protocol",
                (javax.net.ssl.KeyManager[]) null,
                (javax.net.ssl.TrustManager[]) null,
                new SecureRandom()));
        assertThrows(IOException.class, () -> SSLContexts.createSSLContext(
                "not-a-protocol", file, "changeit", (chain, authType) -> true));
        assertThrows(java.security.KeyStoreException.class,
                () -> KeyManagerUtils.createClientKeyManager(file, "changeit", null));
        assertThrows(java.security.KeyStoreException.class,
                () -> KeyManagerUtils.createClientKeyManager(file, "changeit"));

        try (SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket()) {
            assertTrue(SSLSocketUtils.enableEndpointNameVerification(socket));
        }
    }

    @Test
    void shouldDelegateTrustAndKeySelection() throws Exception {
        X509TrustManager trustManager = mock(X509TrustManager.class);
        X509Certificate certificate = mock(X509Certificate.class);
        when(trustManager.getAcceptedIssuers()).thenReturn(new X509Certificate[]{certificate});
        SSLContextBuilder.TrustManagerDelegate trusted = new SSLContextBuilder.TrustManagerDelegate(
                trustManager, (chain, authType) -> true);
        trusted.checkClientTrusted(new X509Certificate[]{certificate}, "RSA");
        trusted.checkServerTrusted(new X509Certificate[]{certificate}, "RSA");
        assertArrayEquals(new X509Certificate[]{certificate}, trusted.getAcceptedIssuers());
        verify(trustManager).checkClientTrusted(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("RSA"));

        SSLContextBuilder.TrustManagerDelegate fallback = new SSLContextBuilder.TrustManagerDelegate(
                trustManager, (chain, authType) -> false);
        fallback.checkServerTrusted(new X509Certificate[]{certificate}, "RSA");
        verify(trustManager).checkServerTrusted(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("RSA"));

        X509ExtendedKeyManager keyManager = mock(X509ExtendedKeyManager.class);
        PrivateKey privateKey = mock(PrivateKey.class);
        when(keyManager.getClientAliases("RSA", null)).thenReturn(new String[]{"client"});
        when(keyManager.getServerAliases("RSA", null)).thenReturn(new String[]{"server"});
        when(keyManager.getCertificateChain("client")).thenReturn(new X509Certificate[]{certificate});
        when(keyManager.getCertificateChain("server")).thenReturn(new X509Certificate[]{certificate});
        when(keyManager.getPrivateKey("client")).thenReturn(privateKey);
        SSLContextBuilder.KeyManagerDelegate delegate = new SSLContextBuilder.KeyManagerDelegate(
                keyManager, (aliases, socket) -> aliases.keySet().stream().findFirst().orElse(null));

        assertArrayEquals(new String[]{"client"}, delegate.getClientAliases("RSA", null));
        assertEquals("client", delegate.chooseClientAlias(new String[]{"RSA"}, null, new Socket()));
        assertArrayEquals(new String[]{"server"}, delegate.getServerAliases("RSA", null));
        assertEquals("server", delegate.chooseServerAlias("RSA", null, new Socket()));
        assertArrayEquals(new X509Certificate[]{certificate}, delegate.getCertificateChain("client"));
        assertSame(privateKey, delegate.getPrivateKey("client"));
        assertEquals("client", delegate.chooseEngineClientAlias(new String[]{"RSA"}, null, mock(SSLEngine.class)));
        assertEquals("server", delegate.chooseEngineServerAlias("RSA", null, mock(SSLEngine.class)));
        assertTrue(delegate.getClientAliasMap(new String[]{"NONE"}, null).isEmpty());
        assertTrue(delegate.getServerAliasMap("NONE", null).isEmpty());
    }

    @Test
    void shouldProvideTrustManagersAndCloseQuietly() throws Exception {
        X509TrustManager acceptAll = TrustManagerUtils.getAcceptAllTrustManager();
        acceptAll.checkClientTrusted(new X509Certificate[0], "RSA");
        acceptAll.checkServerTrusted(new X509Certificate[0], "RSA");
        assertEquals(0, acceptAll.getAcceptedIssuers().length);
        X509Certificate certificate = mock(X509Certificate.class);
        TrustManagerUtils.getValidateServerCertificateTrustManager()
                .checkServerTrusted(new X509Certificate[]{certificate}, "RSA");
        verify(certificate).checkValidity();
        assertTrue(TrustManagerUtils.getAcceptAllHostnameVerifier().verify("localhost", null));
        assertTrue(new TrustAllHostnameVerifier().verify("localhost", null));
        assertNotNull(TrustManagerUtils.getDefaultTrustManager(null));

        KeyStore empty = KeyStore.getInstance(KeyStore.getDefaultType());
        empty.load(null, "changeit".toCharArray());
        assertThrows(java.security.KeyStoreException.class,
                () -> KeyManagerUtils.createClientKeyManager(empty, null, "changeit"));
        AtomicBoolean closed = new AtomicBoolean();
        KeyManagerUtils.closeQuietly((Closeable) () -> closed.set(true));
        assertTrue(closed.get());
        KeyManagerUtils.closeQuietly((Closeable) () -> { throw new IOException("ignored"); });
        KeyManagerUtils.closeQuietly(null);
        SSLInitializationException exception = new SSLInitializationException("failed", new IOException("cause"));
        assertEquals("failed", exception.getMessage());
        assertNotNull(exception.getCause());
    }

    @Test
    void shouldCreateAndUseClientKeyManagerFromKeyStore() throws Exception {
        KeyStore keyStore = mock(KeyStore.class);
        PrivateKey privateKey = mock(PrivateKey.class);
        X509Certificate certificate = mock(X509Certificate.class);
        when(keyStore.aliases()).thenReturn(Collections.enumeration(Collections.singletonList("client")));
        when(keyStore.isKeyEntry("client")).thenReturn(true);
        when(keyStore.getKey("client", "secret".toCharArray())).thenReturn(privateKey);
        when(keyStore.getCertificateChain("client")).thenReturn(new Certificate[]{certificate});

        X509ExtendedKeyManager manager = (X509ExtendedKeyManager)
                KeyManagerUtils.createClientKeyManager(keyStore, null, "secret");
        assertEquals("client", manager.chooseClientAlias(new String[]{"RSA"}, new Principal[0], new Socket()));
        assertArrayEquals(new String[]{"client"}, manager.getClientAliases("RSA", new Principal[0]));
        assertArrayEquals(new X509Certificate[]{certificate}, manager.getCertificateChain("client"));
        assertSame(privateKey, manager.getPrivateKey("client"));
        assertNull(manager.getServerAliases("RSA", new Principal[0]));
        assertNull(manager.chooseServerAlias("RSA", new Principal[0], new Socket()));

        KeyStore empty = mock(KeyStore.class);
        when(empty.aliases()).thenReturn(Collections.emptyEnumeration());
        assertThrows(KeyStoreException.class,
                () -> KeyManagerUtils.createClientKeyManager(empty, null, "secret"));
    }

    /**
     * The default protocol advertised by {@link SSLContextBuilder} must pin to TLSv1.2 so
     * that consumers do not silently negotiate TLSv1.0/TLSv1.1 on JDK 8.
     */
    @Test
    void shouldDefaultToTlsV12ToAvoidDowngrade() {
        assertEquals("TLSv1.2", SSLContextBuilder.TLS);
    }

    /**
     * Both trust-all entry points on {@link TrustManagerUtils} and the standalone
     * {@link TrustAllHostnameVerifier} must carry {@code @Deprecated} so that static
     * analysis tooling flags any usage and reviewers immediately see the security warning.
     */
    @Test
    void shouldMarkTrustAllApisAsDeprecated() throws NoSuchMethodException {
        assertNotNull(TrustManagerUtils.class.getMethod("getAcceptAllTrustManager").getAnnotation(Deprecated.class),
                "getAcceptAllTrustManager must be @Deprecated");
        assertNotNull(TrustManagerUtils.class.getMethod("getAcceptAllHostnameVerifier").getAnnotation(Deprecated.class),
                "getAcceptAllHostnameVerifier must be @Deprecated");
        assertNotNull(TrustAllHostnameVerifier.class.getAnnotation(Deprecated.class),
                "TrustAllHostnameVerifier must be @Deprecated");
    }
}
