package chatapp;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public final class AudioUtil {

    private AudioUtil(){}

    /** Reproduce WAV (PCM) desde bytes. */
    public static void playWav(byte[] data) throws UnsupportedAudioFileException, IOException, LineUnavailableException {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data))) {
            AudioFormat base = ais.getFormat();
            AudioFormat decoded = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    base.getSampleRate(),
                    16,
                    base.getChannels(),
                    base.getChannels()*2,
                    base.getSampleRate(),
                    false
            );
            try (AudioInputStream din = AudioSystem.getAudioInputStream(decoded, ais)) {
                Clip clip = AudioSystem.getClip();
                clip.open(din);
                clip.start();
            }
        }
    }
}
