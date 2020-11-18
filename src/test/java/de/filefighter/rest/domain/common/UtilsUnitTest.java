package de.filefighter.rest.domain.common;

import de.filefighter.rest.rest.exceptions.RequestDidntMeetFormalRequirementsException;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings(value = "ConstantConditions")
class UtilsUnitTest {

    @Test
    void stringIsValid() {
        String string = "string";
        boolean actual = Utils.stringIsValid(string);
        assertTrue(actual);
    }

    @Test
    void stringIsNotValid() {
        String nullString = null;
        String empty = "";

        boolean actual = Utils.stringIsValid(nullString);
        boolean actual1 = Utils.stringIsValid(empty);
        assertFalse(actual);
        assertFalse(actual1);
    }

    @Test
    void validateHeaderThrows() {
        String dummyHeaderPrefix = "UGABUGA: ";

        String notValid = "";
        String validButDoesntMatch = "something";
        String noContent = dummyHeaderPrefix + "";

        assertThrows(RequestDidntMeetFormalRequirementsException.class, () ->
                Utils.validateAuthorizationHeader(dummyHeaderPrefix, notValid)
        );
        assertThrows(RequestDidntMeetFormalRequirementsException.class, () ->
                Utils.validateAuthorizationHeader(dummyHeaderPrefix, validButDoesntMatch)
        );
        assertThrows(RequestDidntMeetFormalRequirementsException.class, () ->
                Utils.validateAuthorizationHeader(dummyHeaderPrefix, noContent)
        );
    }

    @Test
    void validateHeaderWorks() {
        String dummyHeaderPrefix = "UGABUGA: ";
        String expected = "baum";
        String valid = dummyHeaderPrefix.concat(expected);

        String actual = Utils.validateAuthorizationHeader(dummyHeaderPrefix, valid);

        assertEquals(expected, actual);
    }

    @Test
    void readLinesFromFilesThrows() throws IOException {
        File dummyFile = File.createTempFile("test", "txt");
        int expected = 0;

        int actual = Utils.getLinesFromFile(dummyFile).size();
        assertEquals(expected, actual);
    }
}