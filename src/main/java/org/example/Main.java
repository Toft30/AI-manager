package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Hovudklassen for AI Manager applikasjonen.
 * Denne klassen startar Spring Boot-applikasjonen.
 */
@SpringBootApplication
public class Main {

    /**
     * Inngangspunkt for Spring Boot-applikasjonen.
     *
     * @param args Kommandolinjeargument.
     */
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}
