//Clase que encapsula el nombre del archivo, número de paquete, no. de paquetes, tam arreglo y datos.
package com.example.network;

import java.io.Serializable;

public class Packet implements Serializable {
    private String fileName;
    private int sequenceNumber;
    private int totalPackets;
    private int dataLength;
    private byte[] data;

    public Packet(String fileName, int sequenceNumber, int totalPackets, int dataLength, byte[] data) {
        this.fileName = fileName;
        this.sequenceNumber = sequenceNumber;
        this.totalPackets = totalPackets;
        this.data = data;
        this.dataLength = data.length;
    }

    public String getFileName() { return fileName; }
    public int getSequenceNumber() { return sequenceNumber; }
    public int getTotalPackets() { return totalPackets; }
    public int getDataLength() { return dataLength; }
    public byte[] getData() { return data; }
}

