package com.multiverse.mod;

import java.util.ArrayList;
import java.util.List;

/** Tiny tick-based scheduler so cutscenes can run their later beats on the server. */
public final class ServerTasks {
    private record Task(long at, Runnable run) {}

    private static final List<Task> TASKS = new ArrayList<>();

    private ServerTasks() {}

    public static void schedule(long atTick, Runnable run) {
        TASKS.add(new Task(atTick, run));
    }

    public static void tick(long now) {
        List<Runnable> due = new ArrayList<>();
        TASKS.removeIf(t -> {
            if (t.at() <= now) {
                due.add(t.run());
                return true;
            }
            return false;
        });
        due.forEach(Runnable::run);
    }
}
