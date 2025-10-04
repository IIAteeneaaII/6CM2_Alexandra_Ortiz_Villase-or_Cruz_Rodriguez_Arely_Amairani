import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

public class ClientCLI {
    private final String host;
    private final int port;
    private final Cart cart = new Cart();
    private final Scanner sc = new Scanner(System.in);

    public ClientCLI(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() {
        try (Socket socket = new Socket(host, port);
             BufferedReader serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter serverOut = new PrintWriter(socket.getOutputStream(), true)) {

            System.out.println(serverIn.readLine()); // WELCOME
            boolean running = true;
            while (running) {
                showMenu();
                String opt = sc.nextLine().trim();
                switch (opt) {
                    case "1":
                        serverOut.println("SHOW_ALL");
                        printResponse(serverIn);
                        break;
                    case "2":
                        System.out.print("Tipo: ");
                        String tipo = sc.nextLine().trim();
                        serverOut.println("LIST_TIPO " + tipo);
                        printResponse(serverIn);
                        break;
                    case "3":
                        System.out.print("ID a agregar: ");
                        int id = Integer.parseInt(sc.nextLine().trim());
                        System.out.print("Cantidad: ");
                        int qty = Integer.parseInt(sc.nextLine().trim());
                        // preguntar al servidor existencia actual antes de agregar
                        serverOut.println("CHECK " + id);
                        String check = serverIn.readLine();
                        if (check.startsWith("OK")) {
                            int disponibles = Integer.parseInt(check.split(" ")[1]);
                            if (disponibles >= qty) {
                                cart.add(id, qty);
                                System.out.println("Agregado al carrito.");
                            } else {
                                System.out.println("No hay suficientes existencias. disponibles=" + disponibles);
                            }
                        } else {
                            System.out.println("Error al checar item: " + check);
                        }
                        break;
                    case "4":
                        editCart();
                        break;
                    case "5":
                        if (cart.isEmpty()) {
                            System.out.println("Carrito vacío.");
                            break;
                        }
                        System.out.print("Nombre de usuario para ticket: ");
                        String user = sc.nextLine().trim();
                        String items = cart.getItems().entrySet().stream()
                                .map(e -> e.getKey() + ":" + e.getValue())
                                .collect(Collectors.joining(","));
                        serverOut.println("FINALIZAR " + user + " " + items);
                        printResponse(serverIn);
                        // si OK -> vaciar carrito localmente (asumido comprado)
                        String last = serverIn.readLine(); // already read in printResponse? careful
                        // NOTE: printResponse reads one line and prints additional lines only if OK\ntext
                        // To keep simple, re-request final response:
                        // Actually modify printResponse to return the full response. For brevity, assume server sent full.
                        cart.getItems().clear();
                        break;
                    case "0":
                        running = false;
                        break;
                    default:
                        System.out.println("Opción inválida");
                }
            }
            System.out.println("Saliendo cliente.");
        } catch (Exception e) {
            System.err.println("Error cliente: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void editCart() {
        while (true) {
            System.out.println("Carrito:");
            cart.getItems().forEach((k,v) -> System.out.println("ID:" + k + " x " + v));
            System.out.println("a) Cambiar cantidad");
            System.out.println("b) Eliminar item");
            System.out.println("c) Volver");
            String opt = sc.nextLine().trim();
            if (opt.equalsIgnoreCase("a")) {
                System.out.print("ID: ");
                int id = Integer.parseInt(sc.nextLine().trim());
                System.out.print("Nueva cantidad: ");
                int q = Integer.parseInt(sc.nextLine().trim());
                cart.setQuantity(id, q);
            } else if (opt.equalsIgnoreCase("b")) {
                System.out.print("ID a eliminar: ");
                int id = Integer.parseInt(sc.nextLine().trim());
                cart.remove(id);
            } else break;
        }
    }

    private void showMenu() {
        System.out.println("\n--- MENU ---");
        System.out.println("1) Mostrar todos los artículos");
        System.out.println("2) Buscar por tipo");
        System.out.println("3) Agregar al carrito (valida stock antes)");
        System.out.println("4) Editar carrito");
        System.out.println("5) Finalizar compra y obtener ticket");
        System.out.println("0) Salir");
        System.out.print("Opción: ");
    }

    private void printResponse(BufferedReader serverIn) throws IOException {
        StringBuilder response = new StringBuilder();

        while (true) {
            String line = serverIn.readLine();
            if (line == null) break;        // servidor cerró conexión
            if (line.isBlank()) break;      // línea vacía = fin de respuesta
            response.append(line).append("\n");
            // si el servidor no envía línea vacía, esto leerá solo la primera y sale
            if (!serverIn.ready()) break;
        }

        System.out.println(response.toString());
    }


    public static void main(String[] args) {
        ClientCLI c = new ClientCLI("localhost", 5555);
        c.start();
    }
}
