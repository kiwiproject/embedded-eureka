package org.kiwiproject.eureka;

import com.netflix.servo.monitor.StatsMonitor;
import com.netflix.servo.util.ThreadFactories;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@UtilityClass
public class EurekaTestHelpers {

    /**
     * THIS IS A TOTAL HACK!!!!!!!
     * <p>
     * The {@link StatsMonitor} contains a {@code protected static final}
     * {@link java.util.concurrent.ScheduledExecutorService} that when Eureka is shut down gets shutdown. There is not
     * a way to "reset" that executor to a new one on each test so we have to do this reflection hack to reset it.
     * <p>
     * Why don't we use Mockito's mockStatic or PowerMock's mockStatic to mock the method in
     * {@link com.netflix.eureka.util.ServoControl} which calls shutdown on the executor? Because...
     * <ul>
     *     <li>Mockito's mockStatic does not support {@code doNothing()} which is needed because the method is void.</li>
     *     <li>PowerMock doesn't support Junit 5 without "tricking" the runner to fall back to Junit 4 which is yuck!</li>
     * </ul>
     */
    public static void resetStatsMonitor() {
        var threadFactory = ThreadFactories.withName("StatsMonitor-%d");
        var poolExecutor = new ScheduledThreadPoolExecutor(1, threadFactory);
        poolExecutor.setRemoveOnCancelPolicy(true);

        try {
            // Get field instance
            var field = StatsMonitor.class.getDeclaredField("DEFAULT_EXECUTOR");
            field.setAccessible(true); // Suppress Java language access checking

            // Remove "final" modifier
            var modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

            // Set value
            field.set(null, poolExecutor);
        } catch (Exception e) {
            throw new RuntimeException("Problem reseting StatsMonitor#DEFAULT_EXECUTOR", e);
        }
    }
}
