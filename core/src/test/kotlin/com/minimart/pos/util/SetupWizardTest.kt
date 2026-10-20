package com.minimart.pos.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupWizardTest {
    @Test fun showsOnlyForAnEmptyNewShopOwner() {
        assertTrue(SetupWizard.shouldShow(false, 0, 0, true))
        assertFalse(SetupWizard.shouldShow(true, 0, 0, true))
        assertFalse(SetupWizard.shouldShow(false, 12, 0, true))
        assertFalse(SetupWizard.shouldShow(false, 0, 3, true))
        assertFalse(SetupWizard.shouldShow(false, 0, 0, false))
    }

    @Test fun pinRules() {
        assertFalse(SetupWizard.validOpeningPin("1234"))
        assertFalse(SetupWizard.validOpeningPin("1111"))
        assertFalse(SetupWizard.validOpeningPin("123"))
        assertFalse(SetupWizard.validOpeningPin("12a4"))
        assertTrue(SetupWizard.validOpeningPin("4829"))
    }

    @Test fun storeNameRules() {
        assertTrue(SetupWizard.isDefaultStoreName("My MiniMart"))
        assertTrue(SetupWizard.isDefaultStoreName("  "))
        assertFalse(SetupWizard.isDefaultStoreName("Baraka Stores"))
        assertTrue(SetupWizard.validStoreName("Baraka Stores"))
        assertFalse(SetupWizard.validStoreName("B"))
    }
}
