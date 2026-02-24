package controller.budgetTab;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import entities.Transaction;

/**
 * Erkennt wiederkehrende Muster in Transaktionslisten.
 * Gruppiert ähnliche Transaktionen (Payee + Betrag) und analysiert Datum-Patterns.
 */
public class RecurringPatternDetector {

    private static final int MIN_OCCURRENCES_DEFAULT = 3;
    private static final double PAYEE_SIMILARITY_THRESHOLD = 0.75;
    private static final double AMOUNT_VARIANCE_THRESHOLD = 0.15; // ±15%

    // ============== RESULT RECORD ==============

    public record RecurringCandidate(
        String normalizedPayee,          // "REWE" (ohne Filial-Nr.)
        String displayPayee,             // Erster Original-Payee für Anzeige
        Long categoryId,                 // Häufigste Kategorie
        int avgAmountCents,
        int minAmountCents,
        int maxAmountCents,
        Transaction.RecurringType suggestedType,
        int suggestedValue,              // z.B. Tag 15 oder Intervall 7
        DayOfWeek suggestedDayOfWeek,    // für WEEKLY
        List<Long> transactionIds,       // Betroffene Transaktionen
        double confidenceScore           // 0.0-1.0
    ) {}

    // ============== MAIN ENTRY POINT ==============

    /**
     * Analysiert Transaktionen und findet wiederkehrende Muster.
     *
     * @param transactions Alle Transaktionen eines Kontos (chronologisch)
     * @param minOccurrences Mindestanzahl für Pattern (default: 3)
     * @return Liste von Kandidaten, sortiert nach Confidence absteigend
     */
    public static List<RecurringCandidate> detectPatterns(
            List<Transaction> transactions, int minOccurrences) {

        if (transactions == null || transactions.isEmpty()) {
            return new ArrayList<>();
        }

        // Nur nicht-recurring, nicht-predicted Transaktionen (echte Buchungen)
        List<Transaction> eligibleTx = transactions.stream()
            .filter(tx -> !tx.isRecurring)
            .filter(tx -> !tx.isPredicted)
            .filter(tx -> tx.parentRecurringId == null)  // Noch nicht verknüpft
            .filter(tx -> tx.payee != null && !tx.payee.trim().isEmpty())
            .collect(Collectors.toList());

        if (eligibleTx.size() < minOccurrences) {
            return new ArrayList<>();
        }

        // 1. Gruppiere nach normalisiertem Payee
        Map<String, List<Transaction>> groups = new HashMap<>();
        for (Transaction tx : eligibleTx) {
            String normalized = normalizePayee(tx.payee);
            if (normalized.isEmpty()) continue;

            // Fuzzy-Match gegen existierende Gruppen
            String matchedKey = findMatchingGroup(normalized, groups.keySet());
            if (matchedKey != null) {
                groups.get(matchedKey).add(tx);
            } else {
                groups.computeIfAbsent(normalized, k -> new ArrayList<>()).add(tx);
            }
        }

        // 2. Filtere Gruppen mit zu wenigen Transaktionen
        List<RecurringCandidate> candidates = new ArrayList<>();

        for (Map.Entry<String, List<Transaction>> entry : groups.entrySet()) {
            List<Transaction> txList = entry.getValue();
            if (txList.size() < minOccurrences) continue;

            // Nach Datum sortieren
            txList.sort(Comparator.comparing(tx -> tx.transactionDate));

            // 3. Betrag-Konsistenz prüfen (alle ähnlicher Betrag?)
            if (!hasConsistentAmounts(txList)) continue;

            // 4. Datum-Pattern erkennen
            RecurringCandidate candidate = analyzePattern(entry.getKey(), txList);
            if (candidate != null && candidate.suggestedType() != null) {
                candidates.add(candidate);
            }
        }

        // Sortiere nach Confidence absteigend
        candidates.sort((a, b) -> Double.compare(b.confidenceScore(), a.confidenceScore()));

        return candidates;
    }

    /**
     * Convenience-Überladung mit Default-minOccurrences.
     */
    public static List<RecurringCandidate> detectPatterns(List<Transaction> transactions) {
        return detectPatterns(transactions, MIN_OCCURRENCES_DEFAULT);
    }

    // ============== PAYEE NORMALIZATION ==============

    /**
     * Normalisiert Payee-Namen: Großbuchstaben, Nummern/Sonderzeichen entfernen.
     */
    public static String normalizePayee(String payee) {
        if (payee == null) return "";
        return payee
            .toUpperCase()
            .replaceAll("[0-9#*]+", "")           // Nummern und Sonderzeichen
            .replaceAll("[^A-ZÄÖÜ\\s]", " ")      // Andere Sonderzeichen → Leerzeichen
            .replaceAll("\\s+", " ")               // Multiple Spaces
            .trim();
    }

    /**
     * Fuzzy-Matching für Payee-Namen mittels Levenshtein-Ähnlichkeit.
     * @return Similarity 0.0-1.0
     */
    public static double payeeSimilarity(String a, String b) {
        if (a == null || b == null) return 0.0;
        if (a.equals(b)) return 1.0;

        int distance = levenshteinDistance(a, b);
        int maxLength = Math.max(a.length(), b.length());
        if (maxLength == 0) return 1.0;

        return 1.0 - ((double) distance / maxLength);
    }

    /**
     * Levenshtein-Distanz zwischen zwei Strings.
     */
    private static int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            for (int j = 0; j <= b.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                    dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                    );
                }
            }
        }
        return dp[a.length()][b.length()];
    }

    /**
     * Findet einen passenden Gruppen-Schlüssel via Fuzzy-Match.
     */
    private static String findMatchingGroup(String normalized, java.util.Set<String> keys) {
        for (String key : keys) {
            if (payeeSimilarity(normalized, key) >= PAYEE_SIMILARITY_THRESHOLD) {
                return key;
            }
        }
        return null;
    }

    // ============== PATTERN ANALYSIS ==============

    /**
     * Analysiert eine Gruppe von Transaktionen und erkennt das Pattern.
     */
    private static RecurringCandidate analyzePattern(String normalizedPayee,
                                                      List<Transaction> txList) {
        // Statistiken sammeln
        int sumAmounts = 0;
        int minAmount = Integer.MAX_VALUE;
        int maxAmount = Integer.MIN_VALUE;
        Map<Long, Integer> categoryCounts = new HashMap<>();
        List<Long> txIds = new ArrayList<>();
        String displayPayee = txList.get(0).payee; // Erster für Anzeige

        for (Transaction tx : txList) {
            int absAmount = Math.abs(tx.amountCents);
            sumAmounts += absAmount;
            minAmount = Math.min(minAmount, absAmount);
            maxAmount = Math.max(maxAmount, absAmount);
            txIds.add(tx.id);

            if (tx.categoryId != null) {
                categoryCounts.merge(tx.categoryId, 1, Integer::sum);
            }
        }

        int avgAmount = sumAmounts / txList.size();

        // Häufigste Kategorie
        Long categoryId = categoryCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);

        // Vorzeichen beibehalten (Ausgaben sind negativ)
        if (txList.get(0).amountCents < 0) {
            avgAmount = -avgAmount;
            int temp = minAmount;
            minAmount = -maxAmount;
            maxAmount = -temp;
        }

        // Pattern erkennen
        PatternResult pattern = detectDatePattern(txList);

        if (pattern == null) {
            return null;
        }

        // Confidence berechnen
        double confidence = calculateConfidence(txList, pattern, avgAmount, minAmount, maxAmount);

        return new RecurringCandidate(
            normalizedPayee,
            displayPayee,
            categoryId,
            avgAmount,
            minAmount,
            maxAmount,
            pattern.type,
            pattern.value,
            pattern.dayOfWeek,
            txIds,
            confidence
        );
    }

    private record PatternResult(
        Transaction.RecurringType type,
        int value,
        DayOfWeek dayOfWeek
    ) {}

    /**
     * Erkennt das Datum-Pattern einer Transaktions-Gruppe.
     */
    private static PatternResult detectDatePattern(List<Transaction> txList) {
        if (txList.size() < 2) return null;

        // Daten extrahieren
        List<LocalDate> dates = txList.stream()
            .map(tx -> tx.transactionDate)
            .sorted()
            .collect(Collectors.toList());

        // 1. Prüfe MONTHLY_DAY: Immer gleicher Tag des Monats (±2 Tage Toleranz)
        PatternResult monthlyDay = checkMonthlyDay(dates);
        if (monthlyDay != null) return monthlyDay;

        // 2. Prüfe MONTHLY_LAST: Immer am Monatsende (letzte 3 Tage)
        PatternResult monthlyLast = checkMonthlyLast(dates);
        if (monthlyLast != null) return monthlyLast;

        // 3. Prüfe WEEKLY: Immer gleicher Wochentag
        PatternResult weekly = checkWeekly(dates);
        if (weekly != null) return weekly;

        // 4. Prüfe INTERVAL: Festes Intervall (±2 Tage Toleranz)
        PatternResult interval = checkInterval(dates);
        if (interval != null) return interval;

        return null;
    }

    private static PatternResult checkMonthlyDay(List<LocalDate> dates) {
        List<Integer> daysOfMonth = dates.stream()
            .map(LocalDate::getDayOfMonth)
            .collect(Collectors.toList());

        // Mode (häufigster Wert) berechnen
        int mode = mode(daysOfMonth);

        // Prüfe ob alle Tage innerhalb ±2 vom Mode liegen
        boolean allMatch = daysOfMonth.stream()
            .allMatch(d -> Math.abs(d - mode) <= 2 ||
                          // Spezialfall: Monatsende-Wrap (28→1)
                          (mode >= 28 && d <= 3) ||
                          (d >= 28 && mode <= 3));

        if (allMatch) {
            return new PatternResult(Transaction.RecurringType.MONTHLY_DAY, mode, null);
        }
        return null;
    }

    private static PatternResult checkMonthlyLast(List<LocalDate> dates) {
        // Prüfe ob alle Transaktionen in den letzten 3 Tagen des Monats sind
        boolean allLast = dates.stream().allMatch(d -> {
            int lastDay = d.lengthOfMonth();
            return d.getDayOfMonth() >= lastDay - 2;
        });

        if (allLast) {
            return new PatternResult(Transaction.RecurringType.MONTHLY_LAST, 0, null);
        }
        return null;
    }

    private static PatternResult checkWeekly(List<LocalDate> dates) {
        List<DayOfWeek> weekdays = dates.stream()
            .map(LocalDate::getDayOfWeek)
            .collect(Collectors.toList());

        // Mode berechnen
        Map<DayOfWeek, Long> counts = weekdays.stream()
            .collect(Collectors.groupingBy(d -> d, Collectors.counting()));

        DayOfWeek mode = counts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);

        // Prüfe ob mindestens 80% auf denselben Tag fallen
        long modeCount = counts.getOrDefault(mode, 0L);
        if (modeCount >= dates.size() * 0.8) {
            // Zusätzlich: Intervalle prüfen (sollten ~7 Tage sein)
            List<Long> intervals = calculateIntervals(dates);
            double avgInterval = intervals.stream().mapToLong(Long::longValue).average().orElse(0);

            if (avgInterval >= 5 && avgInterval <= 9) {
                return new PatternResult(Transaction.RecurringType.WEEKLY, 0, mode);
            }
        }
        return null;
    }

    private static PatternResult checkInterval(List<LocalDate> dates) {
        List<Long> intervals = calculateIntervals(dates);
        if (intervals.isEmpty()) return null;

        // Durchschnitt und Varianz
        double avgInterval = intervals.stream().mapToLong(Long::longValue).average().orElse(0);

        // Prüfe ob alle Intervalle innerhalb ±20% vom Durchschnitt liegen
        boolean consistent = intervals.stream()
            .allMatch(i -> Math.abs(i - avgInterval) <= avgInterval * 0.2 + 2);

        if (consistent && avgInterval >= 3) { // Mindestens 3 Tage Intervall
            int intervalDays = (int) Math.round(avgInterval);
            return new PatternResult(Transaction.RecurringType.INTERVAL, intervalDays, null);
        }
        return null;
    }

    // ============== HELPER METHODS ==============

    private static boolean hasConsistentAmounts(List<Transaction> txList) {
        if (txList.size() < 2) return true;

        List<Integer> amounts = txList.stream()
            .map(tx -> Math.abs(tx.amountCents))
            .collect(Collectors.toList());

        int avg = amounts.stream().mapToInt(Integer::intValue).sum() / amounts.size();
        if (avg == 0) return false;

        // Alle innerhalb ±15% vom Durchschnitt?
        return amounts.stream().allMatch(a ->
            Math.abs(a - avg) <= avg * AMOUNT_VARIANCE_THRESHOLD);
    }

    private static List<Long> calculateIntervals(List<LocalDate> dates) {
        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < dates.size(); i++) {
            intervals.add(ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i)));
        }
        return intervals;
    }

    private static int mode(List<Integer> values) {
        Map<Integer, Long> counts = values.stream()
            .collect(Collectors.groupingBy(v -> v, Collectors.counting()));
        return counts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(values.get(0));
    }

    private static double calculateConfidence(List<Transaction> txList,
                                               PatternResult pattern,
                                               int avgAmount, int minAmount, int maxAmount) {
        double score = 0.0;

        // Mehr Transaktionen = höhere Confidence (max 0.3)
        score += Math.min(txList.size() / 10.0, 0.3);

        // Konsistenter Betrag = höhere Confidence (max 0.3)
        if (avgAmount != 0) {
            double amountVariance = Math.abs(maxAmount - minAmount) / (double) Math.abs(avgAmount);
            score += Math.max(0.3 - amountVariance, 0);
        }

        // Erkanntes Pattern = Basis-Confidence
        if (pattern.type != null) {
            score += 0.3;
        }

        // Bekannte Abo-Payees = Bonus (0.1)
        // (Vereinfachte Erkennung über häufige Muster)
        String normalized = txList.get(0).payee != null ?
            normalizePayee(txList.get(0).payee) : "";
        if (isKnownSubscription(normalized)) {
            score += 0.1;
        }

        return Math.min(score, 1.0);
    }

    /**
     * Prüft ob ein Payee ein bekanntes Abo/Recurring-Muster ist.
     */
    private static boolean isKnownSubscription(String normalizedPayee) {
        String[] knownPatterns = {
            "NETFLIX", "SPOTIFY", "AMAZON PRIME", "DISNEY", "APPLE",
            "GOOGLE", "MICROSOFT", "ADOBE", "DROPBOX", "ZOOM",
            "GYM", "FITNESS", "STUDIO", "TELEKOM", "VODAFONE",
            "O2", "VERSICHERUNG", "INSURANCE", "RUNDFUNK", "GEZ"
        };

        for (String pattern : knownPatterns) {
            if (normalizedPayee.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}
