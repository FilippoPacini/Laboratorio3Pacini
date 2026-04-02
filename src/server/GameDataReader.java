package server;

import com.google.gson.stream.JsonReader;

import java.io.*;
import java.util.*;

/**
 * Lettore streaming del file JSON delle partite.
 * Usa Gson JsonReader per leggere un oggetto alla volta senza caricare tutto in memoria.
 */
public class GameDataReader implements Closeable {

    private final JsonReader reader;
    private boolean arrayStarted;
    private boolean exhausted;

    public GameDataReader(String filePath) throws IOException {
        Reader fileReader = new BufferedReader(new FileReader(filePath));
        this.reader = new JsonReader(fileReader);
        this.arrayStarted = false;
        this.exhausted = false;
    }

    public GameData readNextGame() throws IOException {
        if (exhausted) return null;

        if (!arrayStarted) {
            reader.beginArray();
            arrayStarted = true;
        }

        if (!reader.hasNext()) {
            reader.endArray();
            exhausted = true;
            return null;
        }

        return parseGameObject();
    }

    /** Salta partite fino all'indice desiderato (per riavvio del server). */
    public GameData skipToGame(int targetIndex) throws IOException {
        for (int i = 0; i < targetIndex; i++) {
            GameData skipped = readNextGame();
            if (skipped == null) return null;
        }
        return readNextGame();
    }

    private GameData parseGameObject() throws IOException {
        int gameId = -1;
        List<GameGroup> groups = new ArrayList<>();

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "gameId": gameId = reader.nextInt(); break;
                case "groups": groups = parseGroups(); break;
                default: reader.skipValue(); break;
            }
        }
        reader.endObject();
        return new GameData(gameId, groups);
    }

    private List<GameGroup> parseGroups() throws IOException {
        List<GameGroup> groups = new ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) groups.add(parseGroup());
        reader.endArray();
        return groups;
    }

    private GameGroup parseGroup() throws IOException {
        String theme = null;
        Set<String> words = new HashSet<>();

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "theme": theme = reader.nextString(); break;
                case "words":
                    reader.beginArray();
                    while (reader.hasNext()) words.add(reader.nextString());
                    reader.endArray();
                    break;
                default: reader.skipValue(); break;
            }
        }
        reader.endObject();
        return new GameGroup(theme, words);
    }

    public boolean isExhausted() { return exhausted; }

    @Override
    public void close() throws IOException { reader.close(); }

    /** Dati grezzi di una partita letti dal JSON. */
    public static class GameData {
        private final int gameId;
        private final List<GameGroup> groups;

        public GameData(int gameId, List<GameGroup> groups) {
            this.gameId = gameId;
            this.groups = groups;
        }

        public int getGameId() { return gameId; }
        public List<GameGroup> getGroups() { return groups; }
    }
}
