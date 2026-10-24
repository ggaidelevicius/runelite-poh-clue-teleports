package com.clueteleports;

import java.awt.Rectangle;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.ScriptEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class TeleportMapsGuidanceTest
{
	private static final String[] TREE = {"Tree Gnome Village", "Gnome Stronghold", "Battlefield of Khazard", "Grand Exchange"};
	private static final String[] XERIC = {"Xeric's Lookout", "Xeric's Glade", "Xeric's Inferno", "Xeric's Heart", "Xeric's Honour"};
	private static final String[] OBELISK = {"Level 13 Wilderness", "Level 19 Wilderness", "Level 27 Wilderness", "Level 35 Wilderness", "Level 44 Wilderness", "Level 50 Wilderness"};
	private static final JavaScriptCallback NEVER_RUN = event ->
	{
		throw new AssertionError("Guidance must never execute a map action");
	};

	@Test
	public void bothNativeInterfacesGuideTheExistingTravelWrapperAndClipTheMap()
	{
		for (int script : new int[]{217, 9142})
		{
			Fixture f = new Fixture(script, -19000, TREE);
			Node button = f.button("Tree Gnome Stronghold", "Travel");
			f.decorativeIcon();
			TeleportMapsGuidance.View view = f.read();

			assertNotNull(view);
			assertEquals(TeleportSource.SPIRIT_TREE, view.source);
			assertEquals(new Rectangle(102, 113, 510, 321), view.bounds);
			assertSame(button.widget, view.destination(destination("tree-gnome-stronghold")));
			assertNull(view.destination(destination("xeric-lookout")));
			assertNull(view.destination(null));
		}
	}

	@Test
	public void arbitraryStyledXericNamesFollowTheirRetainedNativeRows()
	{
		for (int script : new int[]{217, 9142})
		{
			Fixture f = new Fixture(script, -19400, XERIC);
			f.rows[4].text = "<col=ffffff>5</col>: <col=12abef>Raid: west</col>";
			Node button = f.button("<col=12abef>Raid: west</col>", "Travel");
			TeleportMapsGuidance.View view = f.read();

			assertNotNull(view);
			assertEquals(TeleportSource.MOUNTED_XERICS_TALISMAN, view.source);
			assertSame(button.widget, view.destination(destination("xeric-honour")));
			assertNull(view.destination(destination("xeric-heart")));
		}
	}

	@Test
	public void canonicalAndRegionalXericNamesRemainCompatible()
	{
		Fixture f = new Fixture(217, -19400, XERIC);
		Node button = f.button("Shayzien", "Travel");
		assertSame(button.widget, f.read().destination(destination("xeric-lookout")));
	}

	@Test
	public void bothNativeInterfacesRecognizeTheObeliskSetAction()
	{
		for (int script : new int[]{217, 9142})
		{
			Fixture f = new Fixture(script, -19600, OBELISK);
			Node button = f.button("Level 35 Wilderness", "Set");
			TeleportMapsGuidance.View view = f.read();
			assertEquals(TeleportSource.WILDERNESS_OBELISK, view.source);
			assertSame(button.widget, view.destination(destination("obelisk-35")));
			button.actions = new String[]{"Travel"};
			assertNull(view.destination(destination("obelisk-35")));
		}
	}

	@Test
	public void capturedNativeLocksOverrideAnErroneousTravelButton()
	{
		for (String locked : new String[]{"<str>Xeric's Honour</str>", "<col=5f5f5f>Xeric's Honour</col>"})
		{
			String[] originals = XERIC.clone();
			originals[4] = locked;
			Fixture f = new Fixture(217, -19400, originals);
			f.rows[4].text = "<col=ffffff>5</col>: A cosmetic unlocked-looking name";
			f.button("A cosmetic unlocked-looking name", "Travel");
			assertNull(f.read().destination(destination("xeric-honour")));
		}
	}

	@Test
	public void examineOnlyButtonsNeverBecomeRecommendedActions()
	{
		Fixture f = new Fixture(217, -19000, TREE);
		f.button("Grand Exchange", "Examine");
		assertNull(f.read().destination(destination("tree-grand-exchange")));
	}

	@Test
	public void malformedOrNativeCallbackArraysCannotBeUsedAsMapActions()
	{
		Object[][] invalid = {null, new Object[0], {217}, {NEVER_RUN, 1}, {null}};
		for (Object[] callback : invalid)
		{
			Fixture f = new Fixture(217, -19000, TREE);
			Node button = f.button("Grand Exchange", "Travel");
			button.onOp = callback;
			assertNull(f.read().destination(destination("tree-grand-exchange")));
		}
	}

	@Test
	public void duplicateMapNamesRejectTheViewEvenWhenOneButtonOnlyExamines()
	{
		Fixture f = new Fixture(217, -19400, XERIC);
		f.button("Same alias", "Travel");
		f.button("<col=777777>Same alias</col>", "Examine");
		assertNull(f.read());
	}

	@Test
	public void aCanonicalAliasOwnedByAnotherRenamedNativeRowCannotRedirectGuidance()
	{
		Fixture f = new Fixture(9142, -19400, XERIC);
		f.rows[0].text = "<col=ffffff>1</col>: My lookout";
		f.rows[3].text = "<col=ffffff>4</col>: Shayzien";
		Node heartButton = f.button("Shayzien", "Travel");
		TeleportMapsGuidance.View view = f.read();

		assertNull(view.destination(destination("xeric-lookout")));
		assertSame(heartButton.widget, view.destination(destination("xeric-heart")));
	}

	@Test
	public void duplicateNativeDestinationIdentitiesDoNotSelectAnArbitraryButton()
	{
		Fixture f = new Fixture(217, -19000,
			new String[]{"Tree Gnome Village", "Grand Exchange", "Grand Exchange"});
		f.button("Grand Exchange", "Travel");
		assertNull(f.read().destination(destination("tree-grand-exchange")));
	}

	@Test
	public void hiddenOrMissingRootsAndBackgroundsDoNotProduceAView()
	{
		Fixture f = new Fixture(217, -19000, TREE);
		f.root.hidden = true;
		assertNull(f.read());
		f.root.hidden = false;
		f.background.hidden = true;
		assertNull(f.read());
		f.background.hidden = false;
		f.root.bounds = new Rectangle(1, 1, 0, 20);
		assertNull(f.read());
		f.widgets.remove(f.root.id);
		assertNull(f.read());
	}

	@Test
	public void hiddenClippedAndNonGraphicButtonsAreNotClickableGuidance()
	{
		Fixture f = new Fixture(217, -19000, TREE);
		Node button = f.button("Grand Exchange", "Travel");
		TeleportMapsGuidance.View view = f.read();
		button.hidden = true;
		assertNull(view.destination(destination("tree-grand-exchange")));
		button.hidden = false;
		button.bounds = new Rectangle(99, 150, 100, 30);
		assertNull(view.destination(destination("tree-grand-exchange")));
		button.bounds = new Rectangle(150, 150, 100, 30);
		button.type = WidgetType.TEXT;
		assertNull(view.destination(destination("tree-grand-exchange")));
	}

	@Test
	public void sourceAndBackgroundIdentityMustAgreeWithCapturedNativeInputs()
	{
		Fixture f = new Fixture(217, -19400, TREE);
		assertNull(f.read());
		f.background.sprite = -12345;
		assertNull(f.read());
		f.background.sprite = -19000;
		assertNotNull(f.read());
		Node duplicate = f.decorativeIcon();
		duplicate.sprite = -19600;
		assertNull(f.read());
	}

	@Test
	public void onlyDirectChildrenOfTheExactNativeRootAreAccepted()
	{
		for (int variant = 0; variant < 4; variant++)
		{
			Fixture f = new Fixture(217, -19000, TREE);
			Node button = f.button("Grand Exchange", "Travel");
			if (variant == 0) button.parent = InterfaceID.MenuNew.INFINITE;
			if (variant == 1) button.id = InterfaceID.MenuNew.INFINITE;
			if (variant == 2) button.index++;
			if (variant == 3) f.root.children[1] = null;
			assertNull(f.read());
		}
	}

	@Test
	public void nativeCaptureIsMandatoryAndClosedMenusCannotKeepTheirBindings()
	{
		Fixture f = new Fixture(217, -19000, TREE);
		f.button("Grand Exchange", "Travel");
		TeleportMapsGuidance.View view = f.read();
		f.menus.clear();
		assertNull(f.read());
		assertNull(view.destination(destination("tree-grand-exchange")));
	}

	@Test
	public void replacingOneNativeRowInvalidatesTheWholePreviouslyReadView()
	{
		Fixture f = new Fixture(9142, -19000, TREE);
		f.button("Grand Exchange", "Travel");
		TeleportMapsGuidance.View view = f.read();
		Widget old = f.nativeList.children[0];
		f.nativeList.children[0] = new Node(f.nativeList.id, 0).widget;
		assertNull(view.destination(destination("tree-grand-exchange")));
		f.nativeList.children[0] = old;
		assertNull(f.read());
	}

	@Test
	public void replacingMapChildrenDoesNotReuseTheOldBackground()
	{
		Fixture f = new Fixture(217, -19000, TREE);
		f.button("Grand Exchange", "Travel");
		TeleportMapsGuidance.View view = f.read();
		Node replacement = new Node(f.root.id, 0);
		replacement.sprite = -19000;
		f.root.children[0] = replacement.widget;
		assertNull(view.destination(destination("tree-grand-exchange")));
	}

	@Test
	public void nullInputsAndAbsentInterfacesAreHarmless()
	{
		Fixture f = new Fixture(217, -19000, TREE);
		assertNull(TeleportMapsGuidance.read(null, f.menus));
		assertNull(TeleportMapsGuidance.read(f.client, null));
		f.widgets.clear();
		assertNull(f.read());
	}

	private static TeleportDestination destination(String id)
	{
		return TeleportCatalog.all().stream().filter(destination -> destination.getId().equals(id))
			.findFirst().orElseThrow(() -> new AssertionError("Missing destination " + id));
	}

	private static final class Fixture
	{
		private final NativeTeleportMenus menus = new NativeTeleportMenus();
		private final Map<Integer, Node> widgets = new HashMap<>();
		private final Node root;
		private final Node nativeList;
		private final Node background;
		private final Node[] rows;
		private final Client client = proxy(Client.class, (method, args) ->
		{
			if ("isClientThread".equals(method)) return true;
			if ("getWidget".equals(method))
			{
				Node node = widgets.get((Integer) args[0]);
				return node == null ? null : node.widget;
			}
			throw new AssertionError("Map guidance must only read widgets: " + method);
		});

		private Fixture(int script, int sprite, String[] originals)
		{
			int rootId = script == 217 ? InterfaceID.Menu.LJ_LAYER2 : InterfaceID.MenuNew.INFINITE;
			int listId = script == 217 ? InterfaceID.Menu.LJ_LAYER1 : InterfaceID.MenuNew.TEXT;
			root = new Node(rootId, -1);
			root.bounds = new Rectangle(100, 100, 512, 334);
			root.children = new Widget[0];
			widgets.put(rootId, root);
			nativeList = new Node(listId, -1);
			nativeList.hidden = true;
			rows = new Node[originals.length];
			nativeList.children = new Widget[rows.length];
			for (int index = 0; index < rows.length; index++)
			{
				rows[index] = new Node(listId, index);
				rows[index].text = "<col=ffffff>" + (index + 1) + "</col>: " + originals[index];
				rows[index].hidden = true;
				nativeList.children[index] = rows[index].widget;
			}
			widgets.put(listId, nativeList);
			background = append();
			background.sprite = sprite;
			background.bounds = new Rectangle(102, 113, 512, 334);
			String options = String.join("|", originals);
			Object[] arguments = script == 217 ? new Object[]{script, "Travel", options, 0}
				: new Object[]{script, "Travel", options, 0, 0, 0, 0};
			ScriptPreFired event = new ScriptPreFired(script);
			event.setScriptEvent(proxy(ScriptEvent.class, (method, args) ->
			{
				if ("getArguments".equals(method)) return arguments;
				throw new AssertionError("Native inputs must only be read: " + method);
			}));
			menus.onScriptPreFired(event);
			menus.onScriptPostFired(client, new ScriptPostFired(script));
		}

		private TeleportMapsGuidance.View read() { return TeleportMapsGuidance.read(client, menus); }

		private Node button(String name, String action)
		{
			Node node = append();
			node.name = "<col=ff9040>" + name + "</col>";
			node.actions = new String[]{action};
			node.onOp = new Object[]{NEVER_RUN};
			return node;
		}

		private Node decorativeIcon()
		{
			Node node = append();
			node.onOp = new Object[]{NEVER_RUN};
			node.actions = new String[]{"Travel"};
			return node;
		}

		private Node append()
		{
			Node node = new Node(root.id, root.children.length);
			root.children = Arrays.copyOf(root.children, root.children.length + 1);
			root.children[node.index] = node.widget;
			return node;
		}
	}

	private static final class Node
	{
		private int id;
		private int parent;
		private int index;
		private int type = WidgetType.GRAPHIC;
		private int sprite = -1;
		private String text = "";
		private String name = "";
		private boolean hidden;
		private Rectangle bounds = new Rectangle(150, 150, 100, 30);
		private Widget[] children;
		private String[] actions;
		private Object[] onOp;
		private final Widget widget = proxy(Widget.class, this::invoke);

		private Node(int id, int index)
		{
			this.id = id;
			this.parent = id;
			this.index = index;
		}

		private Object invoke(String method, Object[] args)
		{
			switch (method)
			{
				case "getId": return id;
				case "getParentId": return parent;
				case "getIndex": return index;
				case "getType": return type;
				case "getSpriteId": return sprite;
				case "getText": return text;
				case "getName": return name;
				case "isHidden": return hidden;
				case "getBounds": return bounds;
				case "getDynamicChildren": return children;
				case "getActions": return actions;
				case "getOnOpListener": return onOp;
				default: throw new AssertionError("Map widgets must never be mutated: " + method);
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
