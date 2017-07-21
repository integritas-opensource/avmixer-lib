package net.integritas.android.avmixer;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import net.integritas.android.avmixer.util.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;


/**
 * <p>
 *  Class to clone audio and/or video tracks from a source video media file specified on an
 *  AVMixerObject
 * </p>
 *
 * @author AlexGP (alexandre.gomes@integritas.net)
 *
 * @version 1.2
 * @since 1.0
 */
public class AVCloneTracks {
    private static final String TAG = "AVCloneTracks";
    private static final int MAX_SAMPLE_SIZE = 1024 * 1024;
    private static boolean processDone;

    public static enum EXTRACTION_TYPE {AudioOnly, VideoOnly, AudioVideo}

    ;

    public static enum VIDEO_RES_TYPE {Res_360p, Res_480p, Res_720p, Res_1080p, Res_Native}

    ;

    /**
     * Using the MediaMuxer to clone a media file.
     */
    public static void cloneTrack(final AVMixerObject mixerObj, final String videoPath,
                                  final String outputFilenamePath, final int degrees,
                                  final EXTRACTION_TYPE extractionType,
                                  final VIDEO_RES_TYPE resType, final int startFrame,
                                  final int endFrame) throws IOException {
        processDone = false;

        // Set up MediaExtractor to read from the source.
        Thread t = new Thread() {
            @Override
            public void run() {
                try {

                    // Set up MediaMuxer for the destination.
                    MediaMuxer muxer;
                    if (mixerObj != null) {
                        muxer = new MediaMuxer(mixerObj.getFileName(),
                                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                    } else {
                        muxer = new MediaMuxer(outputFilenamePath,
                                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                    }

                    MediaFormat format = null;

                    MediaExtractor extractor = new MediaExtractor();
                    HashMap<Integer, Integer> indexMap = new HashMap<Integer, Integer>();

                    if (mixerObj != null && mixerObj.getVideos() != null) {
                        extractor.setDataSource(mixerObj.getVideos().get(0)
                                .getComponentFile().getAbsolutePath());
                    } else {
                        extractor.setDataSource(videoPath);
                    }

                    int trackCount = extractor.getTrackCount();

                    for (int i = 0; i < trackCount; i++) {
                        format = extractor.getTrackFormat(i);
                        String mime = format.getString(MediaFormat.KEY_MIME);
                        if (extractionType == EXTRACTION_TYPE.VideoOnly) {
                            if (mime.startsWith("video/")) {
                                extractor.selectTrack(i);
                                if (resType == VIDEO_RES_TYPE.Res_360p) {
                                    format.setInteger(MediaFormat.KEY_WIDTH, 640);
                                    format.setInteger(MediaFormat.KEY_HEIGHT, 360);
                                    format.setInteger(MediaFormat.KEY_BIT_RATE, 750);
                                } else if (resType == VIDEO_RES_TYPE.Res_480p) {
                                    format.setInteger(MediaFormat.KEY_WIDTH, 854);
                                    format.setInteger(MediaFormat.KEY_HEIGHT, 480);
                                    format.setInteger(MediaFormat.KEY_BIT_RATE, 1000);
                                } else if (resType == VIDEO_RES_TYPE.Res_720p) {
                                    format.setInteger(MediaFormat.KEY_WIDTH, 1280);
                                    format.setInteger(MediaFormat.KEY_HEIGHT, 720);
                                    format.setInteger(MediaFormat.KEY_BIT_RATE, 4400);
                                } else if (resType == VIDEO_RES_TYPE.Res_1080p) {
                                    format.setInteger(MediaFormat.KEY_WIDTH, 1920);
                                    format.setInteger(MediaFormat.KEY_HEIGHT, 1080);
                                    format.setInteger(MediaFormat.KEY_BIT_RATE,
                                            (int) (1920 * 1080 * 30 * 0.1));
                                }

                                int dstIndex = muxer.addTrack(format);
                                indexMap.put(i, dstIndex);
                            }
                        } else if (extractionType == EXTRACTION_TYPE.AudioOnly) {
                            if (mime.startsWith("audio/")) {
                                extractor.selectTrack(i);
                                int dstIndex = muxer.addTrack(format);
                                indexMap.put(i, dstIndex);
                            }
                        } else if (extractionType == EXTRACTION_TYPE.AudioVideo) {
                            extractor.selectTrack(i);
                            int dstIndex = muxer.addTrack(format);
                            indexMap.put(i, dstIndex);
                        }
                    }

                    // Copy the samples from MediaExtractor to MediaMuxer.
                    boolean sawEOS = false;
                    int bufferSize = MAX_SAMPLE_SIZE;
                    int frameCount = 0;
                    int offset = 100;
                    ByteBuffer dstBuf = ByteBuffer.allocate(bufferSize);
                    BufferInfo bufferInfo = new BufferInfo();
                    if (degrees >= 0) {
                        muxer.setOrientationHint(degrees);
                    }

                    muxer.start();
                    while (!sawEOS) {
                        bufferInfo.offset = offset;
                        bufferInfo.size = extractor.readSampleData(dstBuf, offset);
                        if (bufferInfo.size < 0) {
                            Logger.LOGV(TAG, "saw input EOS.");
                            sawEOS = true;
                            bufferInfo.size = 0;
                        } else {
                            if (frameCount >= startFrame
                                    && (frameCount <= endFrame || endFrame == -1)) {
                                bufferInfo.presentationTimeUs = extractor.getSampleTime();
                                bufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                                int trackIndex = extractor.getSampleTrackIndex();
                                muxer.writeSampleData(indexMap.get(trackIndex), dstBuf,
                                        bufferInfo);

                                Logger.LOGV(TAG, "Frame (" + frameCount + ") " +
                                        "PresentationTimeUs:" + bufferInfo.presentationTimeUs +
                                        " Flags:" + bufferInfo.flags +
                                        " TrackIndex:" + trackIndex +
                                        " Size(KB) " + bufferInfo.size / 1024);
                            }

                            extractor.advance();
                            frameCount++;
                        }
                    }
                    muxer.stop();
                    muxer.release();
                } catch (Throwable t) {

                } finally {
                    processDone = true;
                }

            }
        };

        t.setPriority(Thread.MAX_PRIORITY);
        t.start();

        while (!processDone) {
            Thread.yield();
        }
    }
}