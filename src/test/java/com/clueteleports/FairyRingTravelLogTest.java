package com.clueteleports;

import java.awt.Rectangle;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class FairyRingTravelLogTest
{
	private final Map<Integer, Node> widgets = new HashMap<>();
	private final FairyRingTravelLog guide = new FairyRingTravelLog();
	private boolean clientThread = true;
	private int savedScroll;
	private int savedScrollWrites;
	private Node contents;
	private Node confirm;
	private final Client client = proxy(Client.class, (method, args) ->
	{
		switch (method)
		{
			case "isClientThread": return clientThread;
			case "getVarcIntValue":
				assertEquals(VarClientID.FAIRYRINGS_SCROLLPOS, args[0]);
				return savedScroll;
			case "setVarcIntValue":
				assertEquals(VarClientID.FAIRYRINGS_SCROLLPOS, args[0]);
				savedScroll = (Integer) args[1];
				savedScrollWrites++;
				return null;
			case "getWidget":
				Node node = widgets.get((Integer) args[0]);
				return node == null ? null : node.widget;
			default: throw new AssertionError("Only local widget reads are permitted: " + method);
		}
	});

	@Before
	public void openInterface()
	{
		contents = new Node(new Rectangle(300, 100, 200, 100));
		contents.scrollHeight = 1000;
		confirm = new Node(new Rectangle(100, 250, 200, 25));
		widgets.put(InterfaceID.FairyringsLog.CONTENTS, contents);
		widgets.put(InterfaceID.Fairyrings.CONFIRM, confirm);
	}

	@Test
	public void scrollsTheNativeDestinationDescriptionAndRevalidatesWithoutOperatingIt()
	{
		Node row = row(InterfaceID.FairyringsLog.BKR, 600);
		row.text = "Morytania: Mort Myre Swamp";

		guide.update(client, "fairy-bkr");

		assertEquals(560, contents.scroll);
		assertEquals(1, contents.scrollWrites);
		assertEquals(1, contents.revalidations);
		assertSame(row.widget, FairyRingTravelLog.readableEntry(client, "fairy-bkr"));
	}

	@Test
	public void nativeRebuildRestoresTheActualClampedOneShotPosition()
	{
		row(InterfaceID.FairyringsLog.BKR, 980);
		contents.heightAfterRevalidation = 150;

		guide.update(client, "fairy-bkr");

		// Revalidation enlarges the viewport, clamping the requested 900 to 850.
		assertEquals(850, contents.scroll);
		assertEquals(850, savedScroll);
		assertEquals(1, savedScrollWrites);
		contents.scroll = 0;
		nativeRebuild();
		assertEquals(850, contents.scroll);
		guide.update(client, "fairy-bkr");
		assertEquals(1, contents.scrollWrites);
		assertEquals(1, savedScrollWrites);
	}

	@Test
	public void anAlreadyVisibleRowSynchronizesTheStaleSavedNativePosition()
	{
		contents.scroll = 300;
		row(InterfaceID.FairyringsLog.BKR, 340);
		assertEquals(0, savedScroll);

		guide.update(client, "fairy-bkr");

		assertEquals(0, contents.scrollWrites);
		assertEquals(300, savedScroll);
		nativeRebuild();
		assertEquals(300, contents.scroll);
		guide.update(client, "fairy-bkr");
		assertEquals(1, savedScrollWrites);
	}

	@Test
	public void laterManualScrollingAndItsSavedNativePositionAreNotOverwritten()
	{
		row(InterfaceID.FairyringsLog.BKR, 600);
		guide.update(client, "fairy-bkr");
		assertEquals(560, savedScroll);
		assertEquals(1, savedScrollWrites);

		// Native wheel/drag handling changes both the widget and its saved varc.
		contents.scroll = 80;
		savedScroll = 80;
		guide.update(client, "fairy-bkr");
		nativeRebuild();
		guide.update(client, "fairy-bkr");

		assertEquals(80, contents.scroll);
		assertEquals(80, savedScroll);
		assertEquals(1, contents.scrollWrites);
		assertEquals(1, savedScrollWrites);
	}

	@Test
	public void usesTheRowsCurrentSortedPositionAndExistingScroll()
	{
		contents.scroll = 200;
		row(InterfaceID.FairyringsLog.AIP, 720);

		guide.update(client, "fairy-aip");

		assertEquals(680, contents.scroll);
	}

	@Test
	public void respectsManualScrollingAfterTheInitialPositioning()
	{
		row(InterfaceID.FairyringsLog.BKR, 600);
		guide.update(client, "fairy-bkr");
		contents.scroll = 0;

		guide.update(client, "fairy-bkr");

		assertEquals(0, contents.scroll);
		assertEquals(1, contents.scrollWrites);
	}

	@Test
	public void anAlreadyVisibleEntryDoesNotScrollOrLaterFightManualScrolling()
	{
		row(InterfaceID.FairyringsLog.BKR, 40);
		guide.update(client, "fairy-bkr");
		assertEquals(0, contents.scrollWrites);
		contents.scroll = 400;

		guide.update(client, "fairy-bkr");

		assertEquals(400, contents.scroll);
		assertEquals(0, contents.scrollWrites);
	}

	@Test
	public void changingTheRecommendationPermitsOneMoreScroll()
	{
		row(InterfaceID.FairyringsLog.BKR, 600);
		row(InterfaceID.FairyringsLog.AIP, 20);
		guide.update(client, "fairy-bkr");

		guide.update(client, "fairy-aip");

		assertEquals(0, contents.scroll);
		assertEquals(2, contents.scrollWrites);
	}

	@Test
	public void closingAndReopeningTheSelectorPermitsAnotherScroll()
	{
		row(InterfaceID.FairyringsLog.BKR, 600);
		guide.update(client, "fairy-bkr");
		confirm.hidden = true;
		guide.update(client, "fairy-bkr");
		confirm.hidden = false;
		contents.scroll = 0;

		guide.update(client, "fairy-bkr");

		assertEquals(560, contents.scroll);
		assertEquals(2, contents.scrollWrites);
	}

	@Test
	public void aNewNativeContentsWidgetIsTreatedAsANewOpening()
	{
		row(InterfaceID.FairyringsLog.BKR, 600);
		guide.update(client, "fairy-bkr");
		openInterface();
		row(InterfaceID.FairyringsLog.BKR, 600);

		guide.update(client, "fairy-bkr");

		assertEquals(560, contents.scroll);
		assertEquals(1, contents.scrollWrites);
	}

	@Test
	public void nullAndNonFairyRecommendationsResetTheOneShot()
	{
		row(InterfaceID.FairyringsLog.BKR, 600);
		guide.update(client, "fairy-bkr");
		guide.update(client, null);
		contents.scroll = 0;
		guide.update(client, "fairy-bkr");
		assertEquals(2, contents.scrollWrites);
		guide.update(client, "nexus-varrock");
		contents.scroll = 0;

		guide.update(client, "fairy-bkr");

		assertEquals(3, contents.scrollWrites);
	}

	@Test
	public void explicitResetHandlesAnOpeningBetweenObservedTicks()
	{
		row(InterfaceID.FairyringsLog.BKR, 600);
		guide.update(client, "fairy-bkr");
		guide.reset();
		contents.scroll = 0;

		guide.update(client, "fairy-bkr");

		assertEquals(2, contents.scrollWrites);
	}

	@Test
	public void missingHiddenAndUnhydratedRowsRemainEligibleForALaterRetry()
	{
		guide.update(client, "fairy-bkr");
		Node row = row(InterfaceID.FairyringsLog.BKR, 600);
		row.hidden = true;
		guide.update(client, "fairy-bkr");
		row.hidden = false;
		row.text = "";
		guide.update(client, "fairy-bkr");
		row.text = "Morytania: Mort Myre Swamp";
		contents.scrollHeight = 0;
		guide.update(client, "fairy-bkr");
		assertEquals(0, contents.scrollWrites);
		contents.scrollHeight = 1000;

		guide.update(client, "fairy-bkr");

		assertEquals(560, contents.scroll);
		assertEquals(1, contents.scrollWrites);
	}

	@Test
	public void noninteractiveTextAndDisabledRowsDoNotCountAsUsableTravelLogEntries()
	{
		Node row = row(InterfaceID.FairyringsLog.BKR, 600);
		row.actions = null;
		assertNull(FairyRingTravelLog.readableEntry(client, "fairy-bkr"));
		row.actions = new String[]{"Add favourite"};
		assertNull(FairyRingTravelLog.readableEntry(client, "fairy-bkr"));
		row.actions = new String[]{null, "Use code", "Add favourite"};
		row.text = "<str>Morytania: Mort Myre Swamp</str>";
		assertNull(FairyRingTravelLog.readableEntry(client, "fairy-bkr"));
		row.text = "<col=808080>Morytania: Mort Myre Swamp</col>";
		assertNull(FairyRingTravelLog.readableEntry(client, "fairy-bkr"));
		row.text = "Morytania: Mort Myre Swamp";
		row.colour = 0x7f7f7f;
		assertNull(FairyRingTravelLog.readableEntry(client, "fairy-bkr"));
		guide.update(client, "fairy-bkr");
		assertEquals(0, contents.scrollWrites);
		row.colour = 0;

		guide.update(client, "fairy-bkr");

		assertSame(row.widget, FairyRingTravelLog.readableEntry(client, "fairy-bkr"));
		assertEquals(1, contents.scrollWrites);
	}

	@Test
	public void clampsTheScrollAtBothEndsOfTheNativeContent()
	{
		row(InterfaceID.FairyringsLog.BKR, 980);
		row(InterfaceID.FairyringsLog.AIP, 0);
		guide.update(client, "fairy-bkr");
		assertEquals(900, contents.scroll);

		guide.update(client, "fairy-aip");

		assertEquals(0, contents.scroll);
	}

	@Test
	public void nativeComponentMappingCoversEarlyMiddleAndFinalCodes()
	{
		Map<String, Integer> examples = Map.of("fairy-aip", InterfaceID.FairyringsLog.AIP,
			"fairy-bkr", InterfaceID.FairyringsLog.BKR, "fairy-cjq", InterfaceID.FairyringsLog.CJQ,
			"fairy-dls", InterfaceID.FairyringsLog.DLS);
		for (Map.Entry<String, Integer> example : examples.entrySet())
		{
			Node row = row(example.getValue(), 40);
			assertSame(example.getKey(), row.widget, FairyRingTravelLog.readableEntry(client, example.getKey()));
		}
	}

	@Test
	public void malformedCodesAndInvisibleInterfacesDoNotScroll()
	{
		row(InterfaceID.FairyringsLog.BKR, 600);
		for (String id : new String[]{"fairy-", "fairy-bkrr", "fairy-zzz", "fairy-bk1", "bkr"})
		{
			guide.update(client, id);
			assertNull(FairyRingTravelLog.readableEntry(client, id));
		}
		contents.hidden = true;
		guide.update(client, "fairy-bkr");
		assertNull(FairyRingTravelLog.readableEntry(client, "fairy-bkr"));
		contents.hidden = false;
		confirm.hidden = true;
		guide.update(client, "fairy-bkr");
		assertEquals(0, contents.scrollWrites);
	}

	@Test(expected = IllegalStateException.class)
	public void refusesToAccessWidgetsOffTheClientThread()
	{
		clientThread = false;
		guide.update(client, "fairy-bkr");
	}

	/** Models sort_update -> scrollbar_resize -> scrollbar_vertical_doscroll. */
	private void nativeRebuild()
	{
		int saved = client.getVarcIntValue(VarClientID.FAIRYRINGS_SCROLLPOS);
		contents.scroll = Math.max(0, Math.min(saved, contents.scrollHeight - contents.rectangle.height));
		savedScroll = contents.scroll;
	}

	private Node row(int component, int y)
	{
		Node row = new Node(new Rectangle(20, y, 180, 20));
		row.parent = contents;
		widgets.put(component, row);
		return row;
	}

	private static final class Node
	{
		private final Rectangle rectangle;
		private Node parent;
		private boolean hidden;
		private int scroll;
		private int scrollHeight;
		private int scrollWrites;
		private int revalidations;
		private int heightAfterRevalidation = -1;
		private int colour;
		private String text = "Native destination description";
		private String[] actions = {"Use code"};
		private final Widget widget = proxy(Widget.class, this::invoke);

		private Object invoke(String method, Object[] args)
		{
			switch (method)
			{
				case "isHidden": return hidden;
				case "getText": return text;
				case "getTextColor": return colour;
				case "getActions": return actions;
				case "getScrollY": return scroll;
				case "getScrollHeight": return scrollHeight;
				case "getBounds":
					Rectangle bounds = new Rectangle(rectangle);
					if (parent != null) bounds.translate(parent.rectangle.x, parent.rectangle.y - parent.scroll);
					return bounds;
				case "setScrollY":
					scroll = (Integer) args[0];
					scrollWrites++;
					return widget;
				case "revalidateScroll":
					revalidations++;
					if (heightAfterRevalidation > 0) rectangle.height = heightAfterRevalidation;
					scroll = Math.max(0, Math.min(scroll, scrollHeight - rectangle.height));
					return null;
				default: throw new AssertionError("Unexpected widget operation: " + method);
			}
		}

		private Node(Rectangle rectangle)
		{
			this.rectangle = rectangle;
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
