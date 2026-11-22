package za.co.securebank.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @ParameterizedTest
    @ValueSource(strings = {"Str0ng!Passw0rd", "Abcdefgh1!", "C0mpl3x#Secret99"})
    void acceptsStrongPasswords(String password) {
        assertTrue(validator.isValid(password, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "short1!A",
            "alllowercase1!",
            "ALLUPPERCASE1!",
            "NoDigitsHere!!",
            "NoSpecial12345",
            "Has Space1!aaa"})
    void rejectsWeakPasswords(String password) {
        assertFalse(validator.isValid(password, null));
    }

    @Test
    void rejectsNullAndOverlongPasswords() {
        assertFalse(validator.isValid(null, null));
        assertFalse(validator.isValid("Aa1!" + "x".repeat(80), null));
    }
}
