package net.tfminecraft.simplefactions.vehicles.berth;


import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.berth.InstallationVehicleService;
import net.tfminecraft.simplefactions.vehicles.berth.InstallationVehicleOwnerSync;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleTransferConsentService;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleTransferSessionManager;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleTransferMessages;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RequestManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.request.VehicleTransferConsentRequest;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.InstallationBounds;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
import net.tfminecraft.vehicleframework.data.OwnerData;

class VehicleTransferConsentServiceTest {
    private Path tempDir;
    private List<Faction> previousFactions;
    private PlayerVehicleRegistry registry;
    private InstallationVehicleOwnerSync ownerSync;
    private InstallationVehicleService installationVehicleService;
    private VehicleTransferSessionManager sessionManager;
    private VehicleTransferConsentService consentService;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("sf-consent-service-");
        writeVehiclesFixture();
        InstallationConfigLoader.load(writeInstallationsFixture().toFile());

        previousFactions = FactionManager.factions;
        FactionManager.factions = new ArrayList<>();

        registry = new PlayerVehicleRegistry();
        ownerSync = new InstallationVehicleOwnerSync(registry);
        installationVehicleService = new InstallationVehicleService(registry, ownerSync);
        sessionManager = new VehicleTransferSessionManager();
        consentService = new VehicleTransferConsentService(
                installationVehicleService,
                registry,
                sessionManager);
    }

    @AfterEach
    void tearDown() throws IOException {
        FactionManager.factions = previousFactions;
        if (tempDir != null) {
            Files.walk(tempDir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void acceptRequest_registersVehicleWhenValidationPasses() {
        UUID ownerUuid = UUID.randomUUID();
        UUID leaderUuid = UUID.randomUUID();
        List<String> ownerMessages = new ArrayList<>();

        Installation installation = new Installation(
                "port-1",
                "Harbour",
                InstallationKind.PORT,
                42,
                0,
                0,
                0L);
        InstallationHandler handler = mock(InstallationHandler.class);
        when(handler.getById("port-1")).thenReturn(installation);

        Faction faction = mock(Faction.class);
        Guild guild = mock(Guild.class);
        when(faction.getOrCreateMainGuild()).thenReturn(guild);
        when(faction.getLeader()).thenReturn("Leader");
        when(faction.getInstallationHandler()).thenReturn(handler);
        FactionManager.factions.add(faction);

        Player owner = playerStub(ownerUuid, "Owner", ownerMessages);
        Player leader = playerStub(leaderUuid, "Leader", new ArrayList<>());

        VehicleTransferConsentRequest request = new VehicleTransferConsentRequest(
                guild,
                "port-1",
                "Harbour",
                "vehicle-1",
                "ironclad",
                ownerUuid,
                leaderUuid);

        OwnerData ownerData = new OwnerData();
        ownerData.setOwner("player_Owner");
        InstallationVehicleService.VehicleBerthTarget vehicle =
                berthTarget("vehicle-1", "ironclad", locationAt(0, 64, 0), ownerData);

        VehicleTransferConsentService spiedService = spy(consentService);
        doReturn(vehicle).when(spiedService).resolveBerthTarget("vehicle-1");

        try (MockedStatic<RequestManager> requestManager = mockStatic(RequestManager.class);
                MockedStatic<FactionManager> factionManager = mockStatic(FactionManager.class);
                MockedStatic<InstallationBounds> bounds = mockStatic(InstallationBounds.class);
                MockedStatic<org.bukkit.Bukkit> bukkit = mockStatic(org.bukkit.Bukkit.class);
                MockedStatic<net.tfminecraft.simplefactions.SimpleFactions> sf =
                        mockStatic(net.tfminecraft.simplefactions.SimpleFactions.class)) {
            requestManager.when(() -> RequestManager.getRequest(owner)).thenReturn(request);
            bukkit.when(() -> org.bukkit.Bukkit.getPlayer(leaderUuid)).thenReturn(leader);
            factionManager.when(() -> FactionManager.getByLeader("Leader")).thenReturn(faction);
            bounds.when(() -> InstallationBounds.isWithinRadius(eq(installation), any())).thenReturn(true);
            bounds.when(() -> InstallationBounds.isCorrectProvince(eq(installation), any())).thenReturn(true);
            net.tfminecraft.simplefactions.SimpleFactions plugin =
                    mock(net.tfminecraft.simplefactions.SimpleFactions.class);
            sf.when(net.tfminecraft.simplefactions.SimpleFactions::getInstance).thenReturn(plugin);

            spiedService.acceptRequest(owner);

            PlayerVehicleRecord updated = registry.getByVehicleUuid("vehicle-1").orElseThrow();
            assertEquals(OwnershipMode.INSTALLATION, updated.getMode());
            assertEquals("player_Leader", ownerData.getOwner());
            assertEquals(
                    List.of(VehicleTransferMessages.berthSuccess(installation)),
                    ownerMessages);
            verify(plugin).saveVehicleRegistry();
        }
    }

    @Test
    void acceptRequest_refusesWhenTheVehicleChangedOwner() {
        UUID ownerUuid = UUID.randomUUID();
        UUID leaderUuid = UUID.randomUUID();
        List<String> ownerMessages = new ArrayList<>();

        Installation installation = new Installation(
                "port-1",
                "Harbour",
                InstallationKind.PORT,
                42,
                0,
                0,
                0L);
        InstallationHandler handler = mock(InstallationHandler.class);
        when(handler.getById("port-1")).thenReturn(installation);

        Faction faction = mock(Faction.class);
        Guild guild = mock(Guild.class);
        when(faction.getOrCreateMainGuild()).thenReturn(guild);
        when(faction.getLeader()).thenReturn("Leader");
        when(faction.getInstallationHandler()).thenReturn(handler);
        FactionManager.factions.add(faction);

        Player owner = playerStub(ownerUuid, "Owner", ownerMessages);
        Player leader = playerStub(leaderUuid, "Leader", new ArrayList<>());

        VehicleTransferConsentRequest request = new VehicleTransferConsentRequest(
                guild,
                "port-1",
                "Harbour",
                "vehicle-1",
                "ironclad",
                ownerUuid,
                leaderUuid);

        OwnerData ownerData = new OwnerData();
        ownerData.setOwner("player_Owner");
        InstallationVehicleService.VehicleBerthTarget vehicle =
                berthTarget("vehicle-1", "ironclad", locationAt(0, 64, 0), ownerData);

        VehicleTransferConsentService spiedService = spy(consentService);
        doReturn(vehicle).when(spiedService).resolveBerthTarget("vehicle-1");
        doReturn("player_Buyer").when(spiedService).currentOwnerEntry("vehicle-1");

        try (MockedStatic<RequestManager> requestManager = mockStatic(RequestManager.class);
                MockedStatic<FactionManager> factionManager = mockStatic(FactionManager.class);
                MockedStatic<InstallationBounds> bounds = mockStatic(InstallationBounds.class);
                MockedStatic<org.bukkit.Bukkit> bukkit = mockStatic(org.bukkit.Bukkit.class);
                MockedStatic<net.tfminecraft.simplefactions.SimpleFactions> sf =
                        mockStatic(net.tfminecraft.simplefactions.SimpleFactions.class)) {
            requestManager.when(() -> RequestManager.getRequest(owner)).thenReturn(request);
            bukkit.when(() -> org.bukkit.Bukkit.getPlayer(leaderUuid)).thenReturn(leader);
            factionManager.when(() -> FactionManager.getByLeader("Leader")).thenReturn(faction);
            bounds.when(() -> InstallationBounds.isWithinRadius(eq(installation), any())).thenReturn(true);
            bounds.when(() -> InstallationBounds.isCorrectProvince(eq(installation), any())).thenReturn(true);
            net.tfminecraft.simplefactions.SimpleFactions plugin =
                    mock(net.tfminecraft.simplefactions.SimpleFactions.class);
            sf.when(net.tfminecraft.simplefactions.SimpleFactions::getInstance).thenReturn(plugin);

            spiedService.acceptRequest(owner);

            assertEquals(java.util.Optional.empty(), registry.getByVehicleUuid("vehicle-1"));
            assertEquals("player_Owner", ownerData.getOwner());
            assertEquals(List.of(VehicleTransferMessages.ownerChanged()), ownerMessages);
        }
    }

    @Test
    void acceptRequest_rejectsWrongAcceptor() {
        UUID acceptorUuid = UUID.randomUUID();
        List<String> messages = new ArrayList<>();
        Player owner = playerStub(acceptorUuid, "Owner", messages);

        VehicleTransferConsentRequest request = new VehicleTransferConsentRequest(
                mock(Guild.class),
                "port-1",
                "Harbour",
                "vehicle-1",
                "ironclad",
                UUID.randomUUID(),
                UUID.randomUUID());

        try (MockedStatic<RequestManager> requestManager = mockStatic(RequestManager.class)) {
            requestManager.when(() -> RequestManager.getRequest(owner)).thenReturn(request);

            consentService.acceptRequest(owner);

            assertEquals(List.of("§cYou cannot accept this request."), messages);
        }
    }

    @Test
    void notifyExpired_sendsLockedMessageToOwner() {
        List<String> messages = new ArrayList<>();
        UUID ownerUuid = UUID.randomUUID();
        Player owner = playerStub(ownerUuid, "Owner", messages);
        VehicleTransferConsentRequest request = new VehicleTransferConsentRequest(
                mock(Guild.class),
                "port-1",
                "Harbour",
                "vehicle-1",
                "ironclad",
                ownerUuid,
                UUID.randomUUID());

        try (MockedStatic<org.bukkit.Bukkit> bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(() -> org.bukkit.Bukkit.getPlayer(request.getProposerLeaderUuid()))
                    .thenReturn(null);

            consentService.notifyExpired(request, owner);
        }

        assertEquals(List.of(VehicleTransferMessages.consentExpired()), messages);
    }

    private static Player playerStub(UUID uuid, String name, List<String> messages) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getUniqueId" -> uuid;
                        case "getName" -> name;
                        case "isOnline" -> true;
                        case "sendMessage" -> {
                            if (args[0] instanceof String message) {
                                messages.add(message);
                            }
                            yield null;
                        }
                        case "hashCode" -> uuid.hashCode();
                        case "equals" -> proxy == args[0];
                        case "toString" -> "PlayerStub[" + name + "]";
                        default -> defaultValue(method.getReturnType());
                    };
                });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == double.class) {
            return 0.0d;
        }
        if (returnType == float.class) {
            return 0.0f;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }

    private static InstallationVehicleService.VehicleBerthTarget berthTarget(
            String uuid,
            String typeId,
            Location location,
            OwnerData ownerData) {
        return new InstallationVehicleService.VehicleBerthTarget() {
            @Override
            public String getVehicleUuid() {
                return uuid;
            }

            @Override
            public String getVehicleTypeId() {
                return typeId;
            }

            @Override
            public Location getLocation() {
                return location;
            }

            @Override
            public OwnerData getOwnerData() {
                return ownerData;
            }
        };
    }

    private static Location locationAt(int x, int y, int z) {
        World world = mock(World.class);
        return new Location(world, x, y, z);
    }

    private Path writeInstallationsFixture() throws IOException {
        Path installationsYaml = tempDir.resolve("installations.yml");
        Files.writeString(installationsYaml, """
            consent-proximity-blocks: 20
            transfer-request-timeout-seconds: 60

            fort:
              radius: 80
              daily-upkeep: 50
              construction-time: 10
              slots:
                static_emplacements: 8
            port:
              radius: 80
              daily-upkeep: 20
              construction-time: 10
              slots:
                ships: 8
            airport:
              radius: 80
              daily-upkeep: 35
              construction-time: 10
              slots:
                aircraft: 10
            """);
        return installationsYaml;
    }

    private void writeVehiclesFixture() throws IOException {
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
            personal-slot-limit: 1

            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
              static_emplacements: {}
              aircraft: {}
            """);
        VehiclesConfigLoader.load(vehiclesYaml.toFile());
    }
}
