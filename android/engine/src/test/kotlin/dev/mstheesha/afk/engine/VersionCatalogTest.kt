package dev.mstheesha.afk.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VersionCatalogTest {
    @Test
    fun releasesAreNonEmptyOrderedAndDistinct() {
        val releases = VersionCatalog.releases
        assertTrue(releases.isNotEmpty())
        for (i in 1 until releases.size) {
            assertTrue(
                releases[i - 1].protocol < releases[i].protocol,
                "releases must ascend by protocol at index $i",
            )
        }
        assertEquals(releases.size, releases.map { it.protocol }.distinct().size)
    }

    @Test
    fun containsOldestAndNewestSupported() {
        assertTrue(VersionCatalog.releases.contains(VersionCatalog.Release("1.8", 47)))
        assertTrue(VersionCatalog.releases.contains(VersionCatalog.Release("26.1", 775)))
    }

    @Test
    fun nearestSupportedClampsNewerProtocol() {
        assertEquals(775, VersionCatalog.nearestSupported(777))
    }

    @Test
    fun nearestSupportedReturnsExactMatch() {
        assertEquals(47, VersionCatalog.nearestSupported(47))
    }

    @Test
    fun nearestSupportedFallsBackToSmallest() {
        assertEquals(47, VersionCatalog.nearestSupported(1))
    }

    @Test
    fun nameOfLatestIsNonEmpty() {
        val name = assertNotNull(VersionCatalog.nameOf(775))
        assertTrue(name.isNotEmpty())
    }
}
