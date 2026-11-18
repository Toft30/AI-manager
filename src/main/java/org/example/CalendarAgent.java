package org.example;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ein under-agent som les og skriv hendingar i Google Kalender.
 *
 * <p>
 * Innlogginga hos Google skjer i {@link GoogleAuth}, som kalenderen deler med Gmail.
 * </p>
 */
@Component
public class CalendarAgent {

    private static final DateTimeFormatter VIS_FORMAT =
            DateTimeFormatter.ofPattern("EEEE d. MMMM yyyy 'kl.' HH:mm", Locale.forLanguageTag("nn"));

    private final GoogleAuth googleAuth;
    private final String calendarId;
    private final ZoneId zone;

    private final AtomicLong changes = new AtomicLong();

    private Calendar service;

    public CalendarAgent(GoogleAuth googleAuth,
                         @Value("${google.calendar.id:primary}") String calendarId,
                         @Value("${google.calendar.timezone:Europe/Oslo}") String timezone) {
        this.googleAuth = googleAuth;
        this.calendarId = calendarId;
        this.zone = ZoneId.of(timezone);
    }

    /**
     * Gir modellen dagens dato og klokkeslett, slik at han kan tolke ord som «i morgon» og «neste fredag».
     * @return Noverande tidspunkt i kalenderen si tidssone.
     */
    @Tool("Returns the current date, weekday and time. The current time is already in your instructions, "
            + "so only call this if you need the time to the second.")
    public String getCurrentDateTime() {
        return ZonedDateTime.now(zone).format(VIS_FORMAT) + " (ISO: " + LocalDateTime.now(zone).withNano(0) + ", tidssone " + zone + ")";
    }

    /**
     * Opprettar eit møte i Google Kalender.
     * @param startTime Starttidspunkt, til dømes 2026-11-03T10:00.
     * @param durationMinutes Lengda på møtet i minutt.
     * @param title Tittel på møtet.
     * @param details Skildring av møtet.
     * @param attendees Kommaseparerte e-postadresser til deltakarar, eller tom tekst.
     * @return Stadfesting med lenkje og id, eller ei feilmelding.
     */
    @Tool("Creates a meeting in the user's Google Calendar.")
    public String scheduleMeeting(@P("Start time as ISO-8601 local date-time, e.g. 2026-11-03T10:00") String startTime,
                                  @P("Duration in minutes, use 60 if not specified") int durationMinutes,
                                  @P("Short title of the meeting") String title,
                                  @P("Description of the meeting, may be empty") String details,
                                  @P("Comma-separated e-mail addresses of attendees, empty if none") String attendees) {
        System.out.println("CalendarAgent opprettar møte: " + title + " " + startTime);
        try {
            ZonedDateTime start = LocalDateTime.parse(startTime).atZone(zone);
            ZonedDateTime end = start.plusMinutes(durationMinutes > 0 ? durationMinutes : 60);

            Event event = new Event()
                    .setSummary(title)
                    .setDescription(details)
                    .setStart(eventTime(start))
                    .setEnd(eventTime(end));

            List<EventAttendee> guests = Arrays.stream(attendees == null ? new String[0] : attendees.split(","))
                    .map(String::trim)
                    .filter(email -> !email.isEmpty())
                    .map(email -> new EventAttendee().setEmail(email))
                    .toList();
            if (!guests.isEmpty()) {
                event.setAttendees(guests);
            }

            Event created = calendar().events().insert(calendarId, event)
                    .setSendUpdates(guests.isEmpty() ? "none" : "all")
                    .execute();
            changes.incrementAndGet();
            return ToolResponse.ok("Møtet «" + created.getSummary() + "» er oppretta " + start.format(VIS_FORMAT)
                    + " (id: " + created.getId() + ", lenkje: " + created.getHtmlLink() + ")").toString();
        } catch (Exception e) {
            return ToolResponse.failed("Klarte ikkje å opprette møtet: " + e.getMessage()).toString();
        }
    }

    /**
     * Sjekkar om brukaren er ledig i eit tidsrom, ved å sjå etter hendingar som overlappar.
     * @param startTime Starttidspunkt, til dømes 2026-11-03T10:00.
     * @param durationMinutes Kor lenge tidsrommet varer.
     * @return Strukturert svar: ledig, eller opptatt med hendingane som kolliderer.
     */
    @Tool("Checks if the user is available in a time slot, or if the calendar already has events then. "
            + "Use it before scheduling a meeting when the user asks if a time is free.")
    public String checkAvailability(@P("Start time as ISO-8601 local date-time, e.g. 2026-11-03T10:00") String startTime,
                                    @P("Length of the time slot in minutes, use 60 if not specified") int durationMinutes) {
        System.out.println("CalendarAgent sjekkar tilgjenge for: " + startTime);
        try {
            ZonedDateTime start = LocalDateTime.parse(startTime).atZone(zone);
            ZonedDateTime end = start.plusMinutes(durationMinutes > 0 ? durationMinutes : 60);

            Events events = calendar().events().list(calendarId)
                    .setTimeMin(toDateTime(start))
                    .setTimeMax(toDateTime(end))
                    .setSingleEvents(true)
                    .setOrderBy("startTime")
                    .execute();

            // Hendingar merkte som «ledig» (transparent) blokkerer ikkje tida.
            List<Event> busy = events.getItems().stream()
                    .filter(event -> !"transparent".equals(event.getTransparency()))
                    .toList();
            if (busy.isEmpty()) {
                return ToolResponse.ok("Ledig " + start.format(VIS_FORMAT) + " i " + durationMinutes + " minutt.").toString();
            }
            StringBuilder sb = new StringBuilder("Opptatt " + start.format(VIS_FORMAT) + ". Kolliderer med: ");
            for (Event event : busy) {
                sb.append(event.getSummary()).append(" (").append(describeStart(event)).append("); ");
            }
            return ToolResponse.ok(sb.toString().trim()).toString();
        } catch (Exception e) {
            return ToolResponse.failed("Klarte ikkje å sjekke kalenderen: " + e.getMessage()).toString();
        }
    }

    /**
     * Listar hendingar i kalenderen mellom to datoar.
     * @param fromDate Første dato, til dømes 2026-11-03.
     * @param toDate Siste dato (inkludert).
     * @return Ei liste over hendingar med id, eller ei feilmelding.
     */
    @Tool("Lists the events in the user's Google Calendar between two dates (inclusive).")
    public String listMeetings(@P("First date, ISO-8601, e.g. 2026-11-03") String fromDate,
                               @P("Last date (inclusive), ISO-8601") String toDate) {
        try {
            ZonedDateTime from = LocalDate.parse(fromDate).atStartOfDay(zone);
            ZonedDateTime to = LocalDate.parse(toDate).plusDays(1).atStartOfDay(zone);

            Events events = calendar().events().list(calendarId)
                    .setTimeMin(toDateTime(from))
                    .setTimeMax(toDateTime(to))
                    .setSingleEvents(true)
                    .setOrderBy("startTime")
                    .execute();

            if (events.getItems().isEmpty()) {
                return ToolResponse.ok("Ingen hendingar mellom " + fromDate + " og " + toDate + ".").toString();
            }
            StringBuilder sb = new StringBuilder();
            for (Event event : events.getItems()) {
                sb.append("- ").append(describeStart(event)).append(": ").append(event.getSummary())
                        .append(" (id: ").append(event.getId()).append(")\n");
            }
            return ToolResponse.ok(sb.toString()).toString();
        } catch (Exception e) {
            return ToolResponse.failed("Klarte ikkje å hente hendingar: " + e.getMessage()).toString();
        }
    }

    /**
     * Slettar ei hending frå kalenderen.
     * @param eventId Id-en til hendinga, som listMeetings gir.
     * @return Stadfesting eller ei feilmelding.
     */
    @Tool("Deletes an event from the user's Google Calendar. Use listMeetings first to find the event id.")
    public String deleteMeeting(@P("The id of the event to delete") String eventId) {
        try {
            calendar().events().delete(calendarId, eventId).execute();
            changes.incrementAndGet();
            return ToolResponse.ok("Hendinga med id " + eventId + " er sletta.").toString();
        } catch (Exception e) {
            return ToolResponse.failed("Klarte ikkje å slette hendinga: " + e.getMessage()).toString();
        }
    }

    /**
     * Kor mange gonger kalenderen er endra (møte oppretta eller sletta) sidan oppstart.
     * AgentRouter brukar dette for å sjå om eit mislukka forsøk alt hadde endra noko.
     * @return Talet på endringar.
     */
    public long changeCount() {
        return changes.get();
    }

    /**
     * Lagar Calendar-klienten første gong han trengst. Finst det ikkje lagra tilgang, loggar GoogleAuth inn via nettlesaren.
     */
    private synchronized Calendar calendar() throws Exception {
        if (service != null) {
            return service;
        }
        service = new Calendar.Builder(googleAuth.transport(), GoogleAuth.JSON_FACTORY, googleAuth.credential())
                .setApplicationName(GoogleAuth.APPLICATION_NAME)
                .build();
        return service;
    }

    private EventDateTime eventTime(ZonedDateTime time) {
        return new EventDateTime().setDateTime(toDateTime(time)).setTimeZone(zone.getId());
    }

    private static DateTime toDateTime(ZonedDateTime time) {
        return new DateTime(Date.from(time.toInstant()));
    }

    private String describeStart(Event event) {
        DateTime start = event.getStart().getDateTime();
        if (start == null) {
            return event.getStart().getDate() + " (heile dagen)";
        }
        return Instant.ofEpochMilli(start.getValue()).atZone(zone).format(VIS_FORMAT);
    }
}
