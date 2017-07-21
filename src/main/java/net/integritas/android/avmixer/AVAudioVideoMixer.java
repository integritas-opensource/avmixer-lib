package net.integritas.android.avmixer;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import net.integritas.android.avmixer.util.Logger;

import java.nio.ByteBuffer;
import java.util.HashMap;

/**
 * <p>
 *  Class responsible to mix an audio and a video track into one single media track.
 * </p>
 *
 * @author AlexGP (alexandre.gomes@integritas.net)
 * @author Raphael Kohn (raphael.kohn@integritas.net)
 *
 * @version 1.4
 * @since 1.0
 */
public class AVAudioVideoMixer {

    private boolean mMixingOver = false;
    private AVMixerObject mMixObj;
    private IAVInternalListener mListener;
    private static int MAX_SAMPLE_SIZE = 1024 * 1024;
    private static final String TAG = "AVAudioVideoMixer";
    private Exception mException;
    private boolean mAudioNVideoSync = true;
    private long mLastPresentationVideoTimestamp;
    private long mMuteVideoOffet;
    private final long PRES_TIME_MULTIPLIER = 1000l;

    public AVAudioVideoMixer(AVMixerObject mixObj, IAVInternalListener listener) {
        this.mMixObj = mixObj;
        this.mListener = listener;
    }

    public AVAudioVideoMixer(AVMixerObject mixObj, IAVInternalListener listener,
                             boolean syncAudioNVideo) {
        this(mixObj, listener);
        this.mAudioNVideoSync = syncAudioNVideo;
    }

    public AVAudioVideoMixer(AVMixerObject mixObj, IAVInternalListener listener,
                             boolean syncAudioNVideo, long muteVideoOffset) {
        this(mixObj, listener, syncAudioNVideo);
        this.mMuteVideoOffet = muteVideoOffset * PRES_TIME_MULTIPLIER;
    }

    /**
     * Method for mixing the Audio generated from AVAudioMixer and AVVideoMixer
     *
     * @throws Exception
     */
    public void mixAudioAndVideo() throws Exception {
        mMixingOver = false;
        Runnable doMix = new Runnable() {
            @Override
            public void run() {
                try {
                    int videoTrackIndex = 0;
                    int audioTrackIndex = 1;

                    MediaExtractor videoExtractor = new MediaExtractor();
                    videoExtractor.setDataSource(mMixObj.getTempVideoFilePath());
                    int trackCount = videoExtractor.getTrackCount();

                    MediaExtractor audioExtractor = new MediaExtractor();
                    audioExtractor.setDataSource(mMixObj.getTempAudioFilePath());

                    // Set up MediaMuxer for the destination.
                    MediaMuxer muxer = new MediaMuxer(mMixObj.getFilePathName(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                    // Set up the tracks.
                    HashMap<Integer, Integer> indexMap = new HashMap<Integer, Integer>(trackCount);

                    for (int i = 0; i < trackCount; i++) {
                        MediaFormat format = videoExtractor.getTrackFormat(i);
                        String mime = format.getString(MediaFormat.KEY_MIME);
                        if (mime.startsWith("video/")) {
                            videoExtractor.selectTrack(i);
                            int dstIndex = muxer.addTrack(format);
                            indexMap.put(videoTrackIndex, dstIndex);
                        }
                    }

                    trackCount = audioExtractor.getTrackCount();

                    for (int i = 0; i < trackCount; i++) {
                        MediaFormat format = audioExtractor.getTrackFormat(i);
                        String mime = format.getString(MediaFormat.KEY_MIME);
                        if (mime.startsWith("audio/")) {
                            audioExtractor.selectTrack(i);
                            int dstIndex = muxer.addTrack(format);
                            indexMap.put(audioTrackIndex, dstIndex);
                        }
                    }

                    // Copy the samples from MediaExtractor to MediaMuxer.
                    boolean sawEOS = false;
                    int bufferSize = MAX_SAMPLE_SIZE;
                    int videoFrameCount = 0;
                    int audioChunkCount = 0;
                    int offset = 100;
                    ByteBuffer dstBuf = ByteBuffer.allocate(bufferSize);
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

                    muxer.start();

                    //reading video
                    while (!sawEOS) {
                        bufferInfo.offset = offset;
                        bufferInfo.size = videoExtractor.readSampleData(dstBuf, offset);
                        if (bufferInfo.size < 0) {
                            Logger.LOGV(TAG, "saw input EOS.");
                            sawEOS = true;
                            bufferInfo.size = 0;
                        } else {

                            bufferInfo.presentationTimeUs = videoExtractor.getSampleTime();
                            mLastPresentationVideoTimestamp = bufferInfo.presentationTimeUs;
                            bufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                            muxer.writeSampleData(indexMap.get(videoTrackIndex), dstBuf,
                                    bufferInfo);

                            Logger.LOGV(TAG, "Video Frame (" + videoFrameCount + ") " +
                                    "PresentationTimeUs:" + bufferInfo.presentationTimeUs +
                                    " Flags:" + bufferInfo.flags +
                                    " TrackIndex:" + videoTrackIndex +
                                    " Size(KB) " + bufferInfo.size / 1024);

                            videoExtractor.advance();
                            videoFrameCount++;
                        }
                    }

                    sawEOS = false;

                    //reading audio
                    while (!sawEOS) {
                        bufferInfo.offset = offset;
                        bufferInfo.size = audioExtractor.readSampleData(dstBuf, offset);
                        if (bufferInfo.size < 0) {
                            Logger.LOGV(TAG, "saw input EOS.");
                            sawEOS = true;
                            bufferInfo.size = 0;
                        } else {
                            bufferInfo.presentationTimeUs = audioExtractor.getSampleTime();
                            bufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                            if (mAudioNVideoSync
                                    && bufferInfo.presentationTimeUs
                                    < (mLastPresentationVideoTimestamp - mMuteVideoOffet)) {
                                muxer.writeSampleData(indexMap.get(audioTrackIndex), dstBuf,
                                        bufferInfo);
                            } else if (!mAudioNVideoSync) {
                                muxer.writeSampleData(indexMap.get(audioTrackIndex), dstBuf,
                                        bufferInfo);
                            }

                            Logger.LOGV(TAG, "Audio Chunk (" + audioChunkCount + ") " +
                                    "PresentationTimeUs:" + bufferInfo.presentationTimeUs +
                                    " Flags:" + bufferInfo.flags +
                                    " TrackIndex:" + audioTrackIndex +
                                    " Size(KB) " + bufferInfo.size / 1024);
                        }

                        audioExtractor.advance();
                        audioChunkCount++;
                    }

                    muxer.stop();
                    muxer.release();
                    mMixingOver = true;

                    mListener.onMixFinished();

                    return;
                } catch (Exception t) {
                    mException = t;
                    mMixingOver = true;
                    mListener.onMixingError(t);
                }
            }
        };

        Thread t = new Thread(doMix);
        t.setPriority(Thread.MAX_PRIORITY);
        t.start();

        while (!mMixingOver) {
            Thread.yield();
        }

        if (mException != null) {
            throw mException;
        }
    }
}