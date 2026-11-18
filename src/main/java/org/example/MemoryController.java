package org.example;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST-kontroller for ordboka, så brukaren kan sjå og rette det ODIN har lært.
 */
@RestController
public class MemoryController {

    private final DialectMemory dialectMemory;

    public MemoryController(DialectMemory dialectMemory) {
        this.dialectMemory = dialectMemory;
    }

    /**
     * @return Alle oppføringar i ordboka.
     */
    @GetMapping("/api/memory")
    public List<DialectMemory.Entry> list() {
        return dialectMemory.all();
    }

    /**
     * Legg til ei oppføring for hand.
     * @param body JSON med «kind» (ORD, FEILHØYRING eller FAKTA), «term» og «meaning».
     * @return Stadfesting.
     */
    @PostMapping("/api/memory")
    public ResponseEntity<Map<String, String>> add(@RequestBody Map<String, String> body) {
        try {
            String kindName = body.get("kind");
            DialectMemory.Kind kind = DialectMemory.Kind.valueOf(kindName == null ? "ORD" : kindName);
            return ResponseEntity.ok(Map.of("message", dialectMemory.add(kind, body.get("term"), body.get("meaning"))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ukjend type: " + body.get("kind")));
        }
    }

    /**
     * Slettar ei oppføring.
     * @param id Id-en til oppføringa.
     * @return 204 om ho vart sletta, 404 om ho ikkje fanst.
     */
    @DeleteMapping("/api/memory/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return dialectMemory.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
