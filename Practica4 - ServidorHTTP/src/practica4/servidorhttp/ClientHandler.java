/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package practica4.servidorhttp;

import java.io.*;
import java.net.Socket;
import java.util.StringTokenizer;

public class ClientHandler implements Runnable {
    private final Socket connect;
    private static final String WEB_ROOT = "www"; // Carpeta donde estarán tus archivos

    public ClientHandler(Socket c) {
        this.connect = c;
    }

    @Override
    public void run() {
        BufferedReader in = null;
        PrintWriter out = null;
        BufferedOutputStream dataOut = null;
        
        try {
            in = new BufferedReader(new InputStreamReader(connect.getInputStream()));
            out = new PrintWriter(connect.getOutputStream());
            dataOut = new BufferedOutputStream(connect.getOutputStream());

            String input = in.readLine();
            if (input == null || input.isEmpty()) return;

            StringTokenizer parse = new StringTokenizer(input);
            if (!parse.hasMoreTokens()) return;

            String method = parse.nextToken().toUpperCase(); 
            String fileRequested = parse.nextToken();

            // SINTAXIS ALTERNATIVA CON IF-ELSE (Elimina errores de Switch)
            switch (method) {
                case "GET" -> processGet(fileRequested, out, dataOut);
                case "POST" -> processPost(in, out, dataOut);
                case "PUT" -> processPut(fileRequested, in, out, dataOut);
                case "DELETE" -> processDelete(fileRequested, out, dataOut);
                default -> sendError(out, dataOut, "501 Not Implemented");
            }

        } catch (IOException ioe) {
            System.err.println("Error en el servidor: " + ioe.getMessage());
        } finally {
            // 1. Lo primero es liberar el espacio en el pool para otros clientes
            try {
                if (Server.activeConnections != null) {
                    Server.activeConnections.decrementAndGet();
                }
            } catch (Exception e) {
                System.err.println("Error al decrementar contador: " + e.getMessage());
            }

            // 2. Cerramos los recursos
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (dataOut != null) dataOut.close();
                if (connect != null) connect.close();
                
                System.out.println("[Pool] Conexión liberada. Activas: " + Server.activeConnections.get());
            } catch (Exception e) {
                System.err.println("Error al cerrar streams: " + e.getMessage());
            }
        }
    }
    
    // Aquí irán los métodos processGet, processPost, etc.
    private String getContentType(String fileRequested) {
        if (fileRequested.endsWith(".html")) return "text/html";
        if (fileRequested.endsWith(".css")) return "text/css";
        if (fileRequested.endsWith(".json")) return "application/json";
        if (fileRequested.endsWith(".jpg") || fileRequested.endsWith(".jpeg")) return "image/jpeg";
        if (fileRequested.endsWith(".png")) return "image/png";
        if (fileRequested.endsWith(".ttf")) return "font/ttf";
        if (fileRequested.endsWith(".svg")) return "image/svg+xml";
        return "text/plain";
    }
    
    private byte[] readFileData(File file, int fileLength) throws IOException {
        FileInputStream fileIn = null;
        byte[] fileData = new byte[fileLength];
        try {
            fileIn = new FileInputStream(file);
            fileIn.read(fileData);
        } finally {
            if (fileIn != null) fileIn.close();
        }
        return fileData;
    }
    
    private void sendError(PrintWriter out, BufferedOutputStream dataOut, String errorCode) throws IOException {
    // Definimos un cuerpo HTML sencillo para el error
        String errorHtml = "<html><head><title>Error</title></head><body>" +
                           "<h1>Error: " + errorCode + "</h1>" +
                           "<p>El servidor no pudo procesar la solicitud.</p>" +
                           "</body></html>";
        byte[] errorData = errorHtml.getBytes();

        // Enviamos las cabeceras de error
        out.println("HTTP/1.1 " + errorCode);
        out.println("Server: Java HTTP Server : 1.0");
        out.println("Content-type: text/html");
        out.println("Content-length: " + errorData.length);
        out.println(); // Línea en blanco obligatoria
        out.flush();

        // Enviamos el cuerpo del error
        dataOut.write(errorData, 0, errorData.length);
        dataOut.flush();
    }
    
    private void processGet(String fileRequested, PrintWriter out, BufferedOutputStream dataOut) throws IOException {
        if (fileRequested.endsWith("/")) fileRequested += "index.html";

        File file = new File(WEB_ROOT, fileRequested);
        int fileLength = (int) file.length();
        String content = getContentType(fileRequested);

        if (file.exists()) {
            byte[] fileData = readFileData(file, fileLength);

            // Cabeceras HTTP
            out.println("HTTP/1.1 200 OK");
            out.println("Server: Java HTTP Server : 1.0");
            out.println("Content-type: " + content);
            out.println("Content-length: " + fileLength);
            out.println(); // Línea en blanco obligatoria entre headers y body
            out.flush();

            dataOut.write(fileData, 0, fileLength);
            dataOut.flush();
        } else {
            sendError(out, dataOut, "404 Not Found");
        }
    }
    
    private void processPost(BufferedReader in, PrintWriter out, BufferedOutputStream dataOut) throws IOException {
        String headerLine;
        int contentLength = -1;

        // 1. Validar Headers
        while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
            if (headerLine.startsWith("Content-Length:")) {
                contentLength = Integer.parseInt(headerLine.substring(15).trim());
            }
        }

        // Error si no hay Content-Length (Requisito para POST estándar)
        if (contentLength <= 0) {
            sendError(out, dataOut, "411 Length Required");
            return;
        }

        // 2. Leer Body
        char[] body = new char[contentLength];
        int bytesRead = in.read(body, 0, contentLength);

        if (bytesRead != contentLength) {
            sendError(out, dataOut, "400 Bad Request (Incomplete Body)");
            return;
        }

        String response = "Recurso creado exitosamente vía POST";
        byte[] responseBytes = response.getBytes();

        // 3. Responder usando dataOut para ser consistentes
        out.println("HTTP/1.1 201 Created");
        out.println("Content-Type: text/plain");
        out.println("Content-Length: " + responseBytes.length);
        out.println();
        out.flush();

        dataOut.write(responseBytes);
        dataOut.flush();
    }

    private void processPut(String fileRequested, BufferedReader in, PrintWriter out, BufferedOutputStream dataOut) throws IOException {
    String headerLine;
    int contentLength = -1;

    // 1. Leer Headers para obtener el tamaño
    while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
        if (headerLine.startsWith("Content-Length:")) {
            contentLength = Integer.parseInt(headerLine.substring(15).trim());
        }
    }

    if (contentLength < 0) {
        sendError(out, dataOut, "411 Length Required");
        return;
    }

    // 2. Limpiar el nombre del archivo solicitado
    String fileName = fileRequested;
    if (fileName.startsWith("/")) {
        fileName = fileName.substring(1); // Quitar la barra inicial
    }
    
    // Si la ruta está vacía (solo enviaron /), asignamos un nombre por defecto
    if (fileName.isEmpty()) {
        fileName = "archivo_sin_nombre.txt";
    }

    // 3. Leer el cuerpo del mensaje
    char[] body = new char[contentLength];
    int read = 0;
    while (read < contentLength) {
        int result = in.read(body, read, contentLength - read);
        if (result == -1) break;
        read += result;
    }

    // 4. Guardar con el nombre original en WEB_ROOT
    File fileToSave = new File(WEB_ROOT, fileName);
    
    // Usamos FileWriter para texto o FileOutputStream si quisieras soportar binarios (imágenes)
    try (FileWriter writer = new FileWriter(fileToSave)) {
        writer.write(body);

        String msg = "Archivo '" + fileName + "' guardado/actualizado correctamente";
        out.println("HTTP/1.1 200 OK");
        out.println("Content-Type: text/plain");
        out.println("Content-Length: " + msg.length());
        out.println();
        out.flush();

        dataOut.write(msg.getBytes());
        dataOut.flush();
        
        System.out.println("[PUT] Archivo creado: " + fileToSave.getAbsolutePath());
    } catch (IOException e) {
        sendError(out, dataOut, "500 Internal Server Error (Error de escritura)");
    }
}

    private void processDelete(String fileRequested, PrintWriter out, BufferedOutputStream dataOut) throws IOException {
    // 1. Limpiar la ruta: si empieza con '/', quitarlo para que sea relativo a WEB_ROOT
    String path = fileRequested;
    if (path.startsWith("/")) {
        path = path.substring(1);
    }

    File file = new File(WEB_ROOT, path);
    
    // Debug para que veas en la consola de NetBeans qué está intentando borrar
    System.out.println("[DELETE] Intentando borrar: " + file.getAbsolutePath());

    if (file.exists() && !file.isDirectory()) {
        if (file.delete()) {
            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: text/plain"); // Es bueno agregar el tipo
            out.println();
            out.println("Archivo eliminado exitosamente");
        } else {
            sendError(out, dataOut, "500 Internal Server Error");
        }
    } else {
        // Si entra aquí, es que file.exists() es falso
        sendError(out, dataOut, "404 Not Found");
    }
    out.flush();
}
}