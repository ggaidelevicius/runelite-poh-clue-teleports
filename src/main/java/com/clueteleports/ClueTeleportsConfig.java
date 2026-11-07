package com.clueteleports;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("clueteleports")
public interface ClueTeleportsConfig extends Config
{
	@ConfigItem(
		keyName = "overlayMode",
		name = "Overlay mode",
		description = "Setup always uses Full.",
		position = 0
	)
	default OverlayMode overlayMode()
	{
		return OverlayMode.FULL;
	}

	@ConfigItem(
		keyName = "chatRecommendations",
		name = "Chat messages",
		description = "Announce new teleport recommendations.",
		position = 1
	)
	default boolean chatRecommendations()
	{
		return false;
	}

	@ConfigItem(
		keyName = "minimapArrow",
		name = "Minimap arrow",
		description = "Point toward the clue after teleporting.",
		position = 2
	)
	default boolean minimapArrow()
	{
		return true;
	}

	enum OverlayMode
	{
		FULL("Full"),
		COMPACT("Compact"),
		HIDDEN("Hidden");

		private final String label;

		OverlayMode(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}
}
