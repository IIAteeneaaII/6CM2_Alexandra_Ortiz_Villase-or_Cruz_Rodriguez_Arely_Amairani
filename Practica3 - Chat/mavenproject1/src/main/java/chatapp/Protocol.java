package chatapp;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class Protocol {

    public enum Type {
        JOIN('J'), LEAVE('L'),
        CHAT_TEXT('T'), STICKER('S'), AUDIO('A'),
        USER_LIST('U'),
        PRIVATE('P'); // nuevo

        public final char code;
        Type(char c){ this.code = c; }
        public static Type fromCode(char c){
            for (Type t : values()) if (t.code == c) return t;
            throw new IllegalArgumentException("Tipo desconocido: " + c);
        }
    }

    public static final int MAX_DATAGRAM = 64 * 1024; // límite UDP práctico

    public static class Parsed {
        public final Type type;
        public final String room;
        public final String from;
        public final String meta;     // "mime=...;filename=..." | "to=<user>" | "maddr=...;port=...;priv=..."
        public final String text;     // texto (CHAT_TEXT, USER_LIST, PRIVATE)
        public final byte[] binary;   // bytes (STICKER/AUDIO)
        public Parsed(Type type, String room, String from, String meta, String text, byte[] binary){
            this.type = type; this.room = room; this.from = from; this.meta = meta; this.text = text; this.binary = binary;
        }
        public boolean isBinary(){ return binary != null; }
    }

    // --- helpers URL-encode/URL-decode de campos de texto ---
    private static String enc(String s){ return s == null ? "" : URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private static String dec(String s){ return s == null ? "" : URLDecoder.decode(s, StandardCharsets.UTF_8); }

    // --- builders ---

    // JOIN (compatibilidad, sin puerto privado)
    public static byte[] buildJoin(String room, String from, String maddr, int port){
        String meta = "maddr="+maddr+";port="+port;
        String msg = Type.JOIN.code + "|" + enc(room) + "|" + enc(from) + "|" + enc(meta) + "|";
        return msg.getBytes(StandardCharsets.UTF_8);
    }

    // JOIN con puerto privado (recomendado)
    public static byte[] buildJoin(String room, String from, String maddr, int port, int privPort){
        String meta = "maddr="+maddr+";port="+port+";priv="+privPort;
        String msg = Type.JOIN.code + "|" + enc(room) + "|" + enc(from) + "|" + enc(meta) + "|";
        return msg.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] buildLeave(String room, String from){
        String msg = Type.LEAVE.code + "|" + enc(room) + "|" + enc(from) + "||";
        return msg.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] buildChatText(String room, String from, String text){
        String msg = Type.CHAT_TEXT.code + "|" + enc(room) + "|" + enc(from) + "||" + enc(text);
        return msg.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] buildSticker(String room, String from, String filename, byte[] data, String mime){
        String meta = "mime="+(mime==null?"image/png":mime)+";filename="+filename;
        String b64 = Base64.getEncoder().encodeToString(data);
        String msg = Type.STICKER.code + "|" + enc(room) + "|" + enc(from) + "|" + enc(meta) + "|" + b64;
        return msg.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] buildAudio(String room, String from, String filename, byte[] data, String mime){
        String meta = "mime="+(mime==null?"audio/wav":mime)+";filename="+filename;
        String b64 = Base64.getEncoder().encodeToString(data);
        String msg = Type.AUDIO.code + "|" + enc(room) + "|" + enc(from) + "|" + enc(meta) + "|" + b64;
        return msg.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] buildUserList(String room, String usersCSV){
        String msg = Type.USER_LIST.code + "|" + enc(room) + "|||" + enc(usersCSV);
        return msg.getBytes(StandardCharsets.UTF_8);
    }

    // PRIVADO (room no aplica; usamos "-")
    public static byte[] buildPrivate(String from, String to, String text){
        String meta = "to="+to;
        String msg = Type.PRIVATE.code + "|-|"+ enc(from) + "|" + enc(meta) + "|" + enc(text);
        return msg.getBytes(StandardCharsets.UTF_8);
    }

    // --- parser ---
    public static Parsed parse(byte[] buf, int len){
        String s = new String(buf, 0, len, StandardCharsets.UTF_8);
        int p1 = s.indexOf('|');
        if (p1 <= 0) throw new IllegalArgumentException("Datagrama malformado.");
        Type t = Type.fromCode(s.charAt(0));

        String[] parts = s.substring(2).split("\\|", -1); // room|from|meta|payload
        if (parts.length < 4) throw new IllegalArgumentException("Partes insuficientes.");

        String room = dec(parts[0]);
        String from = dec(parts[1]);
        String meta = dec(parts[2]);
        String payload = parts[3];

        switch (t){
            case CHAT_TEXT, USER_LIST, PRIVATE -> {
                return new Parsed(t, room, from, meta, dec(payload), null);
            }
            case STICKER, AUDIO -> {
                byte[] bin = Base64.getDecoder().decode(payload);
                return new Parsed(t, room, from, meta, null, bin);
            }
            case JOIN, LEAVE -> {
                return new Parsed(t, room, from, meta, null, null);
            }
            default -> throw new IllegalArgumentException("Tipo no soportado: " + t);
        }
    }

    // meta parser simplón: key=val;key2=val2
    public static String metaGet(String meta, String key){
        if (meta == null) return null;
        String[] parts = meta.split(";");
        for (String p : parts){
            int i = p.indexOf('=');
            if (i>0){
                String k = p.substring(0,i).trim();
                String v = p.substring(i+1).trim();
                if (k.equalsIgnoreCase(key)) return v;
            }
        }
        return null;
    }
}
