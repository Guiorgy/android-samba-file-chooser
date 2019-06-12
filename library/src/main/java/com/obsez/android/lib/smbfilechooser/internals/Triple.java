package com.obsez.android.lib.smbfilechooser.internals;

import android.util.Pair;

public class Triple<F, S, T> extends Pair<F, S> {
    public final T third;

    /**
     * Constructor for a Pair.
     *
     * @param first  the first object in the Pair
     * @param second the second object in the pair
     */
    public Triple(F first, S second) {
        this(first, second, null);
    }

    /**
     * Constructor for a Triple.
     *
     * @param first  the first object in the Triple
     * @param second the second object in the Triple
     * @param third the third object in the Triple
     */
    public Triple(F first, S second, T third) {
        super(first, second);
        this.third = third;
    }

    protected static boolean nullSafeEquals(Object a, Object b) {
        //noinspection EqualsReplaceableByObjectsCall
        return (a == b) || (a != null && a.equals(b));
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof Triple && nullSafeEquals(((Triple) o).first, first) && nullSafeEquals(((Triple) o).second, second) && nullSafeEquals(((Triple) o).third, third));
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ (third == null ? 0 : third.hashCode());
    }

    @Override
    public String toString() {
        return "Triple{" + first + ", " + second + ", " + third + "}";
    }

    /**
     * @deprecated use {@link #create(Object, Object, Object)} instead
     */
    @Deprecated
    public static <A, B> Pair <A, B> create(A a, B b) {
        throw new UnsupportedOperationException();
    }

    /**
     * Convenience method for creating an appropriately typed Triple.
     * @param a the first object in the Triple
     * @param b the second object in the Triple
     * @param c the third object in the Triple
     * @return a Triple that is templatized with the types of a, b and c
     */
    public static <A, B, C> Triple <A, B, C> create(A a, B b, C c) {
        return new Triple<>(a, b, c);
    }
}
