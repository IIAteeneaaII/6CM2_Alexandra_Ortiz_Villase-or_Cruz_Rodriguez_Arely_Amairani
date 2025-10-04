import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class Ticket {
    private final String user;
    private final Map<Integer, Integer> detalles;
    private final double total;
    private final LocalDateTime fecha;

    public Ticket(String user, Map<Integer, Integer> detalles, double total) {
        this.user = user;
        this.detalles = detalles;
        this.total = total;
        this.fecha = LocalDateTime.now();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TICKET\n");
        sb.append("Usuario: ").append(user).append("\n");
        sb.append("Fecha: ").append(fecha.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        sb.append("Detalles:\n");
        detalles.forEach((id, qty) -> sb.append("  Item ID: ").append(id).append(" x ").append(qty).append("\n"));
        sb.append(String.format("TOTAL: $%.2f\n", total));
        return sb.toString();
    }
}
