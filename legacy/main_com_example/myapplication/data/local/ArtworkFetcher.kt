package com.example.myapplication.data.local

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import okio.FileSystem

/**
 * Coil model for embedded artwork. The `toString` of this data class is stable, so Coil uses
 * it as memory/disk cache key.
 */
data class ArtworkModel(val mediaId: Long, val albumId: Long)

/** Lets Coil decode artwork straight from the audio file (see [ArtworkExtractor]). */
class ArtworkFetcherFactory(
    private val extractor: ArtworkExtractor,
) : Fetcher.Factory<ArtworkModel> {

    override fun create(data: ArtworkModel, options: Options, imageLoader: ImageLoader): Fetcher =
        ArtworkFetcher(extractor, data)
}

private class ArtworkFetcher(
    private val extractor: ArtworkExtractor,
    private val data: ArtworkModel,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val bytes = extractor.bytes(data.mediaId, data.albumId) ?: return null
        return SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }
}
