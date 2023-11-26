package org.example.tcp;

public class Utils {

    public static long executeAndMeasureTime(Runnable runnable) {
        long currentTime = System.nanoTime();
        runnable.run();
        return System.nanoTime() - currentTime;
    }

}
