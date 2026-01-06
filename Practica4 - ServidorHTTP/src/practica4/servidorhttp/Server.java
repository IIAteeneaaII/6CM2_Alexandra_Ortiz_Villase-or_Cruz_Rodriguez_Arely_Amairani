/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package practica4.servidorhttp;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Scanner;

public class Server {
    // Puerto principal
    private static final int PORT = 8080;
    
    // Tamaño definido por el usuario
    public static int MAX_POOL_SIZE;
    
    // Contador atómico para rastrear conexiones en tiempo real
    public static AtomicInteger activeConnections = new AtomicInteger(0);
    
    private static ExecutorService pool;

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        
        // 1. El usuario define el tamaño del pool
        System.out.print("Ingrese el tamaño máximo del pool de conexiones: ");
        MAX_POOL_SIZE = sc.nextInt();
        
        // Inicializamos el pool con el tamaño elegido
        pool = Executors.newFixedThreadPool(MAX_POOL_SIZE);
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("--- SERVIDOR PRINCIPAL INICIADO ---");
            System.out.println("Puerto: " + PORT);
            System.out.println("Capacidad máxima: " + MAX_POOL_SIZE);
            System.out.println("Umbral de redirección (50%): " + (MAX_POOL_SIZE / 2));
            System.out.println("Esperando conexiones...\n");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                
                // Lógica de Redirección:
                // Si las conexiones actuales superan la mitad del tamaño definido
                if (activeConnections.get() >= (MAX_POOL_SIZE / 2)) {
                    System.out.println("[ALERTA] Pool al 50% o más (" + activeConnections.get() + "). Redireccionando...");
                    sendTemporaryRedirect(clientSocket, "http://localhost:8081");
                } else {
                    // Si hay espacio, incrementamos el contador y procesamos
                    activeConnections.incrementAndGet();
                    System.out.println("[INFO] Cliente aceptado. Conexiones activas: " + activeConnections.get());
                    pool.execute(new ClientHandler(clientSocket));
                }
            }
        } catch (IOException e) {
            System.err.println("Error crítico en el servidor: " + e.getMessage());
        }
    }

    /**
     * Envía una respuesta HTTP 307 para redirigir al navegador al segundo servidor
     */
    private static void sendTemporaryRedirect(Socket socket, String newUrl) {
        // Al usar Try-with-resources, 'out' y 'socket.getOutputStream()' 
        // se cerrarán automáticamente, lo que cierra el socket.
        try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println("HTTP/1.1 307 Temporary Redirect");
            out.println("Location: " + newUrl);
            out.println("Content-Length: 0");
            out.println("Connection: close");
            out.println();
            // out.flush(); // No es estrictamente necesario si usas 'true' en el constructor
        } catch (IOException e) {
            System.err.println("Error al intentar redireccionar: " + e.getMessage());
        }
    }
}
