package org.example;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.gmail.GmailScopes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Felles innlogging hos Google for kalenderen og Gmail.
 *
 * <p>
 * Første gong eit Google-verktøy blir brukt, opnar nettlesaren seg for innlogging (callback på port 8888).
 * Tilgangen blir lagra i tokens-mappa, så innlogginga trengst berre éin gong.
 * </p>
 */
@Component
public class GoogleAuth {

    static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    static final String APPLICATION_NAME = "AI-Manager";

    /** gmail.modify gir lesing, utkast, sending og merkelappar, men ikkje varig sletting. */
    private static final List<String> SCOPES = List.of(CalendarScopes.CALENDAR, GmailScopes.GMAIL_MODIFY);

    /**
     * Namnet tilgangen blir lagra under. Den gamle tilgangen («user») hadde berre kalender-scope,
     * så eit nytt namn gjer at brukaren blir beden om å logge inn éin gong til med Gmail-tilgang.
     */
    private static final String STORED_USER = "odin";

    private final String credentialsPath;
    private final String tokensDir;

    private NetHttpTransport transport;
    private Credential credential;

    public GoogleAuth(@Value("${google.credentials-path:${google.calendar.credentials-path:credentials.json}}") String credentialsPath,
                      @Value("${google.tokens-dir:${google.calendar.tokens-dir:tokens}}") String tokensDir) {
        this.credentialsPath = credentialsPath;
        this.tokensDir = tokensDir;
    }

    /**
     * @return HTTP-transporten som Google-klientane deler.
     */
    public synchronized NetHttpTransport transport() throws Exception {
        if (transport == null) {
            transport = GoogleNetHttpTransport.newTrustedTransport();
        }
        return transport;
    }

    /**
     * Hentar lagra tilgang, eller loggar inn via nettlesaren om det ikkje finst nokon.
     * Blokkerer til brukaren har logga inn.
     * @return Tilgangen til Google-kontoen.
     */
    public synchronized Credential credential() throws Exception {
        if (credential != null) {
            return credential;
        }
        File credentialsFile = new File(credentialsPath);
        if (!credentialsFile.exists()) {
            throw new IllegalStateException("Finn ikkje OAuth-fila " + credentialsFile.getAbsolutePath()
                    + ". Last ned ein OAuth-klient (Desktop app) frå Google Cloud Console og lagre han der.");
        }

        GoogleClientSecrets secrets;
        try (Reader reader = new FileReader(credentialsFile, StandardCharsets.UTF_8)) {
            secrets = GoogleClientSecrets.load(JSON_FACTORY, reader);
        }

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(transport(), JSON_FACTORY, secrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new File(tokensDir)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize(STORED_USER);
        return credential;
    }
}
