package com.empresa.estructuracion.batch.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HashUtilsTest {
    @Test
    void sha256ShouldCreateDigest() {
        byte[] digest = HashUtils.sha256().digest("abc".getBytes(StandardCharsets.UTF_8));

        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                HashUtils.hex(digest));
    }

    @Test
    void hexShouldFormatBytesInLowercase() {
        assertEquals("000fabff", HashUtils.hex(new byte[]{0, 15, -85, -1}));
    }
}
