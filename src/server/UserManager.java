package server;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/** Gestisce gli utenti registrati. ConcurrentHashMap per registrazione/login concorrenti. */
public class UserManager {

    private final ConcurrentHashMap<String, UserRecord> users;

    public UserManager() {
        this.users = new ConcurrentHashMap<>();
    }

    /** return null se ok, messaggio errore altrimenti */
    public String register(String username, String password) {
        if (username == null || username.isEmpty()) return "Nome utente vuoto";
        if (password == null || password.isEmpty()) return "Password vuota";

        UserRecord existing = users.putIfAbsent(username, new UserRecord(username, password));
        return (existing != null) ? "Nome utente gia' registrato" : null;
    }

    /** @return l'UserRecord se le credenziali sono corrette, null altrimenti */
    public UserRecord authenticate(String username, String password) {
        UserRecord user = users.get(username);
        if (user == null || !user.checkPassword(password)) return null;
        return user;
    }

    /** @return null se ok, messaggio errore altrimenti */
    public String updateCredentials(String oldName, String newName, String oldPsw, String newPsw) {
        UserRecord user = users.get(oldName);
        if (user == null) return "Utente non trovato";
        if (!user.checkPassword(oldPsw)) return "Password attuale errata";

        if (newPsw != null && !newPsw.isEmpty()) {
            user.setPassword(newPsw);
        }

        if (newName != null && !newName.isEmpty() && !newName.equals(oldName)) {
            UserRecord conflict = users.putIfAbsent(newName, user);
            if (conflict != null) return "Nuovo nome utente gia' registrato";
            user.setUsername(newName);
            users.remove(oldName);
        }

        return null;
    }

    public UserRecord getUser(String username) { return users.get(username); }
    public Collection<UserRecord> getAllUsers() { return users.values(); }
    public boolean isRegistered(String username) { return users.containsKey(username); }
    public void loadUsers(ConcurrentHashMap<String, UserRecord> loadedUsers) { users.putAll(loadedUsers); }
    public ConcurrentHashMap<String, UserRecord> getUsersMap() { return users; }
}
