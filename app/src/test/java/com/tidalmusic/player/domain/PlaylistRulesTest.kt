package com.tidalmusic.player.domain

import com.tidalmusic.player.domain.PlaylistRules.NameRejection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The playlist naming and ordering rules.
 */
class PlaylistRulesTest {

    @Test
    fun `a name is trimmed and its inner whitespace collapsed`() {
        assertEquals("Road trip", PlaylistRules.normalizeName("  Road   trip  "))
    }

    @Test
    fun `a blank name is rejected`() {
        assertEquals(
            NameRejection.BLANK,
            PlaylistRules.rejectionReason("   ", null),
        )
    }

    @Test
    fun `a name that is already taken is rejected regardless of case`() {
        assertEquals(
            NameRejection.DUPLICATE,
            PlaylistRules.rejectionReason("Road trip", "Road Trip"),
        )
    }

    @Test
    fun `a free name is accepted`() {
        assertNull(PlaylistRules.rejectionReason("Road trip", null))
        assertNull(PlaylistRules.rejectionReason("Road trip", "Something else"))
    }

    @Test
    fun `reordering keeps the requested order`() {
        val merged = PlaylistRules.mergeTrackOrder(listOf(1L, 2L, 3L), listOf(3L, 1L, 2L))
        assertEquals(listOf(3L, 1L, 2L), merged)
    }

    @Test
    fun `reordering appends tracks the caller forgot so nothing is lost`() {
        val merged = PlaylistRules.mergeTrackOrder(listOf(1L, 2L, 3L), listOf(2L))
        assertEquals(listOf(2L, 1L, 3L), merged)
    }

    @Test
    fun `reordering ignores ids the playlist does not hold`() {
        val merged = PlaylistRules.mergeTrackOrder(listOf(1L, 2L), listOf(99L, 1L))
        assertEquals(listOf(1L, 2L), merged)
    }

    @Test
    fun `reordering removes duplicates`() {
        val merged = PlaylistRules.mergeTrackOrder(listOf(1L, 2L), listOf(1L, 1L, 2L))
        assertEquals(listOf(1L, 2L), merged)
    }

    @Test
    fun `positions are contiguous and start at zero`() {
        val positions = PlaylistRules.positionsFor(listOf(5L, 2L, 9L))
        assertEquals(mapOf(5L to 0, 2L to 1, 9L to 2), positions)
    }
}
