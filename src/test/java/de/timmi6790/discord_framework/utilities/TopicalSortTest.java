package de.timmi6790.discord_framework.utilities;

import de.timmi6790.discord_framework.exceptions.TopicalSortCycleException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TopicalSortTest {
    @Test
    void simplePathSort() throws TopicalSortCycleException {
        final List<Integer> values = Arrays.asList(10, 2, 5, 1, 4, 3, 8, 6, 7, 9);
        final List<TopicalSort.Dependency> dependencies = Arrays.asList(
                new TopicalSort.Dependency(0, 9),
                new TopicalSort.Dependency(9, 6),
                new TopicalSort.Dependency(6, 8),
                new TopicalSort.Dependency(8, 7),
                new TopicalSort.Dependency(7, 2),
                new TopicalSort.Dependency(2, 4),
                new TopicalSort.Dependency(4, 5),
                new TopicalSort.Dependency(5, 1),
                new TopicalSort.Dependency(1, 3)
        );

        final TopicalSort<Integer> topicalSort = new TopicalSort<>(values, dependencies);
        final List<Integer> sortedList = topicalSort.sort();

        final List<Integer> controlList = IntStream.range(1, 11)
                .boxed()
                .collect(Collectors.toList());
        assertThat(sortedList).isEqualTo(controlList);
    }

    @Test
    void simpleLoopDetectionCheck() {
        final List<Integer> values = Arrays.asList(1, 2);
        final List<TopicalSort.Dependency> dependencies = Arrays.asList(
                new TopicalSort.Dependency(0, 1),
                new TopicalSort.Dependency(1, 0)
        );

        final TopicalSort<Integer> topicalSort = new TopicalSort<>(values, dependencies);
        assertThrows(TopicalSortCycleException.class, topicalSort::sort);
    }
}