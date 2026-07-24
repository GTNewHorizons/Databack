package databack.common.util;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public class IntegralFraction {

    public long numerator = 1, denominator = 1;

    public IntegralFraction() {

    }

    public IntegralFraction(long numerator, long denominator) {
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public double toDouble() {
        return numerator / (double) denominator;
    }

    public float toFloat() {
        return numerator / (float) denominator;
    }

    public IntegralFraction clone() {
        return new IntegralFraction(numerator, denominator);
    }

    public IntegralFraction mul(long n) {
        this.numerator *= n;
        return this;
    }

    public IntegralFraction div(long n) {
        this.denominator *= n;
        return this;
    }

    public IntegralFraction reduce() {
        long gcd = DBMathUtils.gcd(this.numerator, this.denominator);

        if (gcd > 1) {
            this.numerator /= gcd;
            this.denominator /= gcd;
        }

        return this;
    }

    public int apply(int x) {
        return (int) apply((long) x);
    }

    public int applyCeil(int x) {
        return (int) applyCeil((long) x);
    }

    public long apply(long x) {
        return x * numerator / denominator;
    }

    public long applyCeil(long x) {
        return DBMathUtils.ceilDiv(x * numerator, denominator);
    }


}
