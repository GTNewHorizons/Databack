package databack.common.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;

@SuppressWarnings({ "unused", "ForLoopReplaceableByForEach" })
public class DBDataUtils {

    public static <S, T> List<T> mapToList(Collection<S> in, Function<S, T> mapper) {
        if (in == null) return null;

        List<T> out = new ArrayList<>(in.size());

        for (S s : in)
            out.add(mapper.apply(s));

        return out;
    }

    public static <S, T> List<T> mapToList(S[] in, Function<S, T> mapper) {
        if (in == null) return null;

        List<T> out = new ArrayList<>(in.length);

        for (S s : in)
            out.add(mapper.apply(s));

        return out;
    }

    public static <S, T> T[] mapToArray(Collection<S> in, IntFunction<T[]> ctor, Function<S, T> mapper) {
        if (in == null) return null;

        T[] out = ctor.apply(in.size());

        Iterator<S> iter = in.iterator();
        for (int i = 0; i < out.length && iter.hasNext(); i++) {
            out[i] = mapper.apply(iter.next());
        }

        return out;
    }

    public static <S, T> T[] mapToArray(S[] in, IntFunction<T[]> ctor, Function<S, T> mapper) {
        if (in == null) return null;

        T[] out = ctor.apply(in.length);

        for (int i = 0; i < out.length; i++)
            out[i] = mapper.apply(in[i]);

        return out;
    }

    public static <T> T find(T[] in, Predicate<T> fn) {
        for (T t : in) {
            if (fn.test(t)) return t;
        }

        return null;
    }

    public static <T> T find(Collection<T> in, Predicate<T> fn) {
        for (T t : in) {
            if (fn.test(t)) return t;
        }

        return null;
    }

    public static <T> int indexOf(T[] array, T value) {
        int l = array.length;

        for (int i = 0; i < l; i++) {
            if (array[i] == value) { return i; }
        }

        return -1;
    }

    public static <T> T[] slice(T[] array, int start, int end) {
        T[] out = Arrays.copyOf(array, end - start);

        int i = 0;

        for (int i2 = start; i2 < end; i2++) {
            out[i++] = array[i2];
        }

        return out;
    }

    public static String join(String sep, String[] array) {
        StringBuilder sb = new StringBuilder();

        for (String chunk : array) {
            if (sb.length() > 0) {
                sb.append(sep);
            }

            sb.append(chunk);
        }

        return sb.toString();
    }

    public static String join(String sep, Collection<String> col) {
        StringBuilder sb = new StringBuilder();

        for (String chunk : col) {
            if (sb.length() > 0) {
                sb.append(sep);
            }

            sb.append(chunk);
        }

        return sb.toString();
    }

    public static int countNonNulls(Object[] array) {
        int l = array.length;
        int count = 0;

        for (Object o : array) {
            if (o != null) count++;
        }

        return count;
    }

    public static <T> T[] withoutNulls(T[] array) {
        if (array.length == 0) return array;

        int nonNullCount = countNonNulls(array);

        if (nonNullCount == array.length) return array;

        T[] out = Arrays.copyOf(array, nonNullCount);

        int j = 0;

        for (T t : array) {
            if (t != null) out[j++] = t;
        }

        return out;
    }

    public static <T> ArrayList<T> filterList(List<T> input, Predicate<T> filter) {
        ArrayList<T> output = new ArrayList<>(input.size());

        for (int i = 0, inputSize = input.size(); i < inputSize; i++) {
            T t = input.get(i);

            if (filter.test(t)) {
                output.add(t);
            }
        }

        return output;
    }

    public static <T, S extends T> void addAllFiltered(List<S> input, List<T> output, Predicate<S> filter) {
        for (int i = 0, inputSize = input.size(); i < inputSize; i++) {
            S s = input.get(i);

            if (filter.test(s)) {
                output.add(s);
            }
        }
    }

    /**
     * Upcasts a list of a concrete type into a list of interfaces since java can't do this implicitly with generics.
     */
    public static <I, T extends I> ArrayList<I> upcast(List<T> input) {
        ArrayList<I> output = new ArrayList<>(input.size());

        for (int i = 0, inputSize = input.size(); i < inputSize; i++) {
            output.add(input.get(i));
        }

        return output;
    }

    public static <T> T getIndexSafe(T[] array, int index) {
        return array == null || index < 0 || index >= array.length ? null : array[index];
    }

    public static <T> T getIndexSafe(List<T> list, int index) {
        return list == null || index < 0 || index >= list.size() ? null : list.get(index);
    }

    public static <T> T choose(List<T> list, Random rng) {
        if (list.isEmpty()) return null;
        if (list.size() == 1) return list.get(0);

        return list.get(rng.nextInt(list.size()));
    }

    public static <K, V> boolean areMapsEqual(Map<K, V> left, Map<K, V> right) {
        if (left == null || right == null) return left == right;

        HashSet<K> keys = new HashSet<>(left.size() + right.size());

        keys.addAll(left.keySet());
        keys.addAll(right.keySet());

        for (K key : keys) {
            if (!Objects.equals(left.get(key), right.get(key))) return false;
        }

        return true;
    }

    public static <T> T[] concat(T[] array, T value) {
        T[] out = Arrays.copyOf(array, array.length + 1);
        out[out.length - 1] = value;
        return out;
    }

    public static <T> T[] concat(T[] first, T[] second) {
        T[] out = Arrays.copyOf(first, first.length + second.length);

        System.arraycopy(second, 0, out, first.length, second.length);

        return out;
    }

    public static <T> List<T> concat(List<T> first, List<T> second) {
        ArrayList<T> out = new ArrayList<>(first.size() + second.size());
        out.addAll(first);
        out.addAll(second);
        return out;
    }

}
