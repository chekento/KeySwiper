package cloud.kosch.keyswiper.security

import org.junit.Assert.assertEquals
import org.junit.Test

class SecurityExposureInspectorTest {
    @Test fun emptyResultNeverClaimsProtection() {
        assertEquals(SecurityExposureInspector.Level.NONE_OBSERVED,
            SecurityExposureInspector.assess(emptyList()))
    }
    @Test fun benignServiceRequiresReviewNotAccusation() {
        assertEquals(SecurityExposureInspector.Level.REVIEW,
            SecurityExposureInspector.assess(listOf(SecurityExposureInspector.AccessibleService("Screenreader", false, false))))
    }
    @Test fun windowReadOrKeyFilteringElevatesPotentialRisk() {
        assertEquals(SecurityExposureInspector.Level.ELEVATED,
            SecurityExposureInspector.assess(listOf(SecurityExposureInspector.AccessibleService("Reader", true, false))))
        assertEquals(SecurityExposureInspector.Level.ELEVATED,
            SecurityExposureInspector.assess(listOf(SecurityExposureInspector.AccessibleService("Service", false, true))))
    }
}
