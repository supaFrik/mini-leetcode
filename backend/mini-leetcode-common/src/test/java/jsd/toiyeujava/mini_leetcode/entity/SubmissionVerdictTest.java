package jsd.toiyeujava.mini_leetcode.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubmissionVerdictTest {

    @Test
    @DisplayName("SubmissionVerdict must contain all the core Online Judge verdicts")
    void testVerdictsExist() {
        assertNotNull(SubmissionVerdict.valueOf("AC"));
        assertNotNull(SubmissionVerdict.valueOf("WA"));
        assertNotNull(SubmissionVerdict.valueOf("TLE"));
        assertNotNull(SubmissionVerdict.valueOf("MLE"));
        assertNotNull(SubmissionVerdict.valueOf("CE"));
        assertNotNull(SubmissionVerdict.valueOf("RE"));
        assertNotNull(SubmissionVerdict.valueOf("SV"));
        assertNotNull(SubmissionVerdict.valueOf("FLAGGED_CHEATING"));
    }

    @Test
    @DisplayName("isAccepted() returns true only for AC")
    void testIsAccepted() {
        assertTrue(SubmissionVerdict.AC.isAccepted(), "AC phải là accepted");
        assertFalse(SubmissionVerdict.WA.isAccepted(), "WA không phải là accepted");
        assertFalse(SubmissionVerdict.TLE.isAccepted(), "TLE không phải là accepted");
    }

    @Test
    @DisplayName("Every verdict must have a meaningful description")
    void testDescriptionNotNullOrBlank() {
        for (SubmissionVerdict verdict : SubmissionVerdict.values()) {
            assertNotNull(verdict.getDescription(), "Description không được null cho " + verdict.name());
            assertFalse(verdict.getDescription().isBlank(), "Description không được để trống cho " + verdict.name());
        }
    }
}