package org.jruby.truffle.nodes.profiler;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrument.Instrument;
import com.oracle.truffle.api.nodes.Node;

public final class TimeProfilerInstrument extends Instrument {

    private final Node node;
    private long counter;
    private long startTime;
    private long totalElapsedTime;

    public TimeProfilerInstrument(Node node) {
        this.node = node;
        this.counter = 0;
        this.startTime = 0;
        this.totalElapsedTime = 0;
    }

    @Override
    public void enter(Node astNode, VirtualFrame frame) {
        this.counter++;
        this.startTime = System.nanoTime();
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame) {
        long endTime = System.nanoTime();
        long elapsedTime = endTime - startTime;
        this.totalElapsedTime = this.totalElapsedTime + elapsedTime;
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame, boolean result) {
        leave(astNode, frame);
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame, int result) {
        leave(astNode, frame);
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame, double result) {
        leave(astNode, frame);
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame, Object result) {
        leave(astNode, frame);
    }

    @Override
    public void leaveExceptional(Node astNode, VirtualFrame frame, Exception e) {
        leave(astNode, frame);
    }

    public Node getNode() {
        return node;
    }

    public long getCounter() {
        return counter;
    }

    public long getTime() {
        return totalElapsedTime;
    }

}
