package net.integritas.android.avmixer.audio;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import net.integritas.android.avmixer.util.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;

import javazoom.jl.converter.Converter;

/**
 * <p>
 *  Class to convert any audio file format to a MPEG4 compliant format (.m4a) which is
 *  the format required by the MediaMuxer.
 * </p>
 *
 * @author AlexGP (alexandre.gomes@integritas.net)
 *
 * @since 1.0
 */
public class M4aAudioConverter {

    public static final int BIT_RATE = 128000;
    public static final int BUFFER_SIZE = 48000;
    public static final int CODEC_TIMEOUT_IN_MS = 5000;
    
    static String LOGTAG = "Audio convertion";
    private static String filePath;
    private static String outpuPath;
    private static boolean processDone;
    
    static Runnable convert = new Runnable() {
        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            try {
                File inputFile = new File(filePath);
                FileInputStream fis = new FileInputStream(inputFile);
    
                File outputFile = new File(outpuPath);
                if (outputFile.exists()) outputFile.delete();
    
                MediaMuxer mux = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                
                MediaExtractor extractor = new MediaExtractor();
                extractor.setDataSource(filePath);
                MediaFormat originalOutputFormat = extractor.getTrackFormat(0);
                String mime = originalOutputFormat.getString(MediaFormat.KEY_MIME);
                if (!mime.startsWith("audio/")) {
                    throw new Exception("invalid audio file!");
                }

                MediaFormat outputFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,
                        originalOutputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE), 
                        originalOutputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
                outputFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
                outputFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
    
                MediaCodec codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
                codec.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                codec.start();
                extractor.release();
    
                ByteBuffer[] codecInputBuffers = codec.getInputBuffers(); // Note: Array of buffers
                ByteBuffer[] codecOutputBuffers = codec.getOutputBuffers();
    
                MediaCodec.BufferInfo outBuffInfo = new MediaCodec.BufferInfo();
                byte[] tempBuffer = new byte[BUFFER_SIZE];
                boolean hasMoreData = true;
                double presentationTimeUs = 0;
                int audioTrackIdx = 0;
                int totalBytesRead = 0;
                int percentComplete = 0;
                do {
                    int inputBufIndex = 0;
                    while (inputBufIndex != -1 && hasMoreData) {
                        inputBufIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_IN_MS);
    
                        if (inputBufIndex >= 0) {
                            ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];
                            dstBuf.clear();
    
                            int bytesRead = fis.read(tempBuffer, 0, dstBuf.limit());
                            Logger.LOGE("bytesRead", "Readed " + bytesRead);
                            if (bytesRead == -1) { // -1 implies EOS
                                hasMoreData = false;
                                codec.queueInputBuffer(inputBufIndex, 0, 0, (long) presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            } else {
                                totalBytesRead += bytesRead;
                                dstBuf.put(tempBuffer, 0, bytesRead);
                                codec.queueInputBuffer(inputBufIndex, 0, bytesRead, (long) presentationTimeUs, 0);
                                presentationTimeUs = 1000000l * (totalBytesRead / 2) / outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                            }
                        }
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
                            }else{
                                mux.writeSampleData(audioTrackIdx, codecOutputBuffers[outputBufIndex], outBuffInfo);
                                codec.releaseOutputBuffer(outputBufIndex, false);
                            }
                        } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            outputFormat = codec.getOutputFormat();
                            Logger.LOGV(LOGTAG, "Output format changed - " + outputFormat);
                            audioTrackIdx = mux.addTrack(outputFormat);
                            mux.start();
                        } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            Logger.LOGE(LOGTAG, "Output buffers changed during encode!");
                        } else if (outputBufIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            // NO OP
                        } else {
                            Logger.LOGE(LOGTAG, "Unknown return code from dequeueOutputBuffer - " + outputBufIndex);
                        }
                    }
                    percentComplete = (int) Math.round(((float) totalBytesRead / (float) inputFile.length()) * 100.0);
                    Logger.LOGV(LOGTAG, "Conversion % - " + percentComplete);
                } while (outBuffInfo.flags != MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                fis.close();
                mux.stop();
                mux.release();
                Logger.LOGV(LOGTAG, "Compression done ...");
            } catch (FileNotFoundException e) {
                Logger.LOGE(LOGTAG, "File not found!", e);
            } catch (Throwable e) {
                Logger.LOGE(LOGTAG, "IO exception!", e);
            } finally {
                processDone = true;
            }
        }
    };
    
    public static void convertAudio(String srcFile, String outputFile){
        try {
            outpuPath = outputFile;
            processDone = false;
            if (!srcFile.endsWith(".wav")) {
                Converter c = new Converter();
                String wavFile = srcFile.replace(".","-") + ".wav";
                c.convert(srcFile, wavFile);
                filePath = wavFile;
            } else {
                filePath = srcFile;         
            }
            
            Thread t = new Thread(convert);
            t.start();
            while (!processDone) {
                Thread.yield();
            }
        } catch (Throwable t){
           t.printStackTrace(); 
        }
    }
}