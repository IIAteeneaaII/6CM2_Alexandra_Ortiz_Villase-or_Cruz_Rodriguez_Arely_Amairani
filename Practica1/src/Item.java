import java.io.Serializable;

public class Item implements Serializable {
    private final int id;
    private final String tipo; // categoría o tipo
    private final String nombre;
    private final double precio;
    private int existencia;

    public Item(int id, String tipo, String nombre, double precio, int existencia) {
        this.id = id;
        this.tipo = tipo;
        this.nombre = nombre;
        this.precio = precio;
        this.existencia = existencia;
    }

    public int getId() { return id; }
    public String getTipo() { return tipo; }
    public String getNombre() { return nombre; }
    public double getPrecio() { return precio; }

    public synchronized int getExistencia() { return existencia; }

    public synchronized boolean decrementar(int cantidad) {
        if (cantidad <= 0) return false;
        if (existencia >= cantidad) {
            existencia -= cantidad;
            return true;
        }
        return false;
    }

    public synchronized void incrementar(int cantidad) {
        existencia += cantidad;
    }

    @Override
    public String toString() {
        return String.format("ID:%d | %s - %s | $%.2f | stock:%d", id, tipo, nombre, precio, existencia);
    }
}
