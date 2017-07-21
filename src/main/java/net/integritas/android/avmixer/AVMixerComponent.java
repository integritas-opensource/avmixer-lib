package net.integritas.android.avmixer;

import java.io.File;

/**
 * <p>
 *  Class that implements the base AVMixerComponent properties
 * </p>
 *
 * @author Raphael Kohn (raphael.kohn@integritas.net)
 *
 * @version 1.1
 * @since 1.0
 */
public abstract class AVMixerComponent {

    private File componentFile;

    public AVMixerComponent(File componentFile) {
        this.componentFile = componentFile;
    }

    public File getComponentFile() {
        return componentFile;
    }

    /**
     * Sets the source media file that represents this media component
     *
     * @param componentFile - source media file
     */
    public void setComponentFile(File componentFile) {
        this.componentFile = componentFile;
    }
}