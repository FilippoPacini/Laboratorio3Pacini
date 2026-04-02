package server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stato di un giocatore in una partita.
 * Sincronizzato su this (lock fine-grained per giocatore).
 */
public class PlayerGameState {

    public enum Status { IN_PROGRESS, WON, LOST, TIME_UP }

    private final String username;
    private final List<GameGroup> correctGroups;
    private int wrongAttempts;
    private int score;
    private Status status;
    private boolean sentProposal;

    private static final int[] CORRECT_BONUS = {0, 6, 12, 18};
    private static final int WRONG_PENALTY = -4;
    private static final int MAX_WRONG = 4;
    private static final int GROUPS_TO_WIN = 3;

    public PlayerGameState(String username) {
        this.username = username;
        this.correctGroups = new ArrayList<>();
        this.wrongAttempts = 0;
        this.score = 0;
        this.status = Status.IN_PROGRESS;
        this.sentProposal = false;
    }

    public synchronized void addCorrectGroup(GameGroup group) {
        correctGroups.add(group);
        sentProposal = true;
        recalculateScore();
        if (correctGroups.size() >= GROUPS_TO_WIN) status = Status.WON;
    }

    public synchronized void addWrongAttempt() {
        wrongAttempts++;
        sentProposal = true;
        recalculateScore();
        if (wrongAttempts >= MAX_WRONG) status = Status.LOST;
    }

    private void recalculateScore() {
        int correctCount = correctGroups.size();
        int bonus = (correctCount >= 0 && correctCount < CORRECT_BONUS.length)
                ? CORRECT_BONUS[correctCount] : 0;
        score = bonus + (wrongAttempts * WRONG_PENALTY);
    }

    public synchronized void timeUp() {
        if (status == Status.IN_PROGRESS) status = Status.TIME_UP;
    }

    public synchronized Status getStatus() { return status; }
    public synchronized int getScore() { return score; }
    public synchronized int getWrongAttempts() { return wrongAttempts; }
    public synchronized int getCorrectCount() { return correctGroups.size(); }
    public synchronized List<GameGroup> getCorrectGroups() {
        return Collections.unmodifiableList(new ArrayList<>(correctGroups));
    }
    public synchronized boolean hasSentProposal() { return sentProposal; }
    public synchronized boolean isFinished() { return status != Status.IN_PROGRESS; }
    public String getUsername() { return username; }
}
