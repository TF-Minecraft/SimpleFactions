package net.tfminecraft.simplefactions.api;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The realm comes from TFMCWeb, loaded beside SimpleFactions the way Bukkit shares plugin classes. */
class GatewayClientRealmTest {
	@TempDir
	Path folder;
	private URLClassLoader loader;
	private Class<?> client;
	private Class<?> web;

	@BeforeEach
	void setup() throws Exception {
		Path source = folder.resolve("TFMCWeb.java");
		Files.writeString(source, """
				package net.tfminecraft.tfmcweb;
				public final class TFMCWeb {
				    public static String realm = " dev ";
				    public static RuntimeException failure;
				    public static String getRealmId() { if (failure != null) throw failure; return realm; }
				}
				""");
		assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", folder.toString(), source.toString()));
		loader = new URLClassLoader(new URL[] {folder.toUri().toURL(),
				GatewayClient.class.getProtectionDomain().getCodeSource().getLocation()}, getClass().getClassLoader()) {
			@Override
			protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
				if (name.startsWith("net.tfminecraft.tfmcweb.") || name.startsWith(GatewayClient.class.getName())) {
					Class<?> found = findLoadedClass(name);
					return found != null ? found : findClass(name);
				}
				return super.loadClass(name, resolve);
			}
		};
		client = loader.loadClass(GatewayClient.class.getName());
		web = loader.loadClass("net.tfminecraft.tfmcweb.TFMCWeb");
	}

	@AfterEach
	void close() throws Exception {
		loader.close();
	}

	private Object realm() throws Exception {
		return client.getMethod("realmId").invoke(null);
	}

	@Test
	void theRealmIsTrimmedAndAMissingOrFailingOneIsNull() throws Exception {
		assertEquals("dev", realm());
		for (String missing : new String[] {null, " "}) {
			web.getField("realm").set(null, missing);
			assertNull(realm());
		}
		web.getField("failure").set(null, new IllegalStateException("disabled"));
		assertNull(realm());
	}

	@Test
	void withoutTFMCWebThereIsNoRealm() {
		assertNull(GatewayClient.realmId());
	}
}
