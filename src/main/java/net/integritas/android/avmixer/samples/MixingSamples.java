package net.integritas.android.avmixer.samples;

import net.integritas.android.avmixer.AVAudioMixerComponent;
import net.integritas.android.avmixer.AVCloneTracks;
import net.integritas.android.avmixer.AVImageMixerComponent;
import net.integritas.android.avmixer.AVMixer;
import net.integritas.android.avmixer.AVMixerObject;
import net.integritas.android.avmixer.AVVideoMixerComponent;
import net.integritas.android.avmixer.IAVMixerListener;
import net.integritas.android.avmixer.audio.M4aAudioConverter;
import net.integritas.android.avmixer.util.Logger;

import java.io.File;
import java.io.IOException;

/**
 * <p>
 * Class to implement a couple of examples of the library usage.
 * </p>
 *
 * @author AlexGP (alexandre.gomes@integritas.net)
 * @since 1.0
 */
public class MixingSamples {

    private MixingSamples() {

    }

    public static void mixVideoAndImages(String videoSrcFile, String image1SrcFile,
                                         String image2SrcFile, String outputDir,
                                         String outputFileName) {

        File image1 = new File(image1SrcFile);
        File image2 = new File(image2SrcFile);
        File video = new File(videoSrcFile);

        AVMixerObject mixerObject =
                new AVMixerObject(outputFileName, outputDir);


        mixerObject.addImage(new AVImageMixerComponent(image1));
        mixerObject.addImage(new AVImageMixerComponent(image2));
        mixerObject.addVideo(new AVVideoMixerComponent(video));

        AVMixer mixer = new AVMixer(mixerObject, new IAVMixerListener() {

            @Override
            public void onFinishedMixing(AVMixerObject obj) {
                Logger.LOGV("MixingSamples", "onFinishedMixing!!");
            }

            @Override
            public void onMixerError(Exception e) {
                Logger.LOGE("Test", e.getMessage(), e);
            }
        });

        mixer.mix(false);
    }

    public static void mixVideoAndAudio(String videoSrcFile, String audioFile,
                                        String outputDir, String outputFileName) {


        File video = new File(videoSrcFile);
        File audio = new File(audioFile);

        AVMixerObject mixerObject =
                new AVMixerObject(outputFileName, outputDir);

        mixerObject.addVideo(new AVVideoMixerComponent(video));
        mixerObject.addSoundComponent(new AVAudioMixerComponent(audio));

        AVMixer mixer = new AVMixer(mixerObject, new IAVMixerListener() {

            @Override
            public void onFinishedMixing(AVMixerObject obj) {
                Logger.LOGV("MixingSamples", "onFinishedMixing!!");
            }

            @Override
            public void onMixerError(Exception e) {
                Logger.LOGE("MixingSamples", e.getMessage(), e);
            }
        });

        mixer.mix(false);
    }

    public static void mixVideoAndAudioWithAudioDelay(String videoSrcFile, String audioFile,
                                                      String outputDir, String outputFileName,
                                                      long delayInMilliseconds) {

        File video = new File(videoSrcFile);
        File audio = new File(audioFile);

        AVMixerObject mixerObject =
                new AVMixerObject(outputFileName, outputDir, delayInMilliseconds);

        mixerObject.addVideo(new AVVideoMixerComponent(video));
        mixerObject.addSoundComponent(new AVAudioMixerComponent(audio));

        AVMixer mixer = new AVMixer(mixerObject, new IAVMixerListener() {

            @Override
            public void onFinishedMixing(AVMixerObject obj) {
                Logger.LOGV("MixingSamples", "onFinishedMixing!!");
            }

            @Override
            public void onMixerError(Exception e) {
                Logger.LOGE("MixingSamples", e.getMessage(), e);
            }
        });

        mixer.mix(false);
    }

    public static void mixVideoAndAudioWithAudioCroppingAndVolumeSetting(String videoSrcFile,
                                                                         String audioFile, String outputDir, String outputFileName,
                                                                         long audioCroppingInMilliseconds, float volume) {

        File video = new File(videoSrcFile);
        File audio = new File(audioFile);

        AVMixerObject mixerObject =
                new AVMixerObject(outputFileName, outputDir);

        mixerObject.addVideo(new AVVideoMixerComponent(video));
        mixerObject.addSoundComponent(
                new AVAudioMixerComponent(audio, volume, audioCroppingInMilliseconds));

        AVMixer mixer = new AVMixer(mixerObject, new IAVMixerListener() {

            @Override
            public void onFinishedMixing(AVMixerObject obj) {
                Logger.LOGV("MixingSamples", "onFinishedMixing!!");
            }

            @Override
            public void onMixerError(Exception e) {
                Logger.LOGE("MixingSamples", e.getMessage(), e);
            }
        });

        mixer.mix(false);
    }

    public static void mixVideoAndAudioWithAudioConversion(String videoSrcFile, String mp3AudioFile,
                                                           String outputDir, String outputFileName) {


        String m4aAudioFilepath = outputDir + "test.m4a";
        M4aAudioConverter.convertAudio(mp3AudioFile, m4aAudioFilepath);

        File video = new File(videoSrcFile);
        File audio = new File(m4aAudioFilepath);

        AVMixerObject mixerObject =
                new AVMixerObject(outputFileName, outputDir);

        mixerObject.addVideo(new AVVideoMixerComponent(video));
        mixerObject.addSoundComponent(new AVAudioMixerComponent(audio));

        AVMixer mixer = new AVMixer(mixerObject, new IAVMixerListener() {

            @Override
            public void onFinishedMixing(AVMixerObject obj) {
                Logger.LOGV("MixingSamples", "onFinishedMixing!!");
            }

            @Override
            public void onMixerError(Exception e) {
                Logger.LOGE("MixingSamples", e.getMessage(), e);
            }
        });

        mixer.mix(false);
    }

    public static void cloneAudioTrackFromVideoFile(String videoFilepath,
                                                    String outputDir, String outputFileName) {
        File video = new File(videoFilepath);

        AVMixerObject mixerObject =
                new AVMixerObject(outputFileName, outputDir);
        mixerObject.addVideo(new AVVideoMixerComponent(video));

        try {
            AVCloneTracks.cloneTrack(mixerObject, null, null, 0,
                    AVCloneTracks.EXTRACTION_TYPE.AudioOnly,
                    AVCloneTracks.VIDEO_RES_TYPE.Res_Native, -1, -1);
        } catch (IOException e) {
            Logger.LOGE("MixingSamples", e.getMessage(), e);
        }
    }

    public static void cropVideoFromVideoFile(String videoFilepath,
                                              String outputDir, String outputFileName) {
        File video = new File(videoFilepath);

        AVMixerObject mixerObject =
                new AVMixerObject(outputFileName, outputDir);
        mixerObject.addVideo(new AVVideoMixerComponent(video));

        try {
            AVCloneTracks.cloneTrack(mixerObject, null, null, 0,
                    AVCloneTracks.EXTRACTION_TYPE.AudioOnly,
                    AVCloneTracks.VIDEO_RES_TYPE.Res_Native, 30, 150);
        } catch (IOException e) {
            Logger.LOGE("MixingSamples", e.getMessage(), e);
        }
    }
}