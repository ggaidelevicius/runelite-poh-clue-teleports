package com.clueteleports;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.ScriptEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NativeTeleportMenusTest
{
	private static final int LEGACY = 217;
	private static final int MODERN = 9142;
	private final NativeTeleportMenus menus = new NativeTeleportMenus();
	private final Map<Integer, Node> widgets = new HashMap<>();
	private boolean clientThread = true;
	private final Client client = proxy(Client.class, (method, args) ->
	{
		if ("isClientThread".equals(method)) return clientThread;
		if ("getWidget".equals(method))
		{
			Node node = widgets.get((Integer) args[0]);
			return node == null ? null : node.widget;
		}
		throw new AssertionError("Capture must only read native widgets: " + method);
	});

	@Test
	public void legacyInputsSurviveCosmeticRenamesAndPreserveOriginalLocks()
	{
		Node container = menu(LEGACY, "My custom first choice", "My custom second choice");
		Object[] arguments = args(LEGACY, "Digsite|<str>Fossil Island</str>");
		menus.onScriptPreFired(pre(LEGACY, arguments));
		arguments[2] = "Replaced argument after the original event";
		post(LEGACY);

		assertTrue(menus.hasCapturedMenu(container.widget));
		assertTrue(menus.isCaptured(container.nodes[0].widget));
		assertEquals("Digsite", menus.text(container.nodes[0].widget));
		assertEquals("<str>Fossil Island</str>", menus.text(container.nodes[1].widget));
		container.nodes[0].text = "Renamed again";
		assertEquals("Digsite", menus.text(container.nodes[0].widget));
	}

	@Test
	public void modernInputsPermitInterleavedIntegerArguments()
	{
		Node container = menu(MODERN, "Shayzien alias", "Hosidius alias");
		menus.onScriptPreFired(pre(MODERN,
			new Object[]{MODERN, 1, "Choose a destination", 2, "Xeric's Lookout|Xeric's Glade", 3, 4}));
		post(MODERN);

		assertEquals("Xeric's Lookout", menus.text(container.nodes[0].widget));
		assertEquals("Xeric's Glade", menus.text(container.nodes[1].widget));
	}

	@Test
	public void hiddenNativeContainerAndNonemptyRowsRetainTheirOriginalEvidence()
	{
		Node container = menu(LEGACY, "Cosmetic first choice", "Cosmetic locked choice");
		container.hidden = true;
		container.nodes[0].hidden = true;
		container.nodes[1].hidden = true;
		bind(LEGACY, "Tree Gnome Village|<col=808080>Prifddinas</col>");

		assertTrue(menus.hasCapturedMenu(container.widget));
		assertTrue(menus.isCaptured(container.nodes[1].widget));
		assertEquals("<col=808080>Prifddinas</col>", menus.text(container.nodes[1].widget));
	}

	@Test
	public void modernEmptyInteriorInputsDoNotConsumeNativeRowIndices()
	{
		Node container = menu(MODERN, "Custom village", "Custom stronghold");
		bind(MODERN, "|Tree Gnome Village||Gnome Stronghold");

		assertEquals("Tree Gnome Village", menus.text(container.nodes[0].widget));
		assertEquals("Gnome Stronghold", menus.text(container.nodes[1].widget));
		assertTrue(menus.hasCapturedMenu(container.widget));
	}

	@Test
	public void modernTrailingEmptyInputsLeaveOneHiddenBlankPlaceholder()
	{
		Node container = menu(MODERN, "Custom village", "Custom stronghold", "");
		container.nodes[2].hidden = true;
		bind(MODERN, "Tree Gnome Village||Gnome Stronghold||");

		assertEquals("Gnome Stronghold", menus.text(container.nodes[1].widget));
		assertEquals("", menus.text(container.nodes[2].widget));
		assertTrue(menus.isCaptured(container.nodes[2].widget));
	}

	@Test
	public void legacyEmptyInputsKeepTheirIndicesInsteadOfShiftingFollowingChoices()
	{
		Node container = menu(LEGACY, "Custom village", "", "Custom stronghold");
		container.nodes[1].hidden = true;
		bind(LEGACY, "Tree Gnome Village||Gnome Stronghold");

		assertEquals("", menus.text(container.nodes[1].widget));
		assertEquals("Gnome Stronghold", menus.text(container.nodes[2].widget));
	}

	@Test
	public void visibleEmptyPlaceholdersAndMissingNonemptyLabelsRejectTheWholeBinding()
	{
		Node container = menu(MODERN, "Custom village", "");
		bind(MODERN, "Tree Gnome Village|");
		assertFalse(menus.hasCapturedMenu(container.widget));
		container.nodes[1].hidden = true;
		container.nodes[0].text = " ";
		bind(MODERN, "Tree Gnome Village|");
		assertFalse(menus.isCaptured(container.nodes[1].widget));
	}

	@Test
	public void malformedRootArgumentsClearEarlierEvidence()
	{
		Object[][] malformed = {
			null,
			{LEGACY, "Title", "Digsite"},
			{MODERN, "Title", "Digsite", 1},
			{LEGACY, "Title", "Digsite", 1L},
			{LEGACY, "Title", "Digsite", null},
			{LEGACY, "Title", "Digsite", "Third string"},
			{LEGACY, "Title", 1, 2},
			{LEGACY, "Title", "Digsite", new int[]{1}}
		};
		for (Object[] arguments : malformed)
		{
			Node container = menu(LEGACY, "Visible alias");
			bind(LEGACY, "Digsite");
			assertTrue(menus.isCaptured(container.nodes[0].widget));
			menus.onScriptPreFired(pre(LEGACY, arguments));
			post(LEGACY);
			assertFalse(menus.hasCapturedMenu(container.widget));
			assertEquals("Visible alias", menus.text(container.nodes[0].widget));
		}
	}

	@Test
	public void nestedGenericInvocationHasNoRootArgumentsAndCannotReuseOuterInputs()
	{
		Node container = menu(LEGACY, "Visible alias");
		menus.onScriptPreFired(pre(LEGACY, args(LEGACY, "Digsite")));
		menus.onScriptPreFired(new ScriptPreFired(LEGACY));
		post(LEGACY);
		post(LEGACY);

		assertFalse(menus.isCaptured(container.nodes[0].widget));
	}

	@Test
	public void unrelatedNestedScriptsDoNotDiscardAValidRootInitialization()
	{
		Node container = menu(LEGACY, "Visible alias");
		menus.onScriptPreFired(pre(LEGACY, args(LEGACY, "Digsite")));
		menus.onScriptPreFired(new ScriptPreFired(218));
		post(218);
		post(LEGACY);

		assertEquals("Digsite", menus.text(container.nodes[0].widget));
	}

	@Test
	public void aNewGenericInitializationClearsOtherMenuGroupsAndIgnoresTheirLatePosts()
	{
		Node old = menu(LEGACY, "Old visible alias");
		bind(LEGACY, "Digsite");
		Node current = menu(MODERN, "New visible alias");
		menus.onScriptPreFired(pre(MODERN, args(MODERN, "Tree Gnome Village")));
		assertFalse(menus.isCaptured(old.nodes[0].widget));
		post(LEGACY);
		assertFalse(menus.isCaptured(current.nodes[0].widget));
		post(MODERN);

		assertEquals("Tree Gnome Village", menus.text(current.nodes[0].widget));
		assertEquals("Old visible alias", menus.text(old.nodes[0].widget));
	}

	@Test
	public void childCountNullIndicesAndComponentIdsMustAllMatch()
	{
		Node container = menu(LEGACY, "Alias", "Unexpected extra row");
		bind(LEGACY, "Digsite");
		assertFalse(menus.hasCapturedMenu(container.widget));
		container = menu(LEGACY, "Alias");
		container.children[0] = null;
		bind(LEGACY, "Digsite");
		assertFalse(menus.hasCapturedMenu(container.widget));
		container = menu(LEGACY, "Alias");
		container.nodes[0].index = 1;
		bind(LEGACY, "Digsite");
		assertFalse(menus.hasCapturedMenu(container.widget));
		container.nodes[0].index = 0;
		container.nodes[0].id = InterfaceID.MenuNew.TEXT;
		bind(LEGACY, "Digsite");
		assertFalse(menus.hasCapturedMenu(container.widget));
		container = menu(LEGACY, "Alias");
		container.id = 0;
		bind(LEGACY, "Digsite");
		assertFalse(menus.hasCapturedMenu(container.widget));
	}

	@Test
	public void postBindingIsOneTimeAndDoesNotRebindAnInitiallyIncompleteMenu()
	{
		Node container = menu(LEGACY, "Alias");
		Widget original = container.children[0];
		container.children[0] = null;
		bind(LEGACY, "Digsite");
		container.children[0] = original;
		post(LEGACY);

		assertFalse(menus.hasCapturedMenu(container.widget));
		bind(LEGACY, "Digsite");
		assertTrue(menus.hasCapturedMenu(container.widget));
	}

	@Test
	public void replacingOneRowInvalidatesEveryOriginalAndRestoringItCannotResurrectTheSnapshot()
	{
		Node container = menu(LEGACY, "First alias", "Second alias");
		bind(LEGACY, "Digsite|Fossil Island");
		Widget original = container.children[1];
		Node replacement = new Node(container.id, 1, "Replacement alias");
		container.children[1] = replacement.widget;

		assertFalse(menus.isCaptured(replacement.widget));
		assertEquals("First alias", menus.text(container.nodes[0].widget));
		assertFalse(menus.isCaptured(container.nodes[0].widget));
		container.children[1] = original;
		assertFalse(menus.hasCapturedMenu(container.widget));
		assertEquals("Second alias", menus.text(original));
	}

	@Test
	public void nullDuplicateAndChangedCountInvalidateTheWholeCapturedIdentitySet()
	{
		for (int variant = 0; variant < 3; variant++)
		{
			Node container = menu(LEGACY, "First alias", "Second alias");
			bind(LEGACY, "Digsite|Fossil Island");
			container.children = variant == 0 ? new Widget[]{container.children[0], null}
				: variant == 1 ? new Widget[]{container.children[0], container.children[0]}
				: new Widget[]{container.children[0]};
			assertFalse(menus.hasCapturedMenu(container.widget));
			assertFalse(menus.isCaptured(container.nodes[0].widget));
		}
	}

	@Test
	public void reorderingTheSameWidgetObjectsRetainsTheirOriginalIdentities()
	{
		Node container = menu(LEGACY, "First alias", "Second alias");
		bind(LEGACY, "Digsite|Fossil Island");
		container.children = new Widget[]{container.nodes[1].widget, container.nodes[0].widget};

		assertTrue(menus.hasCapturedMenu(container.widget));
		assertEquals("Fossil Island", menus.text(container.children[0]));
		assertEquals("Digsite", menus.text(container.children[1]));
	}

	@Test
	public void aReplacementContainerWithTheSameComponentIdHasNoInheritedProvenance()
	{
		menu(LEGACY, "Old alias");
		bind(LEGACY, "Digsite");
		Node replacement = menu(LEGACY, "New alias");

		assertFalse(menus.hasCapturedMenu(replacement.widget));
		assertFalse(menus.isCaptured(replacement.nodes[0].widget));
		assertEquals("New alias", menus.text(replacement.nodes[0].widget));
	}

	@Test
	public void menuLoadCloseAndExplicitLifecycleClearDiscardActiveAndPendingInputs()
	{
		for (int action = 0; action < 3; action++)
		{
			Node container = menu(LEGACY, "Alias");
			bind(LEGACY, "Digsite");
			if (action == 0) menus.onWidgetLoaded(InterfaceID.MENU_NEW);
			else if (action == 1) menus.onWidgetClosed(InterfaceID.MENU);
			else menus.clear();
			assertFalse(menus.hasCapturedMenu(container.widget));
			menus.onScriptPreFired(pre(LEGACY, args(LEGACY, "Digsite")));
			if (action == 0) menus.onWidgetLoaded(InterfaceID.MENU);
			else if (action == 1) menus.onWidgetClosed(InterfaceID.MENU_NEW);
			else menus.clear();
			post(LEGACY);
			assertFalse(menus.isCaptured(container.nodes[0].widget));
		}
	}

	@Test
	public void unrelatedWidgetLifecycleAndScriptEventsLeaveCurrentEvidenceIntact()
	{
		Node container = menu(LEGACY, "Alias");
		bind(LEGACY, "Digsite");
		menus.onWidgetLoaded(InterfaceID.POH_JEWELLERY_BOX);
		menus.onWidgetClosed(InterfaceID.TELENEXUS_TELEPORT);
		menus.onScriptPreFired(new ScriptPreFired(1690));
		post(1690);

		assertTrue(menus.hasCapturedMenu(container.widget));
		assertEquals("Digsite", menus.text(container.nodes[0].widget));
		assertNull(menus.text(null));
		assertFalse(menus.isCaptured(null));
	}

	@Test(expected = IllegalStateException.class)
	public void pairingRequiresTheClientThread()
	{
		menu(LEGACY, "Alias");
		menus.onScriptPreFired(pre(LEGACY, args(LEGACY, "Digsite")));
		clientThread = false;
		post(LEGACY);
	}

	private void bind(int script, String options)
	{
		menus.onScriptPreFired(pre(script, args(script, options)));
		post(script);
	}

	private void post(int script) { menus.onScriptPostFired(client, new ScriptPostFired(script)); }

	private static Object[] args(int script, String options)
	{
		return script == LEGACY ? new Object[]{script, "Choose destination", options, 1}
			: new Object[]{script, "Choose destination", options, 1, 0, 0, 0};
	}

	private static ScriptPreFired pre(int script, Object[] arguments)
	{
		ScriptPreFired event = new ScriptPreFired(script);
		event.setScriptEvent(proxy(ScriptEvent.class, (method, args) ->
		{
			if ("getArguments".equals(method)) return arguments;
			throw new AssertionError("Native script inputs must only be read: " + method);
		}));
		return event;
	}

	private Node menu(int script, String... visible)
	{
		int component = script == LEGACY ? InterfaceID.Menu.LJ_LAYER1 : InterfaceID.MenuNew.TEXT;
		Node container = new Node(component, -1, "");
		container.nodes = new Node[visible.length];
		container.children = new Widget[visible.length];
		for (int index = 0; index < visible.length; index++)
		{
			Node row = new Node(component, index, visible[index]);
			container.nodes[index] = row;
			container.children[index] = row.widget;
		}
		widgets.put(component, container);
		return container;
	}

	private static final class Node
	{
		private int id;
		private int index;
		private String text;
		private boolean hidden;
		private Node[] nodes;
		private Widget[] children;
		private final Widget widget = proxy(Widget.class, this::invoke);

		private Node(int id, int index, String text)
		{
			this.id = id;
			this.index = index;
			this.text = text;
		}

		private Object invoke(String method, Object[] args)
		{
			switch (method)
			{
				case "getId": return id;
				case "getIndex": return index;
				case "getText": return text;
				case "isHidden": return hidden;
				case "getDynamicChildren": return children;
				default: throw new AssertionError("Native menu widgets must only be read: " + method);
			}
		}
	}

	private interface Handler { Object invoke(String method, Object[] args); }

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, Handler handler)
	{
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
			(instance, method, args) -> handler.invoke(method.getName(), args));
	}
}
