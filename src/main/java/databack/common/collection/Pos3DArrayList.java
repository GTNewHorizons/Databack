package databack.common.collection;

import java.util.ListIterator;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;
import org.joml.Vector3i;
import org.joml.Vector3ic;

import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import databack.common.functional.Pos3DConsumer;
import databack.common.functional.Pos3DPredicate;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongListIterator;

public class Pos3DArrayList extends LongArrayList {

    private PosIterable iterable;

    public boolean add(int x, int y, int z) {
        return super.add(CoordinatePacker.pack(x, y, z));
    }

    public boolean removeIf(Pos3DPredicate filter) {
        return super.removeIf(l -> filter.test(CoordinatePacker.unpackX(l), CoordinatePacker.unpackY(l), CoordinatePacker.unpackZ(l)));
    }

    public void forEach(Pos3DConsumer action) {
        super.forEach((long l) -> {
            action.accept(CoordinatePacker.unpackX(l), CoordinatePacker.unpackY(l), CoordinatePacker.unpackZ(l));
        });
    }

    public void replaceAll(Consumer<Vector3i> operator) {
        Vector3i v = new Vector3i();

        super.replaceAll((long l) -> {
            v.set(CoordinatePacker.unpackX(l), CoordinatePacker.unpackY(l), CoordinatePacker.unpackZ(l));

            operator.accept(v);

            return CoordinatePacker.pack(v.x, v.y, v.z);
        });
    }

    public PosIterable fastIterable() {
        if (iterable == null) {
            iterable = () -> {
                LongListIterator iter = Pos3DArrayList.this.listIterator();

                Vector3i pooled = new Vector3i();

                return new ListIterator<>() {

                    @Override
                    public boolean hasNext() {
                        return iter.hasNext();
                    }

                    @Override
                    public Vector3ic next() {
                        long l = iter.nextLong();

                        pooled.set(CoordinatePacker.unpackX(l), CoordinatePacker.unpackY(l), CoordinatePacker.unpackZ(l));

                        return pooled;
                    }

                    @Override
                    public boolean hasPrevious() {
                        return iter.hasPrevious();
                    }

                    @Override
                    public Vector3ic previous() {
                        long l = iter.previousLong();

                        pooled.set(CoordinatePacker.unpackX(l), CoordinatePacker.unpackY(l), CoordinatePacker.unpackZ(l));

                        return pooled;
                    }

                    @Override
                    public int nextIndex() {
                        return iter.nextIndex();
                    }

                    @Override
                    public int previousIndex() {
                        return iter.previousIndex();
                    }

                    @Override
                    public void remove() {
                        iter.remove();
                    }

                    @Override
                    public void set(Vector3ic pos) {
                        iter.set(CoordinatePacker.pack(pos.x(), pos.y(), pos.z()));
                    }

                    @Override
                    public void add(Vector3ic pos) {
                        iter.add(CoordinatePacker.pack(pos.x(), pos.y(), pos.z()));
                    }
                };
            };
        }

        return iterable;
    }

    public interface PosIterable extends Iterable<Vector3ic> {

        @Override
        @NotNull ListIterator<Vector3ic> iterator();
    }
}
