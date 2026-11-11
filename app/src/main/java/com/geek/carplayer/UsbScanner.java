package com.geek.carplayer;

import android.os.Environment;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UsbScanner {

    // 比亚迪车机常见的 U 盘挂载点与内部存储路径
    private static final String[] SEARCH_PATHS = {
            "/storage",
            "/mnt/media_rw",
            Environment.getExternalStorageDirectory().getAbsolutePath()
    };

    private static final Set<String> SUPPORTED_FORMATS = new HashSet<>(
            Arrays.asList("wav", "flac", "mp3", "m4a")
    );

    public static List<MusicTrack> scanTracks() {
        List<MusicTrack> tracks = new ArrayList<>();
        for (String basePath : SEARCH_PATHS) {
            File root = new File(basePath);
            if (root.exists() && root.canRead()) {
                scanDirectoryRecursively(root, tracks, 0, 5);
            }
        }
        return tracks;
    }

    private static void scanDirectoryRecursively(File dir, List<MusicTrack> outList, int currentDepth, int maxDepth) {
        if (currentDepth > maxDepth || dir == null) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory() && !f.getName().startsWith(".")) {
                scanDirectoryRecursively(f, outList, currentDepth + 1, maxDepth);
            } else if (f.isFile() && !f.getName().startsWith(".")) {
                String name = f.getName();
                int dotIndex = name.lastIndexOf('.');
                if (dotIndex > 0) {
                    String ext = name.substring(dotIndex + 1).toLowerCase();
                    if (SUPPORTED_FORMATS.contains(ext)) {
                        String title = name.substring(0, dotIndex);
                        outList.add(new MusicTrack(title, f.getAbsolutePath(), ext.toUpperCase()));
                    }
                }
            }
        }
    }
}
