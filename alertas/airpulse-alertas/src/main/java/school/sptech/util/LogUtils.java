package school.sptech.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logging centralizado no formato único do projeto:
 * {@code yyyy-MM-dd HH:mm:ss - LEVEL - mensagem}.
 * <p>
 * Usado por {@code Main} (produção), {@code BackfillMain}/{@code BackfillRunner}
 * (backfill) e pelos serviços, para que uma mudança de formato seja feita
 * em um único lugar.
 */
public final class LogUtils {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private LogUtils() {
    }

    public static void info(String msg) {
        System.out.println(agora() + " - INFO - " + msg);
    }

    public static void error(String msg) {
        System.err.println(agora() + " - ERROR - " + msg);
    }

    public static void warn(String msg) {
        System.err.println(agora() + " - WARN - " + msg);
    }

    private static String agora() {
        return LocalDateTime.now().format(FMT);
    }
}