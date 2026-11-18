package org.pharos.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pharos.core.protocol.TacticalCard
import org.pharos.core.storage.PharosEncryptedVault

class AppIntegrationUnitTest {

    @Test
    fun `cockpit viewModel initializes verified vs quarantine isolation and handles duress wipe`() {
        val vm = PharosViewModel(appContext = null)
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

        // Trigger Duress PIN ("9911") -> <100ms cryptographic wipe + Decoy First-Aid state
        val pinRes = vm.submitVaultPin("9911")
        assertEquals(PharosEncryptedVault.PinUnlockResult.TRIGGERED_DURESS_DECOY_MODE, pinRes)
        val afterDuress = vm.uiState.value
        assertTrue(afterDuress.isDecoyMode)
        assertTrue(afterDuress.lastDuressWipeMs in 0..99)
        assertEquals(0, afterDuress.ringBufferPackets)
        assertTrue(afterDuress.verifiedCards.any { it.note.contains("FIRST AID GUIDE") })
        assertTrue(afterDuress.quarantineCards.isEmpty())
    }
}
