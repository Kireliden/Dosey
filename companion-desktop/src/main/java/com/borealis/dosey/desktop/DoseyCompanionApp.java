package com.borealis.dosey.desktop;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;

/**
 * First desktop Companion slice: explicit certificate pinning, pairing and authenticated snapshot read.
 * The pin must be copied from the Android Companion screen; the client never trusts a certificate silently.
 */
public final class DoseyCompanionApp {
    private final JFrame frame = new JFrame("Dosey Companion");
    private final JTextField hostField = new JTextField("192.168.1.1", 16);
    private final JTextField portField = new JTextField("443", 6);
    private final JTextField fingerprintField = new JTextField(48);
    private final JTextField clientNameField = new JTextField(System.getProperty("user.name", "My PC"), 20);
    private final JButton connectButton = new JButton("Verify certificate & connect");
    private final JButton pairButton = new JButton("Request pairing");
    private final JButton snapshotButton = new JButton("Refresh snapshot");
    private final JLabel statusLabel = new JLabel("Not connected");
    private final JTextArea outputArea = new JTextArea();
    private volatile PinnedHttpClient client;
    private volatile String baseUrl;
    private volatile String credential;
    private volatile String requestId;

    private DoseyCompanionApp() {
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(760, 560));
        frame.setSize(900, 680);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        root.setBackground(new Color(30, 32, 48));

        JLabel title = new JLabel("Dosey Companion");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 25f));
        title.setForeground(new Color(202, 158, 230));
        JLabel subtitle = new JLabel("Secure local connection to your Android Dosey app");
        subtitle.setForeground(new Color(198, 208, 245));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(title);
        header.add(Box.createVerticalStrut(5));
        header.add(subtitle);

        JPanel connection = new JPanel(new GridBagLayout());
        connection.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0; c.gridy = 0; connection.add(label("Android IP / host"), c);
        c.gridx = 1; c.weightx = 1; connection.add(hostField, c);
        c.gridx = 2; c.weightx = 0; connection.add(label("Port"), c);
        c.gridx = 3; connection.add(portField, c);
        c.gridx = 0; c.gridy = 1; connection.add(label("TLS SHA-256 fingerprint"), c);
        c.gridx = 1; c.gridwidth = 3; c.weightx = 1; connection.add(fingerprintField, c);
        c.gridx = 0; c.gridy = 2; c.gridwidth = 1; c.weightx = 0; connection.add(label("Computer name"), c);
        c.gridx = 1; c.gridwidth = 3; c.weightx = 1; connection.add(clientNameField, c);

        connectButton.addActionListener(e -> connect());
        pairButton.addActionListener(e -> requestPairing());
        snapshotButton.addActionListener(e -> refreshSnapshot());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        actions.setOpaque(false);
        actions.add(connectButton);
        actions.add(pairButton);
        actions.add(snapshotButton);

        outputArea.setEditable(false);
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        outputArea.setBackground(new Color(41, 44, 60));
        outputArea.setForeground(new Color(198, 208, 245));
        outputArea.setBorder(new EmptyBorder(12, 12, 12, 12));

        statusLabel.setForeground(new Color(166, 189, 230));
        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setOpaque(false);
        top.add(header, BorderLayout.NORTH);
        JPanel fields = new JPanel(new BorderLayout(0, 8));
        fields.setOpaque(false);
        fields.add(connection, BorderLayout.CENTER);
        fields.add(actions, BorderLayout.SOUTH);
        top.add(fields, BorderLayout.CENTER);

        root.add(top, BorderLayout.NORTH);
        root.add(new JScrollPane(outputArea), BorderLayout.CENTER);
        root.add(statusLabel, BorderLayout.SOUTH);
        frame.setContentPane(root);
        append("Before connecting, enable Companion on Android and copy its TLS SHA-256 fingerprint here.\n"
            + "Use the Android device's LAN IP and the discovered service port. The certificate is never trusted automatically.\n");
        pairButton.setEnabled(false);
        snapshotButton.setEnabled(false);
    }

    private JLabel label(String value) {
        JLabel label = new JLabel(value);
        label.setForeground(new Color(198, 208, 245));
        return label;
    }

    private void connect() {
        String host = hostField.getText().trim();
        String fingerprint = normaliseFingerprint(fingerprintField.getText());
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535 || host.isBlank() || fingerprint.length() != 64) {
                throw new IllegalArgumentException();
            }
        } catch (Exception ex) {
            showError("Enter a valid host, port, and 64-hex-character SHA-256 fingerprint copied from Android.");
            return;
        }

        setBusy(true, "Verifying the pinned Android certificate…");
        CompletableFuture.runAsync(() -> {
            try {
                X509TrustManager trustManager = new PinningTrustManager(fingerprint);
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[]{trustManager}, null);
                PinnedHttpClient newClient = new PinnedHttpClient(sslContext);
                String url = "https://" + (host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host) + ":" + port;
                PinnedHttpClient.Response response = newClient.send(url + "/v1/status", "GET", null, null);
                if (response.statusCode() != 200) throw new IOException("Android returned HTTP " + response.statusCode());
                JSONObject status = new JSONObject(response.body());
                if (status.optInt("protocolVersion", -1) != 1) throw new IOException("Unsupported Companion protocol version");
                client = newClient;
                baseUrl = url;
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Certificate verified · " + status.optString("status", "connected"));
                    pairButton.setEnabled(true);
                    snapshotButton.setEnabled(credential != null);
                    setBusy(false, null);
                    append("TLS certificate pin matched. Protocol v" + status.optInt("protocolVersion")
                        + " · server time " + status.optLong("serverTime") + "\n");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    client = null;
                    baseUrl = null;
                    pairButton.setEnabled(false);
                    snapshotButton.setEnabled(false);
                    setBusy(false, null);
                    showError("Connection failed: " + safeMessage(ex));
                });
            }
        });
    }

    private void requestPairing() {
        PinnedHttpClient current = client;
        String url = baseUrl;
        if (current == null || url == null) return;
        String name = clientNameField.getText().trim();
        if (name.isBlank() || name.length() > 80) {
            showError("Computer name must contain 1–80 characters.");
            return;
        }
        setBusy(true, "Requesting approval on Android…");
        CompletableFuture.runAsync(() -> {
            try {
                JSONObject request = new JSONObject().put("clientName", name);
                PinnedHttpClient.Response response = current.send(url + "/v1/pair/request", "POST", request.toString(), null);
                JSONObject body = new JSONObject(response.body());
                if (response.statusCode() != 200) throw new IOException("Pair request failed: " + body.optString("error", response.statusCode() + ""));
                requestId = body.getString("requestId");
                String code = body.getString("verificationCode");
                pairingCodeFromLastPrompt = code;
                SwingUtilities.invokeLater(() -> {
                    setBusy(false, null);
                    append("Pairing code: " + code + "\nApprove this exact request in Dosey on Android.\n");
                    int choice = JOptionPane.showConfirmDialog(frame,
                        "Compare code " + code + " with the request shown on Android.\nOnly continue if they match and you approved this PC.",
                        "Confirm pairing code", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (choice == JOptionPane.OK_OPTION) pollPairing();
                    else append("Pairing confirmation cancelled.\n");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> { setBusy(false, null); showError(safeMessage(ex)); });
            }
        });
    }

    private void pollPairing() {
        HttpClient current = client;
        String url = baseUrl;
        String id = requestId;
        if (current == null || url == null || id == null) return;
        setBusy(true, "Waiting for Android approval…");
        CompletableFuture.runAsync(() -> {
            try {
                long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
                while (System.nanoTime() < deadline) {
                    PinnedHttpClient.Response response = current.send(url + "/v1/pair/status?id=" + id, "GET", null, null);
                    JSONObject body = new JSONObject(response.body());
                    String state = body.optString("status", "not_found");
                    if ("approved".equals(state)) {
                        JSONObject confirm = new JSONObject().put("requestId", id)
                            .put("verificationCode", pairingCodeFromLastPrompt);
                        PinnedHttpClient.Response confirmed = current.send(url + "/v1/pair/confirm", "POST", confirm.toString(), null);
                        JSONObject result = new JSONObject(confirmed.body());
                        if (confirmed.statusCode() != 200) throw new IOException("Pair confirmation failed: " + result.optString("error"));
                        credential = result.getString("credential");
                        SwingUtilities.invokeLater(() -> {
                            setBusy(false, "Paired securely");
                            snapshotButton.setEnabled(true);
                            append("Pairing complete. Credential is held in memory for this session only.\n");
                        });
                        return;
                    }
                    if ("rejected".equals(state) || "expired".equals(state) || "not_found".equals(state)) {
                        throw new IOException("Pairing " + state + ".");
                    }
                    Thread.sleep(1200);
                }
                throw new IOException("Pairing timed out. Start a new request.");
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> { setBusy(false, null); showError(safeMessage(ex)); });
            }
        });
    }

    private volatile String pairingCodeFromLastPrompt = "";

    private void refreshSnapshot() {
        HttpClient current = client;
        String url = baseUrl;
        String token = credential;
        if (current == null || url == null || token == null) return;
        setBusy(true, "Refreshing medication snapshot…");
        CompletableFuture.runAsync(() -> {
            try {
                PinnedHttpClient.Response response = current.send(url + "/v1/snapshot", "GET", null, token);
                JSONObject snapshot = new JSONObject(response.body());
                if (response.statusCode() != 200) throw new IOException("Snapshot request failed: " + snapshot.optString("error"));
                if (snapshot.optInt("protocolVersion", -1) != 1 || !snapshot.has("revision")) {
                    throw new IOException("Android returned an invalid snapshot.");
                }
                String formatted = "Snapshot revision: " + snapshot.getLong("revision")
                    + "\nGenerated: " + snapshot.getLong("generatedAt")
                    + "\nMedications: " + snapshot.getJSONArray("medications").length()
                    + "\nSchedules: " + snapshot.getJSONArray("schedules").length()
                    + "\nDose logs: " + snapshot.getJSONArray("doseLogs").length()
                    + "\nOne-off doses: " + snapshot.getJSONArray("oneOffDoses").length()
                    + "\n\n" + prettyMedications(snapshot.getJSONArray("medications"));
                SwingUtilities.invokeLater(() -> {
                    outputArea.setText(formatted);
                    setBusy(false, "Snapshot refreshed · revision " + snapshot.optLong("revision"));
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> { setBusy(false, null); showError(safeMessage(ex)); });
            }
        });
    }

    private String prettyMedications(JSONArray medications) {
        StringBuilder text = new StringBuilder("Medication list\n--------------\n");
        for (int i = 0; i < medications.length(); i++) {
            JSONObject medication = medications.getJSONObject(i);
            text.append(medication.optString("name", "Unnamed medication"))
                .append(" · dose: ").append(medication.optString("doseDescription", "—"))
                .append(" · stock: ");
            int stock = medication.optInt("stockQuantity", -1);
            text.append(stock < 0 ? "not tracked" : stock).append('\n');
            String notes = medication.optString("notes", "");
            if (!notes.isBlank()) text.append("  ").append(notes).append('\n');
        }
        return text.toString();
    }

    /**
     * Android currently uses a self-signed CN-only certificate without DNS/IP SANs.
     * The exact certificate SHA-256 pin is checked on every TLS handshake instead.
     */
    private static final class PinnedHttpClient {
        private final SSLContext sslContext;
        private PinnedHttpClient(SSLContext sslContext) { this.sslContext = sslContext; }

        private record Response(int statusCode, String body) {}

        private Response send(String url, String method, String body, String token) throws IOException {
            HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
            connection.setSSLSocketFactory(sslContext.getSocketFactory());
            connection.setHostnameVerifier((hostname, session) -> true);
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Cache-Control", "no-store");
            if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
            if ("POST".equals(method)) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (var output = connection.getOutputStream()) { output.write(bytes); }
            }
            int status = connection.getResponseCode();
            var stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String responseBody = "";
            if (stream != null) {
                try (var input = stream) {
                    responseBody = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            connection.disconnect();
            return new Response(status, responseBody);
        }
    }

    private static String normaliseFingerprint(String value) {
        return value.replace(":", "").replace("-", "").replaceAll("\\s", "").toLowerCase();
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, "Dosey Companion", JOptionPane.ERROR_MESSAGE);
    }

    private void append(String text) {
        outputArea.append(text);
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }

    private void setBusy(boolean busy, String status) {
        connectButton.setEnabled(!busy);
        if (status != null) statusLabel.setText(status);
        else if (!busy) statusLabel.setText("Ready");
    }

    private static final class PinningTrustManager implements X509TrustManager {
        private final String expectedFingerprint;
        private PinningTrustManager(String expectedFingerprint) { this.expectedFingerprint = expectedFingerprint; }

        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("Client certificates are not accepted");
        }

        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (chain == null || chain.length == 0) throw new CertificateException("Server certificate missing");
            try {
                String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded()));
                if (!MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),
                    expectedFingerprint.getBytes(StandardCharsets.US_ASCII))) {
                    throw new CertificateException("TLS certificate fingerprint does not match the fingerprint copied from Android.");
                }
            } catch (CertificateException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new CertificateException("Could not verify TLS certificate fingerprint", ex);
            }
        }

        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }

    private void setBusy(boolean busy, String status, boolean unused) { setBusy(busy, status); }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new DoseyCompanionApp().show());
    }

    private void show() {
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
