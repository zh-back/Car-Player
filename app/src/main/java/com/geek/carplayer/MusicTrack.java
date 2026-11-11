package com.geek.carplayer;

public class MusicTrack {
    private String title;
    private String path;
    private String format;

    public MusicTrack(String title, String path, String format) {
        this.title = title;
        this.path = path;
        this.format = format;
    }

    public String getTitle() {
        return title;
    }

    public String getPath() {
        return path;
    }

    public String getFormat() {
        return format;
    }

    @Override
    public String toString() {
        return title + " [" + format + "]";
    }
}
