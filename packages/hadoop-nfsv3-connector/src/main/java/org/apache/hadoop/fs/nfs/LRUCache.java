package org.apache.hadoop.fs.nfs;

import java.util.LinkedHashMap;
import java.util.Map;

public class LRUCache<K, V> {

	private final Map<K, V> cacheMap;
	private int cacheSize;

	public LRUCache(final int cacheSize) {

		this.cacheSize = cacheSize;
		// true = use access order instead of insertion order.
		this.cacheMap = new LinkedHashMap<K, V>(cacheSize, 0.75f, true) {
			private static final long serialVersionUID = -7556524103423461876L;

			@Override
			protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
				// When to remove the eldest entry.
				return size() > cacheSize; // Size exceeded the max allowed.
			}
		};
	}

	public synchronized void put(K key, V elem) {
		cacheMap.put(key, elem);
	}

	public synchronized V get(K key) {
		return cacheMap.get(key);
	}

	/* remove a stale handle by key. */
	public synchronized void remove(K key) {
		cacheMap.remove(key);
	}

	/* remove a stale handle by value. */
	public synchronized void removeByValue(V handle) {
		cacheMap.values().remove(handle);
	}

	public int getCacheSize() {
		return cacheSize;
	}
}
