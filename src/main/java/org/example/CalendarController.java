package org.example;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST-kontroller for innlogging til Google Kalender frå HUD-en.
 */
@RestController
public class CalendarController {

    private final CalendarAgent calendarAgent;

    public CalendarController(CalendarAgent calendarAgent) {
        this.calendarAgent = calendarAgent;
    }

    /**
     * Startar innlogging hos Google i bakgrunnen og opnar innloggingssida i nettlesaren.
     * @return Status, og adressa til innloggingssida når ho er klar.
     */
    @PostMapping("/api/calendar/login")
    public ResponseEntity<Map<String, String>> login() {
        try {
            calendarAgent.startLogin();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
        }
        Map<String, String> body = new HashMap<>();
        body.put("status", calendarAgent.loginStatus());
        if (calendarAgent.loginUrl() != null) {
            body.put("url", calendarAgent.loginUrl());
        }
        return ResponseEntity.ok(body);
    }
}
