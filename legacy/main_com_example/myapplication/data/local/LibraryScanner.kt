package com.example.myapplication.data.local

import com.example.myapplication.data.db.SongEntity

/**
 * The result of a library scan.
 */
data class LibraryScanResult(
    val songs: List<SongEntity>,
    val scannedFolders: List<String>,
)

/**
 * Reads the audio files of the device.
 *
 * The interface keeps the repository free of Android specific classes, so it can be tested
 * with a scanner that returns a fixed list of songs.
 */
interface LibraryScanner {

    /**
     * Scans the device for audio files.
     *
     * @param minDurationMs files that are shorter are ignored.
     * @param excludedFolders folders (in lower case) that are not part of the library.
     */
    suspend fun scan(
        minDurationMs: Long = 0L,
        excludedFolders: Set<String> = emptySet(),
    ): LibraryScanResult
}
