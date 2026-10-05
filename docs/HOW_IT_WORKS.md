# How the analyzer works, step by step

This follows real input through the real code. Every score below was printed by the analyzer, not worked out by hand.

The pipeline has three stages after parsing:

```
MusicXML  ->  Chart  ->  [1 key detection]  ->  [2 chord function]  ->  [3 scale suggestion]  ->  ChartAnalysis
              (chords      which key is each     Roman numeral and      ranked scales from
              with place)  chord in?             role of each chord     the data file
```

## Parsing (before the three stages)

`MusicXmlParser` turns the XML file into a `Chart`: a list of chords, each with a measure and a beat.
`ChordClassifier` turns the raw MusicXML (`kind=dominant`, degree `b9`...) into one normalised `Chord`
(root `G`, quality `DOMINANT7`, alteration `b9`). After this point nothing looks at XML again.

## Example A: `Dm7 | G7 | Cmaj7`, key signature C major

### Stage 1: key detection

The detector scores every chord against all 24 keys (12 major, 12 minor).

**Fit.** What fraction of the chord's notes are in the key's scale? Dm7 is D F A C. All four are in C major,
so its fit is 1.0. In D minor (which has C natural and no B natural) all four are also in. In F# minor, almost none.

**Bonuses.** A small bonus when the chord is the key's own tonic chord, and a bonus when the key matches the key signature.

**Cadence.** The detector scans for ii-V-I: a minor 7th chord, then a dominant a fourth up, then a chord a
fourth up again (`Dm7 G7 Cmaj7`: D to G to C). Found, so every chord in it gets +1.5 for the key of the I chord (C major).

Scores for the three chords (columns are Dm7, G7, Cmaj7):

```
C major   3.10  3.10  3.85   total 10.05
D minor   1.26  0.75  0.75   total  2.76
```

C major wins by a mile, so the whole tune is one region: `C major, measures 1-3, "ii-V-I in measures 1-3"`.

### Stage 2: chord function

Each chord is described by how far its root is above the tonic. G is 7 semitones above C, so G7 is `V7`.
The rules run in order and the first that explains the chord wins:

1. secondary dominant? (a dominant that is not the V and resolves down a fifth) no
2. diatonic? (a chord that belongs to the key) yes: a dominant 7 on the 5th degree is `V7`, function **dominant**

Dm7 is `ii7` (subdominant) and Cmaj7 is `Imaj7` (tonic) the same way.

### Stage 3: scale suggestion

`scale-rules.json` is a list of rules. The analyzer tries them top to bottom and the first match wins.
For G7 (function dominant, resolving to a major chord):

```
dominant-altered?            needs alt chord             no
dominant-just-flat9?         needs exactly a b9          no
dominant-flat9-sharp11?      needs b9 and #11            no
dominant-sharp11?            needs #11                   no
tritone-substitution?        needs that function         no
dominant-resolving-to-minor? needs resolution = MINOR    no (it resolves to Cmaj7)
dominant-flat13? ... dominant-flat9?                     no
dominant-as-V?               dominant function           YES -> Mixolydian, then Altered
```

Each scale is spelled from the chord's root: G Mixolydian = G A B C D E F.

## Example B: `Dm7 | A7 | Dm7`, key signature C major

This one shows why the key is not just "whatever the first chord is".

There is no ii-V-I here (Dm7 to A7 goes up a fifth, not a fourth). So the scores come only from fit and the bonuses:

```
              Dm7   A7    Dm7
C major      1.60  1.35  1.60   total 4.55
D minor      1.26  1.00  1.75   total 4.01
```

- Dm7 in C major: fit 1.0 + 0.6 for matching the key signature = 1.60.
- A7 (A C# E G) in C major: C# is not in C major, so 3 of 4 notes fit = 0.75, + 0.6 = 1.35.
- D minor fits every chord perfectly, and Dm7 is its tonic (+0.25, and +0.5 on the last chord): 4.01.

C major wins, 4.55 to 4.01, because the key signature said C major and A7 only needs one chromatic note to
be explained as a **secondary dominant** (V7/ii: the dominant of the ii chord).

Remove the key signature and the answer flips to D minor (2.75 vs 4.01). That is the right behaviour: the key
signature is evidence, not an order. Confidence for the C major reading is only 0.55 because there is no
cadence and one chord is not diatonic. The analyzer tells you it is not sure.

Stage 2 for A7: it is a dominant, it is not the V, and the next chord (Dm7) is a fifth below it, so it is a
**secondary dominant** named `V7/ii`. Stage 3: resolving to a *minor* chord, so the rule
`dominant-resolving-to-minor` wins: **A Phrygian dominant** (A Bb C# D E F G), then Altered, then Mixolydian b13.

## Example C: why there is a "switch penalty" (Autumn Leaves, G minor)

The first 8 bars: `Cm7 | F7 | Bbmaj7 | Ebmaj7 | Am7b5 | D7 | Gm | Gm`. Scores per chord for two candidate keys:

```
Bb major  2.75  2.75  3.00  2.00  1.25  1.00  1.25  1.25   total 15.25
G minor   1.60  1.60  1.60  1.60  2.60  2.60  2.85  2.85   total 17.30
```

Bb major scores high on bars 1-4 because of the ii-V-I (Cm7 F7 Bbmaj7). G minor scores high on bars 5-8
because of the ii-V-i (Am7b5 D7 Gm). A single key for all 8 bars would score at best 17.30.

Switching key halfway: 10.50 (Bb for bars 1-4) + 10.90 (G minor for bars 5-8) - 1.5 (the switch penalty) = 19.90.
That beats 17.30, so the detector reports two regions, Bb major then G minor, which is exactly how a jazz
musician hears this tune.

The penalty is what stops the answer flickering. Without it, one odd chord would flip the key and flip back.
I tested values from 0.5 to 4.0: all key-detection tests pass for 1.0 to 2.0 and fail outside that range, so
1.5 is in the middle. Too low and a one-chord detour becomes a "modulation"; too high and a four-bar key centre
is swallowed by the key signature.

The dynamic-programming step is the algorithm that picks one key per chord to maximise the total score minus
penalties, without trying all 24^n combinations. It keeps, for each chord and each key, the best total so far.

## Known limits (be honest about these in the README and in interviews)

- Rule-based: no learning. It knows the patterns it was given (ii-V-I, tritone ii-bII7-I, diatonic chords,
  secondary dominants, borrowed chords). Anything else is labelled `UNCLASSIFIED`.
- Confidence is a heuristic number, not a probability.
- A very short key centre (a lone ii-V-I that is not followed by anything in the new key) can be read as a
  passing tonicisation, not a modulation. That is a judgement call baked into the penalty.
