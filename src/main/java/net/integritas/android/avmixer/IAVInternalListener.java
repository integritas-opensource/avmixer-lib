package net.integritas.android.avmixer;

/**
 * <p>
 *  Interface to define the internal callback methods for each phase of the mixing
 * </p>
 *
 * @author Alexandre Gomes (alexandre.gomes@integritas.net)
 * @author Raphael Kohn (raphael.kohn@integritas.net)
 *
 * @version 1.1
 * @since 1.0
 */
interface IAVInternalListener {

    /**
     * Called from a backgroung thread. Must have a Handler if needed to execute
     * code in another Thread.
     */
    void onVideoFinishedMixing();

    /**
     * Called when the Audio has finished it´s merging without errors.
     * It's called from a background Thread.
     */
    void onAudioFinishedMixing();

    /**
     * Called when the Mix with the generated Audio and the Generated video
     * has been done successfully.
     */
    void onMixFinished();

    /**
     * Called when an error occurs during the mixing process
     *
     * @param t - Exception thrown
     */
    void onMixingError(Throwable t);
}