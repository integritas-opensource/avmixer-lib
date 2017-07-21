package net.integritas.android.avmixer;

import java.util.ArrayList;

/**
 * <p>
 * Class responsible to call all other mixers to render a final media file which
 * has all the media included in the AVMixerObject. It is possible to render low and high
 * resolution media using the isPreview option to true for a default low resolution or
 * false to render in full HD resolution or to choose the following resolutions: 360p, 480p,
 * 720p and 1080p.
 * </p>
 *
 * @author AlexGP (alexandre.gomes@integritas.net)
 * @author Raphael Kohn (raphael.kohn@integritas.net)
 * @version 1.4
 * @since 1.0
 */
public class AVMixer implements IAVInternalListener {

    /**
     * Logging
     */
    private static String TAG = "AVMixer";

    /**
     * Constants
     */
    private static int LOW_FRAME_RATE = 30;
    private static int HIGH_FRAME_RATE = 60;
    private static int DEFAULT_FRAME_RATE = 15;
    private static int LOW_RES_WIDTH = 640;
    private static int LOW_RES_HEIGHT = 360;
    private static int PREVIEW_WIDTH = 800;
    private static int PREVIEW_HEIGHT = 480;
    private static int HD_WIDTH = 1280;
    private static int HD_HEIGHT = 720;
    private static int FULL_HD_WIDTH = 1920;
    private static int FULL_HD_HEIGHT = 1080;
    private static double LOW_RES_BIT_RATE =
            LOW_RES_HEIGHT * LOW_RES_WIDTH * DEFAULT_FRAME_RATE * 0.1;
    private static double DEFAULT_PREVIEW_BIT_RATE =
            PREVIEW_HEIGHT * PREVIEW_WIDTH * DEFAULT_FRAME_RATE * 0.1;
    private static double MID_RES_BIT_RATE =
            HD_HEIGHT * HD_WIDTH * DEFAULT_FRAME_RATE * 0.1;
    private static double HIGH_RES_BIT_RATE =
            FULL_HD_HEIGHT * FULL_HD_WIDTH * DEFAULT_FRAME_RATE * 0.1;

    AVMixerObject mMixObj;
    IAVMixerListener mListener;
    boolean mAudioNVideoSync = true;
    long mMuteVideoOffset;
    boolean mUseLandscapeOrientation;

    public enum VIDEO_RES_TYPE {Res_360p, Res_480p, Res_720p, Res_1080p}

    int mWidth = -1;
    int mHeight = -1;
    double mBitRate = -1;
    int mFrameRate = DEFAULT_FRAME_RATE;


    /**
     * Constructor. Takes a object to mix with all needed file paths and a listener for Libevents.
     *
     * @param mixObject
     * @param listener
     */
    public AVMixer(AVMixerObject mixObject, IAVMixerListener listener) {
        mMixObj = mixObject;
        mListener = listener;
    }

    /**
     * Takes a boolean to whether sync audio and video or not. Audio and Video synced will always
     * end at the same time.
     *
     * @param mixObject
     * @param listener
     * @param audioVideoSync
     */
    public AVMixer(AVMixerObject mixObject, IAVMixerListener listener, boolean audioVideoSync) {
        this(mixObject, listener);
        mAudioNVideoSync = audioVideoSync;
    }


    /**
     * Takes an additional long value to define an offset for muting the audio
     * of the finals N seconds of the mixed video. The value is represented in milliseconds
     * (e.g. 3000, the last 3 seconds of the video will have no audio).
     *
     * @param mixObject
     * @param listener
     * @param audioVideoSync
     * @param muteVideoOffset
     */
    public AVMixer(AVMixerObject mixObject, IAVMixerListener listener, boolean audioVideoSync,
                   long muteVideoOffset) {
        this(mixObject, listener, audioVideoSync);
        mMuteVideoOffset = muteVideoOffset;
    }

    /**
     * Takes an additional boolean value to define whether to use or not the landscape orientation
     * for rendering videos and images.
     *
     * @param mixObject
     * @param listener
     * @param audioVideoSync
     * @param muteVideoOffset
     * @param useLandscapeOrientation
     */
    public AVMixer(AVMixerObject mixObject, IAVMixerListener listener, boolean audioVideoSync,
                   long muteVideoOffset, boolean useLandscapeOrientation) {
        this(mixObject, listener, audioVideoSync, muteVideoOffset);
        this.mUseLandscapeOrientation = useLandscapeOrientation;
    }

    /**
     * Method to call to mix video and audio. First, it mixes the video.
     * Then, after video successfully mixed, calls the mixAudio() from the callback.
     *
     * @param isPreview Change the BITRATE, WIDTH and HEIGHT of the generated video.
     */
    public void mix(boolean isPreview) {

        try {

            mWidth = isPreview ? PREVIEW_WIDTH : FULL_HD_WIDTH;
            mHeight = isPreview ? PREVIEW_HEIGHT : FULL_HD_HEIGHT;
            mBitRate = isPreview ? DEFAULT_PREVIEW_BIT_RATE : HIGH_RES_BIT_RATE;
            mFrameRate = isPreview ? LOW_FRAME_RATE : HIGH_FRAME_RATE;

            mixVideo();
        } catch (Exception ex) {
            mMixObj.clearTempData();
            mListener.onMixerError(ex);
        }
    }


    /**
     * Method to call to mix video and audio setting a resolution for the video.
     * First, it mixes the video then, after video successfully mixed,
     * calls the mixAudio() from the callback.
     *
     * @param videoRes
     */
    public void mix(VIDEO_RES_TYPE videoRes) {

        try {
            switch (videoRes) {
                case Res_360p:
                    mWidth = LOW_RES_WIDTH;
                    mHeight = LOW_RES_HEIGHT;
                    mBitRate = LOW_RES_BIT_RATE;
                    mFrameRate = DEFAULT_FRAME_RATE;
                    break;
                case Res_480p:
                    mWidth = PREVIEW_WIDTH;
                    mHeight = PREVIEW_HEIGHT;
                    mBitRate = DEFAULT_PREVIEW_BIT_RATE;
                    mFrameRate = LOW_FRAME_RATE;
                    break;
                case Res_720p:
                    mWidth = HD_WIDTH;
                    mHeight = HD_HEIGHT;
                    mBitRate = MID_RES_BIT_RATE;
                    mFrameRate = LOW_FRAME_RATE;
                    break;
                case Res_1080p:
                    mWidth = FULL_HD_WIDTH;
                    mHeight = FULL_HD_HEIGHT;
                    mBitRate = HIGH_RES_BIT_RATE;
                    mFrameRate = HIGH_FRAME_RATE;
                    break;
                default:
                    mWidth = PREVIEW_WIDTH;
                    mHeight = PREVIEW_HEIGHT;
                    mBitRate = MID_RES_BIT_RATE;
                    mFrameRate = DEFAULT_FRAME_RATE;
                    break;
            }

            mixVideo();
        } catch (Exception ex) {
            mMixObj.clearTempData();
            mListener.onMixerError(ex);
        }
    }

    /**
     * Calls the AVVideoMixer to generate the Mix between the Video and the Images if there are images to mix.
     * Also, transforms the video to the correct format and size.
     */
    private void mixVideo() {
        AVVideoMixer vm = new AVVideoMixer(mMixObj, mWidth, mHeight,
                (int) mBitRate, mFrameRate, this, mUseLandscapeOrientation);
        vm.mixVideo();
    }

    /**
     * Calls AVAudioMixer to generate the audiofile to be mixed into the video.
     */
    private void mixAudio() {
        try {

            ArrayList<AVAudioMixerComponent> audios = mMixObj.getAudioComponents();

            if (needToMixAudio()) {
                AVAudioMixer am = new AVAudioMixer(mMixObj, this);
                am.mixSamples();
            } else if (audios.size() == 1 && mMixObj.getAudioDelay() <= 0) {
                //Only one Audio and no need adding an audio start delay.
                mMixObj.oneAudio();
                mixAudioAndVideo();
            } else {
                //No Audios -> Final product is the produced Video
                mMixObj.noAudio();
                AVMixer.this.onMixFinished();
            }
        } catch (Exception ex) {
            mMixObj.clearTempData();
            mListener.onMixerError(ex);
        }
    }

    private boolean needToMixAudio() {
        boolean doMixAudio = false;
        doMixAudio = mMixObj.getAudioComponents().size() > 1 || mMixObj.getAudioDelay() > 0;

        if (!doMixAudio) {
            for (AVAudioMixerComponent c : mMixObj.getAudioComponents()) {
                doMixAudio = c.getStartAt() > 0 || c.getDelay() > 0;
                if (doMixAudio)
                    break;
            }
        }

        return doMixAudio;
    }

    /**
     * Generate the final product with the produced audio and the merged video
     */
    private void mixAudioAndVideo() {
        try {
            AVAudioVideoMixer avm = new AVAudioVideoMixer(mMixObj, this,
                    mAudioNVideoSync, mMuteVideoOffset);
            avm.mixAudioAndVideo();
        } catch (Exception ex) {
            mMixObj.clearTempData();
            mListener.onMixerError(ex);
        }
    }

    @Override
    public void onVideoFinishedMixing() {
        mixAudio();
    }

    @Override
    public void onAudioFinishedMixing() {
        mixAudioAndVideo();
    }

    @Override
    public void onMixFinished() {
        mMixObj.clearTempData();
        mListener.onFinishedMixing(mMixObj);
    }

    @Override
    public void onMixingError(Throwable t) {
        onMixingError(t);
    }

    public boolean isAudioNVideoSynced() {
        return mAudioNVideoSync;
    }

    public void setSyncAudioNVideo(boolean mAudioNVideoSync) {
        this.mAudioNVideoSync = mAudioNVideoSync;
    }
}