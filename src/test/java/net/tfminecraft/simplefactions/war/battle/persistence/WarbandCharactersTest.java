package net.tfminecraft.simplefactions.war.battle.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.tfminecraft.simplefactions.database.WarbandData;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.tlibs.character.CharacterRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import net.tfminecraft.simplefactions.database.DurableJson;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.*;

class WarbandCharactersTest {
    @TempDir Path folder;
    private final UUID owner = UUID.fromString("aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb");
    private final UUID invitee = UUID.randomUUID();
    private final CharacterRef heir = new CharacterRef(owner, UUID.randomUUID());
    private final CharacterRef invitedHeir = new CharacterRef(invitee, UUID.randomUUID());

    private Path source() throws IOException {
        WarbandData data = new WarbandData();
        data.id = "one";
        data.leaderId = owner.toString();
        data.memberIds.add(owner.toString());
        data.invitedIds.add(invitee.toString());
        JsonObject raw = new GsonBuilder().serializeNulls().create().toJsonTree(data).getAsJsonObject();
        raw.remove("memberCharacters");
        raw.remove("invitedCharacters");
        raw.add("extension", JsonParser.parseString("{\"future\":null,\"nested\":[1,null,\"kept\"]}"));
        Path file = folder.resolve("warband_one.json");
        Files.writeString(file, new GsonBuilder().serializeNulls().create().toJson(raw));
        return file;
    }

    private Warband read(Path file) throws IOException {
        return WarbandCharacters.read(file, () -> true,
                account -> Optional.ofNullable(Map.of(owner, heir, invitee, invitedHeir).get(account)));
    }

    @Test
    void productionHeirLookupWaitsForReadinessAndUsesOnlyReviewedRecipients() throws Exception {
        Path file = source();
        try (var identity = new net.tfminecraft.simplefactions.testsupport.SharedIdentity()) {
            identity.heirsReady = false;
            assertTrue(WarbandCharacters.read(file.toFile()).getMemberCharacters().isEmpty());
            identity.heirs.put(owner, heir);
            identity.heirs.put(invitee, invitedHeir);
            identity.heirsReady = true;
            assertEquals(Optional.of(heir), WarbandCharacters.read(file.toFile()).getMemberCharacter(owner));
        }
    }

    @Test
    void actualOlderTlibsLoadsWithoutLinkingItsMissingHeirMethod() throws Exception {
        Path repository = Path.of(System.getProperty("maven.repo.local",
                Path.of(System.getProperty("user.home"), ".m2/repository").toString()));
        Path jar = repository.resolve("me/plugins/tlibs/2.3.0/tlibs-2.3.0.jar");
        assertTrue(Files.isRegularFile(jar), "Install the pinned .github/compatibility/tlibs-2.3.xml fixture");
        var classes = WarbandCharacters.class.getProtectionDomain().getCodeSource().getLocation();
        try (var loader = new java.net.URLClassLoader(new java.net.URL[]{classes, jar.toUri().toURL()},
                getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("net.tfminecraft.tlibs") || name.startsWith(WarbandCharacters.class.getName())) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) type = findClass(name);
                    if (resolve) resolveClass(type);
                    return type;
                }
                return super.loadClass(name, resolve);
            }
        }) {
            Class<?> api = loader.loadClass("net.tfminecraft.tlibs.character.Characters");
            assertTrue(api.getProtectionDomain().getCodeSource().getLocation().toString().contains("2.3.0.jar"));
            assertThrows(NoSuchMethodException.class, () -> api.getMethod("legacyHeir", UUID.class));
            Path file = source();
            String original = Files.readString(file);
            Class<?> store = loader.loadClass(WarbandCharacters.class.getName());
            Warband band = (Warband) store.getMethod("read", java.io.File.class).invoke(null, file.toFile());
            assertTrue(band.getMemberCharacters().isEmpty());
            assertEquals(original, Files.readString(file));
        }
    }

    @Test
    void reviewedMigrationAndNormalSavePreserveUnknownNullsAndFixedRecipients() throws Exception {
        Path file = source();
        String original = Files.readString(file);
        Warband band = read(file);
        assertEquals(Optional.of(heir), band.getMemberCharacter(owner));
        assertEquals(invitedHeir, band.getInvitedCharacters().get(invitee));
        Path backup = folder.resolve("character-identity-v1/warband_one.json/source.json");
        assertEquals(original, JsonParser.parseString(Files.readString(backup)).getAsJsonObject().get("source").getAsString());
        JsonObject before = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        band.setLocked(true);
        WarbandCharacters.save(file.toFile(), band);
        Warband restarted = WarbandCharacters.read(file, () -> true, account -> {
            fail("A completed migration must not re-derive an heir");
            return Optional.empty();
        });
        assertTrue(restarted.isLocked());
        assertEquals(before.get("extension"), restarted.getStoredFields().get("extension"));
        assertEquals(Optional.of(heir), restarted.getMemberCharacter(owner));
        assertEquals(original, JsonParser.parseString(Files.readString(backup)).getAsJsonObject().get("source").getAsString());
    }

    @Test
    void missingHeirsStayExplicitlyUnassignedWithoutLaterActiveFallback() throws Exception {
        Path file = source();
        Warband band = WarbandCharacters.read(file, () -> true, account -> Optional.empty());
        assertTrue(band.getMemberCharacters().isEmpty());
        assertTrue(band.getInvitedCharacters().isEmpty());
        assertTrue(band.getStoredFields().getAsJsonObject("memberCharacters").get(owner.toString()).isJsonNull());
        WarbandCharacters.save(file.toFile(), band);
        Warband later = read(file);
        assertTrue(later.getMemberCharacters().isEmpty());
        assertTrue(later.getStoredFields().getAsJsonObject("memberCharacters").get(owner.toString()).isJsonNull());
    }

    @Test
    void unavailableHeirsDoNotWriteAMigrationOrConsultLivePlayers() throws Exception {
        Path file = source();
        String original = Files.readString(file);
        Warband band = WarbandCharacters.read(file, () -> false, account -> {
            fail("Heirs must not be queried before readiness");
            return Optional.empty();
        });
        assertTrue(band.getMemberCharacters().isEmpty());
        assertEquals(original, Files.readString(file));
        assertFalse(Files.exists(folder.resolve("character-identity-v1")));
    }

    @Test
    void invalidExistingRefIsHeldWhileValidRefExtensionsSurviveSaves() throws Exception {
        Path file = source();
        JsonObject raw = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        raw.add("memberCharacters", JsonParser.parseString("{\"" + owner + "\":{\"ownerId\":\"broken\"}}"));
        JsonObject invite = new GsonBuilder().serializeNulls().create().toJsonTree(invitedHeir).getAsJsonObject();
        invite.add("future", com.google.gson.JsonNull.INSTANCE);
        JsonObject invitations = new JsonObject();
        invitations.add(invitee.toString(), invite);
        raw.add("invitedCharacters", invitations);
        Files.writeString(file, new GsonBuilder().serializeNulls().create().toJson(raw));
        AtomicInteger lookups = new AtomicInteger();
        Warband band = WarbandCharacters.read(file, () -> true, account -> {
            lookups.incrementAndGet();
            return Optional.of(heir);
        });
        assertEquals(0, lookups.get());
        assertTrue(band.getMemberCharacters().isEmpty());
        WarbandCharacters.save(file.toFile(), band);
        JsonObject saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(raw.get("memberCharacters"), saved.get("memberCharacters"));
        assertEquals(invite, saved.getAsJsonObject("invitedCharacters").get(invitee.toString()));
    }

    @Test
    void unreadableAndFutureVersionSourcesRemainHeldAndCannotBeOverwritten() throws Exception {
        Path file = source();
        for (String source : new String[] {"null", "{broken", "{\"character-identity-version\":9}"}) {
            Files.writeString(file, source);
            assertThrows(IOException.class, () -> read(file));
            assertTrue(WarbandCharacters.isHeld(file.toFile()));
            assertThrows(IOException.class, () -> WarbandCharacters.save(file.toFile(),
                    Warband.createWithMemberIds("one", owner, false)));
            assertEquals(source, Files.readString(file));
        }
    }

    @Test
    void publicationFailureReplaysTheFixedPlanWithoutLookingUpNewHeirs() throws Exception {
        Path file = source();
        String original = Files.readString(file);
        try (MockedStatic<DurableJson> writes = mockStatic(DurableJson.class, CALLS_REAL_METHODS)) {
            writes.when(() -> DurableJson.write(eq(file), any(), any(com.google.gson.Gson.class)))
                    .thenThrow(new IOException("cut before publication"));
            assertThrows(IOException.class, () -> read(file));
        }
        assertEquals(original, Files.readString(file));
        assertTrue(WarbandCharacters.isHeld(file.toFile()));
        Warband replayed = WarbandCharacters.read(file, () -> true, account -> {
            fail("Prepared recipients must remain fixed after restart");
            return Optional.empty();
        });
        assertEquals(Optional.of(heir), replayed.getMemberCharacter(owner));
        assertFalse(WarbandCharacters.isHeld(file.toFile()));
    }

    @Test
    void aSourceChangedDuringHeirResolutionIsHeldBeforePublication() throws Exception {
        Path file = source();
        String edited = Files.readString(file) + "\n";
        assertThrows(IOException.class, () -> WarbandCharacters.read(file, () -> true, account -> {
            try { Files.writeString(file, edited); }
            catch (IOException error) { throw new java.io.UncheckedIOException(error); }
            return Optional.ofNullable(Map.of(owner, heir, invitee, invitedHeir).get(account));
        }));
        assertTrue(WarbandCharacters.isHeld(file.toFile()));
        assertEquals(edited, Files.readString(file));
    }

    @Test
    void fixedPlanRejectsChangedSourcesAndCorruptOutputOrBackup() throws Exception {
        Path file = source();
        String original = Files.readString(file);
        read(file);
        Path plan = folder.resolve("character-identity-v1/warband_one.json/plan.json");
        Path backup = folder.resolve("character-identity-v1/warband_one.json/source.json");
        String fixed = Files.readString(plan);
        String archived = Files.readString(backup);
        Files.writeString(file, original + "\n");
        assertThrows(IOException.class, () -> read(file));
        assertEquals(original + "\n", Files.readString(file));
        Files.writeString(file, original);
        JsonObject corrupt = JsonParser.parseString(fixed).getAsJsonObject();
        JsonObject changed = JsonParser.parseString(corrupt.get("after").getAsString()).getAsJsonObject();
        changed.addProperty("name", "changed recipient plan");
        corrupt.addProperty("after", new GsonBuilder().serializeNulls().create().toJson(changed));
        Files.writeString(plan, new GsonBuilder().serializeNulls().create().toJson(corrupt));
        assertThrows(IOException.class, () -> read(file));
        assertEquals(original, Files.readString(file));
        Files.writeString(plan, fixed);
        corrupt = JsonParser.parseString(archived).getAsJsonObject();
        corrupt.addProperty("source", "{} ");
        Files.writeString(backup, corrupt.toString());
        assertThrows(IOException.class, () -> read(file));
        assertEquals(original, Files.readString(file));
    }

    @Test
    void dummyAndPendingIdsNeverBecomeCharacterOwnersAndMapMetadataSurvivesPruning() throws Exception {
        Path file = source();
        JsonObject raw = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        UUID pending = Warband.pendingLeaderUuid("one");
        UUID dummy = UUID.randomUUID();
        raw.getAsJsonArray("memberIds").add(pending.toString());
        raw.getAsJsonArray("memberIds").add(dummy.toString());
        raw.add("dummyMemberIds", JsonParser.parseString("[\"" + dummy + "\"]"));
        raw.add("memberCharacters", JsonParser.parseString("{\"future-metadata\":{\"null\":null}}"));
        Files.writeString(file, new GsonBuilder().serializeNulls().create().toJson(raw));
        Warband band = WarbandCharacters.read(file, () -> true, account -> {
            assertNotEquals(pending, account);
            assertNotEquals(dummy, account);
            return Optional.ofNullable(Map.of(owner, heir, invitee, invitedHeir).get(account));
        });
        assertTrue(band.getMemberCharacter(pending).isEmpty());
        assertTrue(band.getMemberCharacter(dummy).isEmpty());
        band.removeMember(owner);
        WarbandCharacters.save(file.toFile(), band);
        JsonObject saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertFalse(saved.getAsJsonObject("memberCharacters").has(owner.toString()));
        assertEquals(raw.getAsJsonObject("memberCharacters").get("future-metadata"),
                saved.getAsJsonObject("memberCharacters").get("future-metadata"));
    }

    @Test
    void duplicateKeysTrailingDataAndFilenameDisagreementAreHeldWithoutRewriting() throws Exception {
        Path file = source();
        for (String corrupt : new String[]{"{\"id\":\"one\",\"extension\":{\"x\":1,\"x\":2}}",
                "{\"id\":\"one\"} {}", "{\"id\":\"other\"}",
                "{\"id\":\"one\",\"character-identity-version\":1.5}"}) {
            Files.writeString(file, corrupt);
            assertThrows(IOException.class, () -> read(file));
            assertTrue(WarbandCharacters.isHeld(file.toFile()));
            assertEquals(corrupt, Files.readString(file));
        }
    }

    @Test
    void conflictingCaseAliasesAndMalformedRefsGrantNoAuthorityAndKeepRawEvidence() throws Exception {
        Path file = source();
        JsonObject raw = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        String key = owner.toString();
        assertNotEquals(key, key.toUpperCase(java.util.Locale.ROOT));
        JsonObject refs = new JsonObject();
        refs.add(key, new GsonBuilder().create().toJsonTree(heir));
        refs.add(key.toUpperCase(java.util.Locale.ROOT), new GsonBuilder().create()
                .toJsonTree(new CharacterRef(owner, UUID.randomUUID())));
        raw.add("memberCharacters", refs);
        Files.writeString(file, new GsonBuilder().serializeNulls().create().toJson(raw));
        Warband band = read(file);
        assertTrue(band.getMemberCharacter(owner).isEmpty());
        WarbandCharacters.save(file.toFile(), band);
        assertEquals(refs, JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("memberCharacters"));
        raw.addProperty("character-identity-version", 1);
        refs.add(key.toUpperCase(java.util.Locale.ROOT), JsonParser.parseString("{\"ownerId\":\"broken\"}"));
        Files.writeString(file, new GsonBuilder().serializeNulls().create().toJson(raw));
        assertTrue(read(file).getMemberCharacter(owner).isEmpty());
    }

    @Test
    void normalSavesHoldChangedOwnershipAndMalformedDocumentsInsteadOfOverwritingThem() throws Exception {
        Path file = source();
        Warband first = read(file);
        JsonObject changed = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        changed.getAsJsonObject("memberCharacters").add(owner.toString(),
                new GsonBuilder().create().toJsonTree(new CharacterRef(owner, UUID.randomUUID())));
        String edited = new GsonBuilder().serializeNulls().create().toJson(changed);
        Files.writeString(file, edited);
        assertThrows(IOException.class, () -> WarbandCharacters.save(file.toFile(), first));
        assertTrue(WarbandCharacters.isHeld(file.toFile()));
        assertEquals(edited, Files.readString(file));
        Warband loaded = read(file);
        Files.writeString(file, "null");
        assertThrows(IOException.class, () -> WarbandCharacters.save(file.toFile(), loaded));
        assertTrue(WarbandCharacters.isHeld(file.toFile()));
        assertEquals("null", Files.readString(file));
    }

    @Test
    void anExternallyDeletedSourceIsNotRecreatedFromItsRuntimeCache() throws Exception {
        Path file = source();
        Warband band = read(file);
        Files.delete(file);
        assertThrows(IOException.class, () -> WarbandCharacters.save(file.toFile(), band));
        assertTrue(WarbandCharacters.isHeld(file.toFile()));
        assertFalse(Files.exists(file));
    }

    @Test
    void hashConsistentPlansCannotChangeSourceStateExistingRefsOrPublicationEncoding() throws Exception {
        Path file = source();
        JsonObject raw = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        raw.add("memberCharacters", JsonParser.parseString("{\"" + owner + "\":null}"));
        Files.writeString(file, new GsonBuilder().serializeNulls().create().toJson(raw));
        String original = Files.readString(file);
        read(file);
        Path planFile = folder.resolve("character-identity-v1/warband_one.json/plan.json");
        String fixed = Files.readString(planFile);
        for (String change : java.util.List.of("id", "memberIds", "invitedIds", "extension", "existing-ref", "version", "encoding")) {
            Files.writeString(file, original);
            JsonObject plan = JsonParser.parseString(fixed).getAsJsonObject();
            JsonObject after = JsonParser.parseString(plan.get("after").getAsString()).getAsJsonObject();
            switch (change) {
                case "id" -> after.addProperty("id", "another");
                case "memberIds" -> after.add("memberIds", new com.google.gson.JsonArray());
                case "invitedIds" -> after.add("invitedIds", new com.google.gson.JsonArray());
                case "extension" -> after.add("extension", com.google.gson.JsonNull.INSTANCE);
                case "existing-ref" -> after.getAsJsonObject("memberCharacters").add(owner.toString(), new GsonBuilder().create().toJsonTree(heir));
                case "version" -> after.remove("character-identity-version");
                default -> { }
            }
            String bytes = net.tfminecraft.simplefactions.database.JsonUtil.GSON.newBuilder().serializeNulls().create().toJson(after);
            if (change.equals("encoding")) bytes += "\n";
            plan.addProperty("after", bytes);
            plan.addProperty("afterHash", com.google.common.hash.Hashing.sha256().hashString(bytes, java.nio.charset.StandardCharsets.UTF_8).toString());
            Files.writeString(planFile, new GsonBuilder().serializeNulls().create().toJson(plan));
            assertThrows(IOException.class, () -> read(file), change);
            assertEquals(original, Files.readString(file));
        }
    }

    @Test
    void orphanCharacterMetadataSurvivesNormalSavesWithoutBecomingAMember() throws Exception {
        Path file = source();
        JsonObject raw = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        UUID orphan = UUID.randomUUID();
        JsonObject entries = new JsonObject();
        entries.add(orphan.toString(), JsonParser.parseString("{\"future\":null}"));
        raw.add("memberCharacters", entries);
        Files.writeString(file, new GsonBuilder().serializeNulls().create().toJson(raw));
        Warband band = read(file);
        assertFalse(band.hasMember(orphan));
        WarbandCharacters.save(file.toFile(), band);
        assertEquals(entries.get(orphan.toString()), JsonParser.parseString(Files.readString(file)).getAsJsonObject()
                .getAsJsonObject("memberCharacters").get(orphan.toString()));
    }
}
