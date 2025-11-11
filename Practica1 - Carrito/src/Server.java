import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private final int port;
    private final ConcurrentHashMap<Integer, Item> inventory = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    //Constructor de la clase Server
    public Server(int port) {
        this.port = port; //Puerto de conexion
        seedInventory(); //Se llena la estrctura inventory
    }

    //Llenado del hash Map con los datos del inventario
    private void seedInventory() {
        // ejemplo: id, tipo, nombre, precio, existencia
        inventory.put(1, new Item(1, "Electronicos", "Audifonos XYZ", 499.0, "JBL", 10));
        inventory.put(2, new Item(2, "Electronicos", "Cargador USB-C", 199.0, "TechFast", 15));
        inventory.put(3, new Item(3, "Ropa", "Playera Azul", 299.0, "WearIt\t", 8));
        inventory.put(4, new Item(4, "Hogar", "Taza Ceramica", 89.0, "DecoHome", 25));
        inventory.put(5, new Item(5, "Electronicos",	"Smartwatch Deportivo",	1599.0,	"Chronos",	5));
        inventory.put(6, new Item(6,	"Juguetes",	"Set de Bloques Armables",	350.0,	"Blocky",	12));
        inventory.put(7, new Item(7,	"Ropa",	"Jeans Slim Fit Negro",	799.0,	"DenimCo",	7));
        inventory.put(8, new Item(8,	"Hogar",	"Veladora Aromática Vainilla",	120.0,	"Scents",	30));
        inventory.put(9, new Item(9, "Libros",	"Novela de Misterio 'La Clave'",	390.0,	"Planeta",	20));
        inventory.put(10, new Item(10, "Electronicos", "Mouse Inalámbrico Ergonómico",	320.0,	"Periph",	18));
        inventory.put(11, new Item(11, "Deportes", "Tapete de Yoga Antideslizante",	450.0,	"FitLife",	14));
        inventory.put(12, new Item(12, "Hogar", "Set de 3 Cuchillos de Chef",	850.0,	"CutPro",	9));
        inventory.put(13, new Item(13, "Ropa", "Sudadera con Capucha Gris", 	650.0,	"Cozy",	11));
        inventory.put(14, new Item(14, "Libros", "Libro de Recetas Italianas",	410.0,	"GourmetPub",	6));
        inventory.put(15, new Item(15, "Electronicos", "Webcam Full HD",	550.0,	"ZoomTech",	16));
        inventory.put(16, new Item(16, "Belleza", "Crema Hidratante Facial",	280.0,	"Natura",	22));
        inventory.put(17, new Item(17, "Hogar", "Lámpara de Escritorio LED",	599.0,	"LightUp",	13));
        inventory.put(18, new Item(18, "Juguetes", "Drone Pequeño para Niños",	999.0,	"FlyFun",	4));
        inventory.put(19, new Item(19, "Deportes", "Botella de Agua Térmica 1L",	250.0,	"Hydro",	28));
        inventory.put(20, new Item(20, "Belleza", "Set de Brochas de Maquillaje",	399.0,	"BrushKit",	17)); 
    }

    //Metodo start de la clase server
    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) { // se crea el server Socket
            System.out.println("Servidor escuchando en puerto " + port);
            while (true) {
                Socket client = serverSocket.accept(); //En cuanto llegue una peticion de un cliente, esta se hacepta y se define un socket para esta conexion
                pool.submit(() -> handleClient(client)); //Se inicializa un hilo para ejecutar el metodo handle Client
            }
        } finally {
            pool.shutdown(); //Reutilizar hilo
        }
    }

    //Metodo handle Client
    private void handleClient(Socket socket) {
        String clientInfo = socket.getRemoteSocketAddress().toString();
        System.out.println("Conexión: " + clientInfo);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String line;
            // protocolo simple: comando args...
            // comandos: LIST_TIPO <tipo>, SHOW_ALL, CHECK <id>, ADD_TO_CART ... (cliente maneja carrito localmente)
            // FINALIZAR <user> <items> where items: id:qty,id:qty,...
            out.println("WELCOME"); // saludo
            while ((line = in.readLine()) != null) {
                String resp = processCommand(line.trim());
                out.println(resp);
            }
        } catch (Exception e) {
            System.err.println("Error cliente: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            System.out.println("Desconectado: " + clientInfo);
        }
    }

    private String processCommand(String line) {
        if (line.isEmpty()) return "ERROR Empty command";
        String[] parts = line.split(" ", 2);
        String cmd = parts[0].toUpperCase();
        String arg = parts.length > 1 ? parts[1].trim() : "";

        switch (cmd) {
            case "SHOW_ALL":
                return showAll();
            case "LIST_TIPO":
                return listTipo(arg);
            case "SEARCH":
                return searchByNameOrBrand(arg);
            case "CHECK":
                return checkItem(arg);
            case "FINALIZAR":
                return finalizarCompra(arg);
            default:
                return "ERROR Unknown command";
        }
    }

    private String showAll() {
        StringBuilder sb = new StringBuilder();
        inventory.values().forEach(i -> sb.append(i.toString()).append("\n"));
        return "OK\n" + sb.toString();
    }

    private String listTipo(String tipo) {
        if (tipo.isEmpty()) return "ERROR Tipo vacío";
        StringBuilder sb = new StringBuilder();
        inventory.values().stream()
                .filter(i -> i.getTipo().equalsIgnoreCase(tipo))
                .forEach(i -> sb.append(i.toString()).append("\n"));
        return "OK\n" + sb.toString();
    }

    private String searchByNameOrBrand(String query) {
    if (query.isEmpty()) return "ERROR Consulta vacía";
    StringBuilder sb = new StringBuilder();
    String q = query.toLowerCase();
    inventory.values().stream()
            .filter(i -> i.getNombre().toLowerCase().contains(q) || i.getMarca().toLowerCase().contains(q))
            .forEach(i -> sb.append(i.toString()).append("\n"));
    return sb.length() > 0 ? "OK\n" + sb.toString() : "ERROR No se encontraron coincidencias";
}

    private String checkItem(String arg) {
        try {
            int id = Integer.parseInt(arg);
            Item it = inventory.get(id);
            if (it == null) return "ERROR Item no encontrado";
            return "OK " + it.getExistencia();
        } catch (NumberFormatException e) {
            return "ERROR id inválido";
        }
    }

    /**
     * FINALIZAR formato: <usuario> <id:qty,id:qty,...>
     * Ej: FINALIZAR leonardo 1:2,3:1
     *
     * Las validaciones y decrementos se hacen de forma atómica sincronizando
     * sobre un lock global para mantenerlo simple (alternativa: locks por item).
     */
    private final Object compraLock = new Object();

    private String finalizarCompra(String arg) {
        if (arg.isEmpty()) return "ERROR formato FINALIZAR <user> <items>";
        String[] parts = arg.split(" ", 2);
        if (parts.length < 2) return "ERROR formato FINALIZAR <user> <items>";
        String user = parts[0];
        String itemsStr = parts[1];

        Map<Integer, Integer> deseos = new HashMap<>();
        try {
            String[] pares = itemsStr.split(",");
            for (String p : pares) {
                String[] kv = p.split(":");
                int id = Integer.parseInt(kv[0].trim());
                int qty = Integer.parseInt(kv[1].trim());
                if (qty <= 0) return "ERROR cantidad inválida para id " + id;
                deseos.put(id, qty);
            }
        } catch (Exception e) {
            return "ERROR formato items inválido. Ej: 1:2,3:1";
        }

        // bloqueo global de compra para validar y decrementar atomícamente
        synchronized (compraLock) {
            // validar existencias
            for (Map.Entry<Integer, Integer> e : deseos.entrySet()) {
                Item it = inventory.get(e.getKey());
                if (it == null) return "ERROR item " + e.getKey() + " no existe";
                if (it.getExistencia() < e.getValue()) {
                    return "ERROR no hay suficiente stock para item " + e.getKey() + ". disponible=" + it.getExistencia();
                }
            }
            // decrementar
            double total = 0.0;
            for (Map.Entry<Integer, Integer> e : deseos.entrySet()) {
                Item it = inventory.get(e.getKey());
                boolean ok = it.decrementar(e.getValue());
                if (!ok) {
                    // rollback parcial: para simplicidad incrementamos lo que ya se decrementó
                    // (pero con el lock global esto no debería pasar)
                    for (Map.Entry<Integer, Integer> rolled : deseos.entrySet()) {
                        if (rolled.getKey() == e.getKey()) break;
                        inventory.get(rolled.getKey()).incrementar(rolled.getValue());
                    }
                    return "ERROR al decrementar item " + e.getKey();
                }
                total += it.getPrecio() * e.getValue();
            }
            // generar ticket y devolverlo (como texto)
            Ticket ticket = new Ticket(user, deseos, total);
            return "OK\n" + ticket.toString();
        }
    }

    //Instancia de la clase Server
    public static void main(String[] args) throws IOException {
        int port = 5555;
        Server s = new Server(port);
        s.start();
    }
}
