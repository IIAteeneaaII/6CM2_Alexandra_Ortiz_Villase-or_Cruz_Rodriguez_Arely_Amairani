package com.example.network;

import java.io.Serializable;

public class AckPacket implements Serializable {
    private final int ackNumber;
    private final boolean isNack;  // Nuevo: para indicar si hubo error o pérdida

    public AckPacket(int ackNumber, boolean isNack) {
        this.ackNumber = ackNumber;
        this.isNack = isNack;
    }

    public int getAckNumber() { return ackNumber;}

    public boolean isNack() { return isNack; }

}
