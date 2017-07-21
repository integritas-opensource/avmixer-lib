package net.integritas.android.avmixer;

import java.io.File;

/**
 * <p>
 *  Class that represents an video file to be used in the AVMixer.
 * </p>
 *
 * @author AlexGP (alexandre.gomes@integritas.net)
 * @author Raphael Kohn (raphael.kohn@integritas.net)
 *
 * @version 1.2
 * @since 1.0
 */
public class AVVideoMixerComponent extends AVMixerComponent {

    private float volume;
    private double startFrame;

    /* boolean to consider the video's audio track during the mixing process */
    private boolean mConsiderOriginalAudio;

    public AVVideoMixerComponent(File componentFile) {
        super(componentFile);
    }

    public AVVideoMixerComponent(File componentFile, float volume) {
        super(componentFile);
        this.volume = volume;
    }

    public AVVideoMixerComponent(File componentFile, boolean originalAudio) {
        super(componentFile);
        this.mConsiderOriginalAudio = originalAudio;
    }

    public AVVideoMixerComponent(File componentFile, float volume, boolean originalAudio) {
        super(componentFile);
        this.volume = volume;
        this.mConsiderOriginalAudio = originalAudio;
    }

    public float getVolume() {
        return volume;
    }

    /**
     * Sets the volume for the video's audio track. The volume must be a float value between 0.0f and 1.0f
     *
     * @param volume - video's audio track volume
     */
    public void setVolume(float volume) {
        this.volume = volume;
    }

    public double getStartFrame() {
        return startFrame;
    }

    /**
     * <p>
     * Sets the starting offset of the video file.
     *
     * The starting frame value must be specified in milliseconds(e.g. 3000 for starting
     * after the 1st 3 seconds of the audio)
     * </p>
     *
     * @param startFrame - miliseconds offset to start the audio
     */
    public void setStartFrame(double startFrame) {
        this.startFrame = startFrame;
    }

    public boolean isOriginalAudioProcessed() {
        return mConsiderOriginalAudio;
    }

    /**
     * Sets wheather the video's audio track should be considered during the mixing or not
     *
     * @param mConsiderOriginalAudio - boolean to flag wheater the video's audio track is being consired during the mixing or not
     */
    public void setProcessOriginalAudio(boolean mConsiderOriginalAudio) {
        this.mConsiderOriginalAudio = mConsiderOriginalAudio;
    }
}