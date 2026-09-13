package com.zerotoship.z2term.distro

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DistroOperationsTest {
    @Test fun refusesDeletionUntilEveryStartupOrInstallerLeaseIsClosed() {
        val first = DistroOperations.use("test-install")
        val second = DistroOperations.use("test-install")
        try {
            assertThrows(DistroOperations.Busy::class.java) { DistroOperations.delete("test-install") }
            first.close(); first.close()
            assertThrows(DistroOperations.Busy::class.java) { DistroOperations.delete("test-install") }
        } finally { first.close(); second.close() }
        DistroOperations.delete("test-install").close()
    }

    @Test fun blocksOnlyTargetDistroAndReleasesAfterFailure() {
        assertThrows(IllegalStateException::class.java) {
            DistroOperations.delete("test-delete").use {
                assertThrows(DistroOperations.Busy::class.java) { DistroOperations.use("test-delete") }
                assertThrows(DistroOperations.Busy::class.java) { DistroOperations.delete("test-delete") }
                DistroOperations.use("test-other").close()
                error("Simulate deletion failure")
            }
        }
        DistroOperations.use("test-delete").close()
    }

    @Test fun concurrentLaunchCannotEnterWhileDeleting() {
        val worker = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            val future = worker.submit {
                DistroOperations.delete("test-race").use {
                    started.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                }
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertThrows(DistroOperations.Busy::class.java) { DistroOperations.use("test-race") }
            release.countDown(); future.get(5, TimeUnit.SECONDS)
            DistroOperations.use("test-race").close()
        } finally { release.countDown(); worker.shutdownNow() }
    }
}
