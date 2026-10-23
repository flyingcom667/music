package com.tidalmusic.player.ui.viewmodel

import com.tidalmusic.player.data.preferences.AppPreferences
import com.tidalmusic.player.domain.model.RepeatMode
import com.tidalmusic.player.playback.PlaybackEvent
import com.tidalmusic.player.testing.FakePlaybackController
import com.tidalmusic.player.testing.FakePlaybackSessionStore
import com.tidalmusic.player.testing.FakeSettingsStore
import com.tidalmusic.player.testing.FakeUserLibraryRepository
import com.tidalmusic.player.testing.TestData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The player screen model: transport, the queue, favourites and the sleep timer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var controller: FakePlaybackController
    private lateinit var userLibrary: FakeUserLibraryRepository
    private lateinit var settings: FakeSettingsStore
    private lateinit var sessionStore: FakePlaybackSessionStore

    private val songs = listOf(
        TestData.song(id = 1, title = "One"),
        TestData.song(id = 2, title = "Two"),
        TestData.song(id = 3, title = "Three"),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        controller = FakePlaybackController()
        userLibrary = FakeUserLibraryRepository()
        settings = FakeSettingsStore()
        sessionStore = FakePlaybackSessionStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PlayerViewModel(
        controller = controller,
        userLibraryRepository = userLibrary,
        preferences = settings,
        stateStore = sessionStore,
    )

    @Test
    fun `playing a list starts at the chosen track`() = runTest {
        val vm = viewModel()
        vm.playSongs(songs, startIndex = 1)

        assertEquals(1L, controller.state.value.currentSong?.id)
        assertEquals(1, controller.lastStartIndex)
        assertTrue(controller.state.value.isPlaying)
    }

    @Test
    fun `playing an empty list does nothing`() = runTest {
        val vm = viewModel()
        vm.playSongs(emptyList(), startIndex = 0)

        assertEquals(null, controller.state.value.currentSong)
    }

    @Test
    fun `an out of range index is clamped`() = runTest {
        val vm = viewModel()
        vm.playSongs(songs, startIndex = 99)

        assertEquals(2, controller.lastStartIndex)
    }

    @Test
    fun `play and pause toggle the transport`() = runTest {
        val vm = viewModel()
        vm.playSongs(songs, startIndex = 0)
        vm.togglePlayPause()

        assertFalse(controller.state.value.isPlaying)
    }

    @Test
    fun `duplicate track start events are counted once per playback session`() = runTest {
        val vm = viewModel()
        vm.playSongs(songs, startIndex = 0)
        val start = PlaybackEvent.TrackStarted(songs.first())

        controller.emit(start)
        controller.emit(start)

        assertEquals(1, userLibrary.playCounts[1L])

        controller.pause()
        controller.play()
        controller.emit(start)

        assertEquals(1, userLibrary.playCounts[1L])

        vm.playSongs(songs, startIndex = 0)
        controller.emit(start)

        assertEquals(2, userLibrary.playCounts[1L])
    }

    @Test
    fun `a new session is shuffled when the setting says so`() = runTest {
        settings = FakeSettingsStore(
            AppPreferences.Settings(shuffleNewSessions = true),
        )
        val vm = viewModel()
        vm.playSongs(songs, startIndex = 0)

        assertTrue(controller.lastShuffle)
    }

    @Test
    fun `the default repeat mode and speed are applied on start`() = runTest {
        settings = FakeSettingsStore(
            AppPreferences.Settings(defaultRepeatMode = "all", defaultPlaybackSpeed = 1.5f),
        )
        viewModel()

        assertEquals(RepeatMode.ALL, controller.state.value.repeatMode)
        assertEquals(1.5f, controller.state.value.playbackSpeed)
    }

    @Test
    fun `the previous session is restored when the setting says so`() = runTest {
        sessionStore.save(songs[1], 42_000L)
        val vm = viewModel()

        assertEquals(2L, controller.state.value.currentSong?.id)
        assertEquals(42_000L, controller.progress.value.positionMs)
        // Restoring must not start the audio on its own.
        assertFalse(controller.state.value.isPlaying)
    }

    @Test
    fun `nothing is restored when the setting is off`() = runTest {
        settings = FakeSettingsStore(AppPreferences.Settings(resumePlayback = false))
        sessionStore.save(songs[1], 42_000L)
        viewModel()

        assertEquals(null, controller.state.value.currentSong)
    }

    @Test
    fun `favouriting the current track is remembered`() = runTest {
        val vm = viewModel()
        vm.playSongs(songs, startIndex = 0)
        vm.toggleFavorite()

        assertTrue(vm.isFavorite.value)
        assertTrue(1L in userLibrary.favoriteIds())
    }

    @Test
    fun `the sleep timer becomes active and can be cancelled`() = runTest {
        val vm = viewModel()
        vm.startSleepTimer(5)

        assertTrue(vm.sleepTimer.value.isActive)

        vm.cancelSleepTimer()
        assertFalse(vm.sleepTimer.value.isActive)
    }

    @Test
    fun `a sleep timer of zero minutes is off`() = runTest {
        val vm = viewModel()
        vm.startSleepTimer(0)

        assertFalse(vm.sleepTimer.value.isActive)
    }

    @Test
    fun `the volume is clamped to the valid range`() = runTest {
        val vm = viewModel()
        vm.setVolume(2f)
        assertEquals(1f, vm.volume.value)

        vm.setVolume(-1f)
        assertEquals(0f, vm.volume.value)
    }

    @Test
    fun `queue entries can be moved and removed`() = runTest {
        val vm = viewModel()
        vm.playSongs(songs, startIndex = 0)

        vm.moveQueueItem(0, 2)
        assertEquals(listOf(2L, 3L, 1L), vm.queue.value.map { it.song.id })

        vm.removeFromQueue(0)
        assertEquals(listOf(3L, 1L), vm.queue.value.map { it.song.id })
    }
}
