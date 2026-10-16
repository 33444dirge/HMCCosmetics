package com.hibiscusmc.hmccosmetics.database;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.config.section.DatabaseSettings;
import com.hibiscusmc.hmccosmetics.database.types.Data;
import com.hibiscusmc.hmccosmetics.database.types.MySQLData;
import com.hibiscusmc.hmccosmetics.database.types.NoneData;
import com.hibiscusmc.hmccosmetics.database.types.SQLiteData;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.CosmeticUsers;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import com.hibiscusmc.hmccosmetics.util.ShutdownHandoff;
import lombok.Getter;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

public class Database {

    @Getter
    private static Data data;
    private static final MySQLData MYSQL_DATA = new MySQLData();
    private static final SQLiteData SQLITE_DATA = new SQLiteData();
    private static final NoneData NONE_DATA = new NoneData();
    private static final Set<CompletableFuture<?>> pendingOperations = new HashSet<>();
    private static CompletableFuture<Void> lastWrite = CompletableFuture.completedFuture(null);
    private static CompletableFuture<Void> priorHandoff = CompletableFuture.completedFuture(null);
    private static CompletableFuture<Void> closeFuture = new CompletableFuture<>();
    private static Throwable writeFailure;
    private static boolean closing;
    private static boolean closed;

    public Database() {
        CompletableFuture<Void> prior = ShutdownHandoff.awaitPrior();
        synchronized (Database.class) {
            closing = false;
            closed = false;
            pendingOperations.clear();
            lastWrite = CompletableFuture.completedFuture(null);
            priorHandoff = prior;
            closeFuture = new CompletableFuture<>();
            writeFailure = null;
        }
        String databaseType = DatabaseSettings.getDatabaseType();
        data = SQLITE_DATA; // default to SQLite, then check if it's anything different
        switch (databaseType.toLowerCase()) {
            case "mysql":
                data = MYSQL_DATA;
                break;
            case "sqlite":
                // already the default
                break;
            case "none":
                data = NONE_DATA;
                MessagesUtil.sendDebugMessages("Database is set to none. Data will not be saved.", Level.WARNING);
                break;
            default:
                MessagesUtil.sendDebugMessages("Invalid database type. Defaulting to SQLite.", Level.WARNING);
        }
        MessagesUtil.sendDebugMessages("Database is " + data);

        setup();
    }

    public static void setup() {
        data.setup();
    }

    public static void save(CosmeticUser user) {
        data.save(user);
    }

    public static void save(Player player) {
        data.save(CosmeticUsers.getUser(player));
    }

    public static synchronized CompletableFuture<UserData> get(UUID uniqueId) {
        if (closing) return CompletableFuture.failedFuture(new IllegalStateException("Cosmetic database is closing"));
        CompletableFuture<UserData> read = priorHandoff.thenCompose(ignored -> {
            synchronized (Database.class) {
                if (closing) return CompletableFuture.failedFuture(
                    new IllegalStateException("Cosmetic database closed before prior hot-reload handoff"));
                return data.get(uniqueId);
            }
        });
        pendingOperations.add(read);
        read.whenComplete((result, error) -> {
            synchronized (Database.class) {
                pendingOperations.remove(read);
                closeIfIdle();
            }
        });
        return read;
    }

    /** Keep writes alive across plugin disable and close the connection after the last one. */
    public static synchronized CompletableFuture<Void> submitWrite(Runnable write) {
        if (closing) {
            IllegalStateException error = new IllegalStateException("Cosmetic database is closing");
            HMCCosmeticsPlugin.getInstance().getLogger().log(Level.WARNING, "Rejected cosmetic database write", error);
            return CompletableFuture.failedFuture(error);
        }
        CompletableFuture<Void> result = lastWrite.handle((ignored, error) -> null)
            .thenCompose(ignored -> priorHandoff).thenRunAsync(write);
        lastWrite = result;
        pendingOperations.add(result);
        result.whenComplete((ignored, error) -> {
            synchronized (Database.class) {
                if (error != null && writeFailure == null) writeFailure = error;
                pendingOperations.remove(result);
                closeIfIdle();
            }
            if (error != null) HMCCosmeticsPlugin.getInstance().getLogger().log(Level.SEVERE,
                "Unable to save cosmetic database data", error);
        });
        return result;
    }

    public static void clearData(UUID uniqueId) {
        data.clear(uniqueId);
    }

    public static synchronized CompletableFuture<Void> close() {
        closing = true;
        closeIfIdle();
        return closeFuture;
    }

    private static void closeIfIdle() {
        if (closing && !closed && pendingOperations.isEmpty()) {
            closed = true;
            Throwable failure = writeFailure;
            try {
                if (data != null) data.close();
            } catch (Throwable ex) {
                if (failure == null) failure = ex;
                else failure.addSuppressed(ex);
            }
            if (failure == null) closeFuture.complete(null);
            else closeFuture.completeExceptionally(failure);
        }
    }
}
