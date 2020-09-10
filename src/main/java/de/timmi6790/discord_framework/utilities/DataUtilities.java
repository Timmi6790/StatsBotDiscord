package de.timmi6790.discord_framework.utilities;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.ricecode.similarity.LevenshteinDistanceStrategy;
import net.ricecode.similarity.SimilarityStrategy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Data utilities.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DataUtilities {
    private static final SimilarityStrategy SIMILARITY_STRATEGY = new LevenshteinDistanceStrategy();

    /**
     * Returns values that are similar to the given source.
     * The levenshtein distance strategy is used to compare all values.
     *
     * @param source      source to match against values
     * @param values      values to check
     * @param minimumRate minimum inclusive similarity rate
     * @param limit       limit of returned elements
     * @return similar values
     */
    public static List<String> getSimilarityList(final String source, final Collection<String> values, final double minimumRate, final int limit) {
        return getSimilarityList(source, values, String::toString, minimumRate, limit);
    }

    /**
     * Returns values that are similar to the given source.
     * The levenshtein distance strategy is used to compare all values.
     *
     * @param <T>         type parameter
     * @param source      source to match against values
     * @param values      values to check
     * @param toString    value to string function
     * @param minimumRate minimum inclusive similarity rate
     * @param limit       limit of returned elements
     * @return similar values
     */
    public static <T> List<T> getSimilarityList(final String source, final Collection<T> values, final Function<T, String> toString, final double minimumRate, final int limit) {
        if (1 > limit) {
            return new ArrayList<>();
        }

        final String sourceLower = source.toLowerCase();
        final Multimap<Double, T> sortedMap = MultimapBuilder.
                treeKeys(Collections.reverseOrder())
                .arrayListValues()
                .build();
        for (final T target : values) {
            final double value = SIMILARITY_STRATEGY.score(sourceLower, toString.apply(target).toLowerCase());
            if (value >= minimumRate) {
                sortedMap.put(value, target);
            }
        }

        return sortedMap.values()
                .stream()
                .limit(limit)
                .collect(Collectors.toList());
    }
}
