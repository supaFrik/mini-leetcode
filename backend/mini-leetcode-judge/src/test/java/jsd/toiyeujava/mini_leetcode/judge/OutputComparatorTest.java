package jsd.toiyeujava.mini_leetcode.judge;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OutputComparatorTest {

    @Test
    void windowsLineEndingsAreEqualToUnix() {
        assertTrue(OutputComparator.matches("1\r\n2\r\n", "1\n2\n"));
    }

    @Test
    void trailingSpacesAndBlankLinesAreIgnored() {
        assertTrue(OutputComparator.matches("42   \n\n\n", "42"));
    }

    @Test
    void caseIsSensitive() {
        assertFalse(OutputComparator.matches("Yes", "yes"));
    }

    @Test
    void differentContentDoesNotMatch() {
        assertFalse(OutputComparator.matches("41", "42"));
    }
}