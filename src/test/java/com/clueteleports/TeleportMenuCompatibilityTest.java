package com.clueteleports;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.ScriptEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Exercises original native input through the real availability reader. */
public class TeleportMenuCompatibilityTest
{
	private static final int LEGACY = 217;
	private static final int MODERN = 9142;
	private static final int[] SCRIPTS = {LEGACY, MODERN};
	private static final String[] BASELINE_LABELS = {
		"Tree Gnome Village", "Gnome Stronghold", "Battlefield of Khazard", "Grand Exchange"
	};
	private static final Set<String> BASELINE_IDS = Set.of("tree-tree-gnome-village",
		"tree-gnome-stronghold", "tree-battlefield-of-khazard", "tree-grand-exchange");

	@Test
	public void capturedOriginalsSurviveSpiritTreeMenuPrefixSuffixAndShortenedLabels()
	{
		for (int script : SCRIPTS)
		{
			Fixture fixture = new Fixture();
			fixture.capture(script, String.join("|", BASELINE_LABELS), BASELINE_LABELS);
			// Spirit Tree Menu's default two-column layout relocates keys. Its
			// large-font option also abbreviates the Khazard destination.
			fixture.rows.get(0).text = "<col=735a28>(1)</col> Tree Gnome Village";
			fixture.rows.get(1).text = "<col=735a28>(2)</col> Gnome Stronghold";
			fixture.rows.get(2).text = "B'field of Khazard <col=735a28>(3)</col>";
			fixture.rows.get(3).text = "Grand Exchange <col=735a28>(4)</col>";

			assertEquals(BASELINE_IDS, fixture.read().get());
			assertFalse(SpiritTreeReader.readOpenMenu(fixture.client).isPresent());
		}
	}

	@Test
	public void atlasCosmeticHidingPreservesUnlockedTreesAndOriginalGrayAndStrikeLocks()
	{
		String[] original = {"Tree Gnome Village", "Gnome Stronghold", "Battlefield of Khazard",
			"Grand Exchange", "<col=5f5f5f>Port Sarim</col>", "<str>Farming Guild</str>", "Hosidius"};
		Set<String> expected = Set.of("tree-tree-gnome-village", "tree-gnome-stronghold",
			"tree-battlefield-of-khazard", "tree-grand-exchange", "tree-hosidius");
		for (int script : SCRIPTS)
		{
			Fixture fixture = new Fixture();
			fixture.capture(script, String.join("|", original), original);
			for (MutableWidget row : fixture.rows)
			{
				row.hidden = true;
				row.text = "Custom white label";
				row.textColor = 0xffffff;
			}

			assertEquals(expected, fixture.read().get());
			// Selecting one native Travel button must not shrink the network.
			fixture.rows.get(3).hidden = false;
			assertEquals(expected, fixture.read().get());
			assertFalse(fixture.read().get().contains("tree-port-sarim"));
			assertFalse(fixture.read().get().contains("tree-farming-guild"));
		}
	}

	@Test
	public void teleportMapsHiddenCapturedContainerStillTeachesTheOriginalNetwork()
	{
		for (int script : SCRIPTS)
		{
			Fixture fixture = new Fixture();
			fixture.capture(script, String.join("|", BASELINE_LABELS), BASELINE_LABELS);
			fixture.container.hidden = true;

			assertTrue(fixture.rows.get(0).widget.isHidden());
			assertTrue(fixture.menus.hasCapturedMenu(fixture.container.widget));
			assertEquals(BASELINE_IDS, fixture.read().get());
		}
	}

	/**
	 * Teleport Maps at 25382eeebed8d5b88abfb9710d4040f986687caf builds its
	 * legacy map into MENU container 0, keeping the native list in child 3.
	 * Its Pre219 callback hides that list before the enclosing Post217 capture.
	 */
	@Test
	public void teleportMapsLegacyDeferredMapChildrenDoNotInvalidateNativeListCapture()
	{
		Fixture fixture = new Fixture();
		MutableWidget mapRoot = new MutableWidget(InterfaceID.MENU << 16, -1, "", null);
		fixture.beginCapture(LEGACY, String.join("|", BASELINE_LABELS));
		fixture.install(LEGACY, mapRoot, BASELINE_LABELS);
		fixture.container.hidden = true;
		Widget[] nativeRows = fixture.container.children;
		fixture.menus.onScriptPostFired(fixture.client, new ScriptPostFired(LEGACY));
		MenuObservations observations = new MenuObservations();
		assertFalse(observations.observe(TeleportSource.SPIRIT_TREE, fixture.read()).isPresent());

		// Deferred build appends sprites, map buttons, and labels to container
		// 0. It never appends them to, or reparents, the native list's rows.
		mapRoot.children = new Widget[]{
			new MutableWidget(mapRoot.component, 0, "", mapRoot).widget,
			new MutableWidget(mapRoot.component, 1, "Travel", mapRoot).widget,
			new MutableWidget(mapRoot.component, 2, "Tree Gnome Village", mapRoot).widget
		};

		assertSame(nativeRows, fixture.container.children);
		assertSame(fixture.container.widget, fixture.rows.get(0).widget.getParent());
		assertTrue(fixture.menus.hasCapturedMenu(fixture.container.widget));
		assertEquals(BASELINE_IDS, observations.observe(TeleportSource.SPIRIT_TREE, fixture.read()).get());
		assertFalse(SpiritTreeReader.readOpenMenu(fixture.client).isPresent());
	}

	/**
	 * AdventureLogComposite's modern Pre9142 callback first hides UNIVERSE.
	 * Its deferred build replaces only INFINITE's dynamic map children and
	 * hides CONTENT_SCROLL; the native TEXT row container is retained.
	 */
	@Test
	public void teleportMapsModernHiddenAncestorsAndMapRootRebuildPreserveNativeCapture()
	{
		Fixture fixture = new Fixture();
		MutableWidget mapRoot = new MutableWidget(InterfaceID.MenuNew.INFINITE, -1, "", null);
		MutableWidget universe = new MutableWidget(InterfaceID.MenuNew.UNIVERSE, -1, "", mapRoot);
		MutableWidget contentScroll = new MutableWidget(InterfaceID.MenuNew.CONTENT_SCROLL, -1, "", universe);
		fixture.beginCapture(MODERN, String.join("|", BASELINE_LABELS));
		fixture.install(MODERN, contentScroll, BASELINE_LABELS);
		universe.hidden = true;
		Widget[] nativeRows = fixture.container.children;
		assertFalse(fixture.container.hidden);
		assertFalse(fixture.rows.get(0).hidden);
		assertTrue(fixture.container.widget.isHidden());
		assertTrue(fixture.rows.get(0).widget.isHidden());
		fixture.menus.onScriptPostFired(fixture.client, new ScriptPostFired(MODERN));
		assertTrue(fixture.menus.hasCapturedMenu(fixture.container.widget));

		// A prior map's dynamic root children may be cleared without touching
		// static TEXT9 or its dynamic native destination rows.
		mapRoot.children = new Widget[]{new MutableWidget(mapRoot.component, 0, "Old map", mapRoot).widget};
		mapRoot.children = new Widget[0];
		assertEquals(BASELINE_IDS, fixture.read().get());
		mapRoot.children = new Widget[]{
			new MutableWidget(mapRoot.component, 0, "", mapRoot).widget,
			new MutableWidget(mapRoot.component, 1, "Travel", mapRoot).widget,
			new MutableWidget(mapRoot.component, 2, "Close", mapRoot).widget
		};
		contentScroll.hidden = true;
		universe.hidden = false;

		assertFalse(fixture.container.hidden);
		assertTrue(fixture.container.widget.isHidden());
		assertTrue(fixture.rows.get(0).widget.isHidden());
		assertSame(contentScroll.widget, fixture.container.widget.getParent());
		assertSame(nativeRows, fixture.container.children);
		assertTrue(fixture.menus.hasCapturedMenu(fixture.container.widget));
		MenuObservations observations = new MenuObservations();
		assertFalse(observations.observe(TeleportSource.SPIRIT_TREE, fixture.read()).isPresent());
		assertEquals(BASELINE_IDS, observations.observe(TeleportSource.SPIRIT_TREE, fixture.read()).get());
		assertFalse(SpiritTreeReader.readOpenMenu(fixture.client).isPresent());
	}

	@Test
	public void modernMenuOmittedEmptyEntriesAndFinalHiddenPlaceholderKeepTheirIndices()
	{
		String original = "Tree Gnome Village||Gnome Stronghold|||Battlefield of Khazard|Grand Exchange";
		Fixture omitted = new Fixture();
		omitted.capture(MODERN, original, BASELINE_LABELS);
		assertEquals(4, omitted.rows.size());
		assertEquals(BASELINE_IDS, omitted.read().get());

		Fixture trailing = new Fixture();
		trailing.capture(MODERN, original + "||", "Tree Gnome Village", "Gnome Stronghold",
			"Battlefield of Khazard", "Grand Exchange", "");
		assertEquals(5, trailing.rows.size());
		assertTrue(trailing.rows.get(4).widget.isHidden());
		assertTrue(trailing.menus.isCaptured(trailing.rows.get(4).widget));
		assertEquals(BASELINE_IDS, trailing.read().get());
	}

	@Test
	public void unknownDuplicateAndIncompleteOriginalsCannotReplaceTheCachedNetwork()
	{
		String[][] malformed = {
			{"Tree Gnome Village", "Gnome Stronghold", "Battlefield of Khazard", "Unknown new tree"},
			{"Tree Gnome Village", "Gnome Stronghold", "Battlefield of Khazard", "Grand Exchange", "Grand Exchange"},
			{"Tree Gnome Village", "Gnome Stronghold", "Grand Exchange", "Port Sarim"}
		};
		for (int script : SCRIPTS)
		{
			for (String[] original : malformed)
			{
				Fixture fixture = new Fixture();
				AvailabilityCache cache = learnedBaseline();
				fixture.capture(script, String.join("|", original), original);
				for (int index = 0; index < fixture.rows.size(); index++)
				{
					fixture.rows.get(index).text = BASELINE_LABELS[index % BASELINE_LABELS.length];
				}

				Optional<Set<String>> reading = fixture.read();
				assertFalse(reading.isPresent());
				reading.ifPresent(ids -> cache.record(TeleportSource.SPIRIT_TREE, ids));
				assertEquals(BASELINE_IDS, cache.snapshot().get(TeleportSource.SPIRIT_TREE));
			}
		}
	}

	@Test
	public void hidingWithoutOriginalCaptureDoesNotProduceAnAuthoritativeEmptyNetwork()
	{
		for (int script : SCRIPTS)
		{
			Fixture fixture = new Fixture();
			AvailabilityCache cache = learnedBaseline();
			fixture.install(script, BASELINE_LABELS);
			fixture.container.hidden = true;
			assertFalse(fixture.read().isPresent());
			fixture.container.hidden = false;
			for (MutableWidget row : fixture.rows)
			{
				row.hidden = true;
			}

			Optional<Set<String>> reading = fixture.read();
			assertFalse(reading.isPresent());
			reading.ifPresent(ids -> cache.record(TeleportSource.SPIRIT_TREE, ids));
			assertEquals(BASELINE_IDS, cache.snapshot().get(TeleportSource.SPIRIT_TREE));
		}
	}

	@Test
	public void hiddenCosmeticColorWithoutProvenanceCannotRemoveAPlantedTree()
	{
		for (int script : SCRIPTS)
		{
			Fixture fixture = new Fixture();
			fixture.install(script, "Tree Gnome Village", "Gnome Stronghold", "Battlefield of Khazard",
				"Grand Exchange", "<col=00ff00>Port Sarim</col>");
			fixture.rows.get(4).hidden = true;
			AvailabilityCache cache = learnedBaseline();
			cache.record(TeleportSource.SPIRIT_TREE, Set.of("tree-tree-gnome-village", "tree-gnome-stronghold",
				"tree-battlefield-of-khazard", "tree-grand-exchange", "tree-port-sarim"));
			Optional<Set<String>> reading = fixture.read();
			assertFalse(reading.isPresent());
			reading.ifPresent(ids -> cache.record(TeleportSource.SPIRIT_TREE, ids));
			assertTrue(cache.snapshot().get(TeleportSource.SPIRIT_TREE).contains("tree-port-sarim"));
		}
	}

	@Test
	public void replacingOneRowInvalidatesTheCapturedProvenanceForTheWholeMenu()
	{
		for (int script : SCRIPTS)
		{
			Fixture fixture = new Fixture();
			fixture.capture(script, String.join("|", BASELINE_LABELS), BASELINE_LABELS);
			MutableWidget replacement = new MutableWidget(fixture.container.component, 2,
				"Battlefield of Khazard", fixture.container);
			fixture.rows.set(2, replacement);
			fixture.container.children[2] = replacement.widget;
			fixture.container.hidden = true;

			assertFalse(fixture.menus.hasCapturedMenu(fixture.container.widget));
			assertFalse(fixture.menus.isCaptured(fixture.rows.get(0).widget));
			assertFalse(fixture.read().isPresent());
		}
	}

	@Test
	public void closedMenuCannotReuseOriginalsOrBindThemAgainFromALatePostEvent()
	{
		for (int script : SCRIPTS)
		{
			Fixture fixture = new Fixture();
			fixture.capture(script, String.join("|", BASELINE_LABELS), BASELINE_LABELS);
			fixture.menus.onWidgetClosed(script == LEGACY ? InterfaceID.MENU : InterfaceID.MENU_NEW);
			fixture.container.hidden = true;
			fixture.menus.onScriptPostFired(fixture.client, new ScriptPostFired(script));

			assertFalse(fixture.menus.hasCapturedMenu(fixture.container.widget));
			assertFalse(fixture.read().isPresent());
		}
	}

	private static AvailabilityCache learnedBaseline()
	{
		AvailabilityCache cache = new AvailabilityCache();
		cache.record(TeleportSource.SPIRIT_TREE, BASELINE_IDS);
		return cache;
	}

	private static final class Fixture
	{
		private final NativeTeleportMenus menus = new NativeTeleportMenus();
		private final Map<Integer, Widget> widgets = new HashMap<>();
		private final List<MutableWidget> rows = new ArrayList<>();
		private MutableWidget container;
		private final Client client = proxy(Client.class, (method, args) ->
		{
			if ("isClientThread".equals(method)) return true;
			if ("getWidget".equals(method) && args.length == 1) return widgets.get((Integer) args[0]);
			throw new UnsupportedOperationException(method);
		});

		private void capture(int script, String originalList, String... renderedRows)
		{
			beginCapture(script, originalList);
			install(script, renderedRows);
			menus.onScriptPostFired(client, new ScriptPostFired(script));
			assertTrue("native menu should bind exact row identities", menus.hasCapturedMenu(container.widget));
		}

		private void beginCapture(int script, String originalList)
		{
			Object[] arguments = script == LEGACY
				? new Object[]{script, "Spirit Tree Locations", originalList, 1}
				: new Object[]{script, 1, "Spirit Tree Locations", originalList, 0, 0, 0};
			ScriptPreFired pre = new ScriptPreFired(script);
			pre.setScriptEvent(proxy(ScriptEvent.class, (method, args) ->
			{
				if ("getArguments".equals(method)) return arguments;
				throw new UnsupportedOperationException(method);
			}));
			menus.onScriptPreFired(pre);
		}

		private void install(int script, String... labels)
		{
			install(script, null, labels);
		}

		private void install(int script, MutableWidget parent, String... labels)
		{
			int component = script == LEGACY ? InterfaceID.Menu.LJ_LAYER1 : InterfaceID.MenuNew.TEXT;
			widgets.clear();
			rows.clear();
			container = new MutableWidget(component, -1, "", parent);
			container.children = new Widget[labels.length];
			for (int index = 0; index < labels.length; index++)
			{
				String text = labels[index].isEmpty() ? ""
					: "<col=735a28>" + (index + 1) + "</col>: " + labels[index];
				MutableWidget row = new MutableWidget(component, index, text, container);
				row.hidden = labels[index].isEmpty();
				rows.add(row);
				container.children[index] = row.widget;
			}
			widgets.put(component, container.widget);
		}

		private Optional<Set<String>> read()
		{
			return SpiritTreeReader.readOpenMenu(client, menus);
		}
	}

	private static final class MutableWidget
	{
		private final int component;
		private final int index;
		private final MutableWidget parent;
		private String text;
		private int textColor = 0xffffff;
		private boolean hidden;
		private Widget[] children = new Widget[0];
		private final Widget widget;

		private MutableWidget(int component, int index, String text, MutableWidget parent)
		{
			this.component = component;
			this.index = index;
			this.text = text;
			this.parent = parent;
			widget = proxy(Widget.class, (method, args) ->
			{
				switch (method)
				{
					case "getId": return this.component;
					case "getIndex": return this.index;
					case "getText": return this.text;
					case "getTextColor": return textColor;
					case "isHidden": return hidden || this.parent != null && this.parent.widget.isHidden();
					case "getParent": return this.parent == null ? null : this.parent.widget;
					case "isSelfHidden": return hidden;
					case "getDynamicChildren": return children;
					default: throw new UnsupportedOperationException(method);
				}
			});
		}
	}

	@FunctionalInterface
	private interface MethodCall
	{
		Object invoke(String method, Object[] arguments);
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, MethodCall call)
	{
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
			(value, method, arguments) ->
			{
				if ("hashCode".equals(method.getName())) return System.identityHashCode(value);
				if ("equals".equals(method.getName())) return value == arguments[0];
				if ("toString".equals(method.getName())) return type.getSimpleName() + " test proxy";
				return call.invoke(method.getName(), arguments == null ? new Object[0] : arguments);
			});
	}
}
