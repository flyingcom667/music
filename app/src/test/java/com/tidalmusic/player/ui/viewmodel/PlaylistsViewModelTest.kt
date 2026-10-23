package com.tidalmusic.player.ui.viewmodel

import com.tidalmusic.player.R
import com.tidalmusic.player.testing.FakeUserLibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The playlists screen model: creating, renaming and deleting playlists.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeUserLibraryRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeUserLibraryRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PlaylistsViewModel(repository = repository)

    @Test
    fun `a new playlist shows up in the list`() = runTest {
        val vm = viewModel()
        vm.create("Road trip", "Summer")

        assertEquals(1, vm.playlists.value.size)
        assertEquals("Road trip", vm.playlists.value.first().name)
        assertEquals("Summer", vm.playlists.value.first().description)
    }

    @Test
    fun `a blank name is refused`() = runTest {
        val vm = viewModel()
        vm.create("   ", null)

        assertTrue(vm.playlists.value.isEmpty())
    }

    @Test
    fun `a duplicate name is refused`() = runTest {
        val vm = viewModel()
        vm.create("Road trip", null)
        vm.create("road trip", null)

        assertEquals(1, vm.playlists.value.size)
    }

    @Test
    fun `a playlist can be renamed`() = runTest {
        val vm = viewModel()
        vm.create("Road trip", null)
        val id = vm.playlists.value.first().id

        vm.rename(id, "Summer", "Hot days")

        assertEquals("Summer", vm.playlists.value.first().name)
        assertEquals("Hot days", vm.playlists.value.first().description)
    }

    @Test
    fun `a playlist can be deleted`() = runTest {
        val vm = viewModel()
        vm.create("Road trip", null)
        val id = vm.playlists.value.first().id

        vm.delete(id)

        assertTrue(vm.playlists.value.isEmpty())
    }

    @Test
    fun `a created playlist id is published and can be consumed`() = runTest {
        val vm = viewModel()
        vm.create("Road trip", null)

        assertNotNull(vm.createdPlaylistId.value)
        vm.consumeCreatedPlaylist()
        assertNull(vm.createdPlaylistId.value)
    }

    @Test
    fun `the save flag is cleared when the write finishes`() = runTest {
        val vm = viewModel()
        vm.create("Road trip", null)

        assertEquals(false, vm.isSaving.value)
    }

    @Test
    fun `the message stream reports a successful creation`() = runTest {
        val vm = viewModel()
        val messages = collectMessages(vm)

        vm.create("Road trip", null)
        runCurrent()

        assertTrue(messages.any { it.messageRes == R.string.playlist_created })
        assertTrue(messages.none { it.isError })
    }

    @Test
    fun `the message stream reports a refused name`() = runTest {
        val vm = viewModel()
        vm.create("Road trip", null)
        val messages = collectMessages(vm)

        vm.create("Road trip", null)
        runCurrent()

        assertTrue(messages.any { it.isError })
    }

    /** Starts collecting the message stream so that nothing is missed. */
    private fun TestScope.collectMessages(vm: PlaylistsViewModel): MutableList<UiMessage> {
        val messages = mutableListOf<UiMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.messages.collect { messages += it }
        }
        return messages
    }
}
