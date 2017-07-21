package net.integritas.android.avmixer;

import java.io.File;

/**
 * <p>
 *  Class that represents an image file to be used in the AVMixer.
 * </p>
 *
 * @author AlexGP (alexandre.gomes@integritas.net)
 * @author Raphael Kohn (raphael.kohn@integritas.net)
 *
 * @version 1.2
 * @since 1.0
 */
public class AVImageMixerComponent extends AVMixerComponent {

    /* presentation time in seconds */
    private int presentationTime;

    private static final int BASE_PRESENTATION_TIME = 3;

    public AVImageMixerComponent(File componentFile) {
        super(componentFile);
    }

    public int getPresentationTime() {
        return presentationTime > BASE_PRESENTATION_TIME
                ? presentationTime : BASE_PRESENTATION_TIME;
    }

    /**
     * Sets the image's presentation time in seconds
     *
     * @param presentationTime
     */
    public void setPresentationTime(int presentationTime) {
        this.presentationTime = presentationTime;
    }
}