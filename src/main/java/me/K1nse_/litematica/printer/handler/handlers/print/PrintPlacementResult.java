package me.K1nse_.litematica.printer.handler.handlers.print;

public record PrintPlacementResult(
        boolean consumedEffectiveExecution,
        boolean skipIteration,
        TaskEvent taskEvent,
        int cooldownTicks
) {
    public static PrintPlacementResult failure(boolean consumedEffectiveExecution, boolean skipIteration) {
        return new PrintPlacementResult(consumedEffectiveExecution, skipIteration, TaskEvent.FAILURE, -1);
    }

    public enum TaskEvent {
        SUCCESS,
        QUEUED,
        FAILURE
    }
}
