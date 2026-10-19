package dev.laveenh.jazzanalyzer.domain;

/** A chromatic alteration of a chord tone, e.g. b9 = (9, -1), #11 = (11, +1). */
public record Alteration(int degree, int shift) implements Comparable<Alteration> {

    @Override
    public int compareTo(Alteration other) {
        int byDegree = Integer.compare(degree, other.degree);
        return byDegree != 0 ? byDegree : Integer.compare(shift, other.shift);
    }

    @Override
    public String toString() {
        String accidental = shift >= 0 ? "#".repeat(shift) : "b".repeat(-shift);
        return accidental + degree;
    }
}
