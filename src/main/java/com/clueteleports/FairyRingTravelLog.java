package com.clueteleports;

import java.awt.Rectangle;
import java.util.Locale;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;

/**
 * Brings an existing native travel-log action into view without operating it.
 *
 * Native fairyrings_sort_update (8080) positions the static destination rows
 * inside CONTENTS, and draws their red code labels as separate dynamic text.
 * The static rows retain the native "Use code" action. Their positions follow
 * the selected native sort order, rather than the numeric component order.
 * See runelite/cs2-scripts at 1d418ffb6bce96c2d0712eb2652712d28e01b115,
 * scripts/[proc,fairyrings_sort_update].cs2.
 *
 * Native rebuilds restore FAIRYRINGS_SCROLLPOS, so the one-shot also stores
 * the actual scroll in that client-only UI variable. Later native scrolling
 * owns both values. No game action, script, search, or server variable changes.
 */
public final class FairyRingTravelLog
{
	private static final Pattern TAGS = Pattern.compile("<[^>]*>");
	private static final Pattern DISABLED_COLOUR = Pattern.compile("<col=(?:7f7f7f|808080|999999)>", Pattern.CASE_INSENSITIVE);
	private Widget openContents;
	private String destination;
	private boolean positioned;

	/** Pass null whenever no applicable recommendation should guide this interface. */
	public void update(Client client, String destinationId)
	{
		if (!client.isClientThread())
		{
			throw new IllegalStateException("Fairy-ring travel-log scrolling requires the client thread");
		}
		String code = code(destinationId);
		if (code == null)
		{
			reset();
			return;
		}
		Widget contents = client.getWidget(InterfaceID.FairyringsLog.CONTENTS);
		Widget confirm = client.getWidget(InterfaceID.Fairyrings.CONFIRM);
		Rectangle viewport = bounds(contents);
		if (bounds(confirm) == null || viewport == null)
		{
			reset();
			return;
		}
		if (contents != openContents || !code.equals(destination))
		{
			openContents = contents;
			destination = code;
			positioned = false;
		}
		if (positioned)
		{
			return;
		}
		Widget entry = readableEntry(client, destinationId);
		Rectangle row = bounds(entry);
		if (row == null || row.x >= viewport.x + viewport.width || row.x + row.width <= viewport.x)
		{
			return;
		}
		int scroll = contents.getScrollY();
		int height = contents.getScrollHeight();
		if (scroll < 0)
		{
			return;
		}
		if (row.y >= viewport.y && row.y + row.height <= viewport.y + viewport.height)
		{
			// Even an already-visible row consumes the one-shot. Later manual
			// scrolling must not make the plugin pull the log back again.
			client.setVarcIntValue(VarClientID.FAIRYRINGS_SCROLLPOS, contents.getScrollY());
			positioned = true;
			return;
		}
		int top = row.y - viewport.y + scroll;
		if (top < 0 || height <= 0 || top + row.height > height)
		{
			// A partially hydrated layout must remain eligible for a later retry.
			return;
		}
		int desired = Math.max(0, Math.min(Math.max(0, height - viewport.height),
			top - Math.max(0, (viewport.height - row.height) / 2)));
		if (desired != scroll)
		{
			contents.setScrollY(desired);
			contents.revalidateScroll();
		}
		// fairyrings_sort_update restores this saved UI value on its next
		// rebuild. Store the actual result after any native clamping.
		client.setVarcIntValue(VarClientID.FAIRYRINGS_SCROLLPOS, contents.getScrollY());
		positioned = true;
	}

	public void reset()
	{
		openContents = null;
		destination = null;
		positioned = false;
	}

	/** Returns the existing clickable destination row, including when vertically clipped. */
	static Widget readableEntry(Client client, String destinationId)
	{
		String code = code(destinationId);
		if (code == null || bounds(client.getWidget(InterfaceID.Fairyrings.CONFIRM)) == null
			|| bounds(client.getWidget(InterfaceID.FairyringsLog.CONTENTS)) == null)
		{
			return null;
		}
		int index = "ABCD".indexOf(code.charAt(0)) * 16
			+ "IJKL".indexOf(code.charAt(1)) * 4 + "PQRS".indexOf(code.charAt(2));
		Widget entry = client.getWidget(InterfaceID.FairyringsLog.AIP + index * 2);
		if (bounds(entry) == null)
		{
			return null;
		}
		String text = entry.getText();
		if (text == null || TAGS.matcher(text).replaceAll("").replace('\u00a0', ' ').isBlank()
			|| text.toLowerCase(Locale.ROOT).contains("<str") || DISABLED_COLOUR.matcher(text).find())
		{
			return null;
		}
		int colour = entry.getTextColor();
		if (colour == 0x7f7f7f || colour == 0x808080 || colour == 0x999999)
		{
			return null;
		}
		String[] actions = entry.getActions();
		if (actions != null)
		{
			for (String action : actions)
			{
				if ("Use code".equals(action))
				{
					return entry;
				}
			}
		}
		return null;
	}

	private static String code(String destinationId)
	{
		if (destinationId == null || !destinationId.startsWith("fairy-"))
		{
			return null;
		}
		String code = destinationId.substring(6).toUpperCase(Locale.ROOT);
		return code.matches("[ABCD][IJKL][PQRS]") ? code : null;
	}

	private static Rectangle bounds(Widget widget)
	{
		if (widget == null || widget.isHidden())
		{
			return null;
		}
		Rectangle bounds = widget.getBounds();
		return bounds != null && bounds.width > 0 && bounds.height > 0 ? bounds : null;
	}
}
