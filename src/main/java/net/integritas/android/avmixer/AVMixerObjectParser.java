package net.integritas.android.avmixer;

import android.os.Environment;
import android.util.Log;

import net.integritas.android.avmixer.util.Logger;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * <p>
 *  Class that parsers an AVMixerObject into a binary zip file, including all the media files
 *  specified in it. Also, reads a zip file and parses it into an AVMixerObject.
 * </p>
 *
 * @author AlexGP (alexandre.gomes@integritas.net)
 *
 * @since 1.0
 */
public class AVMixerObjectParser {

    private final static String TMP_OUTPUT_PATH =
            Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DCIM).getAbsolutePath() + File.separator + "TMP/";
    private final static String SOUNDBITZ_OUTPUT_PATH =
            Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DCIM).getAbsolutePath() + File.separator + "Soundbitz/";
    private final static File TMP_DIR = new File(TMP_OUTPUT_PATH);
    private final static File SOUNDBITZ_DIR = new File(SOUNDBITZ_OUTPUT_PATH);

    public static File createBinaryFile(AVMixerObject sb) {
        String fName = sb.getFileName().replace(".mp4", "");
        File outputFile = new File(TMP_DIR, fName + ".zip");

        try {
            if (TMP_DIR.exists()) {
                cleanTmpDir(TMP_DIR);
            }

            TMP_DIR.mkdirs();

            for (AVMixerComponent component : sb.getVisualComponents()) {
                File f = component.getComponentFile();
                InputStream in = new FileInputStream(f);
                OutputStream out = new FileOutputStream(new File(TMP_DIR, f.getName()));

                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                in.close();
                out.close();
            }

            zipFile(outputFile);

        } catch (Throwable t) {
            Logger.LOGE("AVMixerObjectParser", t.getMessage());
        }

        return outputFile;
    }

    private static void cleanTmpDir(File f) {
        if (f.isDirectory()) {
            String[] children = f.list();
            for (int i = 0; i < children.length; i++) {
                cleanTmpDir(new File(f, children[i]));
            }
        }
        f.delete();
    }

    private static void zipFile(File destZipFile)
            throws Exception {
        ZipOutputStream
                zip = null;
        FileOutputStream fileWriter = null;
        fileWriter = new FileOutputStream(destZipFile);
        zip = new ZipOutputStream(fileWriter);

        for (File f : TMP_DIR.listFiles()) {
            if (!f.getName().contains(".zip") && !f.isDirectory()) {
                byte[] buf = new byte[1024];
                int len;
                FileInputStream in = new FileInputStream(f);
                zip.putNextEntry(new ZipEntry(f.getName()));
                while ((len = in.read(buf)) > 0) {
                    zip.write(buf, 0, len);
                }
            }
        }

        zip.flush();
        zip.close();
    }

    public static AVMixerObject readBinaryFile(File f) {
        String fName = f.getName().replace(".zip", ".mp4");
        AVMixerObject sb = new AVMixerObject(fName, "");
        try {

            if (SOUNDBITZ_DIR.exists()) {
                cleanTmpDir(SOUNDBITZ_DIR);
            }

            SOUNDBITZ_DIR.mkdirs();

            InputStream is;
            ZipInputStream zis;

            String filename;
            is = new FileInputStream(f);
            zis = new ZipInputStream(new BufferedInputStream(is));
            ZipEntry ze;
            byte[] buffer = new byte[1024];
            int count;

            while ((ze = zis.getNextEntry()) != null) {

                filename = ze.getName();

                if (filename.toLowerCase().contains(".mp4")) {
                    AVVideoMixerComponent v = new AVVideoMixerComponent(new File(SOUNDBITZ_DIR, filename));
                    sb.addVideo(v);
                } else if (filename.toLowerCase().contains(".jpg")
                        || filename.toLowerCase().contains(".png")
                        || filename.toLowerCase().contains(".bmp")) {
                    AVImageMixerComponent i = new AVImageMixerComponent(new File(SOUNDBITZ_DIR, filename));
                    sb.addImage(i);
                } else {
                    AVAudioMixerComponent a = new AVAudioMixerComponent(new File(SOUNDBITZ_DIR, filename));
                    sb.addSoundComponent(a);
                }

                // create dirs
                if (ze.isDirectory()) {
                    File fmd = new File(SOUNDBITZ_DIR, filename);
                    fmd.mkdirs();
                    continue;
                }

                FileOutputStream fout =
                        new FileOutputStream(SOUNDBITZ_DIR + File.separator + filename);

                while ((count = zis.read(buffer)) != -1) {
                    fout.write(buffer, 0, count);
                }

                fout.close();
                zis.closeEntry();
            }

            zis.close();
        } catch (Throwable t) {
            Logger.LOGE("AVMixerObjectParser", t.getMessage());
        }

        return sb;
    }
}