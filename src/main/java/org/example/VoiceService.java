package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Stemma til ODIN: tekst-til-tale og tale-til-tekst via ElevenLabs.
 *
 * <p>
 * Nøkkelen blir verande på serveren. Nettlesaren får berre mp3-bytar frå /api/speak,
 * så han dukkar aldri opp i devtools eller kjeldekoden til sida.
 * </p>
 */
@Service
public class VoiceService {

    private static final String BASE_URL = "https://api.elevenlabs.io/v1";
    private static final int MAX_TEXT_LENGTH = 2500;

    private final String apiKey;
    private final String voiceId;
    private final String model;
    private final String sttModel;
    private final String sttLanguage;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public VoiceService(@Value("${elevenlabs.api-key:}") String apiKey,
                        @Value("${elevenlabs.voice-id:JBFqnCBsd6RMkjVDRZzb}") String voiceId,
                        @Value("${elevenlabs.model:eleven_flash_v2_5}") String model,
                        @Value("${elevenlabs.stt-model:scribe_v1}") String sttModel,
                        @Value("${elevenlabs.stt-language:nor}") String sttLanguage,
                        ObjectMapper mapper) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.voiceId = voiceId;
        this.model = model;
        this.sttModel = sttModel;
        this.sttLanguage = sttLanguage == null ? "" : sttLanguage.trim();
        this.mapper = mapper;
    }

    /**
     * @return Om det finst ein ElevenLabs-nøkkel. Utan nøkkel brukar nettlesaren si eiga stemme.
     */
    public boolean available() {
        return !apiKey.isEmpty();
    }

    /**
     * @return Kort skildring av kva stemme som er i bruk, utan nøkkelen.
     */
    public String describe() {
        return available() ? "ElevenLabs · " + model + " · " + voiceId.substring(0, Math.min(8, voiceId.length())) + "…"
                : "nettlesaren si stemme (legg ELEVENLABS_API_KEY i .env for ekte stemme)";
    }

    /**
     * Gjer tekst om til tale.
     * @param text Teksten som skal lesast opp.
     * @return mp3-bytar.
     */
    public byte[] speak(String text) throws IOException, InterruptedException {
        requireKey();
        String spoken = toSpeech(text);
        if (spoken.isEmpty()) {
            throw new IllegalArgumentException("Tom tekst");
        }
        String body = mapper.writeValueAsString(Map.of("text", spoken, "model_id", model));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/text-to-speech/" + voiceId + "?output_format=mp3_44100_128"))
                .timeout(Duration.ofSeconds(45))
                .header("xi-api-key", apiKey)
                .header("content-type", "application/json")
                .header("accept", "audio/mpeg")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("ElevenLabs TTS HTTP " + response.statusCode() + ": " + snippet(response.body()));
        }
        return response.body();
    }

    /**
     * Gjer eit lydklipp frå nettlesaren om til tekst.
     * @param audio Lydbytar, typisk webm/opus frå MediaRecorder.
     * @param mime Lydtypen.
     * @return Teksten som vart sagt, eller tom tekst om klippet berre var støy.
     */
    public String transcribe(byte[] audio, String mime) throws IOException, InterruptedException {
        requireKey();
        if (audio == null || audio.length == 0) {
            throw new IllegalArgumentException("Tomt lydklipp");
        }
        String type = mime == null || mime.isBlank() ? "audio/webm" : mime;
        String ext = type.contains("ogg") ? "ogg" : type.contains("mp4") ? "mp4" : type.contains("wav") ? "wav"
                : type.contains("mpeg") ? "mp3" : "webm";
        String boundary = "----odin" + UUID.randomUUID().toString().replace("-", "");

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeField(body, boundary, "model_id", sttModel);
        writeField(body, boundary, "tag_audio_events", "false");
        if (!sttLanguage.isBlank()) {
            writeField(body, boundary, "language_code", sttLanguage);
        }
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"tur." + ext + "\"\r\n"
                + "Content-Type: " + type + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(audio);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/speech-to-text"))
                .timeout(Duration.ofSeconds(60))
                .header("xi-api-key", apiKey)
                .header("content-type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("ElevenLabs STT HTTP " + response.statusCode() + ": " + snippet(response.body()));
        }
        JsonNode json = mapper.readTree(response.body());
        String text = json.path("text").asText("").trim();
        // Fjern lydhendingar som (latter) eller [støy]; er det ingenting att, var det ikkje tale.
        text = text.replaceAll("[\\[(][^\\])]{0,40}[\\])]", " ").replaceAll("\\s+", " ").trim();
        return text.replaceAll("[^\\p{L}\\p{N}]", "").length() < 2 ? "" : text;
    }

    /**
     * Fjernar markdown og anna som ikkje skal lesast høgt.
     */
    static String toSpeech(String text) {
        if (text == null) {
            return "";
        }
        String s = text
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("\\[([^\\]]+)]\\([^)]+\\)", "$1")
                .replaceAll("https?://\\S+", "lenkja")
                .replaceAll("[*_#`>|~]", "")
                .replaceAll("(?m)^\\s*[-•]\\s+", "")
                .replaceAll("\\s+", " ")
                .trim();
        return s.length() > MAX_TEXT_LENGTH ? s.substring(0, MAX_TEXT_LENGTH) : s;
    }

    private void requireKey() {
        if (!available()) {
            throw new IllegalStateException("Ingen ELEVENLABS_API_KEY - nettlesaren si stemme tek over.");
        }
    }

    private static void writeField(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private static String snippet(byte[] body) {
        String s = new String(body, StandardCharsets.UTF_8);
        return s.length() > 300 ? s.substring(0, 300) : s;
    }
}
