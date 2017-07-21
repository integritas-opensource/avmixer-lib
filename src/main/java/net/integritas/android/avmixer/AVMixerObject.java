package net.integritas.android.avmixer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * <p>
 * Class that implements the AV mixer object which is used to store all the media which
 * composes the final mixed video.
 * </p>
 *
 * @author AlexGP (alexandre.gomes@integritas.net)
 * @author Raphael Kohn (raphael.kohn@integritas.net)
 * @version 1.2
 * @since 1.0
 */
public class AVMixerObject {

    private final long PRES_TIME_MULTIPLIER = 1000l;

    private File outputFile;
    private File tempVideoFile;
    private File tempAudioFile;
    private File audioFromVideoFile;

    private ArrayList<AVVideoMixerComponent> videoFiles;
    private ArrayList<AVImageMixerComponent> imageFiles;
    private ArrayList<AVAudioMixerComponent> audioFiles;

    private ArrayList<AVMixerComponent> visualComponents;

    private long audioDelay;


    /**
     * Constructor receiving the soundbitz file name, generated output directory.
     *
     * @param fileName
     * @param outputDir
     */
    public AVMixerObject(String fileName, String outputDir) {
        visualComponents = new ArrayList<>();
        imageFiles = new ArrayList<>();
        audioFiles = new ArrayList<>();
        videoFiles = new ArrayList<>();

        outputFile = new File(outputDir + File.separator + fileName);
        tempVideoFile = new File(outputDir + File.separator + "tempVideoMix");
        tempAudioFile = new File(outputDir + File.separator + "tempAudioMix");
        audioFromVideoFile = new File(outputDir + File.separator + "audioFromVideoFile");
    }

    /**
     * Constructor receiving the soundbitz file name, generated output directory and the
     * general audio delay on which the sound should start playing. The audio delay value
     * should be specified on millisecods (e.g. 3000 for a 3 secs delay)
     *
     * @param fileName
     * @param outputDir
     * @param audioDelay
     */
    public AVMixerObject(String fileName, String outputDir, long audioDelay) {
        this(fileName, outputDir);
        this.audioDelay = audioDelay * PRES_TIME_MULTIPLIER;
    }

    public String getFilePathName() {
        return outputFile.getAbsolutePath();
    }

    public String getFileName() {
        return outputFile.getName();
    }

    public String getTempVideoFilePath() {
        return tempVideoFile.getAbsolutePath();
    }

    public String getTempAudioFilePath() {
        return tempAudioFile.getAbsolutePath();
    }

    public ArrayList<AVVideoMixerComponent> getVideos() {
        return videoFiles;
    }

    public ArrayList<AVAudioMixerComponent> getAudioComponents() {
        return audioFiles;
    }

    public ArrayList<AVImageMixerComponent> getImageComponents() {
        return imageFiles;
    }

    public void addSoundComponent(AVAudioMixerComponent newComponent) {
        audioFiles.add(newComponent);
    }

    public void addImage(AVImageMixerComponent newComponent) {
        imageFiles.add(newComponent);
        visualComponents.add(newComponent);
    }

    public void addVideo(AVVideoMixerComponent newComponent) {
        videoFiles.add(newComponent);
        visualComponents.add(newComponent);
    }

    public void addVideo(AVVideoMixerComponent newComponent, int index) {
        videoFiles.add(index, newComponent);
        visualComponents.add(index, newComponent);
    }

    public ArrayList<AVMixerComponent> getVisualComponents() {
        return visualComponents;
    }

    /**
     * After mixing, clear the Temporary Files used in the process
     */
    public void clearTempData() {
        tempAudioFile.delete();
        tempVideoFile.delete();
        audioFromVideoFile.delete();
    }

    /**
     * This method Will make the output file as the Generated Mixed Video File.
     */
    public void noAudio() throws IOException {
        copy(tempVideoFile, outputFile);
    }

    /**
     * Makes the only Audio, the tempAudio for mixing.
     */
    public void oneAudio() throws IOException {
        copy(audioFiles.get(0).getComponentFile(), tempAudioFile);
    }

    /**
     * Copy file from SRC to DST
     *
     * @param src
     * @param dst
     * @throws IOException
     */
    private void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);

        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

    public long getAudioDelay() {
        return audioDelay;
    }

    public void setAudioDelay(long audioDelay) {
        this.audioDelay = audioDelay * PRES_TIME_MULTIPLIER;
    }
}