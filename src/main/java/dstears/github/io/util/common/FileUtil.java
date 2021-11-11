package dstears.github.io.util.common;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileUtil {

    private FileUtil() {
    }

    public static List<String> listFile(String directPath) {
        File file = new File(directPath);
        List<String> filePaths = new ArrayList<>();
        listFile(file, filePaths);
        return filePaths;
    }


    private static void listFile(File file, List<String> filePaths) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isFile()) {
            filePaths.add(file.getAbsolutePath());
        } else {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    listFile(f, filePaths);
                }
            }
        }

    }
}
