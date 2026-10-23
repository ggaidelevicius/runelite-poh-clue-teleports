package com.clueteleports.routing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WalkingCollisionMapTest
{
	@Test
	public void decodesLittleEndianFourFlagRegionPlanes() throws Exception
	{
		WalkingCollisionMap map = WalkingCollisionMap.load(zip("0_0", openPlane()));
		assertTrue(map.hasRegion(pack(1, 1, 0)));
		assertTrue(map.isWalkable(pack(1, 1, 0)));
		assertTrue(map.canWalk(pack(1, 1, 0), pack(2, 1, 0)));
		assertTrue(map.canWalk(pack(1, 1, 0), pack(2, 2, 0)));
		assertFalse(map.hasRegion(pack(1, 1, 1)));
		assertFalse(map.isWalkable(pack(64, 1, 0)));
	}

	@Test
	public void movementCannotCrossAClosedWallInEitherDirection() throws Exception
	{
		byte[] flags = openPlane();
		clear(flags, 4, 4, 0, 1);
		WalkingCollisionMap map = WalkingCollisionMap.load(zip("0_0", flags));
		assertFalse(map.canWalk(pack(4, 4, 0), pack(5, 4, 0)));
		assertFalse(map.canWalk(pack(5, 4, 0), pack(4, 4, 0)));
		assertTrue(map.canWalk(pack(4, 4, 0), pack(4, 5, 0)));
	}

	@Test
	public void diagonalsRequireAllFourCardinalEdgesAroundTheCorner() throws Exception
	{
		byte[] flags = openPlane();
		clear(flags, 5, 4, 0, 0);
		WalkingCollisionMap map = WalkingCollisionMap.load(zip("0_0", flags));
		assertTrue(map.canWalk(pack(4, 4, 0), pack(5, 4, 0)));
		assertTrue(map.canWalk(pack(4, 4, 0), pack(4, 5, 0)));
		assertFalse(map.canWalk(pack(4, 4, 0), pack(5, 5, 0)));
		assertFalse(map.canWalk(pack(5, 5, 0), pack(4, 4, 0)));
	}

	@Test
	public void crossingARegionBoundaryRequiresBothRegions() throws Exception
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes))
		{
			entry(zip, "0_0", openPlane());
			entry(zip, "1_0", openPlane());
		}
		WalkingCollisionMap map = WalkingCollisionMap.load(new ByteArrayInputStream(bytes.toByteArray()));
		assertTrue(map.canWalk(pack(63, 10, 0), pack(64, 10, 0)));
		WalkingCollisionMap partial = WalkingCollisionMap.load(zip("0_0", openPlane()));
		assertFalse(partial.canWalk(pack(63, 10, 0), pack(64, 10, 0)));
	}

	@Test
	public void walkingNeverChangesPlanesOrJumpsAcrossTiles() throws Exception
	{
		byte[] planes = new byte[4096];
		Arrays.fill(planes, (byte) 0x33);
		WalkingCollisionMap map = WalkingCollisionMap.load(zip("0_0", planes));
		assertTrue(map.hasRegion(pack(4, 4, 1)));
		assertFalse(map.canWalk(pack(4, 4, 0), pack(4, 4, 1)));
		assertFalse(map.canWalk(pack(4, 4, 0), pack(6, 4, 0)));
		assertFalse(map.canWalk(pack(4, 4, 0), pack(4, 4, 0)));
		assertFalse(map.isWalkable(-1));
	}

	@Test
	public void explicitPassagesHaveNoOrdinaryOrDiagonalBypass() throws Exception
	{
		WalkingCollisionMap original = WalkingCollisionMap.load(zip("0_0", openPlane()));
		RouteTransport door = new RouteTransport(pack(4, 4, 0), pack(5, 4, 0), 1, "Open locked door",
			RouteRequirements.parse("70 Agility", "", "", "", "").get());
		WalkingCollisionMap guarded = original.withTransportEdges(Collections.singletonList(door));
		assertTrue(original.canWalk(pack(4, 4, 0), pack(5, 4, 0)));
		assertFalse(guarded.canWalk(pack(4, 4, 0), pack(5, 4, 0)));
		assertFalse(guarded.canWalk(pack(5, 4, 0), pack(4, 4, 0)));
		assertFalse(guarded.canWalk(pack(4, 4, 0), pack(5, 5, 0)));
		assertTrue(guarded.canWalk(pack(4, 4, 0), pack(4, 5, 0)));
	}

	@Test(expected = IOException.class)
	public void rejectsMalformedRegionNames() throws Exception
	{
		WalkingCollisionMap.load(zip("../region", openPlane()));
	}

	@Test(expected = IOException.class)
	public void rejectsRegionsLargerThanFourPlanes() throws Exception
	{
		WalkingCollisionMap.load(zip("0_0", new byte[8193]));
	}

	@Test(expected = IOException.class)
	public void rejectsMissingCollisionData() throws Exception
	{
		WalkingCollisionMap.load(null);
	}

	@Test
	public void explicitArrivalCanLeaveSolidFootprintWithoutGrantingOrdinaryWalking() throws Exception
	{
		byte[] flags = openPlane();
		solid(flags, 4, 4, 0);
		WalkingCollisionMap map = WalkingCollisionMap.load(zip("0_0", flags));
		assertFalse(map.isWalkable(pack(4, 4, 0)));
		assertFalse(map.canWalk(pack(4, 4, 0), pack(3, 4, 0)));
		assertTrue(map.canLeaveArrival(pack(4, 4, 0), pack(3, 4, 0)));
		assertTrue(map.canInteractFrom(pack(3, 4, 0), pack(4, 4, 0)));
	}

	@Test
	public void footprintEscapeAndInteractionCannotCrossStructuralWallsOnAnySide() throws Exception
	{
		int[][] sides = {{3, 4, 3, 4, 3}, {5, 4, 4, 4, 3}, {4, 3, 4, 3, 2}, {4, 5, 4, 4, 2}};
		for (int[] side : sides)
		{
			byte[] flags = openPlane();
			solid(flags, 4, 4, 0);
			set(flags, side[2], side[3], 0, side[4]);
			WalkingCollisionMap map = WalkingCollisionMap.load(zip("0_0", flags));
			int face = pack(side[0], side[1], 0);
			assertFalse(map.canLeaveArrival(pack(4, 4, 0), face));
			assertFalse(map.canInteractFrom(face, pack(4, 4, 0)));
		}
	}

	@Test
	public void blockedArrivalEscapeIsOnlyOneCardinalStepOnItsMappedPlane() throws Exception
	{
		byte[] flags = new byte[4096];
		Arrays.fill(flags, (byte) 0x33);
		solid(flags, 4, 4, 0);
		WalkingCollisionMap map = WalkingCollisionMap.load(zip("0_0", flags));
		assertFalse(map.canLeaveArrival(pack(4, 4, 0), pack(3, 3, 0)));
		assertFalse(map.canLeaveArrival(pack(4, 4, 0), pack(2, 4, 0)));
		assertFalse(map.canLeaveArrival(pack(4, 4, 0), pack(4, 4, 1)));
		assertFalse(map.canLeaveArrival(pack(64, 4, 0), pack(63, 4, 0)));
		assertFalse(map.canInteractFrom(pack(63, 4, 0), pack(64, 4, 0)));
	}

	@Test
	public void footprintEscapeAndInteractionDoNotBypassPassageLocks() throws Exception
	{
		byte[] flags = openPlane();
		solid(flags, 4, 4, 0);
		WalkingCollisionMap map = WalkingCollisionMap.load(zip("0_0", flags));
		RouteTransport passage = new RouteTransport(pack(4, 4, 0), pack(3, 4, 0), 1,
			"Locked passage", RouteRequirements.none());
		WalkingCollisionMap guarded = map.withTransportEdges(Collections.singletonList(passage));
		assertFalse(guarded.canLeaveArrival(pack(4, 4, 0), pack(3, 4, 0)));
		assertFalse(guarded.canInteractFrom(pack(3, 4, 0), pack(4, 4, 0)));
	}

	@Test
	public void solidTwoTileObjectCanBeInteractedWithAcrossItsUnwalledFootprint() throws Exception
	{
		byte[] flags = openPlane();
		solid(flags, 3, 4, 0);
		solid(flags, 4, 4, 0);
		WalkingCollisionMap map = WalkingCollisionMap.load(zip("0_0", flags));
		assertTrue(map.canInteractFrom(pack(2, 4, 0), pack(4, 4, 0)));
		assertFalse(map.canWalk(pack(2, 4, 0), pack(3, 4, 0)));
		assertFalse(map.canInteractFrom(pack(1, 4, 0), pack(4, 4, 0)));
		assertFalse(map.canInteractFrom(pack(3, 3, 0), pack(4, 4, 0)));
		set(flags, 3, 4, 0, 3);
		WalkingCollisionMap partitioned = WalkingCollisionMap.load(zip("0_0", flags));
		assertFalse(partitioned.canInteractFrom(pack(2, 4, 0), pack(4, 4, 0)));
	}

	@Test
	public void twoTileInteractionChecksEveryPassageBarrierInTheFootprint() throws Exception
	{
		byte[] flags = openPlane();
		solid(flags, 3, 4, 0);
		solid(flags, 4, 4, 0);
		WalkingCollisionMap map = WalkingCollisionMap.load(zip("0_0", flags));
		RouteTransport passage = new RouteTransport(pack(3, 4, 0), pack(4, 4, 0), 1,
			"Inner lock", RouteRequirements.none());
		WalkingCollisionMap guarded = map.withTransportEdges(Collections.singletonList(passage));
		assertFalse(guarded.canInteractFrom(pack(2, 4, 0), pack(4, 4, 0)));
	}

	@Test
	public void walkableInteractionTargetsStillRequireAnOrdinaryWalkingEdge() throws Exception
	{
		byte[] flags = openPlane();
		clear(flags, 4, 4, 0, 1);
		WalkingCollisionMap map = WalkingCollisionMap.load(zip("0_0", flags));
		assertTrue(map.isWalkable(pack(5, 4, 0)));
		assertFalse(map.canInteractFrom(pack(4, 4, 0), pack(5, 4, 0)));
		assertFalse(map.canInteractFrom(pack(3, 4, 0), pack(5, 4, 0)));
		assertTrue(map.canInteractFrom(pack(5, 5, 0), pack(5, 4, 0)));
	}

	@Test
	public void pinnedCisFairyRingLandingCanLeaveItsSolidFootprintToTheWest() throws Exception
	{
		WalkingCollisionMap map = WalkingCollisionMap.load(
			WalkingCollisionMap.class.getResourceAsStream("/routes/collision-map.zip"));
		int cis = pack(1639, 3868, 0);
		int west = pack(1638, 3868, 0);
		assertTrue(map.hasRegion(cis));
		assertFalse(map.isWalkable(cis));
		assertTrue(map.isWalkable(west));
		assertFalse(map.canWalk(cis, west));
		assertTrue(map.canLeaveArrival(cis, west));
		assertTrue(map.canInteractFrom(west, cis));
	}

	private static void solid(byte[] bytes, int x, int y, int plane)
	{
		clear(bytes, x, y, plane, 0);
		clear(bytes, x, y, plane, 1);
		clear(bytes, x, y - 1, plane, 0);
		clear(bytes, x - 1, y, plane, 1);
	}

	private static void set(byte[] bytes, int x, int y, int plane, int flag)
	{
		int bit = (plane * 4096 + y * 64 + x) * 4 + flag;
		bytes[bit >>> 3] |= 1 << (bit & 7);
	}

	static int pack(int x, int y, int plane) { return plane << 28 | x << 14 | y; }

	static byte[] openPlane()
	{
		byte[] bytes = new byte[2048];
		Arrays.fill(bytes, (byte) 0x33);
		return bytes;
	}

	static ByteArrayInputStream zip(String name, byte[] data) throws IOException
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes))
		{
			entry(zip, name, data);
		}
		return new ByteArrayInputStream(bytes.toByteArray());
	}

	private static void entry(ZipOutputStream zip, String name, byte[] bytes) throws IOException
	{
		zip.putNextEntry(new ZipEntry(name));
		zip.write(bytes);
		zip.closeEntry();
	}

	private static void clear(byte[] bytes, int x, int y, int plane, int flag)
	{
		int bit = (plane * 4096 + y * 64 + x) * 4 + flag;
		bytes[bit >>> 3] &= ~(1 << (bit & 7));
	}
}
