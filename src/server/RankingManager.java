package server;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Classifica dei giocatori con caching.
 * Ricalcolata solo a fine partita; le letture usano la cache.
 * ReadWriteLock: letture concorrenti, lock esclusivo solo durante il ricalcolo.
 */
public class RankingManager {

    public static class RankingEntry implements Comparable<RankingEntry> {
        private final String username;
        private final int totalScore;

        public RankingEntry(String username, int totalScore) {
            this.username = username;
            this.totalScore = totalScore;
        }

        public String getUsername() { return username; }
        public int getTotalScore() { return totalScore; }

        @Override
        public int compareTo(RankingEntry other) {
            int scoreCompare = Integer.compare(other.totalScore, this.totalScore);
            if (scoreCompare != 0) return scoreCompare;
            return this.username.compareTo(other.username);
        }
    }

    private List<RankingEntry> cachedRanking;
    private final ReentrantReadWriteLock lock;

    public RankingManager() {
        this.cachedRanking = new ArrayList<>();
        this.lock = new ReentrantReadWriteLock();
    }

    public void recalculate(Collection<UserRecord> allUsers) {
        List<RankingEntry> newRanking = new ArrayList<>();
        for (UserRecord user : allUsers) {
            newRanking.add(new RankingEntry(user.getUsername(), user.getTotalScore()));
        }
        Collections.sort(newRanking);

        lock.writeLock().lock();
        try { cachedRanking = newRanking; }
        finally { lock.writeLock().unlock(); }
    }

    public List<RankingEntry> getFullRanking() {
        lock.readLock().lock();
        try { return new ArrayList<>(cachedRanking); }
        finally { lock.readLock().unlock(); }
    }

    public List<RankingEntry> getTopK(int k) {
        lock.readLock().lock();
        try {
            int limit = Math.min(k, cachedRanking.size());
            return new ArrayList<>(cachedRanking.subList(0, limit));
        } finally { lock.readLock().unlock(); }
    }

    /** Posizione 1-based del giocatore, -1 se non trovato. */
    public int getPosition(String username) {
        lock.readLock().lock();
        try {
            for (int i = 0; i < cachedRanking.size(); i++) {
                if (cachedRanking.get(i).getUsername().equals(username)) return i + 1;
            }
            return -1;
        } finally { lock.readLock().unlock(); }
    }

    public RankingEntry getPlayerRanking(String username) {
        lock.readLock().lock();
        try {
            for (RankingEntry entry : cachedRanking) {
                if (entry.getUsername().equals(username)) return entry;
            }
            return null;
        } finally { lock.readLock().unlock(); }
    }
}
