package com.clueteleports;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.StructComposition;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/** Reads installed destinations while the nexus teleport menu is open. */
public final class NexusImporter
{
	// Verified from runelite/cs2-scripts [proc,script2674],
	// [proc,telenexus_left_click], and [proc,script2664].
	private static final int NEXUS_DESTINATION_ENUM = 1377;
	private static final int DESTINATION_NAME_PARAM = 660;
	private static final int ALTERNATE_DESTINATION_PARAM = 679;
	private static final int BASE_DESTINATION_PARAM = 680;

	private NexusImporter()
	{
	}

	/**
	 * Returns empty when the teleport menu is closed or cache data is unavailable.
	 * A present empty set means the open nexus has no supported fixed destinations.
	 * The menu can belong to a guest house; callers must verify house ownership.
	 * Invoke on the client thread.
	 */
	public static Optional<Set<String>> readOpenMenu(Client client)
	{
		Widget menu = client.getWidget(InterfaceID.TelenexusTeleport.UNIVERSE);
		if (menu == null || menu.isHidden())
		{
			return Optional.empty();
		}
		EnumComposition destinations = client.getEnum(NEXUS_DESTINATION_ENUM);
		if (destinations == null)
		{
			return Optional.empty();
		}

		Set<String> imported = new LinkedHashSet<>();
		for (int slot : TeleportCatalog.nexusSlotVarbits())
		{
			int key = client.getVarbitValue(slot);
			if (key <= 0)
			{
				continue;
			}
			int structId = destinations.getIntValue(key);
			if (structId <= 0)
			{
				return Optional.empty();
			}
			StructComposition destination = client.getStructComposition(structId);
			if (destination == null)
			{
				return Optional.empty();
			}
			addDestination(client, imported, destination);
			int baseId = destination.getIntValue(BASE_DESTINATION_PARAM);
			if (baseId > 0)
			{
				StructComposition base = client.getStructComposition(baseId);
				if (base != null)
				{
					addDestination(client, imported, base);
					destination = base;
				}
				else
				{
					return Optional.empty();
				}
			}
			int alternateId = destination.getIntValue(ALTERNATE_DESTINATION_PARAM);
			if (alternateId > 0)
			{
				StructComposition alternate = client.getStructComposition(alternateId);
				if (alternate != null)
				{
					addDestination(client, imported, alternate);
				}
				else
				{
					return Optional.empty();
				}
			}
		}
		return Optional.of(Collections.unmodifiableSet(imported));
	}

	private static void addDestination(Client client, Set<String> imported, StructComposition struct)
	{
		TeleportCatalog.nexusDestination(struct.getStringValue(DESTINATION_NAME_PARAM))
			.filter(destination -> alternateUnlocked(client, destination.getId()))
			.ifPresent(destination -> imported.add(destination.getId()));
	}

	private static boolean alternateUnlocked(Client client, String destinationId)
	{
		switch (destinationId)
		{
			case "nexus-grand-exchange":
				return rewarded(client, VarbitID.VARROCK_MEDIUM_REWARD,
					VarbitID.VARROCK_HARD_REWARD, VarbitID.VARROCK_ELITE_REWARD);
			case "nexus-seers-village":
				return rewarded(client, VarbitID.KANDARIN_HARD_REWARD, VarbitID.KANDARIN_ELITE_REWARD);
			case "nexus-yanille":
				return rewarded(client, VarbitID.ARDOUGNE_HARD_REWARD, VarbitID.ARDOUGNE_ELITE_REWARD);
			default:
				return true;
		}
	}

	private static boolean rewarded(Client client, int... varbits)
	{
		for (int varbit : varbits)
		{
			if (client.getVarbitValue(varbit) == 1)
			{
				return true;
			}
		}
		return false;
	}
}
