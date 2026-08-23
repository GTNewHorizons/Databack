package databack.common.worldgen;

import org.jetbrains.annotations.NotNull;

import com.github.bsideup.jabel.Desugar;

public interface Expr<T> {

    Expr<Integer> X = Expr.of(Integer.class, "x");
    Expr<Integer> Y = Expr.of(Integer.class, "y");
    Expr<Integer> Z = Expr.of(Integer.class, "z");

    static <T> Expr<T> of(Class<T> type, String expr) {
        return new TrivialExpr<>(expr);
    }

    @Desugar
    record TrivialExpr<T>(String expr) implements Expr<T> {

        @Override
        public @NotNull String toString() {
            return expr;
        }
    }
}
