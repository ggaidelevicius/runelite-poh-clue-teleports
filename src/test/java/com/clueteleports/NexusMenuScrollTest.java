package com.clueteleports;

import java.awt.Rectangle;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NexusMenuScrollTest
{
	private final Map<Integer, Node> widgets = new HashMap<>();
	private final NexusMenuScroll guide = new NexusMenuScroll();
	private boolean clientThread = true;
	private Node scroll;
	private Node universe;
	private final Client client = proxy(Client.class, (method, args) ->
	{
		switch (method)
		{
			case "isClientThread": return clientThread;
			case "getWidget":
				Node node = widgets.get((Integer) args[0]);
				return node == null ? null : node.widget;
			default: throw new AssertionError("Nexus scrolling permits no scripts, actions, or variable writes: " + method);
		}
	});

	@Before
	public void openMenu()
	{
		scroll = new Node(new Rectangle(200, 100, 250, 100));
		scroll.scrollHeight = 1000;
		universe = new Node(new Rectangle(100, 50, 500, 350));
		widgets.put(InterfaceID.TelenexusTeleport.SCROLLING1, scroll);
		widgets.put(InterfaceID.TelenexusTeleport.UNIVERSE, universe);
	}

	@Test
	public void scrollsTheMatchingNativeMainRowInTheSharedViewport()
	{
		rows(InterfaceID.TelenexusTeleport.TEXT1,
			row("1: Varrock", 0), row("<col=ffffff>2</col> :  Barrows", 600));

		guide.update(client, "nexus-barrows");

		assertEquals(562, scroll.scroll);
		assertEquals(1, scroll.scrollWrites);
		assertEquals(1, scroll.revalidations);
	}

	@Test
	public void alternateExtrasUseTheSameCumulativeScrollSpace()
	{
		rows(InterfaceID.TelenexusTeleport.TEXT1, row("1: Varrock", 0));
		rows(InterfaceID.TelenexusTeleport.EXTRAS, row("<col=ffffff>A</col> :  Grand Exchange", 720));
		scroll.scroll = 200;

		guide.update(client, "nexus-grand-exchange");

		assertEquals(682, scroll.scroll);
		assertEquals(1, scroll.scrollWrites);
	}

	@Test
	public void manualScrollingIsNotRepeatedlyOverridden()
	{
		rows(InterfaceID.TelenexusTeleport.TEXT1, row("1: Barrows", 600));
		guide.update(client, "nexus-barrows");
		scroll.scroll = 80;

		guide.update(client, "nexus-barrows");

		assertEquals(80, scroll.scroll);
		assertEquals(1, scroll.scrollWrites);
	}

	@Test
	public void anAlreadyVisibleRowConsumesTheOneShotWithoutScrolling()
	{
		rows(InterfaceID.TelenexusTeleport.TEXT1, row("1: Barrows", 40));
		guide.update(client, "nexus-barrows");
		scroll.scroll = 500;

		guide.update(client, "nexus-barrows");

		assertEquals(500, scroll.scroll);
		assertEquals(0, scroll.scrollWrites);
	}

	@Test
	public void changingRecommendationPermitsOneNewScroll()
	{
		rows(InterfaceID.TelenexusTeleport.TEXT1, row("1: Varrock", 0), row("2: Barrows", 600));
		guide.update(client, "nexus-barrows");

		guide.update(client, "nexus-varrock");

		assertEquals(0, scroll.scroll);
		assertEquals(2, scroll.scrollWrites);
	}

	@Test
	public void closingAndReopeningOrReplacingTheViewportPermitsScrollingAgain()
	{
		rows(InterfaceID.TelenexusTeleport.TEXT1, row("1: Barrows", 600));
		guide.update(client, "nexus-barrows");
		universe.hidden = true;
		guide.update(client, "nexus-barrows");
		universe.hidden = false;
		scroll.scroll = 0;
		guide.update(client, "nexus-barrows");
		assertEquals(2, scroll.scrollWrites);

		openMenu();
		rows(InterfaceID.TelenexusTeleport.TEXT1, row("1: Barrows", 600));
		guide.update(client, "nexus-barrows");
		assertEquals(562, scroll.scroll);
		assertEquals(1, scroll.scrollWrites);
	}

	@Test
	public void nullDifferentSourceAndExplicitResetClearTheOneShot()
	{
		rows(InterfaceID.TelenexusTeleport.TEXT1, row("1: Barrows", 600));
		guide.update(client, "nexus-barrows");
		guide.update(client, null);
		scroll.scroll = 0;
		guide.update(client, "nexus-barrows");
		guide.update(client, "fairy-bkr");
		scroll.scroll = 0;
		guide.update(client, "nexus-barrows");
		guide.reset();
		scroll.scroll = 0;
		guide.update(client, "nexus-barrows");

		assertEquals(4, scroll.scrollWrites);
	}

	@Test
	public void missingRowsAndIncompleteExtentsRemainEligibleForAHydratedRetry()
	{
		guide.update(client, "nexus-barrows");
		Node container = rows(InterfaceID.TelenexusTeleport.TEXT1);
		container.children = null;
		guide.update(client, "nexus-barrows");
		rows(InterfaceID.TelenexusTeleport.TEXT1, row("1: Barrows", 600));
		scroll.scrollHeight = 0;
		guide.update(client, "nexus-barrows");
		assertEquals(0, scroll.scrollWrites);
		scroll.scrollHeight = 1000;

		guide.update(client, "nexus-barrows");

		assertEquals(562, scroll.scroll);
		assertEquals(1, scroll.scrollWrites);
	}

	@Test
	public void hiddenStruckAndGreyRowsDoNotConsumeTheOneShot()
	{
		Node entry = row("<col=ffffff>1</col> :  Barrows", 600);
		Node container = rows(InterfaceID.TelenexusTeleport.TEXT1, entry);
		container.hidden = true;
		guide.update(client, "nexus-barrows");
		container.hidden = false;
		entry.hidden = true;
		guide.update(client, "nexus-barrows");
		entry.hidden = false;
		entry.text = "1: <str>Barrows</str>";
		guide.update(client, "nexus-barrows");
		entry.text = "1: <col=808080>Barrows</col>";
		guide.update(client, "nexus-barrows");
		entry.text = "1: Barrows";
		entry.colour = 0x7f7f7f;
		guide.update(client, "nexus-barrows");
		assertEquals(0, scroll.scrollWrites);
		entry.colour = 0xff981f;

		guide.update(client, "nexus-barrows");

		assertEquals(1, scroll.scrollWrites);
	}

	@Test
	public void unrelatedRowsUnknownDestinationsAndClosedInterfacesDoNotScroll()
	{
		rows(InterfaceID.TelenexusTeleport.TEXT1, row("1: Varrock", 600));
		guide.update(client, "nexus-barrows");
		guide.update(client, "nexus-unknown");
		guide.update(client, "portal-varrock");
		scroll.hidden = true;
		guide.update(client, "nexus-varrock");
		widgets.remove(InterfaceID.TelenexusTeleport.SCROLLING1);
		guide.update(client, "nexus-varrock");

		assertEquals(0, scroll.scrollWrites);
	}

	@Test
	public void clampsToTheBottomOfTheNativeExtent()
	{
		rows(InterfaceID.TelenexusTeleport.TEXT1, row("1: Barrows", 976));

		guide.update(client, "nexus-barrows");

		assertEquals(900, scroll.scroll);
	}

	@Test
	public void completeNativeScrollbarThumbAndCapsFollowTheActualScroll()
	{
		Node bar = nativeBar();
		rows(InterfaceID.TelenexusTeleport.TEXT1, row("1: Barrows", 600));

		guide.update(client, "nexus-barrows");

		assertEquals(52, bar.nodes[1].rectangle.y);
		assertEquals(52, bar.nodes[2].rectangle.y);
		assertEquals(57, bar.nodes[3].rectangle.y);
		assertEquals(1, bar.nodes[1].positionWrites);
		assertEquals(1, bar.nodes[2].revalidations);
		assertEquals(0, bar.nodes[0].positionWrites);
		assertEquals(0, bar.nodes[4].positionWrites);
		assertEquals(0, bar.nodes[5].positionWrites);
	}

	@Test
	public void thumbUsesTheActualPositionAfterRevalidationClampsTheViewport()
	{
		Node bar = nativeBar();
		rows(InterfaceID.TelenexusTeleport.TEXT1, row("1: Barrows", 976));
		scroll.heightAfterRevalidation = 150;

		guide.update(client, "nexus-barrows");

		assertEquals(850, scroll.scroll);
		assertEquals(74, bar.nodes[1].rectangle.y);
		assertEquals(79, bar.nodes[3].rectangle.y);
	}

	@Test
	public void incompleteOrCustomScrollbarChildrenAreLeftUntouched()
	{
		Node bar = nativeBar();
		bar.children[3] = null;
		rows(InterfaceID.TelenexusTeleport.TEXT1, row("1: Barrows", 600));
		guide.update(client, "nexus-barrows");
		assertEquals(562, scroll.scroll);
		assertEquals(0, bar.nodes[1].positionWrites);
		guide.reset();
		scroll.scroll = 0;
		bar = nativeBar();
		bar.nodes[2].type = WidgetType.TEXT;

		guide.update(client, "nexus-barrows");

		assertEquals(562, scroll.scroll);
		assertEquals(0, bar.nodes[1].positionWrites);
		assertEquals(0, bar.nodes[3].positionWrites);
	}

	@Test(expected = IllegalStateException.class)
	public void refusesWidgetAccessOutsideTheClientThread()
	{
		clientThread = false;
		guide.update(client, "nexus-barrows");
	}

	private Node row(String text, int y)
	{
		Node row = new Node(new Rectangle(30, y, 220, 24));
		row.parent = scroll;
		row.text = text;
		return row;
	}

	private Node rows(int component, Node... rows)
	{
		Node container = new Node(new Rectangle(0, 0, 250, 1000));
		container.children = new Widget[rows.length];
		for (int index = 0; index < rows.length; index++) container.children[index] = rows[index].widget;
		widgets.put(component, container);
		return container;
	}

	private Node nativeBar()
	{
		Node bar = new Node(new Rectangle(460, 100, 16, 100));
		bar.nodes = new Node[6];
		bar.children = new Widget[6];
		for (int index = 0; index < 6; index++)
		{
			int height = index == 0 ? 68 : index == 1 ? 10 : index >= 4 ? 16 : 5;
			int y = index == 4 ? 0 : index == 5 ? 84 : index == 3 ? 21 : 16;
			Node child = new Node(new Rectangle(0, y, 16, height));
			child.parent = bar;
			child.parentId = InterfaceID.TelenexusTeleport.SCROLLBAR1;
			child.index = index;
			child.type = WidgetType.GRAPHIC;
			bar.nodes[index] = child;
			bar.children[index] = child.widget;
		}
		widgets.put(InterfaceID.TelenexusTeleport.SCROLLBAR1, bar);
		return bar;
	}

	private static final class Node
	{
		private final Rectangle rectangle;
		private Node parent;
		private Node[] nodes;
		private Widget[] children;
		private boolean hidden;
		private String text = "";
		private int colour = 0xff981f;
		private int scroll;
		private int scrollHeight;
		private int scrollWrites;
		private int positionWrites;
		private int revalidations;
		private int heightAfterRevalidation = -1;
		private int type = WidgetType.TEXT;
		private int parentId = -1;
		private int index = -1;
		private final Widget widget = proxy(Widget.class, this::invoke);

		private Node(Rectangle rectangle) { this.rectangle = rectangle; }

		private Object invoke(String method, Object[] args)
		{
			switch (method)
			{
				case "isHidden": return hidden;
				case "getText": return text;
				case "getTextColor": return colour;
				case "getDynamicChildren": return children;
				case "getScrollY": return scroll;
				case "getScrollHeight": return scrollHeight;
				case "getType": return type;
				case "getParentId": return parentId;
				case "getIndex": return index;
				case "getBounds":
					Rectangle bounds = new Rectangle(rectangle);
					if (parent != null) bounds.translate(parent.rectangle.x, parent.rectangle.y - parent.scroll);
					return bounds;
				case "setScrollY": scroll = (Integer) args[0]; scrollWrites++; return widget;
				case "setOriginalY": rectangle.y = (Integer) args[0]; positionWrites++; return widget;
				case "revalidate": revalidations++; return null;
				case "revalidateScroll":
					revalidations++;
					if (heightAfterRevalidation > 0) rectangle.height = heightAfterRevalidation;
					scroll = Math.max(0, Math.min(scroll, scrollHeight - rectangle.height));
					return null;
				default: throw new AssertionError("Unexpected widget operation: " + method);
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
