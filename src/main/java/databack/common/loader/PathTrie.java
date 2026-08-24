package databack.common.loader;

import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class PathTrie<T> {

    private final Object2ObjectOpenHashMap<String, PathTrie<T>> children = new Object2ObjectOpenHashMap<>();

    private T value;

    public T get(List<String> path) {
        return getImpl(path, 0);
    }

    private T getImpl(List<String> path, int index) {
        if (index < path.size()) {
            PathTrie<T> child = children.get(path.get(index));

            if (child == null) return null;

            return child.getImpl(path, index + 1);
        } else {
            return value;
        }
    }

    public List<String> findDeepestNode(List<String> path) {
        return findDeepestNodeImpl(path, 0);
    }

    private List<String> findDeepestNodeImpl(List<String> path, int index) {
        if (index < path.size()) {
            PathTrie<T> child = children.get(path.get(index));

            if (child == null) {
                if (this.value == null) {
                    return Collections.emptyList();
                } else {
                    return path.subList(0, index);
                }
            } else {
                List<String> childPath = child.findDeepestNodeImpl(path, index + 1);

                if (childPath.isEmpty() && this.value != null) {
                    return path.subList(0, index);
                } else {
                    return childPath;
                }
            }
        } else if (index == path.size() && this.value != null) {
            return path;
        } else {
            return Collections.emptyList();
        }
    }

    public T put(List<String> path, T value) {
        return putImpl(path, 0, value);
    }

    private T putImpl(List<String> path, int index, T value) {
        if (index < path.size()) {
            PathTrie<T> child = children.computeIfAbsent(path.get(index), $ -> new PathTrie<>());

            return child.putImpl(path, index + 1, value);
        } else {
            T prev = this.value;

            this.value = value;

            return prev;
        }
    }

    public T remove(List<String> path) {
        return removeImpl(path, 0);
    }

    private T removeImpl(List<String> path, int index) {
        if (index < path.size()) {
            PathTrie<T> child = children.get(path.get(index));

            if (child == null) return null;

            T removed = child.removeImpl(path, index + 1);

            if (child.children.isEmpty() && child.value == null) this.children.remove(path.get(index));

            return removed;
        } else if (index == path.size()) {
            T prev = this.value;

            this.value = null;

            return prev;
        } else {
            return null;
        }
    }

    public interface TrieVisitor<T> {
        void accept(List<String> path, @NotNull T value);
    }

    public void dfs(TrieVisitor<T> visitor) {
        ObjectArrayList<String> path = new ObjectArrayList<>();

        dfsImpl(path, visitor);
    }

    private void dfsImpl(ObjectArrayList<String> path, TrieVisitor<T> visitor) {
        if (this.value != null) {
            visitor.accept(path, this.value);
        }

        children.forEach((key, trie) -> {
            path.push(key);

            trie.dfsImpl(path, visitor);

            path.pop();
        });
    }

    public void bfs(TrieVisitor<T> visitor) {
        ObjectArrayList<String> path = new ObjectArrayList<>();

        if (this.value != null) {
            visitor.accept(path, this.value);
        }

        bfsImpl(path, visitor);
    }

    private void bfsImpl(ObjectArrayList<String> path, TrieVisitor<T> visitor) {
        children.forEach((key, trie) -> {
            path.push(key);

            if (trie.value != null) {
                visitor.accept(path, trie.value);
            }

            path.pop();
        });

        children.forEach((key, trie) -> {
            path.push(key);

            trie.bfsImpl(path, visitor);

            path.pop();
        });
    }

    public int size() {
        int size = 0;

        if (this.value != null) size++;

        for (var trie : children.values()) {
            size += trie.size();
        }

        return size;
    }

    public void clear() {
        children.clear();
        this.value = null;
    }
}
