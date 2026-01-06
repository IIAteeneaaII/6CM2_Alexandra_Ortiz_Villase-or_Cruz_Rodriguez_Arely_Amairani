/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package practica4.servidorhttp;

import java.io.*;
import java.net.*;

public class BackupServer {
    public static void main(String[] args) {
        int port = 8081;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println(">>> SERVIDOR DE RESPALDO ACTIVO EN PUERTO " + port + " <<<");
            
            while (true) {
                try (Socket client = serverSocket.accept();
                     PrintWriter out = new PrintWriter(client.getOutputStream())) {
                    
                    System.out.println("Atendiendo petición redirigida...");
                    
                    out.println("HTTP/1.1 200 OK");
                    out.println("Content-Type: text/html; charset=UTF-8");
                    out.println();
                    out.println("<!DOCTYPE html><html><head><title>Respaldo</title></head>");
                    out.println("<body style='font-family:sans-serif; text-align:center; padding-top:50px;'>");
                    out.println("<h1 style='color:#741E41;'>Servidor de Respaldo</h1>");
                    out.println("<p>Has sido redirigido porque el servidor principal alcanzó el 50% de su capacidad.</p>");
                    out.println("<a href='http://localhost:8080'>Intentar volver al principal</a>");
                    out.println("</body></html>");
                    out.flush();
                }
            }
        } catch (IOException e) {
            System.err.println("Error en Backup Server: " + e.getMessage());
        }
    }
}
