package com.example.network;

import java.io.*;
import java.net.*;
import java.util.*;
import javazoom.jl.player.Player;

public class Client {
    private static final int PORT = 6000; // puerto donde escucha el cliente
    private static final int SERVER_PORT = 5000;
    private static final int PACKET_SIZE = 2048;

    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket(PORT)) {
            socket.setSoTimeout(10000); // 10 segundos de espera máxima
            InetAddress serverAddress = InetAddress.getByName("localhost");

            int expectedSeqNum = 0;
            Map<Integer, byte[]> receivedData = new HashMap<>();

            boolean receiving = true;
            String outputFile = "received_song.mp3";
            System.out.println("🎧 Cliente esperando paquetes...");

            while (receiving) {
                try {
                    byte[] buffer = new byte[PACKET_SIZE * 2];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    Packet receivedPacket = (Packet) deserialize(buffer);

                    // ✅ Verifica si es el paquete esperado
                    if (receivedPacket.getSequenceNumber() == expectedSeqNum) {
                        System.out.println("📩 Recibido paquete #" + receivedPacket.getSequenceNumber());
                        receivedData.put(receivedPacket.getSequenceNumber(), receivedPacket.getData());

                        // Envía ACK
                        AckPacket ack = new AckPacket(receivedPacket.getSequenceNumber(), false);
                        sendAck(socket, serverAddress, SERVER_PORT, ack);
                        expectedSeqNum++;

                        // Si es el último paquete, salimos
                        if (expectedSeqNum == receivedPacket.getTotalPackets()) {
                            System.out.println("🎵 Último paquete recibido.");
                            receiving = false;
                        }
                    } else {
                        // Paquete fuera de orden → NACK
                        System.out.println("Paquete fuera de orden. Esperaba #" + expectedSeqNum +
                                " pero recibí #" + receivedPacket.getSequenceNumber());
                        AckPacket nack = new AckPacket(expectedSeqNum - 1, true);
                        sendAck(socket, serverAddress, SERVER_PORT, nack);
                    }

                } catch (SocketTimeoutException e) {
                    System.out.println("Timeout esperando paquete. Terminando recepción...");
                    break;
                }
            }

            // 🧩 Reconstruir el archivo
            saveToFile(receivedData, outputFile);
            System.out.println("✅ Archivo reconstruido: " + outputFile);

            // 🔊 Reproducir MP3 recibido (opcional)
            playAudio(outputFile);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Envía ACK/NACK al servidor
    private static void sendAck(DatagramSocket socket, InetAddress serverAddr, int serverPort, AckPacket ack)
            throws IOException {
        byte[] ackData = serialize(ack);
        DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, serverAddr, serverPort);
        socket.send(ackPacket);
        if (!ack.isNack())
            System.out.println("📤 Enviado ACK #" + ack.getAckNumber());
        else
            System.out.println("📤 Enviado NACK #" + ack.getAckNumber());
    }

    // Guarda los fragmentos recibidos en un solo archivo
    private static void saveToFile(Map<Integer, byte[]> fragments, String outputFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            for (int i = 0; i < fragments.size(); i++) {
                fos.write(fragments.get(i));
            }
        }
    }

    // 🔊 Reproduce el archivo MP3 usando JLayer
    private static void playAudio(String filePath) {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            System.out.println("🎶 Reproduciendo archivo con JLayer...");
            Player player = new Player(fis);
            player.play();
            System.out.println("✅ Reproducción finalizada.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // Serialización / deserialización de objetos
    private static byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(obj);
        oos.flush();
        return bos.toByteArray();
    }

    private static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bis);
        return ois.readObject();
    }
}
