package net.integritas.android.avmixer;

/**
 * <p>
 *  Interface to define the public callback methods to notify whether the mixing is done
 *  successfully or if an exception has been thrown during the process.
 * </p>
 *
 * @author AlexGP (alexandre.gomes@integritas.net)
 * @author Raphael Kohn (raphael.kohn@integritas.net)
 *
 * @since 1.0
 */
public interface IAVMixerListener {

    void onFinishedMixing(AVMixerObject obj);

    void onMixerError(Exception msg);
}