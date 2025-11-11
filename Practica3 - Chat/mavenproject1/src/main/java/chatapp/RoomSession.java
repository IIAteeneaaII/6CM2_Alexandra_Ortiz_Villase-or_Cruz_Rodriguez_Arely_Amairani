package chatapp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class RoomSession implements Runnable {

    /** Callback para entregar USER_LIST al cliente. */
    public interface PeerUpdate { void onUserList(String room, String csv); }

    private final String room;
    private final String maddr;
    private final int port;
    private final String username;

    private final MulticastSocket socket;
    private final InetAddress group;

    private final PeerUpdate peerUpdate;

    private volatile boolean running = true;

    public RoomSession(String room, String maddr, int port, String username, PeerUpdate cb) throws IOException {
        this.room = room; this.maddr = maddr; this.port = port; this.username = username; this.peerUpdate = cb;

        this.group = InetAddress.getByName(maddr);
        if (!group.isMulticastAddress())
            throw new IllegalArgumentException("La dirección no es multicast: " + maddr);

        this.socket = new MulticastSocket(port);
        this.socket.setReuseAddress(true);
        this.socket.setTimeToLive(1);
        // Advertencia deprecada en JDK recientes, funcional:
        this.socket.joinGroup(group);

        System.out.println("[CLIENT] Unido a sala ["+room+"] en "+maddr+":"+port);
    }

    public void sendText(String text) throws IOException {
        byte[] data = Protocol.buildChatText(room, username, text);
        DatagramPacket pkt = new DatagramPacket(data, data.length, group, port);
        socket.send(pkt);
    }

    public void sendSticker(File file, String mime) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        byte[] data = Protocol.buildSticker(room, username, file.getName(), bytes, mime);
        DatagramPacket pkt = new DatagramPacket(data, data.length, group, port);
        socket.send(pkt);
    }

    public void sendAudio(File file, String mime) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        byte[] data = Protocol.buildAudio(room, username, file.getName(), bytes, mime);
        DatagramPacket pkt = new DatagramPacket(data, data.length, group, port);
        socket.send(pkt);
    }

    public void close(){
        running = false;
        try {
            // idem deprecado pero funcional
            socket.leaveGroup(group);
        } catch (IOException ignored) {}
        socket.close();
        System.out.println("[CLIENT] Saliste de sala ["+room+"].");
    }

    private static File ensureDir(File dir){
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    @Override
    public void run() {
        byte[] buf = new byte[Protocol.MAX_DATAGRAM];
        while (running){
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);
                Protocol.Parsed pm = Protocol.parse(p.getData(), p.getLength());

                switch (pm.type){
                    case USER_LIST -> {
                        System.out.println("\n[USER_LIST]["+room+"]: " + pm.text + "\n> ");
                        if (peerUpdate != null) peerUpdate.onUserList(room, pm.text);
                    }
                    case CHAT_TEXT -> {
                        if (!room.equals(pm.room)) break;
                        System.out.println("["+room+"] "+pm.from+": " + pm.text);
                    }
                    case STICKER -> {
                        if (!room.equals(pm.room)) break;
                        String filename = Protocol.metaGet(pm.meta, "filename");
                        File out = ensureDir(new File("downloads/"+room));
                        if (filename == null || filename.isBlank()){
                            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                            filename = "sticker_"+pm.from+"_"+ts+".bin";
                        }
                        File f = new File(out, filename);
                        try (FileOutputStream fos = new FileOutputStream(f)){
                            fos.write(pm.binary);
                        }
                        System.out.println("["+room+"] "+pm.from+" envió sticker → "+f.getPath());
                    }
                    case AUDIO -> {
                        if (!room.equals(pm.room)) break;
                        String filename = Protocol.metaGet(pm.meta, "filename");
                        String mime = Protocol.metaGet(pm.meta, "mime");
                        File out = ensureDir(new File("downloads/"+room));
                        if (filename == null || filename.isBlank()){
                            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                            filename = "audio_"+pm.from+"_"+ts+".wav";
                        }
                        File f = new File(out, filename);
                        try (FileOutputStream fos = new FileOutputStream(f)){
                            fos.write(pm.binary);
                        }
                        System.out.println("["+room+"] "+pm.from+" envió audio → "+f.getPath());
                        if (mime != null && mime.startsWith("audio/")){
                            try {
                                AudioUtil.playWav(pm.binary);
                            } catch (Exception e){
                                System.out.println("   (No se pudo reproducir automáticamente: "+e.getMessage()+")");
                            }
                        }
                    }
                    default -> { /* ignorar JOIN/LEAVE en canal multicast */ }
                }

            } catch (IOException ex){
                if (running) System.err.println("[CLIENT] Error recepción sala ["+room+"]: " + ex.getMessage());
            } catch (Exception ex){
                System.err.println("[CLIENT] Error parseando datagrama: " + ex.getMessage());
            }
        }
    }
}
