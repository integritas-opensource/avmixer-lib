package net.integritas.android.avmixer.util;

import android.util.Log;

/**
 * <p>
 *  Class to do all loggings of the library.
 * </p>
 *
 * @author AlexGP (alexandre.gomes@integritas.net)
 *
 * @since 1.0
 */
public class  Logger {

    private Logger(){}

    // Debug flag
    private static boolean DEBUG;

    /**
     * Enable all the logs
     */
    public static void enable() {
        DEBUG = true;
    }

    /**
     * Disable all the logs
     */
    public static void disable() {
        DEBUG = false;
    }

    /**
     * Verbose logs
     *
     * @param tag
     * @param text
     */
    public static void LOGV(String tag, String text) {
        if (DEBUG == true) {
            Logger.LOGV(tag, text);
        }
    }

    /**
     * Exception logs
     *
     * @param tag
     * @param text
     */
    public static void LOGE(String tag, String text) {
        if (DEBUG == true) {
            Logger.LOGE(tag, text);
        }
    }

    /**
     * Exception logs
     *
     * @param tag
     * @param text
     */
    public static void LOGE(String tag, String text, Throwable t) {
        if (DEBUG == true) {
            Logger.LOGE(tag, text, t);
        }
    }
}