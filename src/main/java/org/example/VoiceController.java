package org.example;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * REST-kontroller for stemma: opplesing, lytting og status til HUD-en.
 */
@RestController
public class VoiceController {

    private final VoiceService voiceService;
    private final TaskRepository taskRepository;
    private final AgentRouter agentRouter;
    private final Instant startedAt = Instant.now();

    public VoiceController(VoiceService voiceService, TaskRepository taskRepository, AgentRouter agentRouter) {
        this.voiceService = voiceService;
        this.taskRepository = taskRepository;
        this.agentRouter = agentRouter;
    }

    /**
     * Les teksten høgt med ElevenLabs.
     * @param body JSON med feltet «text».
     * @return mp3-lyd, eller 503 om det ikkje finst nøkkel (då talar nettlesaren sjølv).
     */
    @PostMapping("/api/speak")
    public ResponseEntity<?> speak(@RequestBody Map<String, String> body) {
        if (!voiceService.available()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "ingen ElevenLabs-nøkkel"));
        }
        try {
            byte[] audio = voiceService.speak(body.getOrDefault("text", ""));
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/mpeg")).body(audio);
        } catch (Exception e) {
            System.out.println("Opplesing feila: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    /**
     * Gjer eit lydklipp om til tekst.
     * @param audio Lydklippet frå mikrofonen.
     * @return JSON med feltet «text».
     */
    @PostMapping("/api/listen")
    public ResponseEntity<Map<String, String>> listen(@RequestParam("audio") MultipartFile audio) {
        if (!voiceService.available()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "ingen ElevenLabs-nøkkel"));
        }
        try {
            return ResponseEntity.ok(Map.of("text", voiceService.transcribe(audio.getBytes(), audio.getContentType())));
        } catch (Exception e) {
            System.out.println("Lytting feila: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    /**
     * Status til telemetri-panelet i HUD-en.
     * @return Stemme, modellen som svarte sist, lytting, oppetid og tal på oppgåver.
     */
    @GetMapping("/api/status")
    public Map<String, Object> status() {
        return Map.of(
                "voice", voiceService.describe(),
                "brain", "Gemini · " + agentRouter.lastModel(),
                "elevenlabs", voiceService.available(),
                "listening", voiceService.available() ? "ElevenLabs Scribe" : "nettlesaren",
                "uptimeSeconds", Duration.between(startedAt, Instant.now()).toSeconds(),
                "tasks", taskRepository.count());
    }
}
