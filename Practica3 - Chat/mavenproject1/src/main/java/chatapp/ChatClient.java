package chatapp;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente en consola.
 * Comandos:
 *   /join <room> <maddr> <port>
 *   /leave <room>
 *   /rooms
 *   /msg <room> <texto...>
 *   /sticker <room> <ruta-img>
 *   /audio <room> <ruta-wav>
 *   /pm <usuario> <texto...>     (nuevo)
 *   /help
 *   /quit
 */
public class ChatClient {

    private final String username;
    private final InetAddress serverHost;
    private final int serverPort;
    private final DatagramSocket ctlSock;   // unicast al servidor JOIN/LEAVE
    private final DatagramSocket privSock;  // PM unicast entre clientes

    private final Map<String, Thread> threads = new ConcurrentHashMap<>();
    private final Map<String, RoomSession> sessions = new ConcurrentHashMap<>();

    // agenda de peers: usuario -> ip:puertoPriv
    private final Map<String, InetSocketAddress> peers = new ConcurrentHashMap<>();

    public ChatClient(String username, String serverHost, int serverPort) throws Exception {
        this.username = username;
        this.serverHost = InetAddress.getByName(serverHost);
        this.serverPort = serverPort;
        this.ctlSock = new DatagramSocket();
        this.privSock = new DatagramSocket(); // puerto aleatorio
        System.out.println("[CLIENT] Hola, "+username+" (server "+serverHost+":"+serverPort+")");
        System.out.println("[CLIENT] Puerto privado (PM): " + privSock.getLocalPort());

        // Hilo receptor de mensajes privados
        Thread pmThread = new Thread(this::privLoop, "pm-recv");
        pmThread.setDaemon(true);
        pmThread.start();
    }

    private void sendToServer(byte[] data) throws IOException {
        DatagramPacket p = new DatagramPacket(data, data.length, serverHost, serverPort);
        ctlSock.send(p);
    }

    private void joinRoom(String room, String maddr, int port) throws Exception {
        if (sessions.containsKey(room)){
            System.out.println("Ya estás en ["+room+"].");
            return;
        }
        // 1) Notificar al servidor para que difunda USER_LIST (incluimos mi puerto privado)
        sendToServer(Protocol.buildJoin(room, username, maddr, port, privSock.getLocalPort()));
        // 2) Abrir sesión/escucha multicast de la sala con callback de USER_LIST
        RoomSession rs = new RoomSession(room, maddr, port, username, this::onUserList);
        Thread t = new Thread(rs, "room-"+room);
        t.setDaemon(true);
        t.start();
        sessions.put(room, rs);
        threads.put(room, t);
    }

    private void leaveRoom(String room) throws IOException {
        RoomSession rs = sessions.remove(room);
        Thread t = threads.remove(room);
        if (rs != null){
            rs.close();
            sendToServer(Protocol.buildLeave(room, username));
        } else {
            System.out.println("No estabas en ["+room+"].");
        }
        if (t != null) t.interrupt();
    }

    private void listRooms(){
        if (sessions.isEmpty()) {
            System.out.println("(No estás en ninguna sala)");
        } else {
            System.out.println("Salas unidas:");
            sessions.keySet().forEach(r -> System.out.println(" - " + r));
        }
    }

    private void sendMsg(String room, String text) throws Exception {
        RoomSession rs = sessions.get(room);
        if (rs == null){
            System.out.println("No estás en ["+room+"].");
            return;
        }
        rs.sendText(text);
    }

    private void sendSticker(String room, File f) throws Exception {
        RoomSession rs = sessions.get(room);
        if (rs == null){ System.out.println("No estás en ["+room+"]."); return; }
        String mime = guessMime(f.getName(), "image/png");
        rs.sendSticker(f, mime);
    }

    private void sendAudio(String room, File f) throws Exception {
        RoomSession rs = sessions.get(room);
        if (rs == null){ System.out.println("No estás en ["+room+"]."); return; }
        String mime = guessMime(f.getName(), "audio/wav");
        rs.sendAudio(f, mime);
    }

    private static String guessMime(String name, String def){
        String low = name.toLowerCase(Locale.ROOT);
        if (low.endsWith(".png")) return "image/png";
        if (low.endsWith(".jpg") || low.endsWith(".jpeg")) return "image/jpeg";
        if (low.endsWith(".gif")) return "image/gif";
        if (low.endsWith(".webp")) return "image/webp";
        if (low.endsWith(".wav")) return "audio/wav";
        if (low.endsWith(".mp3")) return "audio/mpeg";
        return def;
    }

    /** Actualiza agenda con USER_LIST en formato "usuario@ip:puerto,usuario2@ip:puerto2". */
    private void onUserList(String room, String csv){
        if (csv == null || csv.isBlank()) return;
        String[] items = csv.split(",");
        for (String item : items){
            item = item.trim();
            if (item.isEmpty()) continue;
            String[] at = item.split("@", 2);
            String name = at[0];
            if (at.length == 2){
                String[] ap = at[1].split(":", 2);
                if (ap.length == 2){
                    try {
                        String ip = ap[0]; int port = Integer.parseInt(ap[1]);
                        peers.put(name, new InetSocketAddress(ip, port));
                    } catch (Exception ignored){}
                }
            }
        }
    }

    /** Loop que recibe PM por unicast. */
    private void privLoop(){
        byte[] buf = new byte[Protocol.MAX_DATAGRAM];
        while (!privSock.isClosed()){
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                privSock.receive(p);
                Protocol.Parsed pm = Protocol.parse(p.getData(), p.getLength());
                if (pm.type == Protocol.Type.PRIVATE){
                    System.out.println("\n[PM] " + pm.from + ": " + pm.text + "\n> ");
                }
            } catch (IOException ignored) {
            } catch (Exception ex){
                System.err.println("[CLIENT] Error PM: " + ex.getMessage());
            }
        }
    }

    /** Imprime la ayuda de comandos disponible. */
    private void printHelp() {
        System.out.println("""
                Comandos disponibles:
                  /join <sala> <multicast> <puerto>   Unirte a una sala (p.ej. /join general 230.0.0.1 5000)
                  /leave <sala>                       Salir de una sala
                  /rooms                              Ver salas a las que estás unido
                  /msg <sala> <texto...>              Enviar mensaje de texto (emojis incluidos)
                  /sticker <sala> <ruta-img>          Enviar imagen pequeña (~<60 KB)
                  /audio <sala> <ruta-wav>            Enviar audio WAV pequeño
                  /pm <usuario> <texto...>            Enviar mensaje privado (requiere que el usuario esté en la USER_LIST)
                  /help                               Mostrar esta ayuda
                  /quit                               Cerrar el cliente
                """);
    }

    public void repl() {
        try (Scanner sc = new Scanner(System.in)) {
            printHelp();
            System.out.print("> ");
            while (true){
                if (!sc.hasNextLine()) break;
                String line = sc.nextLine().trim();
                if (line.isEmpty()){ System.out.print("> "); continue; }

                if (line.startsWith("/join ")){
                    String[] p = line.split("\\s+");
                    if (p.length < 4){ System.out.println("Uso: /join <room> <maddr> <port>"); System.out.print("> "); continue; }
                    String room = p[1]; String maddr = p[2]; int port = Integer.parseInt(p[3]);
                    try { joinRoom(room, maddr, port); } catch (Exception e){ System.out.println("Error al unirse: "+e.getMessage()); }
                } else if (line.startsWith("/leave ")){
                    String[] p = line.split("\\s+");
                    if (p.length < 2){ System.out.println("Uso: /leave <room>"); System.out.print("> "); continue; }
                    String room = p[1];
                    try { leaveRoom(room); } catch (Exception e){ System.out.println("Error al salir: "+e.getMessage()); }
                    printHelp(); // ayuda automática al salir
                } else if (line.equals("/rooms")){
                    listRooms();
                } else if (line.startsWith("/msg ")){
                    int i2 = line.indexOf(' ', 5);
                    if (i2 < 0){ System.out.println("Uso: /msg <room> <texto...>"); System.out.print("> "); continue; }
                    String room = line.substring(5, i2).trim();
                    String txt = line.substring(i2+1);
                    try { sendMsg(room, txt); } catch (Exception e){ System.out.println("Error enviando mensaje: "+e.getMessage()); }
                } else if (line.startsWith("/sticker ")){
                    String[] p = line.split("\\s+", 3);
                    if (p.length < 3){ System.out.println("Uso: /sticker <room> <ruta-img>"); System.out.print("> "); continue; }
                    try { sendSticker(p[1], new File(p[2])); } catch (Exception e){ System.out.println("Error enviando sticker: "+e.getMessage()); }
                } else if (line.startsWith("/audio ")){
                    String[] p = line.split("\\s+", 3);
                    if (p.length < 3){ System.out.println("Uso: /audio <room> <ruta-wav>"); System.out.print("> "); continue; }
                    try { sendAudio(p[1], new File(p[2])); } catch (Exception e){ System.out.println("Error enviando audio: "+e.getMessage()); }
                } else if (line.startsWith("/pm ")){
                    int i = line.indexOf(' ', 4);
                    if (i < 0){ System.out.println("Uso: /pm <usuario> <texto...>"); System.out.print("> "); continue; }
                    String target = line.substring(4, i).trim();
                    String txt = line.substring(i+1);
                    InetSocketAddress dst = peers.get(target);
                    if (dst == null){
                        System.out.println("No tengo dirección de "+target+" (espera un USER_LIST o verifica que esté en alguna sala contigo).");
                    } else {
                        byte[] data = Protocol.buildPrivate(username, target, txt);
                        DatagramPacket pkt = new DatagramPacket(data, data.length, dst);
                        try { privSock.send(pkt); } catch (Exception e){ System.out.println("No se pudo enviar PM: "+e.getMessage()); }
                    }
                } else if (line.equals("/help")){
                    printHelp();
                } else if (line.equals("/quit")){
                    break;
                } else {
                    System.out.println("(comando desconocido; usa /help)");
                }
                System.out.print("> ");
            }
        } finally {
            // cierre de todas las salas
            for (String room : new ArrayList<>(sessions.keySet())){
                try { leaveRoom(room); } catch (Exception ignored) {}
            }
            privSock.close();
            ctlSock.close();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3){
            System.out.println("Uso: java chatapp.ChatClient <usuario> <serverHost> <serverPort>");
            System.out.println("Ejemplo: java chatapp.ChatClient Alice 127.0.0.1 4446");
            return;
        }
        String user = args[0];
        String host = args[1];
        int port = Integer.parseInt(args[2]);

        ChatClient c = new ChatClient(user, host, port);
        c.repl();
        System.out.println("Adiós.");
    }
}
