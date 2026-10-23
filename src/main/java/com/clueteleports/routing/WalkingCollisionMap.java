package com.clueteleports.routing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Offline collision data pinned with the resources/routes upstream manifest.
 * The four-flag format follows Shortest Path's SplitFlagMap at
 * 31c2e313a348a00fb49dfbee53461610681f94d6; its license is bundled beside the data.
 * Points use RuneLite's 14-bit x/y packing, with the plane in bits 28 and 29.
 */
public class WalkingCollisionMap
{
	private final Map<Integer, byte[]> regions;
	private final Set<Long> transportBarriers;

	/** Empty map for small synthetic graphs that override the public queries. */
	protected WalkingCollisionMap()
	{
		this(Collections.emptyMap(), Collections.emptySet());
	}

	private WalkingCollisionMap(Map<Integer, byte[]> regions, Set<Long> transportBarriers)
	{
		this.regions = regions;
		this.transportBarriers = transportBarriers;
	}

	/** Loads and closes a region ZIP; missing regions and planes remain blocked. */
	public static WalkingCollisionMap load(InputStream stream) throws IOException
	{
		if (stream == null)
		{
			throw new IOException("Missing collision data");
		}
		Map<Integer, byte[]> regions = new HashMap<>();
		try (ZipInputStream zip = new ZipInputStream(stream))
		{
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null)
			{
				if (entry.isDirectory())
				{
					continue;
				}
				if (!entry.getName().matches("[0-9]{1,3}_[0-9]{1,3}"))
				{
					throw new IOException("Invalid collision region: " + entry.getName());
				}
				String[] coordinates = entry.getName().split("_");
				int x = Integer.parseInt(coordinates[0]);
				int y = Integer.parseInt(coordinates[1]);
				if (x > 255 || y > 255)
				{
					throw new IOException("Collision region outside world");
				}
				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				byte[] buffer = new byte[4096];
				int count;
				while ((count = zip.read(buffer)) != -1)
				{
					if (bytes.size() + count > 8192)
					{
						throw new IOException("Collision region exceeds four planes");
					}
					bytes.write(buffer, 0, count);
				}
				if (regions.put((x << 8) | y, bytes.toByteArray()) != null)
				{
					throw new IOException("Duplicate collision region");
				}
			}
		}
		if (regions.isEmpty())
		{
			throw new IOException("Empty collision data");
		}
		return new WalkingCollisionMap(Collections.unmodifiableMap(regions), Collections.emptySet());
	}

	/** Passage eligibility must control these links, including their ordinary-walk bypass. */
	public WalkingCollisionMap withTransportEdges(Collection<RouteTransport> edges)
	{
		Set<Long> barriers = new HashSet<>(transportBarriers);
		for (RouteTransport edge : edges)
		{
			if (adjacent(edge.getFrom(), edge.getTo())) barriers.add(edgeKey(edge.getFrom(), edge.getTo()));
		}
		return withTransportBarriers(barriers);
	}

	WalkingCollisionMap withTransportBarriers(Set<Long> barriers)
	{
		return new WalkingCollisionMap(regions, Collections.unmodifiableSet(new HashSet<>(barriers)));
	}

	public boolean hasRegion(int packed)
	{
		if (!valid(packed))
		{
			return false;
		}
		byte[] bytes = regions.get(regionKey(x(packed), y(packed)));
		return bytes != null && plane(packed) * 2048 < bytes.length;
	}

	public boolean isWalkable(int packed)
	{
		if (!hasRegion(packed))
		{
			return false;
		}
		int x = x(packed);
		int y = y(packed);
		int plane = plane(packed);
		return flag(x, y, plane, 0) || flag(x, y - 1, plane, 0)
			|| flag(x, y, plane, 1) || flag(x - 1, y, plane, 1);
	}

	public boolean canWalk(int from, int to)
	{
		if (!isWalkable(from) || !isWalkable(to) || plane(from) != plane(to))
		{
			return false;
		}
		int dx = x(to) - x(from);
		int dy = y(to) - y(from);
		if (Math.abs(dx) > 1 || Math.abs(dy) > 1 || dx == 0 && dy == 0
			|| transportBarriers.contains(edgeKey(from, to)))
		{
			return false;
		}
		if (dx == 0 || dy == 0)
		{
			return cardinal(from, to);
		}
		// Each of the two paths around the corner must be clear. This also
		// prevents diagonal bypasses of a requirement-gated door edge.
		int horizontal = pack(x(to), y(from), plane(from));
		int vertical = pack(x(from), y(to), plane(from));
		return cardinal(from, horizontal) && cardinal(horizontal, to)
			&& cardinal(from, vertical) && cardinal(vertical, to);
	}

	/**
	 * Only the search's explicit catalog arrival may use this escape rule.
	 * Object footprints can block the forced landing tile; walls and passage
	 * locks still apply. Arbitrary blocked graph nodes must use canWalk.
	 */
	public boolean canLeaveArrival(int from, int to)
	{
		return canWalk(from, to) || (!isWalkable(from) && hasRegion(from) && isWalkable(to)
			&& unwalledCardinal(from, to));
	}

	/**
	 * Tests the usable face of a solid task object without crossing a wall.
	 * A two-tile solid footprint may be interacted with from its outside face;
	 * this does not grant a walking edge through the intervening footprint.
	 */
	public boolean canInteractFrom(int face, int solidTarget)
	{
		if (isWalkable(solidTarget))
		{
			return canWalk(face, solidTarget);
		}
		if (!isWalkable(face) || !hasRegion(solidTarget) || plane(face) != plane(solidTarget))
		{
			return false;
		}
		int dx = x(solidTarget) - x(face);
		int dy = y(solidTarget) - y(face);
		int distance = Math.abs(dx) + Math.abs(dy);
		if (distance < 1 || distance > 2 || dx != 0 && dy != 0)
		{
			return false;
		}
		int current = face;
		for (int step = 0; step < distance; step++)
		{
			int next = pack(x(current) + Integer.signum(dx), y(current) + Integer.signum(dy), plane(face));
			if (!unwalledCardinal(current, next))
			{
				return false;
			}
			current = next;
		}
		return true;
	}

	private boolean unwalledCardinal(int from, int to)
	{
		if (!hasRegion(from) || !hasRegion(to) || plane(from) != plane(to)
			|| Math.abs(x(from) - x(to)) + Math.abs(y(from) - y(to)) != 1
			|| transportBarriers.contains(edgeKey(from, to)))
		{
			return false;
		}
		int x = x(from);
		int y = y(from);
		int plane = plane(from);
		if (x(to) == x + 1) return !flag(x, y, plane, 3);
		if (x(to) == x - 1) return !flag(x - 1, y, plane, 3);
		if (y(to) == y + 1) return !flag(x, y, plane, 2);
		return !flag(x, y - 1, plane, 2);
	}

	private boolean cardinal(int from, int to)
	{
		if (!hasRegion(from) || !hasRegion(to) || transportBarriers.contains(edgeKey(from, to)))
		{
			return false;
		}
		int x = x(from);
		int y = y(from);
		int plane = plane(from);
		if (x(to) == x + 1) return flag(x, y, plane, 1);
		if (x(to) == x - 1) return flag(x - 1, y, plane, 1);
		if (y(to) == y + 1) return flag(x, y, plane, 0);
		if (y(to) == y - 1) return flag(x, y - 1, plane, 0);
		return false;
	}

	private boolean flag(int x, int y, int plane, int flag)
	{
		if (x < 0 || y < 0 || x > 16383 || y > 16383 || plane < 0 || plane > 3)
		{
			return false;
		}
		byte[] bytes = regions.get(regionKey(x, y));
		int bit = (plane * 4096 + (y & 63) * 64 + (x & 63)) * 4 + flag;
		return bytes != null && (bit >>> 3) < bytes.length && (bytes[bit >>> 3] & (1 << (bit & 7))) != 0;
	}

	static long edgeKey(int from, int to)
	{
		return ((long) Math.min(from, to) << 32) | (Math.max(from, to) & 0xffffffffL);
	}

	static boolean adjacent(int from, int to)
	{
		return plane(from) == plane(to) && from != to
			&& Math.abs(x(from) - x(to)) <= 1 && Math.abs(y(from) - y(to)) <= 1;
	}

	private static int regionKey(int x, int y) { return ((x >>> 6) << 8) | (y >>> 6); }
	private static boolean valid(int packed) { return (packed & 0xc0000000) == 0; }
	private static int x(int packed) { return packed >>> 14 & 16383; }
	private static int y(int packed) { return packed & 16383; }
	private static int plane(int packed) { return packed >>> 28 & 3; }
	private static int pack(int x, int y, int plane) { return plane << 28 | x << 14 | y; }
}
