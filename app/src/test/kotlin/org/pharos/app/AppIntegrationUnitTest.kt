package org.pharos.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.pharos.app.data.SurvivalCodexRepository
import org.pharos.core.protocol.TacticalCard
import org.pharos.core.storage.PharosEncryptedVault
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppIntegrationUnitTest {

    @Test
    fun `mainActivity launches on real Android context and SQLite database without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(context)

        // Launch actual MainActivity lifecycle (onCreate -> onStart -> onResume)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull("MainActivity must launch without crashing", activity)
        assertFalse(activity.isFinishing)

        // Also verify SurvivalCodexRepository has all 9 offline survival guides populated
        assertEquals(9, SurvivalCodexRepository.entries.size)
        assertTrue(SurvivalCodexRepository.entries.any { it.id == "grid_down_60m" })
        assertTrue(SurvivalCodexRepository.entries.any { it.id == "cbrn_nuclear_gas" })
        controller.pause().stop().destroy()
    }

    @Test
    fun `cockpit viewModel initializes with real Android SQLite context and handles walkthrough and duress wipe`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = PharosViewModel(appContext = context)
        val initial = vm.uiState.value

        assertTrue(initial.isRed660nmNightVision)
        assertTrue("Walkthrough must automatically launch on first install", initial.isWalkthroughActive)
        assertEquals(0, initial.walkthroughStepIndex)

        // Step through all 8 walkthrough steps and verify automatic tab switching
        vm.nextWalkthroughStep(8) // step 1 (BEAM)
        assertEquals(TacticalTab.BEAM, vm.uiState.value.selectedTab)
        vm.nextWalkthroughStep(8) // step 2 (BEAM)
        vm.nextWalkthroughStep(8) // step 3 (BEAM)
        vm.nextWalkthroughStep(8) // step 4 (PULSE)
        assertEquals(TacticalTab.PULSE, vm.uiState.value.selectedTab)
        vm.nextWalkthroughStep(8) // step 5 (PULSE)
        vm.nextWalkthroughStep(8) // step 6 (BRIDGE)
        assertEquals(TacticalTab.BRIDGE, vm.uiState.value.selectedTab)
        vm.nextWalkthroughStep(8) // step 7 (BRIDGE)
        vm.nextWalkthroughStep(8) // finishes walkthrough
        assertFalse("Walkthrough must close after completing step 8", vm.uiState.value.isWalkthroughActive)

        assertEquals(2, initial.verifiedCards.size)
        assertEquals(1, initial.quarantineCards.size)
        assertEquals(3, initial.ringBufferPackets)
        assertNotNull(initial.activeOtpCodebook)
        assertEquals(36, initial.activeOtpCodebook!!.cells.size)

        // Broadcast a new verified LIFE_SAFETY_SOS card
        vm.broadcastNewTacticalCard(
            tier = TacticalCard.StatusTier.LIFE_SAFETY_SOS,
            noteText = "NORTH SECTOR RIDGE EVAC NEEDED",
            attachCodec2Voice = true
        )
        val afterBroadcast = vm.uiState.value
        assertEquals(3, afterBroadcast.verifiedCards.size)
        assertEquals(4, afterBroadcast.ringBufferPackets)
        assertTrue(afterBroadcast.lastSms160Preview.startsWith("PH1:"))
        assertTrue(afterBroadcast.lastSms160Preview.length <= 160)

        // Toggle 660nm night vision & stealth blackout
        vm.toggleNightVision660nm()
        assertFalse(vm.uiState.value.isRed660nmNightVision)
        vm.setStealthBlackout(true)
        assertTrue(vm.uiState.value.isStealthBlackoutActive)
        vm.setStealthBlackout(false)

        // Trigger Duress PIN ("9911") -> <100ms cryptographic wipe + switches to Manual (SURVIVAL) tab
        val pinRes = vm.submitVaultPin("9911")
        assertEquals(PharosEncryptedVault.PinUnlockResult.TRIGGERED_DURESS_DECOY_MODE, pinRes)
        val afterDuress = vm.uiState.value
        assertTrue(afterDuress.isDecoyMode)
        assertEquals(TacticalTab.SURVIVAL, afterDuress.selectedTab)
        assertTrue(afterDuress.lastDuressWipeMs in 0..99)
        assertEquals(0, afterDuress.ringBufferPackets)
        assertTrue(afterDuress.verifiedCards.any { it.note.contains("FIRST AID GUIDE") })
        assertTrue(afterDuress.quarantineCards.isEmpty())
    }
}
