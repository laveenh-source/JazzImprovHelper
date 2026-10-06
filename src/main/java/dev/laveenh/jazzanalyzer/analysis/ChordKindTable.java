package dev.laveenh.jazzanalyzer.analysis;

import dev.laveenh.jazzanalyzer.domain.ChordQuality;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/** Lookup from a MusicXML {@code <kind>} value to a base quality and natural extension. */
public final class ChordKindTable {

    /** Base quality plus natural extension (0, 9, 11 or 13) for one MusicXML kind. */
    public record Entry(ChordQuality quality, int extension) {
    }

    private static final String RESOURCE = "/theory/chord-kinds.properties";
    private static final Entry UNKNOWN = new Entry(ChordQuality.UNKNOWN, 0);

    private final Map<String, Entry> entries;

    private ChordKindTable(Map<String, Entry> entries) {
        this.entries = entries;
    }

    public static ChordKindTable loadDefault() {
        try (InputStream in = ChordKindTable.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + RESOURCE);
            }
            Properties props = new Properties();
            props.load(in);
            return fromProperties(props);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + RESOURCE, e);
        }
    }

    static ChordKindTable fromProperties(Properties props) {
        Map<String, Entry> map = new HashMap<>();
        for (String kind : props.stringPropertyNames()) {
            String[] parts = props.getProperty(kind).split(",");
            ChordQuality quality = ChordQuality.valueOf(parts[0].trim());
            int extension = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
            map.put(kind.trim(), new Entry(quality, extension));
        }
        return new ChordKindTable(map);
    }

    /** Never null: unmapped kinds resolve to {@link ChordQuality#UNKNOWN}. */
    public Entry lookup(String kind) {
        return entries.getOrDefault(kind == null ? "" : kind.trim(), UNKNOWN);
    }
}
