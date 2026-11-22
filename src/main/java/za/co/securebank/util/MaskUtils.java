package za.co.securebank.util;

public final class MaskUtils {

    private MaskUtils() {
    }

    /** Masks an account number for logs and audit details, keeping only the last four digits. */
    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return "****";
        }
        return "*".repeat(accountNumber.length() - 4) + accountNumber.substring(accountNumber.length() - 4);
    }
}
