package chatapp;

import java.io.IOException;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Servidor:
 * - Recibe JOIN/LEAVE por UDP unicast (serverPort).
 * - Retransmite USER_LIST por multicast a la sala correspondiente.
 * - No toca tráfico de chat; solo listas de usuarios.
 */
public class ChatServer {

    private static class RoomInfo {
        final String room;
        final String maddr;
        final int port;
        // usuario -> "ip:privPort"
        final Map<String, String> users = new ConcurrentHashMap<>();

        RoomInfo(String room, String maddr, int port){
            this.room = room; this.maddr = maddr; this.port = port;
        }

        String usersCSV(){
            return users.entrySet().stream()
                    .map(e -> e.getKey()+"@"+e.getValue())
                    .collect(Collectors.joining(","));
        }
    }

    private final int serverPort;
    private final Map<String, RoomInfo> rooms = new ConcurrentHashMap<>();
    private final DatagramSocket serverSock;

    public ChatServer(int port) throws SocketException {
        this.serverPort = port;
        this.serverSock = new DatagramSocket(serverPort);
        System.out.println("[SERVER] UDP escuchando en puerto " + serverPort);
        System.out.println("[SERVER] Función única: retransmitir USER_LIST al entrar/salir.");
    }

    private void handleJoin(Protocol.Parsed pm, InetAddress senderAddr){
        String room = pm.room;
        String from = pm.from;
        String maddr = Protocol.metaGet(pm.meta, "maddr");
        String portStr = Protocol.metaGet(pm.meta, "port");
        String privStr = Protocol.metaGet(pm.meta, "priv"); // puede venir null (cliente viejo)
        if (maddr == null || portStr == null){
            System.out.println("[SERVER] JOIN malformado de "+from+" room="+room+" meta="+pm.meta);
            return;
        }
        int port = Integer.parseInt(portStr);
        int priv = 0;
        try { if (privStr != null) priv = Integer.parseInt(privStr); } catch (Exception ignored){}

        RoomInfo info = rooms.computeIfAbsent(room, r -> new RoomInfo(r, maddr, port));
        String ip = senderAddr.getHostAddress();
        info.users.put(from, ip + ":" + priv);

        System.out.println("[SERVER] "+from+" se unió a ["+room+
                "] ("+maddr+":"+port+") → usuarios="+info.usersCSV());
        broadcastUserList(info);
    }

    private void handleLeave(Protocol.Parsed pm){
        RoomInfo info = rooms.get(pm.room);
        if (info == null) return;
        info.users.remove(pm.from);
        System.out.println("[SERVER] "+pm.from+" salió de ["+pm.room+"] → usuarios="+info.usersCSV());
        broadcastUserList(info);
    }

    private void broadcastUserList(RoomInfo info){
        try (MulticastSocket ms = new MulticastSocket()){ // enviar (TTL 1)
            ms.setTimeToLive(1);
            byte[] data = Protocol.buildUserList(info.room, info.usersCSV());
            DatagramPacket pkt = new DatagramPacket(
                    data, data.length, InetAddress.getByName(info.maddr), info.port);
            ms.send(pkt);
            System.out.println("[SERVER] USER_LIST → "+info.room+
                    " @ "+info.maddr+":"+info.port+" ["+data.length+" bytes]");
        } catch (IOException e){
            System.err.println("[SERVER] Error al enviar USER_LIST: " + e);
        }
    }

    public void loop() throws IOException {
        byte[] buf = new byte[Protocol.MAX_DATAGRAM];
        while (true){
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            serverSock.receive(p);
            Protocol.Parsed pm;
            try {
                pm = Protocol.parse(p.getData(), p.getLength());
            } catch (Exception ex){
                System.err.println("[SERVER] Ignorando datagrama inválido: " + ex.getMessage());
                continue;
            }
            if (pm.type == Protocol.Type.JOIN) handleJoin(pm, p.getAddress());
            else if (pm.type == Protocol.Type.LEAVE) handleLeave(pm);
            else {
                // No atendemos mensajes de chat: políticas del enunciado
                System.out.println("[SERVER] Ignorado "+pm.type+" de "+pm.from+" sala="+pm.room);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        int port = 4446;
        if (args.length >= 1) port = Integer.parseInt(args[0]);
        ChatServer server = new ChatServer(port);
        server.loop();
    }
}
