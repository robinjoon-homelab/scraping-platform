import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/** Build-time mitigation and independent runtime checks; never installed as a trust manager. */
public final class RuntimeTrust {
    // Oracle's 2026-10-24 guidance: https://ops.java/emudhra
    // Fingerprints: https://repository.emsign.com/ and the incident publisher's roots.json.
    private static final Map<String, String> ROOTS = Map.of(
        "40F6AF0346A99AA1CD1D555A4E9CCE62C7F9634603EE406615833DC8C8D00367", "emSign Root CA - G1",
        "1AA0C2709E831BD6E3B5129A00BA41F7EEEF020872F1E6504BF0F6C3F24F3AF3", "emSign Root CA - G2",
        "86A1ECBA089C4A8D3BBE2734C612BA341D813E043CF9E8A862CD5C57A36BBE6B", "emSign ECC Root CA - G3",
        "D7A7A0FB5D7E2731D771E9484EBCDEF71D5F0C3E0A2948782BC83EE0EA699EF4", "AAA Certificate Services"
    );

    private static String fingerprint(X509Certificate certificate) throws Exception {
        return HexFormat.of().withUpperCase().formatHex(
            MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
    }

    private static boolean affected(X509Certificate certificate) throws Exception {
        return ROOTS.containsKey(fingerprint(certificate));
    }

    private static Collection<X509Certificate> certificates(Path path) throws Exception {
        try (InputStream input = Files.newInputStream(path)) {
            var certificates = CertificateFactory.getInstance("X.509").generateCertificates(input);
            if (certificates.isEmpty()) {
                throw new IllegalStateException("Empty certificate file: " + path);
            }
            return certificates.stream().map(c -> (X509Certificate) c).toList();
        }
    }

    private static X509TrustManager trustManager() throws Exception {
        var factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        var managers = Arrays.stream(factory.getTrustManagers())
            .filter(X509TrustManager.class::isInstance).map(X509TrustManager.class::cast).toList();
        if (managers.size() != 1 || managers.getFirst().getAcceptedIssuers().length == 0) {
            throw new IllegalStateException("Expected a nonempty default X509 trust manager");
        }
        return managers.getFirst();
    }

    private static void requireSafe(Collection<X509Certificate> certificates, String store) throws Exception {
        if (certificates.isEmpty()) {
            throw new IllegalStateException("Empty trust store: " + store);
        }
        for (var certificate : certificates) {
            if (affected(certificate)) {
                throw new IllegalStateException("Affected trust anchor in " + store + ": "
                    + certificate.getSubjectX500Principal());
            }
        }
    }

    private static void verifyRoots() throws Exception {
        requireSafe(Arrays.asList(trustManager().getAcceptedIssuers()), "default JSSE");
        requireSafe(certificates(Path.of("/etc/ssl/certs/ca-certificates.crt")), "OS bundle");
        // Check hashed-directory consumers too, including stale links after bundle regeneration.
        try (var paths = Files.list(Path.of("/etc/ssl/certs"))) {
            for (var path : paths.filter(Files::isRegularFile).toList()) {
                if (path.getFileName().toString().matches(".*\\.pem|[0-9a-f]+\\.[0-9]+")) {
                    requireSafe(certificates(path), path.toString());
                }
            }
        }
        System.out.println("PASS: affected roots absent from default JSSE and OS trust");
    }

    private static void sanitize() throws Exception {
        Path path = Path.of(System.getProperty("java.home"), "lib", "security", "cacerts");
        char[] password = "changeit".toCharArray();
        var store = KeyStore.getInstance(path.toFile(), password);
        if (store.size() == 0) {
            throw new IllegalStateException("Empty JRE trust store");
        }
        for (String alias : Collections.list(store.aliases())) {
            if (affected((X509Certificate) store.getCertificate(alias))) {
                store.deleteEntry(alias);
                System.out.println("Removed JRE alias: " + alias);
            }
        }
        try (var output = Files.newOutputStream(path)) {
            store.store(output, password);
        }
        // Remove source files as well, so update-ca-certificates cannot reintroduce them.
        for (String directory : new String[]{"/usr/share/ca-certificates", "/usr/local/share/ca-certificates"}) {
            try (var paths = Files.walk(Path.of(directory))) {
                for (var file : paths.filter(p -> p.toString().endsWith(".crt")).toList()) {
                    var certs = certificates(file);
                    for (var certificate : certs) {
                        if (affected(certificate)) {
                            if (certs.size() != 1) {
                                throw new IllegalStateException("Refusing to remove mixed CA bundle: " + file);
                            }
                            Files.delete(file);
                            System.out.println("Removed OS certificate: " + file);
                        }
                    }
                }
            }
        }
        int status = new ProcessBuilder("update-ca-certificates", "--fresh").inheritIO().start().waitFor();
        if (status != 0) {
            throw new IllegalStateException("update-ca-certificates failed: " + status);
        }
    }

    private static void verifyChains(Path directory) throws Exception {
        var manager = trustManager();
        try (var paths = Files.list(directory)) {
            var chains = paths.filter(p -> p.toString().endsWith(".pem")).sorted().toList();
            if (chains.size() != 14) {
                throw new IllegalStateException("Expected all 14 compromised TLS chain fixtures");
            }
            for (var path : chains) {
                var chain = certificates(path).toArray(X509Certificate[]::new);
                // A corrupt or expired fixture must not masquerade as a successful distrust check.
                for (int index = 0; index < chain.length; index++) {
                    chain[index].checkValidity(java.util.Date.from(java.time.Instant.parse("2026-10-24T00:00:00Z")));
                    if (index + 1 < chain.length) {
                        chain[index].verify(chain[index + 1].getPublicKey());
                    }
                }
                try {
                    String authType = chain[0].getPublicKey().getAlgorithm().equals("EC") ? "ECDHE_ECDSA" : "RSA";
                    manager.checkServerTrusted(chain, authType);
                } catch (CertificateException rejected) {
                    System.out.println("PASS: rejected " + path.getFileName() + ": " + rejected.getMessage());
                    continue;
                }
                throw new IllegalStateException("Compromised chain accepted: " + path);
            }
        }
    }

    private static void verifyHttps() throws Exception {
        var connection = (HttpsURLConnection) java.net.URI.create("https://example.com/").toURL().openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        try {
            if (connection.getResponseCode() != 200) {
                throw new IllegalStateException("Public HTTPS smoke check did not return 200");
            }
            System.out.println("PASS: public HTTPS with default certificate and hostname validation");
        } finally {
            connection.disconnect();
        }
    }

    public static void main(String[] args) throws Exception {
        if (Runtime.version().feature() != 25) {
            throw new IllegalStateException("JRE 25 required");
        }
        switch (args[0]) {
            case "sanitize" -> sanitize();
            case "roots" -> verifyRoots();
            case "chains" -> verifyChains(Path.of(args[1]));
            case "https" -> verifyHttps();
            default -> throw new IllegalArgumentException("Unknown mode: " + args[0]);
        }
    }
}
