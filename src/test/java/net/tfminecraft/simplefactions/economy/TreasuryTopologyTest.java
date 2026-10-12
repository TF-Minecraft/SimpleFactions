package net.tfminecraft.simplefactions.economy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.denareconomy.api.Result;
import net.tfminecraft.simplefactions.database.*;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TreasuryTopologyTest {
    private PersistenceFilesFixture files;
    private FactionDomainFixture domain;
    private Faction origin, target;
    private Guild moving;
    private Player actor;
    private Path journal;

    @BeforeEach void setup() throws Exception {
        files = new PersistenceFilesFixture();
        domain = new FactionDomainFixture();
        domain.provincesEnabled(true);
        for (int id : List.of(1,2,3,4)) domain.provinceData.put(id, new Province(id,"PLAINS",50));
        domain.provinceData.get(1).addNeighbour(2);
        domain.provinceData.get(2).addNeighbour(1);
        var a = domain.data("origin", "Alice"); a.capital=1; a.provinces.addAll(List.of(1,2));
        var b = domain.data("target", "Bob"); b.capital=3; b.provinces.add(3);
        origin=domain.saved(a); target=domain.saved(b);
        origin.getOrCreateMainGuild(); target.getOrCreateMainGuild();
        moving=domain.guild(origin,"traders","Cara"); moving.setCapital(2);
        origin.getSettlementHandler().found("Origin",1,10,10);
        origin.getSettlementHandler().found("Old_Guild_City",2,20,20);
        target.getSettlementHandler().found("Target",3,30,30);
        TestTreasuries.set(moving.getBank(),100);
        TestTreasuries.set(origin.getBank(),100);
        actor=domain.player("Bob"); domain.player("Cara");
        journal=files.root.resolve("economy/callers"); TreasuryActions.recover(journal);
    }
    @AfterEach void close() throws Exception {
        try { if(domain!=null) domain.close(); } finally { if(files!=null) files.close(); }
    }
    private boolean move(String id) { return TreasuryTopology.relocate(actor,moving,target,3,null,20,id); }
    private JsonObject read(String id) throws IOException { return DurableJson.read(files.root.resolve("Data/"+id+".json"), JsonObject.class); }
    private void write(String id, JsonObject json) throws IOException { DurableJson.write(files.root.resolve("Data/"+id+".json"),json); }
    private JsonObject effect() throws IOException {
        try(var paths=Files.list(journal)) {
            return DurableJson.read(paths.findFirst().orElseThrow(),JsonObject.class).getAsJsonObject("intent")
                    .getAsJsonObject("changes").getAsJsonArray("effects").get(0).getAsJsonObject();
        }
    }
    private JsonObject guild(JsonObject file,String id) {
        for(JsonElement item:file.getAsJsonArray("guilds")) if(id.equals(item.getAsJsonObject().get("id").getAsString())) return item.getAsJsonObject();
        return null;
    }
    private void interruptTargetPublication(String id) throws Exception {
        try(var writes=mockStatic(DurableJson.class,CALLS_REAL_METHODS)) {
            writes.when(()->DurableJson.write(any(Path.class),any())).thenAnswer(call->{
                Path path=call.getArgument(0);
                if(path.getFileName().toString().equals("target.json") && Denars.status(id).orElse(null)==Result.APPLIED)
                    throw new IOException("simulated interrupted target publication");
                return call.callRealMethod();
            });
            assertFalse(move(id));
        }
        assertEquals(Result.APPLIED,Denars.status(id).orElseThrow());
        assertEquals(80,moving.getBank().getWealth());
        assertFalse(EconomyRuntime.ready());
        assertNull(guild(read("origin"),"traders"));
        assertNull(guild(read("target"),"traders"));
    }
    @Test void retryUsesTheOriginalIntentAndChargesOnlyOnce() throws Exception {
        String id=TreasuryActions.newId(); assertTrue(move(id));
        assertSame(target,moving.getFaction()); assertEquals(3,moving.getCapital());
        assertNull(origin.getSettlementHandler().getByProvince(2));
        assertEquals(80,moving.getBank().getWealth());
        assertTrue(TreasuryTopology.relocate(actor,moving,origin,1,"Different",99,id));
        assertSame(target,moving.getFaction()); assertEquals(80,moving.getBank().getWealth());
        assertNotNull(guild(read("target"),"traders"));
    }
    @Test void appliedMoveRepairsBothFilesBeforeLoadingAndReplaysWithoutAnotherDebit() throws Exception {
        String id=TreasuryActions.newId(); interruptTargetPublication(id);
        TreasuryActions.recoverTopologyFiles(journal);
        assertNull(guild(read("origin"),"traders"));
        assertEquals(moving.getIncarnation(),guild(read("target"),"traders").get("incarnation").getAsString());
        assertEquals("Cara",guild(read("target"),"traders").get("leader").getAsString());
        TreasuryActions.recoverTopologyFiles(journal);
        TreasuryActions.recover(journal); EconomyRuntime.recovered();
        assertTrue(move(id)); assertEquals(80,moving.getBank().getWealth());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void rawRecoveryCanResumeAfterEitherSideWasPublished(boolean targetFirst) throws Exception {
        assertTrue(new Database().saveFactionDurably(origin)); assertTrue(new Database().saveFactionDurably(target));
        JsonObject oldOrigin=read("origin"), oldTarget=read("target");
        assertTrue(move(TreasuryActions.newId())); JsonObject effect=effect();
        if(targetFirst) write("origin",oldOrigin); else write("target",oldTarget);
        TreasuryTopology.restoreFiles(effect); TreasuryTopology.restoreFiles(effect);
        assertNull(guild(read("origin"),"traders"));
        assertEquals(3,guild(read("target"),"traders").get("capital").getAsInt());
    }
    @Test void unrelatedFileFieldsAndMovingGuildSettingsSurviveRecovery() throws Exception {
        String id=TreasuryActions.newId(); interruptTargetPublication(id);
        JsonObject targetFile=read("target"); targetFile.addProperty("future setting","retained"); write("target",targetFile);
        TreasuryActions.recoverTopologyFiles(journal);
        assertEquals("retained",read("target").get("future setting").getAsString());
        assertEquals(JsonUtil.GSON.toJsonTree(moving.getMembers()),guild(read("target"),"traders").get("members"));
    }
    @ParameterizedTest @ValueSource(strings={"incarnation","capital","memberships","file-id","missing-guild","filename"})
    void changedIdentityOrTopologyCannotBeOverwritten(String damage) throws Exception {
        assertTrue(move(TreasuryActions.newId())); JsonObject effect=effect(), data=read("target");
        switch(damage) {
            case "incarnation" -> guild(data,"target").addProperty("incarnation",UUID.randomUUID().toString());
            case "capital" -> data.addProperty("capital",999);
            case "memberships" -> data.getAsJsonArray("guilds").remove(guild(data,"traders"));
            case "file-id" -> data.addProperty("id","imposter");
            case "missing-guild" -> {
                effect.getAsJsonObject("after").getAsJsonObject("movingGuilds").remove(moving.getIncarnation());
                data.getAsJsonArray("guilds").remove(guild(data,"traders"));
            }
            case "filename" -> {
                var realms=effect.getAsJsonObject("after").getAsJsonObject("realms");
                realms.add("../outside",realms.remove("target"));
            }
        }
        // A before membership is valid: to reject changed membership, inject a third unknown guild instead.
        if(damage.equals("memberships")) {
            JsonObject extra=guild(data,"target").deepCopy();extra.addProperty("id","unrelated");data.getAsJsonArray("guilds").add(extra);
        }
        write("target",data); String bytes=Files.readString(files.root.resolve("Data/target.json"));
        assertThrows(IOException.class,()->TreasuryTopology.restoreFiles(effect));
        assertEquals(bytes,Files.readString(files.root.resolve("Data/target.json")));
    }
    @Test void beforePublicationFailureDoesNotDebitOrChangeTopology() throws Exception {
        JsonObject before=TreasuryTopology.capture();
        try(var writes=mockStatic(DurableJson.class,CALLS_REAL_METHODS)) {
            writes.when(()->DurableJson.write(any(Path.class),any())).thenThrow(new IOException("disk unavailable"));
            assertFalse(move(TreasuryActions.newId()));
        }
        assertEquals(before,TreasuryTopology.capture()); assertEquals(100,moving.getBank().getWealth());
    }
    @Test void calculatingClaimsIsDetachedAndMissingRulingGuildCannotBeCreated() {
        JsonObject before=TreasuryTopology.capture();
        var claims=origin.getProvinceHandler().previewTopology(1,List.of(1,2,4),Map.of("origin",1,"traders",4));
        assertEquals(List.of(1,2),claims.provinces());assertEquals(-1,claims.capitals().get("traders"));
        assertEquals(before,TreasuryTopology.capture());
        origin.getGuildHandler().restoreTopology(List.of(moving));
        assertThrows(IllegalStateException.class,()->TreasuryTopology.capture());
        assertNull(origin.getGuildHandler().getGuild("origin"));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void capitalLossRevalidatesTitlesUsingTheProjectedRealmAndSubjects(boolean subjectRetainsLand) {
        origin.addProvince(4);
        var title=domain.title("old_county","county",1,2,3);
        origin.getTitles().add(title);
        if(subjectRetainsLand) domain.subject(origin,target);
        assertTrue(TreasuryTopology.capital(domain.player("Alice"),origin,4,"New_Capital",false,20,TreasuryActions.newId()));
        assertEquals(List.of(4),origin.getProvinces());
        assertEquals(-1,moving.getCapital());
        assertEquals(subjectRetainsLand,origin.getTitles().contains(title));
        assertNull(origin.getSettlementHandler().getByProvince(1));
        assertNull(origin.getSettlementHandler().getByProvince(2));
        assertNotNull(origin.getSettlementHandler().getByProvince(4));
        assertEquals(80,origin.getBank().getWealth());
    }
    @Test void anUnregisteredGuildCannotPublishARelocationIntent() {
        origin.getGuildHandler().restoreTopology(List.of(origin.getOrCreateMainGuild()));
        assertThrows(IllegalStateException.class,()->move(TreasuryActions.newId()));
        assertEquals(100,moving.getBank().getWealth());
        assertSame(origin,moving.getFaction());
        assertFalse(Files.exists(journal));
    }

}
