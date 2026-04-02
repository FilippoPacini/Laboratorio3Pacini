package server;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/** Gestisce le sessioni degli utenti loggati. ConcurrentHashMap per login/logout concorrenti. */
public class SessionManager {

    private final ConcurrentHashMap<String, SessionInfo> sessions;

    public SessionManager() {
        this.sessions = new ConcurrentHashMap<>();
    }

    /** @return null se ok, messaggio di errore se l'utente e' gia' loggato */
    public String login(SessionInfo session) {
        SessionInfo existing = sessions.putIfAbsent(session.getUsername(), session);
        return (existing != null) ? "Utente gia' loggato" : null;
    }

    public SessionInfo logout(String username) {
        return sessions.remove(username);
    }

    public boolean isLoggedIn(String username) { return sessions.containsKey(username); }
    public SessionInfo getSession(String username) { return sessions.get(username); }
    public Collection<SessionInfo> getAllSessions() { return sessions.values(); }
    public int getLoggedInCount() { return sessions.size(); }
}
