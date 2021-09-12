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
     * Resets the static {@link java.util.concurrent.ScheduledExecutorService ScheduledExecutorService} that lives
     * inside Eureka's {@link StatsMonitor} class, and which causes problems during tests when we may need to start and
     * stop Eureka multiple times. By "reset" we mean that the ScheduledExecutorService which has been shut down is
     * replaced (via reflection which makes the protected field accessible and removes its final modifier) with a new
     * instance that (obviously) has not been shut down. Yes, THIS IS A TOTAL HACK!!!!!!! Also, <em>we are pretty sure
     * this will NOT work in Java 17 and beyond, and we don't currently have a solution.</em> Please read on for more
     * details, if you dare.
     * <p>
     * The {@link StatsMonitor} class contains a {@code protected static final}
     * {@link java.util.concurrent.ScheduledExecutorService ScheduledExecutorService} that is initialized once in a
     * static initialization block. This executor is started when Eureka starts and is shut down when Eureka is shut
     * down.
     * <p>
     * Specifically, the executor is scheduled in the constructor of {@link StatsMonitor}. In the Eureka lifecycle, this
     * actually occurs when an instance of the {@link com.netflix.servo.monitor.StatsTimer StatsTimer} (a subclass of
     * {@link StatsMonitor}) is constructed.
     * <p>
     * The executor shutdown occurs in the static {@link com.netflix.eureka.util.ServoControl#shutdown()} method which
     * is called in turn by {@link com.netflix.eureka.DefaultEurekaServerContext#shutdown()} during the Eureka shut
     * down process. Because of this design using a static field shared by multiple instances, there is no way to
     * "reset" that executor to a new one on each test, so we have to do this reflection hack to reset it.
     * <p>
     * Why don't we use Mockito's mockStatic or PowerMock's mockStatic to mock the method in
     * {@link com.netflix.eureka.util.ServoControl} which calls shutdown() on the executor? Because...
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
            throw new RuntimeException("Problem resetting StatsMonitor#DEFAULT_EXECUTOR", e);
        }
    }
}
