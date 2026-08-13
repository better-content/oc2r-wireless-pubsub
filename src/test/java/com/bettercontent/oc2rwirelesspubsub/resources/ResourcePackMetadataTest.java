package com.bettercontent.oc2rwirelesspubsub.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ResourcePackMetadataTest {
    @Test
    void packMetadataIsPresentAndValidForMinecraft1201() {
        final var resource = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("pack.mcmeta");

        assertNotNull(resource, "pack.mcmeta must be packaged so loaders can build ResourcePackInfo");

        final JsonObject root;
        try (var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            root = Objects.requireNonNull(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception exception) {
            throw new AssertionError("pack.mcmeta must be valid JSON", exception);
        }

        final var pack = root.getAsJsonObject("pack");
        assertNotNull(pack, "pack.mcmeta must contain a pack object");
        assertEquals(15, pack.get("pack_format").getAsInt());
        assertEquals("OC2R Wireless resources", pack.get("description").getAsString());
    }
}
