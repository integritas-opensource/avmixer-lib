package net.integritas.android.avmixer;

import java.io.File;

/**
 * <p>
 *  Class that represents an audio file to be used in the AVMixer.
 * </p>
 *
 * @author AlexGP (alexandre.gomes@integritas.net)
 * @author Raphael Kohn (raphael.kohn@integritas.net)
 *
 * @version 1.2
 * @since 1.0
 */
public class AVAudioMixerComponent extends AVMixerComponent {

    private float volume;
    private final float DEFAULT_VOLUME = 1.0f;
    private final long PRES_TIME_MULTIPLIER = 1000l;
    private long startAt;

    private long delay;

    public AVAudioMixerComponent(File componentFile) {
        super(componentFile);
    }

    public AVAudioMixerComponent(File componentFile, float volume, long startAt) {
        super(componentFile);

        this.volume = volume;
        this.startAt = startAt * PRES_TIME_MULTIPLIER;
    }

    public AVAudioMixerComponent(File componentFile, float volume, long startAt, long delay) {
        this(componentFile, volume, startAt);
        this.delay = delay * PRES_TIME_MULTIPLIER;
    }

    public float getVolume() {
        return volume >= 0.0f ? volume : DEFAULT_VOLUME;
    }

    /**
     * <p>
     * Sets the audio volume. The volume must be values between 0.0f and 1.0f
     * </p>
     *
     * @param volume - Volume to be set
     */
    public void setVolume(float volume) {
        this.volume = volume;
    }

    public long getStartAt() {
        return startAt;
    }

    /**
     * <p>
     * Sets the starting offset of the audio file.
     * The starting value must be specified in milliseconds(e.g. 3000 for starting
     * after the 1st 3 seconds of the audio)
     * </p>
     *
     * @param startAt - miliseconds offset to start the audio
     */
    public void setStartAt(long startAt) {
        this.startAt = startAt * PRES_TIME_MULTIPLIER;
    }

    public long getDelay() {
        return delay;
    }

    /**
     * <p>
     * Sets a delay for starting to play the audio file.
     * The delay be specified in milliseconds(e.g. 3000 for starting
     * after the 1st 3 seconds of the final mixed video)
     * </p>
     *
     * @param delay
     */
    public void setDelay(long delay) {
        this.delay = delay * PRES_TIME_MULTIPLIER;
    }
}