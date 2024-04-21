package com.example.app_psi.network;

import androidx.annotation.NonNull;

public class PriorityRunnable implements Runnable, Comparable<PriorityRunnable> {
    private final int priority;
    private final Runnable task;

    public PriorityRunnable(int priority, Runnable task) {
        this.priority = priority;
        this.task = task;
    }

    @Override
    public int compareTo(@NonNull PriorityRunnable o) {
        // Más prioridad, se ejecuta antes, es el método que ejecutará la cola de prioridad
        return Integer.compare(o.priority, this.priority);
    }

    @Override
    public void run() {
        task.run();
    }
}