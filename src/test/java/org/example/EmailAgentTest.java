package org.example;

import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testar e-postagenten utan å kontakte Gmail.
 */
class EmailAgentTest {

    /** Ei OAuth-fil som ikkje finst, så eit kall som kjem forbi sperrene feilar i staden for å opne nettlesaren. */
    private final EmailAgent agent = new EmailAgent(
            new GoogleAuth("finst-ikkje/credentials.json", "finst-ikkje/tokens"), "Europe/Oslo");

    @Test
    void kjennerAttStadfestingar() {
        assertThat(EmailAgent.isConfirmation("send det")).isTrue();
        assertThat(EmailAgent.isConfirmation("Ja, gjer det")).isTrue();
        assertThat(EmailAgent.isConfirmation("okei, stadfesta")).isTrue();

        assertThat(EmailAgent.isConfirmation("nei, ikkje send det")).isFalse();
        assertThat(EmailAgent.isConfirmation("vent, endre emnet først")).isFalse();
        assertThat(EmailAgent.isConfirmation("kva står det i e-posten frå Ola")).isFalse();
        assertThat(EmailAgent.isConfirmation("")).isFalse();
    }

    @Test
    void senderIkkjeUtkastIDetSameSporsmaletDetVartLaga() {
        agent.newTurn("skriv ein e-post til ola@example.com");
        agent.rememberDraft("d1");

        assertThat(agent.sendDraft("d1")).contains("feila").contains("nettopp laga");
    }

    @Test
    void senderIkkjeUtanStadfesting() {
        agent.newTurn("skriv ein e-post til ola@example.com");
        agent.rememberDraft("d1");
        agent.newTurn("kva er klokka");

        assertThat(agent.sendDraft("d1")).contains("feila").contains("ikkje stadfesta");
    }

    @Test
    void senderIkkjeUkjendeUtkast() {
        agent.newTurn("send det");

        assertThat(agent.sendDraft("laga-av-nokon-andre")).contains("feila").contains("Fann ikkje");
    }

    @Test
    void provarASendeNarUtkastetErFraForrigeSporsmalOgStadfesta() {
        agent.newTurn("skriv ein e-post til ola@example.com");
        agent.rememberDraft("d1");
        agent.newTurn("ja, send det");

        // Sperrene slepp kallet gjennom; det feilar først når det skal logge inn hos Google.
        assertThat(agent.sendDraft("d1")).contains("Klarte ikkje å sende");
    }

    @Test
    void validererAdresser() {
        assertThat(EmailAgent.invalidAddress("ola@example.com, kari@example.no")).isNull();
        assertThat(EmailAgent.invalidAddress("ola krøll example punktum com")).isEqualTo("ola krøll example punktum com");
        assertThat(EmailAgent.invalidAddress("")).isEqualTo("");
    }

    @Test
    void byggjerMimeUtanAtModellenKanLeggjeTilFelt() {
        String raw = EmailAgent.buildMime("ola@example.com\r\nBcc: spion@example.com", "Møte på fredag",
                "Hei Ola,\nsjåast kl. 10.", null, null);
        String mime = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);

        assertThat(mime).startsWith("To: ola@example.com Bcc: spion@example.com\r\n");
        assertThat(mime).doesNotContain("\r\nBcc:");
        assertThat(mime).contains("Subject: =?UTF-8?B?");

        String body = mime.substring(mime.indexOf("\r\n\r\n") + 4).trim();
        assertThat(new String(Base64.getMimeDecoder().decode(body), StandardCharsets.UTF_8))
                .isEqualTo("Hei Ola,\r\nsjåast kl. 10.");
    }

    @Test
    void hentarTekstFraHtmlNarDetIkkjeFinstRenTekst() {
        String html = "<html><head><style>p{}</style></head><body><p>Hei &amp; velkomen</p><br>Helsing Kari</body></html>";
        MessagePart part = new MessagePart().setMimeType("multipart/alternative").setParts(List.of(
                new MessagePart().setMimeType("text/html").setBody(new MessagePartBody().encodeData(
                        html.getBytes(StandardCharsets.UTF_8)))));

        assertThat(EmailAgent.extractText(part)).isEqualTo("Hei & velkomen\nHelsing Kari");
    }
}
