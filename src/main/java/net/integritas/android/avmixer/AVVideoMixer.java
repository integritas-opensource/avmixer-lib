package net.integritas.android.avmixer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.media.ExifInterface;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import net.integritas.android.avmixer.opengl.BitmapTextureRenderer;
import net.integritas.android.avmixer.opengl.CodecInputSurface;
import net.integritas.android.avmixer.opengl.TextureExternalOESRenderer;
import net.integritas.android.avmixer.util.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * <p>
 * Class responsible to mix images and videos into one single video file.
 * </p>
 *
 * @author AlexGP (alexandre.gomes@integritas.net)
 * @author Raphael Kohn (raphael.kohn@integritas.net)
 * @version 1.3
 * @since 1.0
 */
class AVVideoMixer implements SurfaceTexture.OnFrameAvailableListener {

    //Logging parameters
    private static final String TAG = "AVVideoMixer";

    // parameters for the encoder
    private static final long VIDEO_PRESENTATION_OFFSET = 400000l;
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final int FRAME_RATE = 30;               // 15fps
    private static final int IFRAME_INTERVAL = 10;          // 10 seconds between I-frames
    private static final int IMAGES_FRAME_REPEATER = 5; // equivalent to 1 sec of pres. time
    private static final long IMAGES_FRAME_BASE_PRESENTATION_TIME = 200000l;
    private static long frameDuration;
    private static long videoOffset;
    AVMixerObject mMixObj;
    private int mWidth = -1;
    private int mHeight = -1;
    private int mBitRate = -1;
    private int mFrameRate = -1;
    private IAVInternalListener mListener;
    // encoder / muxer state
    private MediaCodec mEncoder;
    private MediaCodec mVideoDecoder;
    private CodecInputSurface mInputSurface;
    private MediaMuxer mMuxer;
    private MediaExtractor mExtractor;
    private int mTrackIndex;
    private boolean mMuxerStarted;
    private int mFrameIndex;
    private SurfaceTexture decoderTexture;
    private Surface decoderSurface;
    private TextureExternalOESRenderer oesRenderer;
    private BitmapTextureRenderer bmpRenderer;
    private Object frameSyncObj = new Object();
    private boolean mFrameAvailable;
    private MediaCodec.BufferInfo mBufferInfo;
    private String outputFile;
    private ArrayList<AVMixerComponent> mComponents;
    private String originalAudioFilename = "Audio_%s.m4a";
    private boolean mStartWithFrame;
    private boolean mUseLandscapeOrientation;
    private final String TMP_DIR = "TMP";

    public AVVideoMixer(AVMixerObject obj, int width, int height, int bitRate, int frameRate,
                        IAVInternalListener listener) {

        this.mWidth = width;
        this.mHeight = height;
        this.mBitRate = bitRate;
        this.outputFile = obj.getTempVideoFilePath();
        this.mMixObj = obj;
        this.mFrameRate = frameRate;
        this.mListener = listener;
    }

    public AVVideoMixer(AVMixerObject obj, int width, int height, int bitRate, int frameRate,
                        IAVInternalListener listener, boolean useLandscapeOrientation) {

        this(obj, width, height, bitRate, frameRate, listener);
        this.mUseLandscapeOrientation = useLandscapeOrientation;
    }

    /**
     * Generates the presentation time for frame N, in nanoseconds.
     */
    private static long computePresentationTimeNsec(int frameIndex) {
        long value = frameIndex;
        return value * IMAGES_FRAME_BASE_PRESENTATION_TIME;
    }

    /**
     * Configures encoder and muxer state, and prepares the input Surface.
     */
    private void prepareEncoder() throws Exception {
        mBufferInfo = new MediaCodec.BufferInfo();

        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);

        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        Logger.LOGV(TAG, "format: " + format);

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);

        try {
            mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (Throwable t) {
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        }

        mInputSurface = new CodecInputSurface(mEncoder.createInputSurface());
        mEncoder.start();

        // Create a MediaMuxer.  We can't add the video track and start() the muxer here,
        // because our MediaFormat doesn't have the Magic Goodies.  These can only be
        // obtained from the encoder after it has started processing data.
        try {
            mMuxer = new MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("MediaMuxer creation failed", ioe);
        }

        mTrackIndex = -1;
        mMuxerStarted = false;
    }

    /**
     * Encode of AVC video from a Surface.  The output is saved as an MP4 file.
     */
    public void mixVideo() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    frameDuration = 0;
                    videoOffset = 0;
                    prepareEncoder();
                    mInputSurface.makeCurrent();

                    mComponents = mMixObj.getVisualComponents();

                    bmpRenderer = new BitmapTextureRenderer();
                    Bitmap scaledBitmap = null;
                    Bitmap b = null;
                    for (int i = 0; i < mComponents.size(); i++) {

                        if (b != null) {
                            b.recycle();
                        }

                        b = null;

                        if (scaledBitmap != null) {
                            scaledBitmap.recycle();
                        }

                        scaledBitmap = null;

                        BitmapFactory.Options options = new BitmapFactory.Options();
                        b = BitmapFactory.decodeFile(mComponents.get(i)
                                .getComponentFile().getAbsolutePath(), options);

                        //If it is Bitmap... we have an Image as a Frame
                        if (options.outWidth != -1 && options.outHeight != -1) {
                            int presMultiplier = ((AVImageMixerComponent)
                                    mComponents.get(i)).getPresentationTime();


                            /* checks if the video starts with a frame */
                            if (i == 0) {
                                mStartWithFrame = true;
                            }

                            int frameRate = (i == 0 && mFrameIndex == 0
                                    && frameDuration == 0 && videoOffset == 0)
                                    ? (presMultiplier * IMAGES_FRAME_REPEATER) + 2
                                    : presMultiplier * IMAGES_FRAME_REPEATER;
                            for (int x = 0; x < frameRate; x++) {
                                // This is an image file.
                                Matrix m = new Matrix();
                                m.setRectToRect(new RectF(0, 0, b.getWidth(), b.getHeight()),
                                        new RectF(0, 0, mWidth, mHeight),
                                        Matrix.ScaleToFit.CENTER);
                                normalizeImageToLandscape(mComponents.get(i)
                                        .getComponentFile().getAbsolutePath(), m);
                                if (scaledBitmap == null) {
                                    scaledBitmap = Bitmap.createBitmap(b, 0, 0, b.getWidth(),
                                            b.getHeight(), m, true);

                                    bmpRenderer.surfaceCreated(scaledBitmap);
                                }

                                frameDuration =
                                        computePresentationTimeNsec(mFrameIndex) + videoOffset;
                                drainEncoder(false, frameDuration);

                                // Generate a new frame of input.
                                bmpRenderer.drawFrame();
                                mFrameIndex++;

                                // Submit it to the encoder.  The eglSwapBuffers call will block if the input
                                // is full, which would be bad if it stayed full until we dequeued an output
                                // buffer (which we can't do, since we're stuck here).  So long as we fully drain
                                // the encoder before supplying additional input, the system guarantees that we
                                // can supply another frame without blocking.

                                Logger.LOGV(TAG, "sending frame " + i + 1
                                        + " to encoder - frame time: " + frameDuration);
                                mInputSurface.swapBuffers();
                            }
                        } else {
                            prepareVideoDecoder(mComponents.get(i).getComponentFile()
                                    .getAbsolutePath());

                            if (mComponents.get(i) instanceof AVVideoMixerComponent) {
                                processVideo((AVVideoMixerComponent) mComponents.get(i), i);
                            } else {
                                throw new Exception("Invalid video component to process!");
                            }
                        }
                    }

                    // send end-of-stream to encoder, and drain remaining output
                    drainEncoder(true, 0);
                    mListener.onVideoFinishedMixing();
                } catch (Exception e) {
                    mListener.onMixingError(e);
                } finally {
                    // release encoder, muxer, and input Surface
                    releaseAll();
                }
            }
        };

        Thread t = new Thread(runnable);
        t.setPriority(Thread.MAX_PRIORITY);
        t.start();
    }

    private void normalizeImageToLandscape(String imgPath, Matrix m) {
        try {
            if (mUseLandscapeOrientation) {
                ExifInterface exif = new ExifInterface(imgPath);

                int r = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

                if (r == ExifInterface.ORIENTATION_ROTATE_90) {
                    m.postRotate(90);
                } else if (r == ExifInterface.ORIENTATION_ROTATE_180) {
                    m.postRotate(180);
                } else if (r == ExifInterface.ORIENTATION_ROTATE_270) {
                    m.postRotate(270);
                }
            }
        } catch (IOException ex) {
            Log.e("AVVideoMixer", "normalizeImageToLandscape() error", ex);
        }
    }

    private void prepareVideoDecoder(String videoFilenamePath) {
        try {
            mFrameIndex = 0;
            mExtractor = new MediaExtractor();
            mExtractor.setDataSource(videoFilenamePath);
            int trackCount = mExtractor.getTrackCount();

            if (decoderSurface == null) {
                oesRenderer = new TextureExternalOESRenderer();
                oesRenderer.surfaceCreated();
                decoderTexture = new SurfaceTexture(oesRenderer.getTextureId());
                decoderSurface = new Surface(decoderTexture);
                decoderTexture.setOnFrameAvailableListener(AVVideoMixer.this);
            }

            for (int x = 0; x < trackCount; x++) {
                MediaFormat format = mExtractor.getTrackFormat(x);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/")) {
                    mExtractor.selectTrack(x);
                    mVideoDecoder = MediaCodec.createDecoderByType(mime);
                    mVideoDecoder.configure(format, decoderSurface, null, 0);
                    break;
                }
            }

            mVideoDecoder.start();
        } catch (Throwable t) {
            Logger.LOGE("AVVideoMixer", "error preparing video decoder!");
        }
    }

    /**
     * Releases encoder resources.  May be called after partial / failed initialization.
     */
    private void releaseAll() {
        Logger.LOGV(TAG, "releasing encoder objects");
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
        if (mInputSurface != null) {
            mInputSurface.release();
            mInputSurface = null;
        }
        if (decoderTexture != null) {
            decoderTexture.release();
        }
        if (decoderSurface != null) {
            decoderSurface.release();
        }

        if (mVideoDecoder != null) {
            mVideoDecoder.stop();
            mVideoDecoder.release();
            mVideoDecoder = null;
        }
        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
    }

    /**
     * Extracts all pending data from the encoder.
     * <p/>
     * If endOfStream is not set, this returns when there is no more data to drain.  If it
     * is set, we send EOS to the encoder, and then iterate until we see EOS on the output.
     * Calling this with endOfStream set should be done once, right before stopping the muxer.
     */
    private void drainEncoder(boolean endOfStream, long time) {
        final int TIMEOUT_USEC = 10000;
        Logger.LOGV(TAG, "drainEncoder(" + endOfStream + ")");

        if (endOfStream) {
            Logger.LOGV(TAG, "sending EOS to encoder");
            mEncoder.signalEndOfInputStream();
        }

        ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
        while (true) {
            int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!endOfStream) {
                    break;      // out of while
                } else {
                    Logger.LOGV(TAG, "no output available, spinning to await EOS");
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                encoderOutputBuffers = mEncoder.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                if (mMuxerStarted) {
                    throw new RuntimeException("format changed twice");
                }
                MediaFormat newFormat = mEncoder.getOutputFormat();
                Logger.LOGV(TAG, "encoder output format changed: " + newFormat);

                // now that we have the Magic Goodies, start the muxer
                mTrackIndex = mMuxer.addTrack(newFormat);
                mMuxer.start();
                mMuxerStarted = true;
            } else if (encoderStatus < 0) {
                Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                        encoderStatus);
                // let's ignore it
            } else {
                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                            " was null");
                }

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    Logger.LOGV(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mBufferInfo.size = 0;
                }

                if (mBufferInfo.size != 0) {
                    if (!mMuxerStarted) {
                        throw new RuntimeException("muxer hasn't started");
                    }

                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);

                    if (!endOfStream && time > 0) {
                        mBufferInfo.presentationTimeUs = time;

                        Logger.LOGV("EncodeDecode", "pres time on muxer is: "
                                + mBufferInfo.presentationTimeUs);
                        mMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                    }
                    Logger.LOGV(TAG, "sent " + mBufferInfo.size + " bytes to muxer");
                }

                mEncoder.releaseOutputBuffer(encoderStatus, false);

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (!endOfStream) {
                        Log.w(TAG, "reached end of stream unexpectedly");
                    } else {
                        Logger.LOGV(TAG, "end of stream reached");
                    }
                    break;      // out of while
                }
            }
        }
    }

    /**
     * Extract all frames from the Video, render then on a Surface with OpenGL and
     * feed the Encoder with frame data, through an InputSurface.
     */
    private void processVideo(AVVideoMixerComponent videoComp, int index) {
        try {
            long presentationTime = 0;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            final long timeoutUs = 10000;
            boolean sawInputEOS = false;
            boolean sawOutputEOS = false;
            int noOutputCounter = 0;

            long currentOffset = frameDuration > videoOffset ? frameDuration : videoOffset;

            /* checks if the original video's audio track has to be processed */
            if (videoComp.isOriginalAudioProcessed()) {
                File f = new File(Environment
                        .getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                        .getAbsolutePath() + File.separator + TMP_DIR);
                f.mkdirs();

                String outputFilepathName = f.getAbsolutePath() + File.separator
                        + String.format(originalAudioFilename, index);
                AVCloneTracks.cloneTrack(null, videoComp.getComponentFile().getAbsolutePath(),
                        outputFilepathName, 0, AVCloneTracks.EXTRACTION_TYPE.AudioOnly,
                        AVCloneTracks.VIDEO_RES_TYPE.Res_Native, -1, -1);

                AVAudioMixerComponent audio;
                if (mStartWithFrame) {
                    audio =
                            new AVAudioMixerComponent(new File(outputFilepathName), 1, 0,
                                    ((currentOffset - VIDEO_PRESENTATION_OFFSET) / 1000l));
                    videoComp.setStartFrame(currentOffset - VIDEO_PRESENTATION_OFFSET);
                } else {
                    audio =
                            new AVAudioMixerComponent(new File(outputFilepathName), 1, 0,
                                    (currentOffset / 1000l));
                    videoComp.setStartFrame(currentOffset);
                }

                mMixObj.addSoundComponent(audio);
            }


            ByteBuffer[] codecInputBuffers = mVideoDecoder.getInputBuffers();

            int videoFrameCount = 0;
            int counter = 0;

            while (!sawOutputEOS && noOutputCounter < 150) {
                noOutputCounter++;
                long presentationTimeUs = 0;
                if (!sawInputEOS) {
                    int sampleSize = 0;
                    int inputBufferIndex = mVideoDecoder.dequeueInputBuffer(timeoutUs);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer buffer = codecInputBuffers[inputBufferIndex];
                        if (!sawInputEOS) {
                            sampleSize = mExtractor.readSampleData(buffer, 0);
                        }

                        if (sampleSize < 0) {
                            sawInputEOS = true;
                            sampleSize = 0;
                        } else {
                            presentationTimeUs = mExtractor.getSampleTime();
                            ++counter;
                            Logger.LOGV(TAG, "process video counter: " + counter);
                        }
                        mVideoDecoder.queueInputBuffer(inputBufferIndex, 0, sampleSize,
                                presentationTimeUs,
                                sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                        if (!sawInputEOS) {
                            mExtractor.advance();
                        }
                    }
                }

                int outputBufferIndex = mVideoDecoder.dequeueOutputBuffer(info, timeoutUs);
                if (outputBufferIndex >= 0) {
                    if (info.size > 0) {
                        noOutputCounter = 0;

                        if (presentationTime == 0
                                || (presentationTime > 0
                                && presentationTime < info.presentationTimeUs + currentOffset)) {

                            presentationTime = info.presentationTimeUs + currentOffset;

                            //Releases the buffer so the data on the Decoder can pass to the OpenGL buffer.
                            //Awaits the OpenGL processing and then, draw the texture on the InputSurface.
                            mVideoDecoder.releaseOutputBuffer(outputBufferIndex, true);
                            awaitNewImage();
                            drainEncoder(false, presentationTime);
                            oesRenderer.drawFrame(decoderTexture, false);
                            mInputSurface.swapBuffers();
                            videoFrameCount++;

                            Logger.LOGV("EncodeDecode", "Video current frame is: "
                                    + videoFrameCount + " timnestamp: " + info.presentationTimeUs);
                        }
                    } else {
                        mVideoDecoder.releaseOutputBuffer(outputBufferIndex, false);
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true;
                        mVideoDecoder.stop();
                        mVideoDecoder.release();
                        mVideoDecoder = null;
                        videoOffset = presentationTime;
                    }
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = mVideoDecoder.getOutputFormat();
                    Logger.LOGV("D/Decoder", "Decoder output format change: " + newFormat);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * Called when there is a new frame available processed by the SurfaceTexture
     *
     * @param surfaceTexture
     */
    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        Logger.LOGV(TAG, "new frame available!!");
        synchronized (frameSyncObj) {
            if (mFrameAvailable) {
                throw new RuntimeException("mFrameAvailable already set, frame could be dropped");
            }
            mFrameAvailable = true;
            frameSyncObj.notifyAll();
        }
    }

    /**
     * Because of this method, the VideoMixer must be called on a backgroud thread.
     * Locks the Thread until a new Frame is ready to be processed by the OpenGL
     */
    public void awaitNewImage() {
        final int TIMEOUT_MS = 2500;

        synchronized (frameSyncObj) {
            while (!mFrameAvailable) {
                try {
                    // Wait for onFrameAvailable() to signal us.  Use a timeout to avoid
                    // stalling the test if it doesn't arrive.
                    frameSyncObj.wait(TIMEOUT_MS);
                    if (!mFrameAvailable) {
                        // TODO: if "spurious wakeup", continue while loop
                        throw new RuntimeException("frame wait timed out");
                    }
                } catch (InterruptedException ie) {
                    // shouldn't happen
                    throw new RuntimeException(ie);
                }
            }
            mFrameAvailable = false;
        }

        // Latch the data.
        decoderTexture.updateTexImage();
    }
}