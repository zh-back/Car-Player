package com.geek.carplayer

import android.os.Environment
import java.io.File

data class MusicTrack(
    val title: String,
    val path: String,
    val format: String
)

object UsbScanner {

    // 比亚迪车机常见的外部 U 盘和本地音乐挂载目录
    private val SEARCH_PATHS = listOf(
        "/storage",
        "/mnt/media_rw",
        Environment.getExternalStorageDirectory().absolutePath
    )

    private val SUPPORTED_FORMATS = setOf("wav", "flac", "mp3", "m4a")

    fun scanTracks(): List<MusicTrack> {
        val tracks = mutableListOf<MusicTrack>()
        val visitedDirs = mutableSetOf<String>()

        for (basePath in SEARCH_PATHS) {
            val root = File(basePath)
            if (!root.exists() || !root.canRead()) continue

            try {
                root.walkTopDown()
                    .maxDepth(5)
                    .filter { it.isFile }
                    .forEach { file ->
                        val ext = file.extension.lowercase()
                        if (ext in SUPPORTED_FORMATS && !file.name.startsWith(".")) {
                            tracks.add(
                                MusicTrack(
                                    title = file.nameWithoutExtension,
                                    path = file.absolutePath,
                                    format = ext.uppercase()
                                )
                            )
                        }
                    }
            } catch (e: Exception) {
                // 忽略没有权限的部分系统文件夹
            }
        }
        return tracks
    }
}
