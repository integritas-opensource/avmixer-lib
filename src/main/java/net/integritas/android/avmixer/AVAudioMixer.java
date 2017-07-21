package net.integritas.android.avmixer;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import net.integritas.android.avmixer.util.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * <p>
 * Class responsible to mix two or more audio tracks in a single audio track, overlapping them.
 * </p>
 *
 * @author AlexGP (alexandre.gomes@integritas.net)
 * @author Raphael Kohn (raphael.kohn@integritas.net)
 * @version 1.8
 * @since 1.0
 */
class AVAudioMixer {

    /**
     * Constants
     */
    private static final String COMPRESSED_AUDIO_FILE_MIME_TYPE = "audio/mp4a-latm";
    private static final String COMPRESSED_3GPP_AUDIO_FILE_MIME_TYPE = "audio/3gpp";
    private static final int KEY_CHANNEL_COUNT = 2;
    private static final int COMPRESSED_AUDIO_FILE_BIT_RATE = 128000; // 128kbps
    private static final int SAMPLING_RATE = 48000;
    private static final int CODEC_TIMEOUT_IN_MS = 5000;
    private static final boolean VERBOSE = false;
    private static final int THREEGP2_SAMPLING_RATE = 8 * 1024;
    private static final int THREEGP2_BIT_RATE = 8000;
    private static final int THREEGP2_CHANNEL = 1;

    private int audioChunk;
    private AVMixerObject mMixObject;
    private IAVInternalListener mListener;
    private boolean mMixingOver;
    private Exception mException;
    private ArrayList<short[]> mixedSoundBuffer = new ArrayList<short[]>();
    private ArrayList<short[]>[] audioBuffers;
    private ArrayList<Long> presentationArray = new ArrayList<Long>();
    private long lastPresentationTimeUsToMuxer;
    private float volumes[];
    private long[] audioDelays;
    private int mBufferSize;
    private short[] mStaticAudio;


    public AVAudioMixer(AVMixerObject mixObject, IAVInternalListener listener) {

        mMixObject = mixObject;
        mListener = listener;
    }

    /**
     * Mix the samples from AudioFiles in the AVMixerObject
     */
    public void mixSamples() throws Exception {
        Runnable r = new Runnable() {
            @Override
            public void run() {

                ArrayList<AVAudioMixerComponent> audioFiles = mMixObject.getAudioComponents();
                int n_audioFiles = audioFiles.size();

                MediaCodec[] decoders = new MediaCodec[n_audioFiles];
                MediaExtractor[] extractors = new MediaExtractor[n_audioFiles];
                MediaCodec.BufferInfo[] bufferInfos = new MediaCodec.BufferInfo[n_audioFiles];
                boolean[] sawInputEOSs = new boolean[n_audioFiles];

                short[][] shortArray = new short[n_audioFiles][];
                boolean[] outputEOS = new boolean[n_audioFiles];

                /* reads the set audio delays */
                audioDelays = new long[n_audioFiles];
                for (int i = 0; i < n_audioFiles; i++) {
                    audioDelays[i] = audioFiles.get(i).getDelay();
                }

                /* prepare audio buffers */
                audioBuffers = new ArrayList[n_audioFiles];
                for (int i = 0; i < audioBuffers.length; i++) {
                    audioBuffers[i] = new ArrayList<>();
                }

                String mime = "";
                int sampleRate = 0;
                MediaFormat format = null;
                MediaFormat outputFormat = null;
                MediaCodec encoder = null;

                try {
                    MediaMuxer muxer = new MediaMuxer(mMixObject.getTempAudioFilePath(),
                            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

                    /* reads the set audio volume */
                    volumes = new float[n_audioFiles];
                    for (int i = 0; i < n_audioFiles; i++) {
                        volumes[i] += mMixObject.getAudioComponents().get(i).getVolume();
                    }

                    //Setup Decoder and Extractor
                    for (int i = 0; i < n_audioFiles; i++) {

                        extractors[i] = new MediaExtractor();

                        try {
                            extractors[i].setDataSource(audioFiles.get(i)
                                    .getComponentFile().getAbsolutePath());

                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        format = extractors[i].getTrackFormat(0);
                        mime = format.getString(MediaFormat.KEY_MIME);
                        if (!mime.equals(COMPRESSED_3GPP_AUDIO_FILE_MIME_TYPE)) {
                            sampleRate = (sampleRate > format.getInteger(
                                    MediaFormat.KEY_SAMPLE_RATE) || sampleRate == 0)
                                    ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                    : sampleRate;
                        }

                        decoders[i] = MediaCodec.createDecoderByType(mime);
                        decoders[i].configure(format, null, null, 0);
                        decoders[i].start();
                        extractors[i].selectTrack(0);
                        bufferInfos[i] = new MediaCodec.BufferInfo();
                    }

                    if (n_audioFiles == 1
                            && !mime.equals(COMPRESSED_3GPP_AUDIO_FILE_MIME_TYPE)) {
                        outputFormat = MediaFormat.createAudioFormat(
                                mime,
                                sampleRate,
                                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                        outputFormat.setInteger(MediaFormat.KEY_BIT_RATE,
                                COMPRESSED_AUDIO_FILE_BIT_RATE);

                        encoder = MediaCodec
                                .createEncoderByType(mime);

                        try {
                            encoder.configure(outputFormat, null,
                                    null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                        } catch (Throwable t) {
                            outputFormat = MediaFormat.createAudioFormat(
                                    COMPRESSED_AUDIO_FILE_MIME_TYPE, SAMPLING_RATE,
                                    KEY_CHANNEL_COUNT);
                            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE,
                                    COMPRESSED_AUDIO_FILE_BIT_RATE);
                            encoder = MediaCodec
                                    .createEncoderByType(mime);
                            encoder.configure(outputFormat, null,
                                    null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                        }
                    } else if (n_audioFiles == 1
                            && mime.equals(COMPRESSED_3GPP_AUDIO_FILE_MIME_TYPE)) {
                        outputFormat = MediaFormat.createAudioFormat(
                                COMPRESSED_3GPP_AUDIO_FILE_MIME_TYPE,
                                THREEGP2_SAMPLING_RATE, THREEGP2_CHANNEL);
                        outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, THREEGP2_BIT_RATE);

                        encoder = MediaCodec
                                .createEncoderByType(COMPRESSED_3GPP_AUDIO_FILE_MIME_TYPE);
                        encoder.configure(outputFormat, null,
                                null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                    } else {
                        outputFormat = MediaFormat.createAudioFormat(
                                COMPRESSED_AUDIO_FILE_MIME_TYPE, sampleRate, KEY_CHANNEL_COUNT);
                        outputFormat.setInteger(MediaFormat.KEY_BIT_RATE,
                                COMPRESSED_AUDIO_FILE_BIT_RATE);

                        encoder = MediaCodec
                                .createEncoderByType(COMPRESSED_AUDIO_FILE_MIME_TYPE);
                        try {
                            encoder.configure(outputFormat, null,
                                    null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                        } catch (Throwable t) {
                            outputFormat = MediaFormat.createAudioFormat(
                                    COMPRESSED_AUDIO_FILE_MIME_TYPE, SAMPLING_RATE,
                                    KEY_CHANNEL_COUNT);
                            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE,
                                    COMPRESSED_AUDIO_FILE_BIT_RATE);
                            encoder = MediaCodec
                                    .createEncoderByType(COMPRESSED_AUDIO_FILE_MIME_TYPE);
                            encoder.configure(outputFormat, null,
                                    null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                        }
                    }

                    encoder.start();

                    ByteBuffer[] codecInputBuffers3 = encoder.getInputBuffers();
                    ByteBuffer[] codecOutputBuffers3 = encoder.getOutputBuffers();

                    final long timeoutUs = 5000;
                    int noOutputCounter = 0;
                    long diff = 0;
                    while (!isAudioProcessCompleted(outputEOS) && noOutputCounter < 50) {
                        noOutputCounter++;

                        for (int i = 0; i < n_audioFiles; i++) {
                            if (!sawInputEOSs[i]) {
                                ByteBuffer[] codecInputBuffers = decoders[i].getInputBuffers();
                                int inputBufferIndex = decoders[i].dequeueInputBuffer(timeoutUs);
                                if (inputBufferIndex >= 0) {
                                    ByteBuffer buffer = codecInputBuffers[inputBufferIndex];
                                    int sampleSize = extractors[i].readSampleData(buffer, 0);
                                    long presentationTimeUs = 0;
                                    if (sampleSize < 0) {
                                        sawInputEOSs[i] = true;
                                        sampleSize = 0;
                                    } else {
                                        presentationTimeUs = extractors[i].getSampleTime();
                                        Logger.LOGV("AudioMix", "audio file "
                                                + i + " prestime: " + presentationTimeUs);
                                    }
                                    if (presentationTimeUs >= audioFiles.get(i).getStartAt()) {
                                        decoders[i].queueInputBuffer(inputBufferIndex, 0, sampleSize,
                                                presentationTimeUs,
                                                sawInputEOSs[i]
                                                        ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                                    } else {
                                        decoders[i].queueInputBuffer(inputBufferIndex, 0, 0,
                                                presentationTimeUs,
                                                sawInputEOSs[i]
                                                        ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                                        noOutputCounter = 0;
                                    }

                                    if (!sawInputEOSs[i]) {
                                        extractors[i].advance();
                                    }
                                }
                            }
                        }

                        long presentationTimeUsToMuxer = 0;

                        for (int i = 0; i < n_audioFiles; i++) {

                            ByteBuffer[] codecOutputBuffers = decoders[i].getOutputBuffers();
                            int outputBufferIndex =
                                    decoders[i].dequeueOutputBuffer(bufferInfos[i], timeoutUs);
                            if (outputBufferIndex >= 0) {
                                if (bufferInfos[i].size > 0) {
                                    noOutputCounter = 0;

                                    ByteBuffer buffer = codecOutputBuffers[outputBufferIndex];

                                    if (mBufferSize == 0) {
                                        mBufferSize = bufferInfos[i].size / 2;
                                    }

                                    shortArray[i] = new short[bufferInfos[i].size / 2];
                                    audioBuffers[i].add(shortArray[i]);

                                    buffer.rewind();
                                    buffer.asShortBuffer().get(shortArray[i]);
                                    buffer.clear();

                                    if (presentationTimeUsToMuxer == 0) {
                                        presentationTimeUsToMuxer
                                                = bufferInfos[i].presentationTimeUs;
                                    } else {
                                        presentationTimeUsToMuxer
                                                = Math.min(presentationTimeUsToMuxer,
                                                bufferInfos[i].presentationTimeUs);
                                    }

                                    decoders[i].releaseOutputBuffer(outputBufferIndex, false);
                                    if ((bufferInfos[i].flags
                                            & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                        outputEOS[i] = true;
                                    }
                                } else {
                                    if (diff == 0) {
                                        diff = calculatePresentationTimeOffset();
                                    }
                                    shortArray[i] = null;
                                }
                            } else {
                                if (diff == 0) {
                                    diff = calculatePresentationTimeOffset();
                                }
                                shortArray[i] = null;
                            }
                        }

                        /* fill the presentation buffer array */
                        if (presentationArray.size() == 0
                                || (presentationArray.size() >= 0
                                && presentationTimeUsToMuxer > 0)) {

                            if (presentationArray.size() > 0) {
                                if (presentationTimeUsToMuxer >
                                        presentationArray.get(presentationArray.size() - 1)) {
                                    if (diff != 0) {
                                        presentationArray
                                                .add(presentationArray
                                                        .get(presentationArray.size() - 1) + diff);
                                    } else {
                                        presentationArray.add(presentationTimeUsToMuxer);
                                    }
                                }
                            } else {
                                presentationArray.add(presentationTimeUsToMuxer);
                            }
                        }
                    }

                    for (int i = 0; i < n_audioFiles; i++) {
                        decoders[i].stop();
                        decoders[i].release();
                        decoders[i] = null;
                        extractors[i].release();
                        extractors[i] = null;
                    }

                     /* add static audio to a specific audio buffer if its has a delay set */
                    for (int z = 0; z < audioDelays.length; z++) {

                        long presTime = 0;
                        int index = 0;
                        while (audioDelays[z] > presTime) {
                            if (mStaticAudio == null) {
                                mStaticAudio = new short[mBufferSize];
                                Arrays.fill(mStaticAudio, (short) 0);
                            }
                            audioBuffers[z].add(0, mStaticAudio);

                            if (++index < presentationArray.size()) {
                                presTime = presentationArray.get(index);
                            } else {
                                if (diff == 0) {
                                    diff = calculatePresentationTimeOffset();
                                }
                                presTime += diff;
                            }
                        }
                    }

                    Logger.LOGV("AVAudioMixer", "------------------------------------------" +
                            " noOutputCounter is: " + noOutputCounter);

                    doAudioBuffersMixing();

                    /* write audio data */
                    int counter = 0;
                    long multiplier = 1l;
                    for (short[] shortData : mixedSoundBuffer) {
                        if (shortData.length > 0) {
                            byte[] data = shortToByte(shortData);
                            ByteBuffer byteBuf = ByteBuffer.allocate(data.length);
                            byteBuf.put(data);
                            byteBuf.rewind();
                            if (diff == 0) {
                                diff = calculatePresentationTimeOffset();
                            }
                            long presTime = counter < presentationArray.size()
                                    ? presentationArray.get(counter) :
                                    presentationArray.get(presentationArray.size() - 1)
                                            + (diff * multiplier);
                            if (counter >= presentationArray.size()) {
                                ++multiplier;
                            }
                            ++counter;
                            Logger.LOGV("AVAudioMixer",
                                    "counter on writing audio data is: " + counter);
                            writeOnMuxer(encoder, codecInputBuffers3, codecOutputBuffers3, byteBuf,
                                    muxer, presTime);
                        }
                    }

                    encoder.release();

                    muxer.stop();
                    muxer.release();

                    mListener.onAudioFinishedMixing();

                } catch (Exception t) {
                    mListener.onMixingError(t);
                } finally {
                    mMixingOver = true;
                }
            }
        };

        Thread t = new Thread(r);
        t.setPriority(Thread.MAX_PRIORITY);
        t.start();

        while (!mMixingOver) {
            Thread.yield();
        }

        if (mException != null) {
            throw mException;
        }
    }

    private long calculatePresentationTimeOffset() {
        long diff = 0;
        for (int i = presentationArray.size() - 1; i > 0; i--) {
            if (presentationArray.get(i) != 0
                    && presentationArray.get(i - 1) != 0
                    && presentationArray.get(i)
                    > presentationArray.get(i - 1)) {
                long sub = presentationArray.get(i)
                        - presentationArray.get(i - 1);
                if (diff > 0 && Math.abs(diff - sub) <= (float) diff * 0.05) {
                    break;
                }
                diff = sub;
            }
        }

        return diff;
    }

    private void doAudioBuffersMixing() {
        int totalBufferSize = 0;
        for (int i = 0; i < audioBuffers.length; i++) {
            if (totalBufferSize < audioBuffers[i].size()) {
                totalBufferSize = audioBuffers[i].size();
            }
        }

        short[][] data = new short[audioBuffers.length][];
        for (int j = 0; j < totalBufferSize; j++) {
            for (int x = 0; x < audioBuffers.length; x++) {
                if (j < audioBuffers[x].size()) {
                    data[x] = audioBuffers[x].get(j);
                } else {
                    data[x] = null;
                }
            }

            short[][] dataShortArray = verifyBufferData(data);
            if (dataShortArray.length > 0) {
                short[] shortArrayThree;

                /* checks if mixing is needed */
                if (dataShortArray.length > 1 || dataShortArray.length == 1 && volumes[0] < 1.0f) {
                    shortArrayThree = mix(dataShortArray, volumes);
                } else {
                    shortArrayThree = dataShortArray[0];
                }

                mixedSoundBuffer.add(shortArrayThree);

                if (verifyAudioDelays(audioDelays) == 0) {
                    /* considers the general audio delay set on the mixer object
                       if no audio delay is set on any audio track individually  */
                    if (j < presentationArray.size()) {
                        if (presentationArray.get(j) < mMixObject.getAudioDelay()) {
                            if (mStaticAudio == null) {
                                mStaticAudio = new short[shortArrayThree.length];
                                Arrays.fill(mStaticAudio, (short) 0);
                            }

                            mixedSoundBuffer.add(0, mStaticAudio);
                        }
                    }
                }
            }
        }
    }

    private boolean isAudioProcessCompleted(boolean[] values) {
        boolean result = false;
        for (int i = 0; i < values.length; i++) {
            if (values[i]) {
                result = true;
            } else {
                result = false;
                break;
            }
        }

        return result;
    }

    private short[][] verifyBufferData(short[][] samples) {
        for (int i = 0; i < samples.length; i++) {
            if (samples[i] == null) {
                /* fills with static audio if a given audio sample is shorter */
                if (mStaticAudio == null) {
                    mStaticAudio = new short[mBufferSize];
                    Arrays.fill(mStaticAudio, (short) 0);
                }

                samples[i] = mStaticAudio;
            }
        }

        return samples;
    }

    private int verifyAudioDelays(long[] audioDelays) {
        int setDelays = 0;
        for (int i = 0; i < audioDelays.length; i++) {
            if (audioDelays[i] > 0) {
                ++setDelays;
            }
        }

        return setDelays;
    }

    private boolean isSampleBufferReady(short[][] samples) {
        boolean isReady = true;
        for (int i = 0; i < samples.length; i++) {
            if (samples[i] == null) {
                isReady = false;
                break;
            }
        }

        return isReady;
    }

    private byte[] shortToByte(short[] input) {
        int short_index, byte_index;
        int iterations = input.length;

        byte[] buffer = new byte[input.length * 2];

        short_index = byte_index = 0;

        for (/*NOP*/; short_index != iterations; /*NOP*/) {
            buffer[byte_index] = (byte) (input[short_index] & 0x00FF);
            buffer[byte_index + 1] = (byte) ((input[short_index] & 0xFF00) >> 8);

            ++short_index;
            byte_index += 2;
        }

        return buffer;
    }

    /*private static short[] mix(short[] buffer, short[] mixWith) {
        final int length = Math.min(buffer.length, mixWith.length);
        int mixed;
        for (int i = 0; i < length; i++) {
            mixed = (int) buffer[i] + (int) mixWith[i];
            if (mixed > 32767) mixed = 32767;
            if (mixed < -32768) mixed = -32768;
            buffer[i] = (short) mixed;
        }
        return buffer;
    }*/

    private static short[] mix(short[][] samples, float[] volumes) {

        int n_samples = samples.length;
        float[] mixedSamplesf = new float[1];
        short[] mixedSamples = new short[1];

        for (short x = 0; x < n_samples; x++) {

            float selectedVolume = volumes[x];
            short[] sample = samples[x];

            if (mixedSamples.length < sample.length) {
                mixedSamples = Arrays.copyOf(mixedSamples, sample.length);
                mixedSamplesf = Arrays.copyOf(mixedSamplesf, sample.length);
            }

            for (short i = 0; i < sample.length; i++) {

                float samplef = sample[i] / 32768.0f;
                samplef *= selectedVolume;
                mixedSamplesf[i] += samplef;

                // hard clipping
                if (mixedSamplesf[i] > 1.0f) mixedSamplesf[i] = 1.0f;
                if (mixedSamplesf[i] < -1.0f) mixedSamplesf[i] = -1.0f;
            }
        }

        for (int j = 0; j < mixedSamplesf.length; j++) {
            mixedSamples[j] = (short) (mixedSamplesf[j] * 32768.0f);
        }

        return mixedSamples;
    }

    private void writeOnMuxer(MediaCodec codec, ByteBuffer[] codecInputBuffers,
                              ByteBuffer[] codecOutputBuffers, ByteBuffer data, MediaMuxer mux,
                              long presentationTimeUs) {
        MediaCodec.BufferInfo outBuffInfo = new MediaCodec.BufferInfo();
        MediaFormat outputFormat = null;

        int inputBufIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_IN_MS);
        if (inputBufIndex >= 0) {
            ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];
            dstBuf.clear();

            dstBuf.put(data);
            codec.queueInputBuffer(inputBufIndex, 0, dstBuf.limit(), (long) presentationTimeUs, 0);
        }

        // Drain audio
        int outputBufIndex = 0;
        while (outputBufIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {

            outputBufIndex = codec.dequeueOutputBuffer(outBuffInfo, CODEC_TIMEOUT_IN_MS);
            if (outputBufIndex >= 0) {
                ByteBuffer encodedData = codecOutputBuffers[outputBufIndex];
                encodedData.position(outBuffInfo.offset);
                encodedData.limit(outBuffInfo.offset + outBuffInfo.size);

                if ((outBuffInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0 && outBuffInfo.size != 0) {
                    codec.releaseOutputBuffer(outputBufIndex, false);
                } else {
                    outBuffInfo.presentationTimeUs = presentationTimeUs;
                    if (lastPresentationTimeUsToMuxer <= outBuffInfo.presentationTimeUs) {
                        mux.writeSampleData(0, encodedData, outBuffInfo);
                        lastPresentationTimeUsToMuxer = outBuffInfo.presentationTimeUs;
                    }
                    ++audioChunk;
                    if (VERBOSE)
                        Log.d("AudioMix", "Audio chunk wrote: " + audioChunk + " - data size is: "
                                + encodedData.remaining() + " - prestime: "
                                + outBuffInfo.presentationTimeUs);
                    codec.releaseOutputBuffer(outputBufIndex, false);
                }
            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                outputFormat = codec.getOutputFormat();
                Logger.LOGE("test", "Output format changed - " + outputFormat);
                mux.addTrack(outputFormat);
                mux.start();
            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                Logger.LOGE("test", "Output buffers changed during encode!");
            } else if (outputBufIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // NO OP
            } else {
                Logger.LOGE("test", "Unknown return code from dequeueOutputBuffer - "
                        + outputBufIndex);
            }
        }
    }
}