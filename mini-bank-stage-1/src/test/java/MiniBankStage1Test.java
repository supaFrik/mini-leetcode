import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MiniBankStage1Test {

    @BeforeEach
    void resetAccountSequence() {
        try {
            Field nextIdField = Account.class.getDeclaredField("nextId");
            nextIdField.setAccessible(true);
            nextIdField.setInt(null, 1);
        } catch (Exception e) {
            // Field might not exist or be accessible; ignore
        }
    }

    @Test
    @DisplayName("Verify Account Encapsulation (no public fields)")
    void testAccountEncapsulation() {
        for (Field field : Account.class.getDeclaredFields()) {
            assertFalse(Modifier.isPublic(field.getModifiers()),
                    "Field '" + field.getName() + "' in Account must not be public");
        }
    }

    @Test
    @DisplayName("Verify Account basic operations")
    void testAccountOperations() {
        Account acc = new Account("S", "Alice", 1000);
        assertEquals(1, acc.getId());
        assertEquals("SAVINGS", acc.getType());
        assertEquals("Alice", acc.getOwner());
        assertEquals(1000, acc.getBalance());

        long newBalance = acc.deposit(500);
        assertEquals(1500, newBalance);
        assertEquals(1500, acc.getBalance());

        assertThrows(IllegalArgumentException.class, () -> acc.deposit(0));
        assertThrows(IllegalArgumentException.class, () -> acc.deposit(-100));
        assertThrows(IllegalArgumentException.class, () -> new Account("S", "", 100));
        assertThrows(IllegalArgumentException.class, () -> new Account("S", null, 100));
        assertThrows(IllegalArgumentException.class, () -> new Account("S", "Bob", -10));
    }

    @Test
    @DisplayName("Verify Bank open, find and totalBalance")
    void testBankOperations() {
        Bank bank = new Bank("Test Bank");
        assertEquals("Test Bank", bank.getName());

        Account a1 = bank.open("S", "Alice", 100000);
        Account a2 = bank.open("C", "Bob", 50000);

        assertEquals(2, bank.getAccounts().size());
        assertSame(a1, bank.find(a1.getId()));
        assertSame(a2, bank.find(a2.getId()));
        assertEquals(150000, bank.totalBalance());

        assertThrows(IllegalArgumentException.class, () -> bank.find(999));
        assertThrows(IllegalArgumentException.class, () -> bank.open("X", "Charlie", 100));
    }

    @Test
    @DisplayName("Verify Case 01: Two accounts, deposit, balance, list, exit")
    void testCase01_TwoAccounts() throws Exception {
        String input = String.join(System.lineSeparator(),
                "2",
                "An",
                "S",
                "100000",
                "Binh",
                "C",
                "0",
                "DEPOSIT 2 50000",
                "BALANCE 1",
                "LIST",
                "EXIT"
        ) + System.lineSeparator();

        String output = runMainProcess(input);
        assertTrue(output.contains("=== Mini Bank ==="));
        assertTrue(output.contains("#1 SAVINGS      An                 100,000"));
        assertTrue(output.contains("#2 CHECKING     Binh                     0"));
        assertTrue(output.contains("New balance: 50,000"));
        assertTrue(output.contains("Bye!"));
    }

    @Test
    @DisplayName("Verify Case 02: EOF Summary")
    void testCase02_EofSummary() throws Exception {
        String input = String.join(System.lineSeparator(),
                "1",
                "Chi",
                "S",
                "0",
                "DEPOSIT 1 1000",
                "DEPOSIT 1 2500",
                "BALANCE 1"
        ) + System.lineSeparator();

        String output = runMainProcess(input);
        assertTrue(output.contains("=== Mini Bank ==="));
        assertTrue(output.contains("New balance: 1,000"));
        assertTrue(output.contains("New balance: 3,500"));
        assertTrue(output.contains("#1 SAVINGS      Chi                  3,500"));
        assertTrue(output.contains("End of input. Total assets: 3,500"));
    }

    @Test
    @DisplayName("Verify Case 03: No accounts opened")
    void testCase03_NoAccounts() throws Exception {
        String input = String.join(System.lineSeparator(),
                "0",
                "LIST",
                "EXIT"
        ) + System.lineSeparator();

        String output = runMainProcess(input);
        assertTrue(output.contains("=== Mini Bank ==="));
        assertTrue(output.contains("Accounts to open: Commands:"));
        assertTrue(output.contains("Bye!"));
    }

    private String runMainProcess(String input) throws Exception {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        String classPath = System.getProperty("java.class.path");

        ProcessBuilder pb = new ProcessBuilder(javaBin, "-cp", classPath, "Main");
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (OutputStream os = process.getOutputStream()) {
            os.write(input.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        String output;
        try (InputStream is = process.getInputStream()) {
            output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        process.waitFor();
        return output;
    }
}
