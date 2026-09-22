package me.Plugins.SimpleFactions.War.pathfinder;

import java.util.Collections;
import java.util.List;

public class PathfinderResult {
	private final boolean found;
	private final List<Integer> path;
	private final double totalCost;
	private final PathfinderPass passUsed;
	private final int startProvinceId;

	private PathfinderResult(
			boolean found,
			List<Integer> path,
			double totalCost,
			PathfinderPass passUsed,
			int startProvinceId) {
		this.found = found;
		this.path = path == null ? List.of() : List.copyOf(path);
		this.totalCost = totalCost;
		this.passUsed = passUsed;
		this.startProvinceId = startProvinceId;
	}

	public static PathfinderResult found(
			List<Integer> path,
			double totalCost,
			PathfinderPass passUsed,
			int startProvinceId) {
		return new PathfinderResult(true, path, totalCost, passUsed, startProvinceId);
	}

	public static PathfinderResult notFound() {
		return new PathfinderResult(false, List.of(), Double.POSITIVE_INFINITY, null, -1);
	}

	public PathfinderResult withStartProvinceId(int startProvinceId) {
		return new PathfinderResult(found, path, totalCost, passUsed, startProvinceId);
	}

	public boolean isFound() {
		return found;
	}

	public List<Integer> getPath() {
		return path;
	}

	public double getTotalCost() {
		return totalCost;
	}

	public PathfinderPass getPassUsed() {
		return passUsed;
	}

	public int getStartProvinceId() {
		return startProvinceId;
	}

	public List<Integer> getPathView() {
		return Collections.unmodifiableList(path);
	}
}
