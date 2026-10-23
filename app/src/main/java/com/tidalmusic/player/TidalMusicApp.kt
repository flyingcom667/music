package com.tidalmusic.player

import android.app.Application
import com.tidalmusic.player.di.AppContainer

/**
 * Process-wide entry point.
 *
 * The container is created lazily and nothing is allocated in [onCreate]: cold start is spent
 * on the first frame, not on objects that the first screen may never use. Whichever component
 * needs the container first (the Activity or the playback service) creates it, and the lazy
 * initialiser guarantees both observe the same instances.
 */
class TidalMusicApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) }
}
