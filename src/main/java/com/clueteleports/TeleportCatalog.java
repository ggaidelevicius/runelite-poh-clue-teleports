/*
 * Copyright (c) 2018, Morgan Lewis <https://github.com/MESLewis>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.clueteleports;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.runelite.api.coords.WorldPoint;

/**
 * Fixed landing points for supported POH transport facilities.
 *
 * Transport coordinates are adapted from Skretzo/shortest-path at
 * 50f30435f37d1d5c5e2af790925fa7aa70b7840d (fairy_rings.tsv,
 * spirit_trees.tsv, teleportation_boxes.tsv, and teleportation_portals_poh.tsv).
 * The upstream BSD license is reproduced in THIRD_PARTY_NOTICES.txt.
 * Fairy ring descriptions and DLP coordinates are from runelite/runelite at
 * e336ac270345c6c48e0fa69a6432d7fa03c5eb76. Mounted items, obelisks, and house
 * exits also use Skretzo/shortest-path at 142e73e2a5f840bb16e227e564a036156f4cfc77
 * (teleportation_boxes.tsv, wilderness_obelisks.tsv, and teleportation_items.tsv).
 * Cape destinations use RuneLite's worldmap/TeleportLocationData.java at
 * 9fad2c588ca957ae47d883b09544725a796ed9fe.
 *
 * These are representative landing tiles, not pathfinding results. Respawn and
 * Teleport to Boat are deliberately omitted because their targets can move.
 * Owned-boat destinations on the cape hanger are omitted for the same reason.
 * DIQ and the spirit tree's Your house destination do not lead towards a clue.
 * A catalog entry alone says nothing about a player's access to that destination.
 */
public final class TeleportCatalog
{
	private static final List<TeleportDestination> DESTINATIONS;
	private static final Map<String, TeleportDestination> BY_ID;
	private static final Map<String, TeleportDestination> NEXUS_NAMES;
	private static final Map<String, Integer> JEWELLERY_TIERS;

	// Each slot contains a nexus destination key, not a boolean unlock flag.
	// runelite/cs2-scripts: [proc,telenexus_get_slot] and [proc,script2674].
	private static final int[] NEXUS_SLOT_VARBITS = {
		6654, 6655, 6656, 6657, 6658, 6659, 6660, 6661, 6662, 6663, 6664, 6665, 6666, 6667, 6668,
		6554, 6555, 6556, 10080, 10081, 10082, 10083, 10084, 10085, 10086, 10087, 10088, 10089, 10090, 10091,
		10019, 10020, 10021, 10022, 10023, 20111, 20112, 20113, 20114, 20115, 20116, 20117, 20118, 20119, 20120
	};

	static
	{
		List<TeleportDestination> destinations = new ArrayList<>();
		add(destinations, "fairy-aiq", TeleportSource.FAIRY_RING, "AIQ - Mudskipper Point", 2996, 3114, 0);
		add(destinations, "fairy-air", TeleportSource.FAIRY_RING, "AIR - South-east of Ardougne", 2700, 3247, 0);
		add(destinations, "fairy-ais", TeleportSource.FAIRY_RING, "AIS - Auburn Valley", 1429, 3324, 0);
		add(destinations, "fairy-ajp", TeleportSource.FAIRY_RING, "AJP - Avium Savannah", 1651, 3010, 0);
		add(destinations, "fairy-ajq", TeleportSource.FAIRY_RING, "AJQ - Cave south of Dorgesh-Kaan", 2735, 5221, 0);
		add(destinations, "fairy-ajr", TeleportSource.FAIRY_RING, "AJR - Slayer cave", 2780, 3613, 0);
		add(destinations, "fairy-ajs", TeleportSource.FAIRY_RING, "AJS - Penguins near Miscellania", 2500, 3896, 0);
		add(destinations, "fairy-akp", TeleportSource.FAIRY_RING, "AKP - Necropolis", 3284, 2706, 0);
		add(destinations, "fairy-akq", TeleportSource.FAIRY_RING, "AKQ - Piscatoris Hunter area", 2319, 3619, 0);
		add(destinations, "fairy-akr", TeleportSource.FAIRY_RING, "AKR - Hosidius Vinery", 1826, 3540, 0);
		add(destinations, "fairy-aks", TeleportSource.FAIRY_RING, "AKS - Feldip Hunter area", 2571, 2956, 0);
		add(destinations, "fairy-alp", TeleportSource.FAIRY_RING, "ALP - Lighthouse", 2503, 3636, 0);
		add(destinations, "fairy-alq", TeleportSource.FAIRY_RING, "ALQ - Haunted Woods east of Canifis", 3597, 3495, 0);
		add(destinations, "fairy-alr", TeleportSource.FAIRY_RING, "ALR - Abyssal Area", 3059, 4875, 0);
		add(destinations, "fairy-als", TeleportSource.FAIRY_RING, "ALS - McGrubor's Wood", 2644, 3495, 0);
		add(destinations, "fairy-bip", TeleportSource.FAIRY_RING, "BIP - South-west of Mort Myre", 3410, 3324, 0);
		add(destinations, "fairy-biq", TeleportSource.FAIRY_RING, "BIQ - Kalphite Hive", 3251, 3095, 0);
		add(destinations, "fairy-bis", TeleportSource.FAIRY_RING, "BIS - Ardougne Zoo - Unicorns", 2635, 3266, 0);
		add(destinations, "fairy-bjp", TeleportSource.FAIRY_RING, "BJP - Isle of Souls", 2264, 2976, 0);
		add(destinations, "fairy-bjr", TeleportSource.FAIRY_RING, "BJR - Realm of the Fisher King", 2650, 4730, 0);
		add(destinations, "fairy-bjs", TeleportSource.FAIRY_RING, "BJS - Near Zul-Andra", 2150, 3070, 0);
		add(destinations, "fairy-bkp", TeleportSource.FAIRY_RING, "BKP - South of Castle Wars", 2385, 3035, 0);
		add(destinations, "fairy-bkq", TeleportSource.FAIRY_RING, "BKQ - Enchanted Valley", 3041, 4532, 0);
		add(destinations, "fairy-bkr", TeleportSource.FAIRY_RING, "BKR - Mort Myre Swamp, south of Canifis", 3469, 3431, 0);
		add(destinations, "fairy-bks", TeleportSource.FAIRY_RING, "BKS - Zanaris", 2412, 4434, 0);
		add(destinations, "fairy-blp", TeleportSource.FAIRY_RING, "BLP - TzHaar area", 2437, 5126, 0);
		add(destinations, "fairy-blq", TeleportSource.FAIRY_RING, "BLQ - Yu'biusk", 3572, 4372, 0);
		add(destinations, "fairy-blr", TeleportSource.FAIRY_RING, "BLR - Legends' Guild", 2740, 3351, 0);
		add(destinations, "fairy-bls", TeleportSource.FAIRY_RING, "BLS - South of Mount Quidamortem", 1295, 3493, 0);
		add(destinations, "fairy-cip", TeleportSource.FAIRY_RING, "CIP - Miscellania", 2513, 3884, 0);
		add(destinations, "fairy-ciq", TeleportSource.FAIRY_RING, "CIQ - North-west of Yanille", 2528, 3127, 0);
		add(destinations, "fairy-cir", TeleportSource.FAIRY_RING, "CIR - North-east of the Farming Guild", 1302, 3762, 0);
		add(destinations, "fairy-cis", TeleportSource.FAIRY_RING, "CIS - North of the Arceuus Library", 1639, 3868, 0);
		add(destinations, "fairy-cjq", TeleportSource.FAIRY_RING, "CJQ - The Great Conch", 3178, 2447, 0);
		add(destinations, "fairy-cjr", TeleportSource.FAIRY_RING, "CJR - Sinclair Mansion", 2705, 3576, 0);
		add(destinations, "fairy-ckp", TeleportSource.FAIRY_RING, "CKP - Cosmic entity's plane", 2075, 4848, 0);
		add(destinations, "fairy-ckq", TeleportSource.FAIRY_RING, "CKQ - Aldarin", 1359, 2941, 0);
		add(destinations, "fairy-ckr", TeleportSource.FAIRY_RING, "CKR - South of Tai Bwo Wannai Village", 2801, 3003, 0);
		add(destinations, "fairy-cks", TeleportSource.FAIRY_RING, "CKS - Canifis", 3447, 3470, 0);
		add(destinations, "fairy-clp", TeleportSource.FAIRY_RING, "CLP - South of Draynor Village", 3082, 3206, 0);
		add(destinations, "fairy-clr", TeleportSource.FAIRY_RING, "CLR - Ape Atoll", 2740, 2738, 0);
		add(destinations, "fairy-cls", TeleportSource.FAIRY_RING, "CLS - Hazelmere's home", 2682, 3081, 0);
		add(destinations, "fairy-dip", TeleportSource.FAIRY_RING, "DIP - (Sire Boss) Abyssal Nexus", 3037, 4763, 0);
		add(destinations, "fairy-dir", TeleportSource.FAIRY_RING, "DIR - Gorak's Plane", 3038, 5348, 0);
		add(destinations, "fairy-dis", TeleportSource.FAIRY_RING, "DIS - Wizards' Tower", 3108, 3149, 0);
		add(destinations, "fairy-djp", TeleportSource.FAIRY_RING, "DJP - Tower of Life", 2658, 3230, 0);
		add(destinations, "fairy-djr", TeleportSource.FAIRY_RING, "DJR - Chasm of Fire", 1455, 3658, 0);
		add(destinations, "fairy-dkp", TeleportSource.FAIRY_RING, "DKP - South of Musa Point", 2900, 3111, 0);
		add(destinations, "fairy-dkr", TeleportSource.FAIRY_RING, "DKR - Edgeville, Grand Exchange", 3129, 3496, 0);
		add(destinations, "fairy-dks", TeleportSource.FAIRY_RING, "DKS - Polar Hunter area", 2744, 3719, 0);
		add(destinations, "fairy-dlp", TeleportSource.FAIRY_RING, "DLP - Grimstone Dungeon", 2923, 10455, 0);
		add(destinations, "fairy-dlq", TeleportSource.FAIRY_RING, "DLQ - North of Nardah", 3423, 3016, 0);
		add(destinations, "fairy-dlr", TeleportSource.FAIRY_RING, "DLR - Poison Waste south of Isafdar", 2213, 3099, 0);
		add(destinations, "fairy-dls", TeleportSource.FAIRY_RING, "DLS - Myreque hideout under The Hollows", 3447, 9824, 0);
		add(destinations, "tree-tree-gnome-village", TeleportSource.SPIRIT_TREE, "Tree Gnome Village", 2542, 3170, 0);
		add(destinations, "tree-gnome-stronghold", TeleportSource.SPIRIT_TREE, "Gnome Stronghold", 2461, 3444, 0);
		add(destinations, "tree-battlefield-of-khazard", TeleportSource.SPIRIT_TREE, "Battlefield of Khazard", 2555, 3259, 0);
		add(destinations, "tree-grand-exchange", TeleportSource.SPIRIT_TREE, "Grand Exchange", 3185, 3508, 0);
		add(destinations, "tree-feldip-hills", TeleportSource.SPIRIT_TREE, "Feldip Hills", 2488, 2850, 0);
		add(destinations, "tree-prifddinas", TeleportSource.SPIRIT_TREE, "Prifddinas", 3274, 6123, 0);
		add(destinations, "tree-port-sarim", TeleportSource.SPIRIT_TREE, "Port Sarim", 3058, 3257, 0);
		add(destinations, "tree-etceteria", TeleportSource.SPIRIT_TREE, "Etceteria", 2613, 3855, 0);
		add(destinations, "tree-brimhaven", TeleportSource.SPIRIT_TREE, "Brimhaven", 2800, 3203, 0);
		add(destinations, "tree-hosidius", TeleportSource.SPIRIT_TREE, "Hosidius", 1693, 3540, 0);
		add(destinations, "tree-farming-guild", TeleportSource.SPIRIT_TREE, "Farming Guild", 1251, 3750, 0);
		add(destinations, "tree-poison-waste", TeleportSource.SPIRIT_TREE, "Poison Waste", 2339, 3109, 0);
		add(destinations, "tree-laguna-aurorae", TeleportSource.SPIRIT_TREE, "Laguna Aurorae", 1202, 2785, 0);
		add(destinations, "nexus-ape-atoll-dungeon", TeleportSource.PORTAL_NEXUS, "Ape Atoll Dungeon", 2771, 9102, 0);
		add(destinations, "nexus-annakarl", TeleportSource.PORTAL_NEXUS, "Annakarl", 3288, 3888, 0);
		add(destinations, "nexus-ardougne", TeleportSource.PORTAL_NEXUS, "Ardougne", 2662, 3305, 0);
		add(destinations, "nexus-arceuus-library", TeleportSource.PORTAL_NEXUS, "Arceuus Library", 1632, 3837, 0);
		add(destinations, "nexus-barbarian-outpost", TeleportSource.PORTAL_NEXUS, "Barbarian Outpost", 2543, 3569, 0);
		add(destinations, "nexus-barrows", TeleportSource.PORTAL_NEXUS, "Barrows", 3564, 3315, 0);
		add(destinations, "nexus-battlefront", TeleportSource.PORTAL_NEXUS, "Battlefront", 1347, 3740, 0);
		add(destinations, "nexus-camelot", TeleportSource.PORTAL_NEXUS, "Camelot", 2757, 3479, 0);
		add(destinations, "nexus-seers-village", TeleportSource.PORTAL_NEXUS, "Seers' Village", 2726, 3487, 0);
		add(destinations, "nexus-carrallanger", TeleportSource.PORTAL_NEXUS, "Carrallanger", 3158, 3666, 0);
		add(destinations, "nexus-catherby", TeleportSource.PORTAL_NEXUS, "Catherby", 2801, 3449, 0);
		add(destinations, "nexus-civitas-illa-fortis", TeleportSource.PORTAL_NEXUS, "Civitas illa Fortis", 1681, 3133, 0);
		add(destinations, "nexus-cemetery", TeleportSource.PORTAL_NEXUS, "Cemetery", 2981, 3764, 0);
		add(destinations, "nexus-dareeyak", TeleportSource.PORTAL_NEXUS, "Dareeyak", 2968, 3696, 0);
		add(destinations, "nexus-draynor-manor", TeleportSource.PORTAL_NEXUS, "Draynor Manor", 3109, 3351, 0);
		add(destinations, "nexus-falador", TeleportSource.PORTAL_NEXUS, "Falador", 2964, 3378, 0);
		add(destinations, "nexus-fenkenstrains-castle", TeleportSource.PORTAL_NEXUS, "Fenkenstrain's Castle", 3548, 3529, 0);
		add(destinations, "nexus-fishing-guild", TeleportSource.PORTAL_NEXUS, "Fishing Guild", 2613, 3391, 0);
		add(destinations, "nexus-ghorrock", TeleportSource.PORTAL_NEXUS, "Ghorrock", 2976, 3872, 0);
		add(destinations, "nexus-grand-exchange", TeleportSource.PORTAL_NEXUS, "Grand Exchange", 3165, 3479, 0);
		add(destinations, "nexus-harmony-island", TeleportSource.PORTAL_NEXUS, "Harmony Island", 3797, 2866, 0);
		add(destinations, "nexus-ice-plateau", TeleportSource.PORTAL_NEXUS, "Ice Plateau", 2975, 3938, 0);
		add(destinations, "nexus-kharyrll", TeleportSource.PORTAL_NEXUS, "Kharyrll", 3494, 3473, 0);
		add(destinations, "nexus-kourend", TeleportSource.PORTAL_NEXUS, "Kourend", 1641, 3673, 0);
		add(destinations, "nexus-lassar", TeleportSource.PORTAL_NEXUS, "Lassar", 3004, 3470, 0);
		add(destinations, "nexus-lumbridge", TeleportSource.PORTAL_NEXUS, "Lumbridge", 3221, 3218, 0);
		add(destinations, "nexus-lunar-isle", TeleportSource.PORTAL_NEXUS, "Lunar Isle", 2113, 3915, 0);
		add(destinations, "nexus-marim", TeleportSource.PORTAL_NEXUS, "Marim", 2797, 2798, 1);
		add(destinations, "nexus-mind-altar", TeleportSource.PORTAL_NEXUS, "Mind Altar", 2979, 3510, 0);
		add(destinations, "nexus-ourania", TeleportSource.PORTAL_NEXUS, "Ourania", 2468, 3246, 0);
		add(destinations, "nexus-paddewwa", TeleportSource.PORTAL_NEXUS, "Paddewwa", 3097, 9881, 0);
		add(destinations, "nexus-port-khazard", TeleportSource.PORTAL_NEXUS, "Port Khazard", 2636, 3167, 0);
		add(destinations, "nexus-salve-graveyard", TeleportSource.PORTAL_NEXUS, "Salve Graveyard", 3433, 3461, 0);
		add(destinations, "nexus-senntisten", TeleportSource.PORTAL_NEXUS, "Senntisten", 3320, 3337, 0);
		add(destinations, "nexus-trollheim", TeleportSource.PORTAL_NEXUS, "Trollheim", 2890, 3679, 0);
		add(destinations, "nexus-troll-stronghold", TeleportSource.PORTAL_NEXUS, "Troll Stronghold", 2845, 3694, 0);
		add(destinations, "nexus-varrock", TeleportSource.PORTAL_NEXUS, "Varrock", 3213, 3424, 0);
		add(destinations, "nexus-waterbirth-island", TeleportSource.PORTAL_NEXUS, "Waterbirth Island", 2546, 3755, 0);
		add(destinations, "nexus-weiss", TeleportSource.PORTAL_NEXUS, "Weiss", 2846, 3938, 0);
		add(destinations, "nexus-west-ardougne", TeleportSource.PORTAL_NEXUS, "West Ardougne", 2500, 3290, 0);
		add(destinations, "nexus-watchtower", TeleportSource.PORTAL_NEXUS, "Watchtower", 2546, 3114, 0);
		add(destinations, "nexus-yanille", TeleportSource.PORTAL_NEXUS, "Yanille", 2584, 3097, 0);
		add(destinations, "jewellery-emirs-arena", TeleportSource.JEWELLERY_BOX, "Emir's Arena", 3315, 3235, 0);
		add(destinations, "jewellery-castle-wars", TeleportSource.JEWELLERY_BOX, "Castle Wars", 2440, 3090, 0);
		add(destinations, "jewellery-ferox-enclave", TeleportSource.JEWELLERY_BOX, "Ferox Enclave", 3151, 3635, 0);
		add(destinations, "jewellery-fortis-colosseum", TeleportSource.JEWELLERY_BOX, "Fortis Colosseum", 1793, 3107, 0);
		add(destinations, "jewellery-burthorpe", TeleportSource.JEWELLERY_BOX, "Burthorpe", 2899, 3553, 0);
		add(destinations, "jewellery-barbarian-outpost", TeleportSource.JEWELLERY_BOX, "Barbarian Outpost", 2520, 3571, 0);
		add(destinations, "jewellery-chasm-of-tears", TeleportSource.JEWELLERY_BOX, "Chasm of Tears", 3245, 9500, 2);
		add(destinations, "jewellery-corporeal-beast", TeleportSource.JEWELLERY_BOX, "Corporeal Beast", 2964, 4382, 2);
		add(destinations, "jewellery-wintertodt-camp", TeleportSource.JEWELLERY_BOX, "Wintertodt Camp", 1631, 3940, 0);
		add(destinations, "jewellery-warriors-guild", TeleportSource.JEWELLERY_BOX, "Warriors' Guild", 2882, 3547, 0);
		add(destinations, "jewellery-champions-guild", TeleportSource.JEWELLERY_BOX, "Champions' Guild", 3192, 3368, 0);
		add(destinations, "jewellery-monastery", TeleportSource.JEWELLERY_BOX, "Monastery", 3052, 3490, 0);
		add(destinations, "jewellery-ranging-guild", TeleportSource.JEWELLERY_BOX, "Ranging Guild", 2653, 3439, 0);
		add(destinations, "jewellery-fishing-guild", TeleportSource.JEWELLERY_BOX, "Fishing Guild", 2611, 3390, 0);
		add(destinations, "jewellery-mining-guild", TeleportSource.JEWELLERY_BOX, "Mining Guild", 3049, 9763, 0);
		add(destinations, "jewellery-crafting-guild", TeleportSource.JEWELLERY_BOX, "Crafting Guild", 2933, 3295, 0);
		add(destinations, "jewellery-cooking-guild", TeleportSource.JEWELLERY_BOX, "Cooking Guild", 3144, 3438, 0);
		add(destinations, "jewellery-woodcutting-guild", TeleportSource.JEWELLERY_BOX, "Woodcutting Guild", 1662, 3505, 0);
		add(destinations, "jewellery-farming-guild", TeleportSource.JEWELLERY_BOX, "Farming Guild", 1248, 3719, 0);
		add(destinations, "jewellery-miscellania", TeleportSource.JEWELLERY_BOX, "Miscellania", 2534, 3862, 0);
		add(destinations, "jewellery-grand-exchange", TeleportSource.JEWELLERY_BOX, "Grand Exchange", 3163, 3478, 0);
		add(destinations, "jewellery-falador-park", TeleportSource.JEWELLERY_BOX, "Falador Park", 2995, 3375, 0);
		add(destinations, "jewellery-dondakans-rock", TeleportSource.JEWELLERY_BOX, "Dondakan's Rock", 2824, 10168, 0);
		add(destinations, "jewellery-edgeville", TeleportSource.JEWELLERY_BOX, "Edgeville", 3087, 3496, 0);
		add(destinations, "jewellery-karamja", TeleportSource.JEWELLERY_BOX, "Karamja", 2918, 3176, 0);
		add(destinations, "jewellery-draynor-village", TeleportSource.JEWELLERY_BOX, "Draynor Village", 3105, 3251, 0);
		add(destinations, "jewellery-al-kharid", TeleportSource.JEWELLERY_BOX, "Al Kharid", 3293, 3163, 0);
		// Chamber portals share fixed spell landing points with the portal nexus.
		for (TeleportDestination destination : new ArrayList<>(destinations))
		{
			if (destination.getSource() == TeleportSource.PORTAL_NEXUS)
			{
				destinations.add(new TeleportDestination("portal-" + destination.getId().substring("nexus-".length()),
					TeleportSource.PORTAL_CHAMBER, destination.getName(), destination.getLocation()));
			}
		}
		add(destinations, "obelisk-13", TeleportSource.WILDERNESS_OBELISK, "Level 13 Wilderness", 3156, 3620, 0);
		add(destinations, "obelisk-19", TeleportSource.WILDERNESS_OBELISK, "Level 19 Wilderness", 3227, 3667, 0);
		add(destinations, "obelisk-27", TeleportSource.WILDERNESS_OBELISK, "Level 27 Wilderness", 3035, 3732, 0);
		add(destinations, "obelisk-35", TeleportSource.WILDERNESS_OBELISK, "Level 35 Wilderness", 3106, 3794, 0);
		add(destinations, "obelisk-44", TeleportSource.WILDERNESS_OBELISK, "Level 44 Wilderness", 2980, 3866, 0);
		add(destinations, "obelisk-50", TeleportSource.WILDERNESS_OBELISK, "Level 50 Wilderness", 3307, 3916, 0);
		add(destinations, "glory-edgeville", TeleportSource.MOUNTED_GLORY, "Edgeville", 3087, 3496, 0);
		add(destinations, "glory-karamja", TeleportSource.MOUNTED_GLORY, "Karamja", 2918, 3176, 0);
		add(destinations, "glory-draynor-village", TeleportSource.MOUNTED_GLORY, "Draynor Village", 3105, 3251, 0);
		add(destinations, "glory-al-kharid", TeleportSource.MOUNTED_GLORY, "Al Kharid", 3293, 3163, 0);
		add(destinations, "digsite-digsite", TeleportSource.MOUNTED_DIGSITE_PENDANT, "Digsite", 3341, 3445, 0);
		add(destinations, "digsite-fossil-island", TeleportSource.MOUNTED_DIGSITE_PENDANT, "Fossil Island", 3763, 3869, 1);
		add(destinations, "digsite-lithkren", TeleportSource.MOUNTED_DIGSITE_PENDANT, "Lithkren", 3549, 10456, 0);
		add(destinations, "xeric-lookout", TeleportSource.MOUNTED_XERICS_TALISMAN, "Xeric's Lookout", 1579, 3530, 0);
		add(destinations, "xeric-glade", TeleportSource.MOUNTED_XERICS_TALISMAN, "Xeric's Glade", 1752, 3566, 0);
		add(destinations, "xeric-inferno", TeleportSource.MOUNTED_XERICS_TALISMAN, "Xeric's Inferno", 1504, 3815, 0);
		add(destinations, "xeric-heart", TeleportSource.MOUNTED_XERICS_TALISMAN, "Xeric's Heart", 1644, 3673, 0);
		add(destinations, "xeric-honour", TeleportSource.MOUNTED_XERICS_TALISMAN, "Xeric's Honour", 1254, 3560, 0);
		add(destinations, "mythical-myths-guild", TeleportSource.MOUNTED_MYTHICAL_CAPE, "Myths' Guild", 2457, 2850, 0);
		add(destinations, "exit-rimmington", TeleportSource.HOUSE_EXIT, "Rimmington", 2953, 3224, 0);
		add(destinations, "exit-taverley", TeleportSource.HOUSE_EXIT, "Taverley", 2893, 3465, 0);
		add(destinations, "exit-pollnivneach", TeleportSource.HOUSE_EXIT, "Pollnivneach", 3340, 3003, 0);
		add(destinations, "exit-rellekka", TeleportSource.HOUSE_EXIT, "Rellekka", 2670, 3631, 0);
		add(destinations, "exit-brimhaven", TeleportSource.HOUSE_EXIT, "Brimhaven", 2757, 3178, 0);
		add(destinations, "exit-yanille", TeleportSource.HOUSE_EXIT, "Yanille", 2544, 3096, 0);
		add(destinations, "exit-hosidius", TeleportSource.HOUSE_EXIT, "Hosidius", 1743, 3517, 0);
		add(destinations, "exit-prifddinas", TeleportSource.HOUSE_EXIT, "Prifddinas", 3239, 6076, 0);
		add(destinations, "exit-aldarin", TeleportSource.HOUSE_EXIT, "Aldarin", 1422, 2963, 0);
		add(destinations, "cape-rimmington", TeleportSource.CAPE_HANGER, "POH portal - Rimmington", 2954, 3224, 0);
		add(destinations, "cape-taverley", TeleportSource.CAPE_HANGER, "POH portal - Taverley", 2894, 3465, 0);
		add(destinations, "cape-pollnivneach", TeleportSource.CAPE_HANGER, "POH portal - Pollnivneach", 3340, 3004, 0);
		add(destinations, "cape-hosidius", TeleportSource.CAPE_HANGER, "POH portal - Hosidius", 1744, 3517, 0);
		add(destinations, "cape-rellekka", TeleportSource.CAPE_HANGER, "POH portal - Rellekka", 2670, 3632, 0);
		add(destinations, "cape-aldarin", TeleportSource.CAPE_HANGER, "POH portal - Aldarin", 1422, 2966, 0);
		add(destinations, "cape-brimhaven", TeleportSource.CAPE_HANGER, "POH portal - Brimhaven", 2758, 3178, 0);
		add(destinations, "cape-yanille", TeleportSource.CAPE_HANGER, "POH portal - Yanille", 2544, 3095, 0);
		add(destinations, "cape-prifddinas", TeleportSource.CAPE_HANGER, "POH portal - Prifddinas", 3239, 6076, 0);
		add(destinations, "cape-crafting-guild", TeleportSource.CAPE_HANGER, "Crafting Guild", 2931, 3286, 0);
		add(destinations, "cape-farming-guild", TeleportSource.CAPE_HANGER, "Farming Guild", 1248, 3725, 0);
		add(destinations, "cape-fishing-guild", TeleportSource.CAPE_HANGER, "Fishing Guild", 2604, 3401, 0);
		add(destinations, "cape-ottos-grotto", TeleportSource.CAPE_HANGER, "Otto's Grotto", 2504, 3484, 0);
		add(destinations, "cape-warriors-guild", TeleportSource.CAPE_HANGER, "Warriors' Guild", 2865, 3546, 0);
		add(destinations, "cape-legends-guild", TeleportSource.CAPE_HANGER, "Legends' Guild", 2729, 3348, 0);
		add(destinations, "cape-falo-the-bard", TeleportSource.CAPE_HANGER, "Falo the Bard", 2689, 3547, 0);
		add(destinations, "cape-hunter-guild", TeleportSource.CAPE_HANGER, "Hunter Guild", 1558, 3046, 0);
		add(destinations, "cape-red-chinchompas", TeleportSource.CAPE_HANGER, "Red chinchompas", 2556, 2916, 0);
		add(destinations, "cape-black-chinchompas", TeleportSource.CAPE_HANGER, "Black chinchompas", 3144, 3772, 0);
		add(destinations, "cape-pandemonium", TeleportSource.CAPE_HANGER, "The Pandemonium", 3048, 2972, 0);
		add(destinations, "cape-diary-two-pints", TeleportSource.CAPE_HANGER, "Two-pints", 2574, 3324, 0);
		add(destinations, "cape-diary-jarr", TeleportSource.CAPE_HANGER, "Jarr", 3300, 3121, 0);
		add(destinations, "cape-diary-sir-rebral", TeleportSource.CAPE_HANGER, "Sir Rebral", 2977, 3345, 0);
		add(destinations, "cape-diary-thorodin", TeleportSource.CAPE_HANGER, "Thorodin", 2659, 3627, 0);
		add(destinations, "cape-diary-flax-keeper", TeleportSource.CAPE_HANGER, "Flax keeper", 2744, 3444, 0);
		add(destinations, "cape-diary-jackie-the-fruit", TeleportSource.CAPE_HANGER, "Pirate Jackie the Fruit", 2810, 3191, 0);
		add(destinations, "cape-diary-kaleb-paramaya", TeleportSource.CAPE_HANGER, "Kaleb Paramaya", 2861, 2995, 1);
		add(destinations, "cape-diary-jungle-forester", TeleportSource.CAPE_HANGER, "Jungle forester", 2795, 2945, 0);
		add(destinations, "cape-diary-tzhaar-mej", TeleportSource.CAPE_HANGER, "TzHaar-Mej", 2455, 5133, 0);
		add(destinations, "cape-diary-elise", TeleportSource.CAPE_HANGER, "Elise", 1647, 3666, 0);
		add(destinations, "cape-diary-hatius-cosaintus", TeleportSource.CAPE_HANGER, "Hatius Cosaintus", 3239, 3220, 0);
		add(destinations, "cape-diary-le-sabre", TeleportSource.CAPE_HANGER, "Le-sabre", 3465, 3477, 0);
		add(destinations, "cape-diary-toby", TeleportSource.CAPE_HANGER, "Toby", 3224, 3414, 0);
		add(destinations, "cape-diary-lesser-fanatic", TeleportSource.CAPE_HANGER, "Lesser Fanatic", 3122, 3516, 0);
		add(destinations, "cape-diary-elder-gnome-child", TeleportSource.CAPE_HANGER, "Elder Gnome child", 2467, 3459, 0);
		add(destinations, "cape-diary-twiggy-okorn", TeleportSource.CAPE_HANGER, "Twiggy O'Korn", 3097, 3226, 0);
		add(destinations, "cape-myths-guild", TeleportSource.CAPE_HANGER, "Myths' Guild", 2457, 2850, 0);
		add(destinations, "cape-ardougne-monastery", TeleportSource.CAPE_HANGER, "Ardougne Monastery", 2606, 3222, 0);
		add(destinations, "cape-ardougne-farming-patch", TeleportSource.CAPE_HANGER, "Ardougne farming patch", 2673, 3375, 0);
		DESTINATIONS = Collections.unmodifiableList(destinations);

		Map<String, TeleportDestination> byId = new LinkedHashMap<>();
		Map<String, TeleportDestination> nexusNames = new LinkedHashMap<>();
		for (TeleportDestination destination : destinations)
		{
			if (byId.put(destination.getId(), destination) != null)
			{
				throw new IllegalStateException("Duplicate teleport id: " + destination.getId());
			}
			if (destination.getSource() == TeleportSource.PORTAL_NEXUS)
			{
				nexusNames.put(normalizeNexusName(destination.getName()), destination);
			}
		}
		BY_ID = Collections.unmodifiableMap(byId);
		alias(nexusNames, "nexus-kourend", "Great Kourend", "Kourend Castle");
		alias(nexusNames, "nexus-marim", "Ape Atoll");
		alias(nexusNames, "nexus-kharyrll", "Canifis");
		alias(nexusNames, "nexus-senntisten", "Exam Centre", "Digsite");
		alias(nexusNames, "nexus-annakarl", "Demonic Ruins");
		alias(nexusNames, "nexus-ghorrock", "Frozen Waste Plateau", "Frozen Waste");
		alias(nexusNames, "nexus-carrallanger", "Graveyard of Shadows");
		alias(nexusNames, "nexus-paddewwa", "Edgeville Dungeon");
		alias(nexusNames, "nexus-lassar", "Ice Mountain");
		alias(nexusNames, "nexus-dareeyak", "Crazy Archaeologist");
		alias(nexusNames, "nexus-lunar-isle", "Moonclan");
		alias(nexusNames, "nexus-ourania", "Ourania Cave");
		alias(nexusNames, "nexus-barbarian-outpost", "Barbarian");
		alias(nexusNames, "nexus-port-khazard", "Khazard");
		alias(nexusNames, "nexus-cemetery", "The Forgotten Cemetery");
		NEXUS_NAMES = Collections.unmodifiableMap(nexusNames);

		Map<String, Integer> tiers = new LinkedHashMap<>();
		tiers.put("jewellery-emirs-arena", 1);
		tiers.put("jewellery-castle-wars", 1);
		tiers.put("jewellery-ferox-enclave", 1);
		tiers.put("jewellery-fortis-colosseum", 1);
		tiers.put("jewellery-burthorpe", 1);
		tiers.put("jewellery-barbarian-outpost", 1);
		tiers.put("jewellery-chasm-of-tears", 1);
		tiers.put("jewellery-corporeal-beast", 1);
		tiers.put("jewellery-wintertodt-camp", 1);
		tiers.put("jewellery-warriors-guild", 2);
		tiers.put("jewellery-champions-guild", 2);
		tiers.put("jewellery-monastery", 2);
		tiers.put("jewellery-ranging-guild", 2);
		tiers.put("jewellery-fishing-guild", 2);
		tiers.put("jewellery-mining-guild", 2);
		tiers.put("jewellery-crafting-guild", 2);
		tiers.put("jewellery-cooking-guild", 2);
		tiers.put("jewellery-woodcutting-guild", 2);
		tiers.put("jewellery-farming-guild", 2);
		tiers.put("jewellery-miscellania", 3);
		tiers.put("jewellery-grand-exchange", 3);
		tiers.put("jewellery-falador-park", 3);
		tiers.put("jewellery-dondakans-rock", 3);
		tiers.put("jewellery-edgeville", 3);
		tiers.put("jewellery-karamja", 3);
		tiers.put("jewellery-draynor-village", 3);
		tiers.put("jewellery-al-kharid", 3);
		JEWELLERY_TIERS = Collections.unmodifiableMap(tiers);
	}

	private TeleportCatalog()
	{
	}

	public static List<TeleportDestination> all()
	{
		return DESTINATIONS;
	}

	public static List<TeleportDestination> forSource(TeleportSource source)
	{
		List<TeleportDestination> result = new ArrayList<>();
		for (TeleportDestination destination : DESTINATIONS)
		{
			if (destination.getSource() == source)
			{
				result.add(destination);
			}
		}
		return Collections.unmodifiableList(result);
	}

	public static Optional<TeleportDestination> byId(String id)
	{
		return Optional.ofNullable(BY_ID.get(id));
	}

	public static Optional<TeleportDestination> nexusDestination(String name)
	{
		return Optional.ofNullable(NEXUS_NAMES.get(normalizeNexusName(name)));
	}

	public static Optional<TeleportDestination> portalDestination(String name)
	{
		String portalName = name == null ? "" : name.replaceAll("<[^>]*>", "").replaceAll("(?i)\\s+portal\\s*$", "");
		if ("yanillewatchtower".equals(normalizeNexusName(portalName)))
		{
			return byId("portal-watchtower");
		}
		return nexusDestination(portalName)
			.flatMap(destination -> byId("portal-" + destination.getId().substring("nexus-".length())));
	}

	public static int[] nexusSlotVarbits()
	{
		return NEXUS_SLOT_VARBITS.clone();
	}

	/** Minimum box tier: 1 basic, 2 fancy, 3 ornate, or 0 for another source. */
	public static int jewelleryTier(TeleportDestination destination)
	{
		return JEWELLERY_TIERS.getOrDefault(destination.getId(), 0);
	}

	private static void add(List<TeleportDestination> destinations, String id,
		TeleportSource source, String name, int x, int y, int plane)
	{
		destinations.add(new TeleportDestination(id, source, name, new WorldPoint(x, y, plane)));
	}

	private static void alias(Map<String, TeleportDestination> names, String id, String... aliases)
	{
		TeleportDestination destination = BY_ID.get(id);
		for (String alias : aliases)
		{
			names.put(normalizeNexusName(alias), destination);
		}
	}

	private static String normalizeNexusName(String name)
	{
		if (name == null)
		{
			return "";
		}
		return name.replaceAll("<[^>]*>", "")
			.replaceAll("^[A-Za-z0-9]\\s*:\\s*", "")
			.replaceAll("\\s*\\([^)]*\\)\\s*$", "")
			.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]", "");
	}
}
