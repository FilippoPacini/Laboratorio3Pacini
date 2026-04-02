package server;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Gruppo tematico di 4 parole. Immutabile dopo la creazione. */
public class GameGroup {

    private final String theme;
    private final Set<String> words;

    public GameGroup(String theme, Set<String> words) {
        this.theme = theme;
        this.words = Collections.unmodifiableSet(new HashSet<>(words));
    }

    public String getTheme() { return theme; }
    public Set<String> getWords() { return words; }

    public boolean matches(Set<String> proposal) {
        return words.equals(proposal);
    }

    @Override
    public String toString() { return theme + ": " + words; }
}
