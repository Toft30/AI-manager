package org.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ordboka til ODIN: dialektord, typiske feilhøyringar frå talegjenkjenninga og fakta om brukaren.
 *
 * <p>
 * Ordboka blir lagra som JSON på disk, så ho overlever omstart, og blir lagd inn i systemmeldinga
 * ved kvart spørsmål. ODIN kan sjølv leggje til og fjerne oppføringar via verktøya under,
 * og brukaren kan sjå og redigere dei i HUD-en.
 * </p>
 */
@Component
public class DialectMemory {

    /**
     * Kva slags oppføring det er.
     */
    public enum Kind {
        ORD,
        FEILHØYRING,
        FAKTA
    }

    /**
     * Ei oppføring i ordboka.
     * @param id Kort, unik id.
     * @param kind Kva slags oppføring.
     * @param term Dialektordet, den feilhøyrde teksten, eller sjølve faktumet.
     * @param meaning Kva det tyder (tom for fakta).
     * @param added Datoen oppføringa vart lagd til.
     */
    public record Entry(String id, Kind kind, String term, String meaning, String added) {
    }

    private static final int MAX_IN_PROMPT = 300;

    // «Når eg seier X, meiner eg Y» – òg på bokmål og med «betyr/tyder».
    private static final Pattern WORD_PATTERN = Pattern.compile(
            "n[åa]r (?:eg|jeg|e) (?:seier|sier|seie|sei|seie) ([^,.!?]{1,40}?),? (?:så )?(?:meiner|mener|meine|betyr|tyder)"
                    + "(?: (?:eg|jeg|e|det))? ([^.!?]{1,80})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    // «Du høyrde X, eg sa Y»
    private static final Pattern MISHEARD_PATTERN = Pattern.compile(
            "du (?:høyrde|hørte|høyrte) ([^,.!?]{1,40}?),? (?:men )?(?:eg|jeg|e) (?:sa|sei|seie) ([^.!?]{1,40})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    // «Hugs at ...» / «Husk at ...»
    private static final Pattern FACT_PATTERN = Pattern.compile(
            "(?:hugs|husk|hugse) at ([^.!?]{3,160})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final File file;
    private final String dialect;
    private final ObjectMapper mapper;
    private final List<Entry> entries = new ArrayList<>();

    public DialectMemory(@Value("${odin.memory-file:minne/ordbok.json}") String path,
                         @Value("${odin.dialect:norsk dialekt}") String dialect,
                         ObjectMapper mapper) {
        this.file = new File(path);
        this.dialect = dialect;
        this.mapper = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        load();
    }

    @Tool("Saves a dialect word and its meaning to the user's dictionary. Use it when the user explains or "
            + "confirms what a dialect word means. If you are only guessing, ask the user first instead.")
    public String learnWord(@P("The dialect word or phrase, as the user writes or says it") String dialectWord,
                            @P("What it means in standard Norwegian (nynorsk or bokmål)") String meaning) {
        return save(Kind.ORD, dialectWord, meaning);
    }

    @Tool("Saves a typical speech-recognition mistake: text that the transcription produced, and what the user "
            + "actually said or meant. Use it when the user corrects a misheard word.")
    public String learnMishearing(@P("The wrong text as it appeared in the transcription") String heardText,
                                  @P("What the user actually said or meant") String meantText) {
        return save(Kind.FEILHØYRING, heardText, meantText);
    }

    @Tool("Saves a lasting fact about the user, such as names of family, places or preferences. "
            + "Only use it when the user asks you to remember something or confirms it.")
    public String rememberFact(@P("The fact, written as a short sentence") String fact) {
        return save(Kind.FAKTA, fact, "");
    }

    @Tool("Removes an entry from the user's dictionary, for example a word that was saved with the wrong meaning.")
    public String forget(@P("The dialect word, misheard text or fact to remove") String term) {
        synchronized (this) {
            boolean removed = entries.removeIf(e -> e.term().equalsIgnoreCase(term.trim()));
            if (!removed) {
                return "Fann ikkje «" + term + "» i ordboka.";
            }
            persist();
        }
        return "Fjerna «" + term + "» frå ordboka.";
    }

    /**
     * Lærer direkte frå tydelege formuleringar i det brukaren seier, utan å gå via modellen.
     * Svake modellar seier av og til at dei har lagra noko utan å kalle verktøyet, så dette er ei sikring.
     * @param text Det brukaren sa.
     * @return Kva som vart lagra, eller tom liste.
     */
    public List<String> learnFromText(String text) {
        List<String> learned = new ArrayList<>();
        if (text == null) {
            return learned;
        }
        Matcher word = WORD_PATTERN.matcher(text);
        while (word.find()) {
            learned.add(learnWord(clean(word.group(1)), clean(word.group(2))));
        }
        Matcher misheard = MISHEARD_PATTERN.matcher(text);
        while (misheard.find()) {
            learned.add(learnMishearing(clean(misheard.group(1)), clean(misheard.group(2))));
        }
        Matcher fact = FACT_PATTERN.matcher(text);
        while (fact.find()) {
            learned.add(rememberFact(clean(fact.group(1))));
        }
        return learned;
    }

    private static String clean(String s) {
        return s.replaceAll("^[\\s«»\"'“”]+|[\\s«»\"'“”.,!?]+$", "");
    }

    /**
     * @return Alle oppføringar, eldste først.
     */
    public synchronized List<Entry> all() {
        return List.copyOf(entries);
    }

    /**
     * Legg til ei oppføring frå HUD-en.
     */
    public String add(Kind kind, String term, String meaning) {
        return save(kind, term, meaning == null ? "" : meaning);
    }

    /**
     * Slettar ei oppføring etter id, frå HUD-en.
     * @return Om noko vart sletta.
     */
    public synchronized boolean delete(String id) {
        boolean removed = entries.removeIf(e -> e.id().equals(id));
        if (removed) {
            persist();
        }
        return removed;
    }

    /**
     * @return Dialektord og rette ord frå feilhøyringar, til bruk som hint for talegjenkjenninga.
     */
    public synchronized List<String> keyterms() {
        return entries.stream()
                .filter(e -> e.kind() != Kind.FAKTA)
                .map(e -> e.kind() == Kind.ORD ? e.term() : e.meaning())
                .filter(t -> !t.isBlank() && t.length() <= 50)
                .distinct()
                .toList();
    }

    /**
     * Lagar teksten som blir lagd inn i systemmeldinga.
     */
    public synchronized String render() {
        StringBuilder sb = new StringBuilder("Brukaren snakkar ").append(dialect).append(". ");
        List<Entry> recent = entries.size() > MAX_IN_PROMPT
                ? entries.subList(entries.size() - MAX_IN_PROMPT, entries.size()) : entries;
        if (recent.isEmpty()) {
            return sb.append("Ordboka er tom enno.").toString();
        }
        appendSection(sb, recent, Kind.ORD, "Dialektord du har lært: ", "«%s» tyder «%s»");
        appendSection(sb, recent, Kind.FEILHØYRING, "Typiske feilhøyringar frå talegjenkjenninga: ",
                "teksten «%s» tyder truleg «%s»");
        appendSection(sb, recent, Kind.FAKTA, "Ting du veit om brukaren: ", "%s");
        return sb.toString().trim();
    }

    private static void appendSection(StringBuilder sb, List<Entry> list, Kind kind, String heading, String format) {
        List<String> lines = list.stream().filter(e -> e.kind() == kind)
                .map(e -> String.format(format, e.term(), e.meaning())).toList();
        if (!lines.isEmpty()) {
            sb.append(heading).append(String.join("; ", lines)).append(". ");
        }
    }

    private String save(Kind kind, String term, String meaning) {
        String t = term == null ? "" : term.trim();
        String m = meaning == null ? "" : meaning.trim();
        if (t.isEmpty()) {
            return "Kan ikkje lagre ei tom oppføring.";
        }
        synchronized (this) {
            // Same ord på nytt betyr ei retting: byt ut den gamle tydinga.
            entries.removeIf(e -> e.kind() == kind && e.term().toLowerCase(Locale.ROOT).equals(t.toLowerCase(Locale.ROOT)));
            entries.add(new Entry(UUID.randomUUID().toString().substring(0, 8), kind, t, m, LocalDate.now().toString()));
            persist();
        }
        System.out.println("Ordboka lærte (" + kind + "): " + t + (m.isEmpty() ? "" : " = " + m));
        return kind == Kind.FAKTA ? "Hugsar no: " + t : "Lagra i ordboka: «" + t + "» = «" + m + "».";
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        try {
            entries.addAll(mapper.readValue(file, new TypeReference<List<Entry>>() { }));
        } catch (IOException e) {
            System.out.println("Klarte ikkje å lese ordboka " + file.getAbsolutePath() + ": " + e.getMessage());
        }
    }

    private void persist() {
        try {
            File dir = file.getAbsoluteFile().getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            mapper.writeValue(file, entries);
        } catch (IOException e) {
            System.out.println("Klarte ikkje å lagre ordboka: " + e.getMessage());
        }
    }
}
