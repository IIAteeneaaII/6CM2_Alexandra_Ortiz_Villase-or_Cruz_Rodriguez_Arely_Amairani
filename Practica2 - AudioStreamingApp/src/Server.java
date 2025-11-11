package com.example.network;

import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private static final int PORT = 5000;
    private static final int PACKET_SIZE = 1024; // bytes por fragmento
    private static final int WINDOW_SIZE = 5;    // N en Go-Back-N
    private static final int TIMEOUT = 2000;     // ms

    public static void main(String[] args) {
        //Se crea el socket para la comunicación del servidor con el cliente
        try (DatagramSocket socket = new DatagramSocket(PORT)) {
            socket.setSoTimeout(TIMEOUT);

            // 👇 Verifica si se pasó un archivo como argumento
            String fileName; File file;

            if (args.length > 0) {
                file = new File(args[0]);
                fileName = args[0];
            } else {
                // Si no hay argumento, lo pedimos por consola
                Scanner sc = new Scanner(System.in);
                System.out.print("Ingresa el nombre del archivo MP3 a enviar: ");
                fileName = sc.nextLine();
                file = new File(fileName);
            }

            if (!file.exists()) {
                System.out.println("El archivo no existe: " + file.getAbsolutePath());
                return;
            }
            // - - - - - -- - - - - - - - - - - - - - - - - - - -   

            byte[] fileData = readFileToByteArray(file);

            int totalPackets = (int) Math.ceil((double) fileData.length / PACKET_SIZE);
            System.out.println("Total de paquetes a enviar: " + totalPackets);

            InetAddress clientAddress = InetAddress.getByName("localhost");
            int clientPort = 6000;

            int base = 0;
            int nextSeqNum = 0;

            long startTime = 0;

            while (base < totalPackets) {
                // Envía los paquetes dentro de la ventana
                while (nextSeqNum < base + WINDOW_SIZE && nextSeqNum < totalPackets) {
                    byte[] fragment = getPacketData(fileData, nextSeqNum);
                    Packet packet = new Packet(fileName, nextSeqNum, totalPackets, fragment.length, fragment);

                    byte[] sendData = serialize(packet);
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                    socket.send(sendPacket);
                    System.out.println("📦 Enviado paquete #" + nextSeqNum);
                    
                    if (base == nextSeqNum) {
                        startTime = System.currentTimeMillis(); // inicia temporizador
                    }
                    nextSeqNum++;
                }

                // Espera ACK
                try {
                    byte[] ackBuffer = new byte[1024];
                    DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
                    socket.receive(ackPacket);

                    AckPacket ack = (AckPacket) deserialize(ackBuffer);

                    if (!ack.isNack()) {
                        System.out.println("✅ ACK recibido: " + ack.getAckNumber());
                        base = ack.getAckNumber() + 1;

                        if (base == nextSeqNum) {
                            // ventana vacía → detiene temporizador
                            startTime = 0;
                        } else {
                            // reinicia temporizador
                            startTime = System.currentTimeMillis();
                        }
                    } else {
                        System.out.println("NACK recibido: " + ack.getAckNumber());
                        nextSeqNum = base; // retroceso N
                    }

                } catch (SocketTimeoutException e) {
                    System.out.println("Timeout. Retransmitiendo desde " + base);
                    nextSeqNum = base;
                }
            }

            System.out.println("Archivo enviado completamente.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Lee el archivo MP3 como arreglo de bytes
    private static byte[] readFileToByteArray(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[PACKET_SIZE];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
            return bos.toByteArray();
        }
    }

    // Obtiene fragmento i del archivo
    private static byte[] getPacketData(byte[] fileData, int seqNum) {
        int start = seqNum * PACKET_SIZE;
        int end = Math.min(start + PACKET_SIZE, fileData.length);
        return Arrays.copyOfRange(fileData, start, end);
    }

    // Serialización de objetos Packet
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