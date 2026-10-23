package com.tidalmusic.player.ui.viewmodel

import com.tidalmusic.player.domain.model.SortDirection
import com.tidalmusic.player.domain.model.SortOrder
import com.tidalmusic.player.domain.model.SortSpec
import com.tidalmusic.player.testing.FakeLibraryRepository
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
 * The library screen model: scanning, searching, sorting and hiding tracks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var library: FakeLibraryRepository
    private lateinit var userLibrary: FakeUserLibraryRepository
    private lateinit var settings: FakeSettingsStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        library = FakeLibraryRepository(
            listOf(
                TestData.song(id = 1, title = "Banana", artist = "Yellow", dateAdded = 30),
                TestData.song(id = 2, title = "apple", artist = "Red", dateAdded = 10),
                TestData.song(id = 3, title = "Cherry", artist = "Red", dateAdded = 20),
            ),
        )
        userLibrary = FakeUserLibraryRepository()
        settings = FakeSettingsStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = LibraryViewModel(
        libraryRepository = library,
        userLibraryRepository = userLibrary,
        preferences = settings,
    )

    @Test
    fun `a scan fills the library and sorts by title`() = runTest {
        val vm = viewModel()
        vm.refresh(permissionGranted = true)

        assertEquals(LibraryStatus.CONTENT, vm.state.value.status)
        assertEquals(listOf("apple", "Banana", "Cherry"), vm.state.value.songs.map { it.title })
        assertEquals(3, vm.state.value.songCount)
    }

    @Test
    fun `without the permission the library stays empty and asks for it`() = runTest {
        val vm = viewModel()
        vm.refresh(permissionGranted = false)

        assertEquals(LibraryStatus.EMPTY, vm.state.value.status)
        assertFalse(vm.state.value.permissionGranted)
        assertEquals(0, library.loadCount)
    }

    @Test
    fun `revoking permission clears cached songs and reloads after access returns`() = runTest {
        userLibrary.recordPlay(1L)
        val vm = viewModel()
        vm.refresh(permissionGranted = true)
        assertEquals(3, vm.state.value.songs.size)
        assertEquals(1, vm.state.value.recentlyPlayed.size)

        vm.refresh(permissionGranted = false)

        assertEquals(LibraryStatus.EMPTY, vm.state.value.status)
        assertTrue(vm.state.value.songs.isEmpty())
        assertTrue(vm.state.value.recentlyAdded.isEmpty())
        assertTrue(vm.state.value.allSongs.isEmpty())
        assertTrue(vm.state.value.recentlyPlayed.isEmpty())

        vm.refresh(permissionGranted = true)

        assertEquals(LibraryStatus.CONTENT, vm.state.value.status)
        assertEquals(3, vm.state.value.songs.size)
        assertEquals(1, vm.state.value.recentlyPlayed.size)
    }

    @Test
    fun `permission revoked during scan clears the cached library`() = runTest {
        val vm = viewModel()
        vm.refresh(permissionGranted = true)
        assertEquals(3, vm.state.value.songs.size)

        library.failure = SecurityException("permission revoked")
        vm.refresh(permissionGranted = true, force = true)

        assertEquals(LibraryStatus.EMPTY, vm.state.value.status)
        assertFalse(vm.state.value.permissionGranted)
        assertTrue(vm.state.value.songs.isEmpty())
        assertTrue(vm.state.value.allSongs.isEmpty())
    }

    @Test
    fun `a failing scan reports the error`() = runTest {
        library.failure = IllegalStateException("media store is broken")
        val vm = viewModel()
        vm.refresh(permissionGranted = true)

        assertEquals(LibraryStatus.ERROR, vm.state.value.status)
        assertEquals(null, vm.state.value.errorMessage)
    }

    @Test
    fun `the query filters the visible songs`() = runTest {
        val vm = viewModel()
        vm.refresh(permissionGranted = true)
        vm.onQueryChange("cherry")

        assertEquals(listOf("Cherry"), vm.state.value.songs.map { it.title })
        assertTrue(vm.state.value.isSearching)
    }

    @Test
    fun `clearing the query restores the full list`() = runTest {
        val vm = viewModel()
        vm.refresh(permissionGranted = true)
        vm.onQueryChange("cherry")
        vm.clearQuery()

        assertEquals(3, vm.state.value.songs.size)
        assertFalse(vm.state.value.isSearching)
    }

    @Test
    fun `choosing a sort order re-sorts the list`() = runTest {
        val vm = viewModel()
        vm.refresh(permissionGranted = true)
        vm.setSort(SortSpec(SortOrder.ARTIST, SortDirection.ASCENDING))

        assertEquals(listOf("apple", "Cherry", "Banana"), vm.state.value.songs.map { it.title })
    }

    @Test
    fun `the sort order is remembered in the settings`() = runTest {
        val vm = viewModel()
        vm.refresh(permissionGranted = true)
        vm.setSort(SortSpec(SortOrder.DURATION, SortDirection.DESCENDING))

        assertEquals(SortOrder.DURATION, settings.current.defaultSortOrder)
        assertEquals(SortDirection.DESCENDING, settings.current.defaultSortDirection)
    }

    @Test
    fun `hidden tracks are not part of the library`() = runTest {
        userLibrary.setHidden(songId = 2L, hidden = true)
        val vm = viewModel()
        vm.refresh(permissionGranted = true)

        assertEquals(listOf("apple", "Cherry"), vm.state.value.songs.map { it.title }.sorted())
    }

    @Test
    fun `the recently added tab is newest first`() = runTest {
        val vm = viewModel()
        vm.refresh(permissionGranted = true)

        assertEquals(
            listOf("Banana", "Cherry", "apple"),
            vm.state.value.recentlyAdded.map { it.title },
        )
    }

    @Test
    fun `the recently played list follows the history`() = runTest {
        userLibrary.recordPlay(3L)
        userLibrary.recordPlay(1L)
        val vm = viewModel()
        vm.refresh(permissionGranted = true)

        // The most recently played track comes first.
        assertEquals(listOf("Banana", "Cherry"), vm.state.value.recentlyPlayed.map { it.title })
    }
}
